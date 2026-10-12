//! One invocation: the backend selection, the collaborators composed once, and the dispatch to the crate that owns
//! each command. Nothing here decides behaviour a command could decide for itself.

use std::ffi::OsString;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use crate::daemon::{Host, flake, parked_daemon_probe, shard};
use crate::worker::lease::{LeaseCommand, command_lease, receipt_backend};
use crate::worker::worker::{Dependencies, Manager};
use avl_base::config::{Presentation, WORKSPACE_DIR};
use avl_base::report::{self, Mode, Terminal};
use avl_base::{Config, Environment, Exit, Outcome, Refusal, Reporter, Selection};
use avl_host_sys::lock::LockManager;
use avl_host_sys::{Ctx, Interrupts, Runner};
use serde::Serialize;

use crate::cli::{Cmd, Invocation, PROGRAM, Parsed, help, parse};
use crate::image::command_image;
use crate::terminal::{Form, Output, TerminalFacts, screen_for, wants_dashboard};
use crate::viewer::ensure_viewer;
use avl_base::RefusalExt;

/// The pool this invocation runs against, from exactly one source.
///
/// Precedence: the `--backend` flag, then the lease receipt, then [`Selection::DEFAULT`]. The default is the Docker
/// pool on every host, a Linux guest for a measured reason: [`Selection::DEFAULT`] records the measurement and why
/// each host defaults to Docker.
///
/// The two halves are never resolved separately. A flag's backend combined with a receipt's guest OS would be a
/// pair neither source named, and the pool it named might not exist. So a flag and a receipt that disagree on
/// *either* half are a refusal, not a merge: continuing would mean acting on another pool's worker while holding a
/// handle that looks valid.
/// A selection with both of its axes, such as `tart with a macos guest`. A refusal of two selections names both
/// axes, because two selections can have the same backend: a receipt of the retired Tart Linux pool and
/// `--backend tart` are both `tart`.
fn both_axes(selection: Selection) -> String {
    format!("{} with a {} guest", selection.backend, selection.guest_os)
}

pub(crate) fn resolve_selection(flag: Option<Selection>, lease_file: Option<&Path>) -> Result<Selection, Refusal> {
    let inferred = lease_file.and_then(receipt_backend);
    match (flag, inferred) {
        (Some(flag), Some(inferred)) if flag != inferred => Err(Refusal::new(
            "lease_backend_mismatch",
            Exit::NO_PERM,
            format!(
                "lease receipt selects {}, but --backend selected {}",
                both_axes(inferred),
                both_axes(flag)
            ),
        )),
        (Some(flag), _) => Ok(flag),
        (None, Some(inferred)) => Ok(inferred),
        (None, None) => Ok(Selection::DEFAULT),
    }
}

/// The root of the checkout that this controller runs in: the directory that holds `bt.json` and [`WORKSPACE_DIR`].
///
/// `BUILD_WORKSPACE_DIRECTORY` first, because the wrapper exports it and a `bazel run` sets it: it is right by
/// construction rather than by search. Failing that, the working directory and its parents are searched for
/// [`WORKSPACE_DIR`], which covers a binary invoked by hand from inside a checkout.
///
/// It refuses rather than defaulting: a relative `provision/` under whatever the working directory happens to be
/// resolves to a path that exists nowhere and fails much later, at a provisioning script blaming the image
/// pipeline for the controller not knowing where it is.
pub(crate) fn resolve_checkout_root(environment: &Environment) -> Result<PathBuf, Refusal> {
    if let Some(root) = environment.get("BUILD_WORKSPACE_DIRECTORY") {
        return Ok(PathBuf::from(root));
    }
    let unresolved = |message: String| Refusal::new("controller_root_unresolved", Exit::USAGE, message);
    let working = std::env::current_dir().map_err(|error| unresolved(format!("cannot read the working directory: {error}")))?;
    working
        .ancestors()
        .find(|directory| directory.join(WORKSPACE_DIR).is_dir())
        .map(Path::to_path_buf)
        .ok_or_else(|| {
            unresolved(format!(
                "cannot find {WORKSPACE_DIR} above {}; run {PROGRAM}, or set BUILD_WORKSPACE_DIRECTORY to the checkout root",
                working.display()
            ))
        })
}

