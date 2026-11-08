//! `image validate|build`: the golden-image pipeline's shell scripts, for the one pool that has an image.

mod ssh_relay;

use std::io;
use std::path::{Path, PathBuf};
use std::time::Duration;

use crate::terminal::Output;
use crate::worker::hypervisor::unsupported;
use crate::worker::worker::Manager;
use avl_base::RefusalExt;
use avl_base::config::pins;
use avl_base::{Backend, Exit, GuestOs, OrRefuse, Outcome, Refusal};
use avl_host_sys::{Ctx, GuestStream, Runner, SpawnOptions};
use serde::Serialize;
use ssh_relay::{GUEST_SSH_PORT, SshRelay};

/// The timeout of a delegate in JSON mode. `build-golden.sh` is a long Packer run that installs macOS from its
/// restore image, so the bound is far above any build and stops only a delegate that never ends.
const DELEGATE_TIMEOUT: Duration = Duration::from_hours(4);

/// What `image` does.
#[derive(clap::ValueEnum, Clone, Copy, Debug, Default, PartialEq, Eq)]
pub(crate) enum ImageAction {
    /// The offline audit of the sealed golden image.
    #[default]
    Validate,
    /// The Packer build of a new golden image.
    Build,
}

impl ImageAction {
    const fn script(self) -> &'static str {
        match self {
            Self::Validate => "static-validate.sh",
            Self::Build => "build-golden.sh",
        }
    }
}

/// What a captured delegate said, carried in the envelope either way.
///
/// Reported on success as well as on failure, and truncation is a flag rather than a silent cut: a caller reading
/// a delegate's output has to be able to tell a short answer from a cut one.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DelegateProgress {
    stdout: String,
    stderr: String,
    stdout_truncated: bool,
    stderr_truncated: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DelegateData {
    delegate: PathBuf,
    exit_code: i32,
    #[serde(skip_serializing_if = "Option::is_none")]
    progress: Option<DelegateProgress>,
}

/// Validates or builds the golden image, for the one pool that has one.
///
/// The scripts and the Packer Tart plugin call `tart` from `PATH`, so the controller passes the Tart gate first and
/// puts the directory of the Tart it resolved first on the scripts' `PATH`.
///
/// A build names its candidate VM here and serves Packer's SSH to it over the exec channel for as long as the
/// delegate runs, see [`ssh_relay`]: `build-golden.sh` reads both from `AIR_IMAGE_CANDIDATE_VM` and
/// `AIR_IMAGE_SSH_PROXY_PORT`.
pub(crate) async fn command_image(ctx: &Ctx, manager: &Manager, action: ImageAction, output: Output) -> Result<Outcome, Refusal> {
    let settings = manager.settings();
    if settings.backend == Backend::Parallels {
        return Err(unsupported(
            "the Parallels backend uses one pre-existing VM and has no image operations",
        ));
    }
    if settings.guest_os == GuestOs::Linux {
        // The digest-pinned Packer golden, its seal and its offline audit exist to make a *macOS* image
        // trustworthy. Neither Linux pool has one: a Docker worker runs an image its first start builds from this
        // checkout, tagged by a digest of what it is built from, and a container-linux worker runs the image of the
        // testing-ui skill. The default pool is a Linux guest, so an unqualified `image` would otherwise run the
        // macOS pipeline for someone who never asked for it.
        return Err(unsupported(
            "a Linux pool has no golden image to validate or build: the docker pool builds its image from \
             community/tools/vm/docker/Dockerfile on the first pool start or lease, and the container-linux pool \
             runs the image of the testing-ui skill. Use --backend tart for the sealed macOS image pipeline",
        ));
    }
    manager.machine().require_available(ctx, "").await?;
    let runner = manager.runner();
    let inherited = runner
        .environment()
        .into_iter()
        .find_map(|(name, value)| (name == "PATH" && !value.is_empty()).then_some(value));
    #[cfg(unix)]
    let tart_dir = manager
        .tart_executable()
        .and_then(Path::parent)
        .map(|directory| directory.to_string_lossy().into_owned());
    // A Windows host has no Tart backend: `Config::load` refuses the macOS pool there, so no pipeline runs.
    #[cfg(windows)]
    let tart_dir: Option<String> = None;
    let path = [tart_dir, inherited].into_iter().flatten().collect::<Vec<_>>().join(":");
    let script = settings.image_root.join("scripts").join(action.script());
    if action == ImageAction::Validate {
        return run_delegate(ctx, &runner.with_overrides(&[("PATH", &path)]), &script, output).await;
    }
    let candidate = candidate_vm_name();
    let nc = ["/usr/bin/nc", "127.0.0.1", &GUEST_SSH_PORT.to_string()].map(str::to_owned);
    let line = manager.machine().guest_argv(ctx, &candidate, &nc, true).await?;
    let (relay_ctx, relay_runner) = (ctx.clone(), runner.clone());
    let relay = SshRelay::start(move || {
        let child = relay_runner.spawn_piped(&relay_ctx, &line);
        async move { child.map(GuestStream::from).map_err(|refusal| io::Error::other(refusal.message)) }
    })
    .await
    .or_refuse("ssh_relay_failed", Exit::UNAVAILABLE, || {
        "cannot serve Packer's SSH on a loopback port".to_owned()
    })?;
    let port = relay.port().to_string();
    let overrides = [
        ("PATH", path.as_str()),
        ("AIR_IMAGE_CANDIDATE_VM", candidate.as_str()),
        ("AIR_IMAGE_SSH_PROXY_PORT", port.as_str()),
    ];
    let outcome = run_delegate(ctx, &runner.with_overrides(&overrides), &script, output).await;
    drop(relay);
    outcome
}

