//! The `vm` controller: the command line, the dashboard, the runs, the lanes and the workers.
//!
//! The crate root parses the command line, decides the output form and the renderer from the process's descriptors,
//! composes one invocation's collaborators, and routes the command to the module that owns it. The crates below it
//! hold the code that another binary shares (`avl-base`, `avl-host-sys`, `avl-affected` and the contracts) and the
//! frozen bytes of the agent-facing report (`avl-report`). It builds for a macOS, a Linux and a Windows host. A
//! Windows host drives the Docker backend only (ADR 0186).
//!
//! - [`cli`]: the clap tree, which renders the help, and how a parse becomes an invocation or a refusal;
//! - [`terminal`]: the output form, the footer and the dashboard rules;
//! - [`controller`]: the backend selection, the composition and the dispatch;
//! - [`image`], [`viewer`]: the golden-image delegate and the detached trace viewer;
//! - [`console`]: the live terminal dashboard of `run`, `shard` and `flake`;
//! - [`daemon`]: `run`, `shard`, `flake` and `daemon`, and the leased-run driver the first three share;
//! - [`lane`]: lane and selector resolution, `suites`, the host Bazel build, the guest launch environment, and the
//!   commands that observe a leased worker;
//! - [`worker`]: the Tart, Parallels and Docker backends, the Lima engine of a Docker pool on a Mac, readiness gates,
//!   the pool, and leases;
//! - [`bench`]: the start-up measurement of the IDE on this host, from a staged copy of its dev distribution.

mod bench;
mod cli;
mod console;
mod controller;
mod daemon;
mod image;
mod lane;
mod terminal;
mod viewer;
mod worker;

/// The command line of the controller as its documents spell it. The test of the ultimate root that parses every
/// example command of the docs uses it: `//plugins/air/tests/integration/vm-contract:examples-test`.
pub mod examples {
    use std::ffi::OsString;

    use crate::cli::{Parsed, parse};
    use crate::terminal::TerminalFacts;

    /// How the documents spell the controller: the wrapper, by its repository-relative path.
    pub const PROGRAM: &str = crate::cli::PROGRAM;

    /// Parses `argv`, the arguments after [`PROGRAM`], as the controller does. Call
    /// `avl_affected::bridge::install` first, because the help lists the lanes of the Air area.
    ///
    /// # Errors
    ///
    /// The message of the refusal when the controller refuses the command line.
    pub fn parses(argv: &[&str]) -> Result<(), String> {
        let argv: Vec<OsString> = argv.iter().map(OsString::from).collect();
        match parse(&argv, &TerminalFacts::default()) {
            Parsed::Refused { refusal, .. } => Err(refusal.message),
            Parsed::Invocation(_) | Parsed::Help { .. } => Ok(()),
        }
    }
}

use std::ffi::OsString;
use std::io::Write;

use crate::console::{ColorChoice, Dashboard, Options};
use avl_base::report::Renderer;
use avl_base::{Environment, Exit, Refusal, Reporter};
use avl_host_sys::Interrupts;

use terminal::TerminalFacts;
use terminal::{colors_for, query_terminal_colors};

/// Runs `vm`, the whole program behind `main`: `args` are the arguments after the program name, and the answer goes
/// to `stdout` and `stderr`. It builds the async runtime and answers the exit code. `vm` reads no standard input: a
/// guest shell or a VNC helper inherits the process's own descriptors.
///
/// The terminal facts are the process's own, so the form of the output and the live dashboard follow the process's
/// descriptors. A test that needs other facts drives [`controller::run`].
pub fn run(args: impl IntoIterator<Item = OsString>, stdout: impl Write + Send + 'static, stderr: impl Write + Send + 'static) -> u8 {
    let reporter = Reporter::new(stdout, stderr, avl_base::report::program_name());
    let runtime = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
        Ok(runtime) => runtime,
        Err(error) => {
            let refused = Refusal::new(
                "runtime_unavailable",
                Exit::SOFTWARE,
                format!("cannot start the async runtime: {error}"),
            );
            return reporter.refuse("", &refused).code();
        }
    };
    let exit = runtime.block_on(run_process(args.into_iter().collect(), reporter));
    // The answer is written. A task still in flight (a probe, a reaper) must not hold the process open.
    runtime.shutdown_background();
    exit.code()
}

/// The process: the values a running process has that a test cannot be given - its argv, its environment, its
/// descriptors and whether they are terminals - handed to [`controller::run`], which a test drives with all of them
/// supplied.
///
/// Must run inside the tokio runtime that [`run`] built: the interrupt service's listener is a task of it.
async fn run_process(argv: Vec<OsString>, reporter: Reporter) -> Exit {
    let interrupts = match Interrupts::install() {
        Ok(interrupts) => interrupts,
        Err(refusal) => return reporter.refuse("", &refusal),
    };
    // Read once: the settings, the renderer and every child's environment are this one value.
    let environment = Environment::from_os();
    // Before the parse, because the help lists the lanes of the Air area.
    if let Err(refusal) = controller::resolve_checkout_root(&environment).and_then(|root| avl_affected::bridge::install(&root)) {
        return reporter.refuse("", &refusal);
    }
    let facts = TerminalFacts {
        new_dashboard: Some(Box::new(|screen, variables, theme| -> Box<dyn Renderer> {
            // The one query of the terminal, before the first frame, and only for a dashboard in colour.
            let colors = if screen.color {
                colors_for(variables, theme, query_terminal_colors)
            } else {
                None
            };
            Box::new(Dashboard::start(
                Box::new(std::io::stderr()),
                Options {
                    rerun_prefix: cli::PROGRAM.to_owned(),
                    width: screen.width,
                    color: if screen.color { ColorChoice::Auto } else { ColorChoice::Never },
                    colors,
                    no_color: variables.get("NO_COLOR").map(str::to_owned),
                    term: variables.get("TERM").map(str::to_owned),
                    clock: None,
                    follow_stderr_size: true,
                },
            ))
        })),
        ..TerminalFacts::detect()
    };
    controller::run(argv, environment, reporter, facts, interrupts).await
}