/// One invocation's collaborators, composed once.
///
/// Composed before the command is known rather than per branch, because every constructor here is bookkeeping - no
/// process, no filesystem, no hypervisor - and a per-branch composition is how two commands end up with two worker
/// managers, two lock managers and therefore two lock identities for one invocation.
struct Controller {
    settings: Arc<Config>,
    runner: Runner,
    bazel: Arc<crate::lane::Bazel>,
    locks: Arc<LockManager>,
    manager: Arc<Manager>,
    host: Arc<Host>,
    reporter: Reporter,
    /// The process's environment, which names the invoking user for a default lease holder.
    environment: Environment,
}

impl Controller {
    fn new(settings: Config, environment: Environment, reporter: Reporter, interrupts: Interrupts) -> Self {
        let settings = Arc::new(settings);
        let runner = Runner::new(environment.pairs(), interrupts);
        // Before the manager, which takes it: the lifecycle owner resolves the guest agent through the same Bazel
        // the lane build and every lease operation do. The same Bazel builds a boot's own outputs, so `pool start`
        // and `pool recycle` never install bytes older than this controller.
        let bazel = crate::lane::Bazel::new(Arc::clone(&settings), runner.clone(), reporter.clone());
        let locks = Arc::new(LockManager::new(runner.clone()));
        let manager = Arc::new(Manager::new(Dependencies {
            settings: Arc::clone(&settings),
            runner: runner.clone(),
            reporter: reporter.clone(),
            locks: Arc::clone(&locks),
            channel: None,
            bazel: Some(bazel.clone()),
            build_guest_boot: bazel.boot_builder(),
        }));
        let host = Arc::new(Host::new(Arc::clone(&manager), runner.clone(), bazel.clone(), environment.clone()));
        Self {
            settings,
            runner,
            bazel,
            locks,
            manager,
            host,
            reporter,
            environment,
        }
    }

    /// Routes one command to the crate that owns it.
    ///
    /// The argument shapes differ per command and each is the owner's: `shard` takes no receipt because it leases
    /// for itself, `exec` and `peekaboo` take the output form because they choose between capturing and inheriting
    /// the terminal, and the read-only observers take neither the runner nor Bazel.
    async fn dispatch(&self, ctx: &Ctx, command: Cmd, lease_file: Option<&Path>, output: Output) -> Result<Outcome, Refusal> {
        let manager = self.manager.as_ref();
        match command {
            Cmd::Image { action } => command_image(ctx, manager, action, output).await,
            Cmd::Pool { verb } => manager.pool(ctx, verb.into()).await,
            Cmd::Status => crate::lane::command_status(ctx, manager).await,
            Cmd::Lease { verb } => {
                let command = match verb {
                    Some(verb) => verb.into_command(&self.environment)?,
                    None => LeaseCommand::Show,
                };
                let probe = parked_daemon_probe(Arc::clone(&self.settings), self.host.daemon().clone());
                command_lease(ctx, manager, &command, lease_file, &probe).await
            }
            Cmd::Suites { paths } => crate::lane::command_suites(ctx, &self.settings, &self.runner, paths, &self.reporter).await,
            Cmd::Run(args) => self.host.command_run(ctx, &args, lease_file).await,
            Cmd::Shard(args) => shard::command_shard(ctx, &self.host, args).await,
            Cmd::Flake(args) => flake::command_flake(ctx, &self.host, args, lease_file).await,
            Cmd::Daemon { verb } => self.host.command_daemon(ctx, verb, lease_file).await,
            Cmd::Exec { command } => crate::lane::command_exec(ctx, manager, command, lease_file, output).await,
            Cmd::Peekaboo(args) => crate::lane::command_peekaboo(ctx, manager, args, lease_file, output).await,
            Cmd::Ls(args) => crate::lane::command_ls(ctx, manager, args, lease_file).await,
            Cmd::Pull { source, destination } => crate::lane::command_pull(ctx, manager, source, destination, lease_file).await,
            Cmd::Vnc => crate::lane::command_vnc(ctx, manager, lease_file).await,
            Cmd::Bench { verb } => {
                let bench = crate::bench::Bench {
                    settings: &self.settings,
                    runner: &self.runner,
                    reporter: &self.reporter,
                    bazel: &self.bazel,
                    locks: &self.locks,
                    environment: &self.environment,
                };
                bench.command(ctx, verb).await
            }
        }
    }
}