/// The name of an unapproved candidate of the golden that `versions.env` names, the name `build-golden.sh` promotes
/// it to. The time and the pid keep two builds apart.
fn candidate_vm_name() -> String {
    format!(
        "{}-candidate-{}-{}",
        pins::tart_golden_vm(),
        jiff::Timestamp::now().strftime("%Y%m%dT%H%M%SZ"),
        std::process::id(),
    )
}

/// Runs one of the image pipeline's shell scripts.
///
/// The delegate's exit status becomes this process's, because the delegate *is* the command here: a caller
/// branching on the result branches on the pipeline's verdict, not on the controller's ability to spawn it.
///
/// JSON mode captures and text mode inherits the terminal. The split is not cosmetic: `build-golden.sh` is a long
/// Packer run whose output is what an operator watches, and a capture would show nothing until it finished.
async fn run_delegate(ctx: &Ctx, runner: &Runner, path: &Path, output: Output) -> Result<Outcome, Refusal> {
    if !path.exists() {
        return Err(Refusal::new(
            "delegate_missing",
            Exit::UNAVAILABLE,
            format!("delegate does not exist: {}", path.display()),
        ));
    }
    let argv = vec![path.to_string_lossy().into_owned()];
    let name = path
        .file_name()
        .map_or_else(String::new, |name| name.to_string_lossy().into_owned());
    let failed = |exit_code: i32| {
        Refusal::new(
            "delegate_failed",
            Exit::from_status(exit_code, Exit::FAILURE),
            format!("{name} exited with {exit_code}"),
        )
    };
    if output == Output::Json {
        let captured = runner
            .capture(
                ctx,
                &argv,
                &SpawnOptions {
                    allow_truncated: true,
                    ..SpawnOptions::within(DELEGATE_TIMEOUT)
                },
            )
            .await?;
        let progress = DelegateProgress {
            stdout: captured.stdout,
            stderr: captured.stderr,
            stdout_truncated: captured.stdout_truncated,
            stderr_truncated: captured.stderr_truncated,
        };
        if captured.exit_code != 0 {
            return Err(failed(captured.exit_code).with_details(serde_json::json!({
                "exitCode": captured.exit_code,
                "progress": progress,
            })));
        }
        return Outcome::data(DelegateData {
            delegate: path.to_path_buf(),
            exit_code: 0,
            progress: Some(progress),
        });
    }
    let exit_code = runner.inherited(ctx, &argv, None).await?;
    if exit_code != 0 {
        return Err(failed(exit_code));
    }
    // No text: the delegate already wrote everything there was to say, straight to the terminal, and a summary
    // line after it would be the controller talking over its own output.
    Outcome::data(DelegateData {
        delegate: path.to_path_buf(),
        exit_code,
        progress: None,
    })
}