/// What `--help` answers in JSON mode: the same text, as data rather than as prose.
#[derive(Serialize)]
struct HelpData<'a> {
    usage: &'a str,
}

/// The output mode a refusal from the parse is written in: before a parse there is no terminal to redraw.
fn plain_mode(form: Form) -> Mode {
    match form.output {
        Output::Json => Mode::Json,
        Output::Text => Mode::Human(Terminal::default()),
    }
}

/// The whole invocation, answering the status the process should leave with.
///
/// `environment` is the process's environment, read once: the settings and the renderer read it, and the runner
/// hands it to children.
/// Every path writes exactly one answer; the status is returned rather than exited with, so every `Drop` (a lock
/// released, a temporary receipt removed) still runs.
pub(crate) async fn run(
    argv: Vec<OsString>,
    environment: Environment,
    reporter: Reporter,
    facts: TerminalFacts,
    interrupts: Interrupts,
) -> Exit {
    let invocation = match parse(&argv, &facts) {
        Parsed::Invocation(invocation) => invocation,
        Parsed::Help { form, text } => {
            reporter.set_mode(plain_mode(form));
            return report::main("help", &reporter, || Outcome::new(HelpData { usage: &text }, text.as_str()));
        }
        Parsed::Refused { form, command, refusal } => {
            reporter.set_mode(plain_mode(form));
            return reporter.refuse(command.as_deref().unwrap_or(""), &refusal);
        }
    };
    let Invocation {
        form,
        stream,
        selection,
        lease_file,
        command,
    } = invocation;
    let variables = environment;
    let presentation = Presentation::load(&variables);
    let screen = screen_for(&facts, form, &variables);
    reporter.set_mode(match (form.output, stream) {
        (Output::Json, true) => Mode::Stream,
        (Output::Json, false) => Mode::Json,
        (Output::Text, _) => Mode::Human(screen),
    });
    let Some(command) = command else {
        // `command: null`: no command was chosen, which is a different fact from a command named "".
        return reporter.refuse("", &Refusal::usage(help()));
    };
    if let Some(factory) = facts.new_dashboard.as_ref()
        && wants_dashboard(&facts, screen, stream, command.is_run(), &presentation)
    {
        reporter.set_renderer(factory(screen, &variables, presentation.theme));
    }
    let name = command.name();
    let result = async {
        let selection = resolve_selection(selection, lease_file.as_deref())?;
        let root = resolve_checkout_root(&variables)?;
        let settings = Config::load(selection, &variables, &root.join(WORKSPACE_DIR))?;
        let ctx = interrupts.context();
        let controller = Controller::new(settings, variables.clone(), reporter.clone(), interrupts.clone());
        if form.output == Output::Text && command.is_run() {
            ensure_viewer(
                &controller.settings.runtime_root,
                &presentation,
                &variables,
                &controller.runner,
                &reporter,
            )
            .await;
        }
        controller.dispatch(&ctx, command, lease_file.as_deref(), form.output).await
    }
    .await;
    report::main(name, &reporter, || result)
}
