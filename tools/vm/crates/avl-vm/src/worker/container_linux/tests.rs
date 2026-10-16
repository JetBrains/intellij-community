//! The container-linux backend's suite. Hermetic: no script runs, so what is asserted is the argv the script would get,
//! the reading of `list`, and the files the container writes.

use std::time::Duration;

use avl_base::config::WORKSPACE_DIR;
use avl_base::format::words;
use avl_base::phase::Timeline;
use avl_base::report::Mode;
use avl_base::{Backend, Environment, GuestOs, Selection};
use avl_host_sys::Interrupts;
use pretty_assertions::assert_eq;

use super::*;

const WORKER: &str = "container-linux-1";

/// Settings of a container-linux pool whose checkout is `root/repo`, with the host paths declared rather than
/// resolved.
fn settings(root: &Path, repo: &str, bazel: &str) -> Arc<Config> {
    settings_of_checkout(&root.join("repo"), repo, bazel)
}

/// Settings whose checkout is `checkout`: the skill's script and its output root derive from it.
fn settings_of_checkout(checkout: &Path, repo: &str, bazel: &str) -> Arc<Config> {
    let runtime = checkout.join("runtime").to_string_lossy().into_owned();
    let environment = Environment::from_pairs([("HOME", "/Users/air"), ("AIR_VM_RUNTIME_ROOT", runtime.as_str())]);
    let settings = Config::load(
        avl_base::HostFacts::without_memory(),
        Selection {
            backend: Backend::ContainerLinux,
            guest_os: GuestOs::Linux,
        },
        &environment,
        &checkout.join(WORKSPACE_DIR),
    )
    .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"));
    settings.set_host_paths(repo, bazel).expect("the host paths are declared");
    Arc::new(settings)
}

/// The bash driver beside the checkout's `container.cmd`, the head of every argv of the backend.
fn driver(settings: &Config) -> String {
    settings.container_linux_script.with_extension("sh").to_string_lossy().into_owned()
}

fn backend(settings: &Arc<Config>) -> ContainerLinux {
    let runner = Runner::new(
        [("PATH".to_owned(), std::env::var("PATH").unwrap_or_default())],
        Interrupts::detached(),
    );
    let (reporter, _, _) = Reporter::in_memory("vm");
    reporter.set_mode(Mode::Json);
    ContainerLinux::new(Arc::clone(settings), runner, reporter)
}

// --- start ----------------------------------------------------------------------------------------------------

/// The Bazel share is a read-only mount at its guest root, which on a Unix host is the host path itself, so the guest
/// needs no parity layout. The checkout is not mounted at all.
#[test]
fn start_mounts_the_bazel_share_read_only_at_its_host_path_and_no_checkout() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let settings = settings(root.path(), "/Users/air/idea", "/Users/air/.cache/bazel");
    let backend = backend(&settings);
    let worker_dir = settings.worker_dir(WORKER).to_string_lossy().into_owned();
    assert_eq!(
        backend.start_argv(WORKER).expect("the argv renders"),
        words([
            driver(&settings).as_str(),
            "start",
            &worker_dir,
            "--ro",
            "/Users/air/.cache/bazel:/Users/air/.cache/bazel",
            "--publish",
            &format!("{}:{}", settings.daemon_host_port, settings.daemon.port),
        ])
    );
}

/// The script splits a mount at a colon, so a host path with one cannot be mounted and is refused by name.
#[test]
fn a_share_path_with_a_colon_is_refused() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let settings = settings(root.path(), "/Users/air/idea", "/Users/air/a:b/bazel");
    let refusal = backend(&settings).start_argv(WORKER).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("unsafe_share_path", Exit::DATA_ERR));
    assert!(refusal.message.contains("/Users/air/a:b/bazel"), "{}", refusal.message);
}

/// A Windows path carries its drive colon, which the runtime reads as Docker does, so that one colon passes; any
/// other colon, and any colon on a Unix host, is refused.
#[test]
fn a_windows_drive_colon_is_the_one_colon_a_share_path_may_hold() {
    assert_eq!(
        mount_path_on(Path::new("D:/ultimate/intellij"), "repo", HostOs::Windows).unwrap(),
        "D:/ultimate/intellij"
    );
    assert_eq!(
        mount_path_on(Path::new(r"D:\ultimate\intellij"), "repo", HostOs::Windows).unwrap(),
        r"D:\ultimate\intellij"
    );
    let other = mount_path_on(Path::new("D:/a:b/idea"), "repo", HostOs::Windows).unwrap_err();
    assert_eq!((other.code.as_ref(), other.exit), ("unsafe_share_path", Exit::DATA_ERR));
    let unix = mount_path_on(Path::new("/Users/air/a:b/idea"), "repo", HostOs::Linux).unwrap_err();
    assert_eq!((unix.code.as_ref(), unix.exit), ("unsafe_share_path", Exit::DATA_ERR));
}

// --- the developer terminal and pull --------------------------------------------------------------------------

/// The guest argv of the developer terminal and `pull` is the runtime's exec through the script. Its head is declared
/// to phase timing, so a spawn through it counts as a guest call.
#[test]
fn the_exec_head_is_the_script_and_is_declared_to_the_timeline() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let settings = settings(root.path(), "/Users/air/idea", "/Users/air/.cache/bazel");
    let timeline = Timeline::collecting();
    let ctx = Ctx::background().with_timeline(timeline.clone());
    let line = backend(&settings).guest_argv(&ctx, WORKER, &words(["/usr/bin/true"]), true);
    assert_eq!(line, words([driver(&settings).as_str(), "exec", "/usr/bin/true"]));
    let (inner, phase) = ctx.begin("probe");
    inner.phase().record_subprocess(&line, Duration::from_secs(1));
    inner.phase().record_subprocess(&["git", "rev-parse"], Duration::from_secs(1));
    drop(phase);
    let timing = &timeline.take()[0];
    assert_eq!((timing.guest_calls, timing.host_calls), (1, 1), "{timing:?}");
}

// --- list -----------------------------------------------------------------------------------------------------

/// A container line names its checkout root in the third field; only a root equal to this checkout is this
/// worker, with or without a trailing slash, and the indented detail lines are not container lines.
#[test]
fn the_listed_checkout_is_the_output_root_without_its_tail() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let checkout = root.path().join("checkout");
    let settings = settings_of_checkout(&checkout, "/Users/air/idea", "/Users/air/.cache/bazel");
    assert_eq!(backend(&settings).listed_checkout(), checkout);
    let elsewhere = root.path().join("elsewhere");
    let settings = settings_of_checkout(&elsewhere, "/Users/air/idea", "/Users/air/.cache/bazel");
    assert_eq!(backend(&settings).listed_checkout(), elsewhere);
}

// The batch half prints the checkout as the shell spelled it, so a Windows host compares the line without case; a
// Unix host compares the path as it is.
#[test]
fn a_windows_host_matches_the_list_line_without_case() {
    let listed = "ui-1a2b3c4d  running  D:\\ultimate\\intellij\n";
    assert!(running_in_on(listed, Path::new("d:\\ultimate\\intellij"), HostOs::Windows));
    assert!(!running_in_on(listed, Path::new("d:\\ultimate\\other"), HostOs::Windows));
    assert!(!running_in_on(
        "ui-1  running  /Users/air/IDEA\n",
        Path::new("/Users/air/idea"),
        HostOs::Linux
    ));
}

// A Unix host cannot execute a `.cmd`, so it runs the bash half next to it; Windows runs the `.cmd` itself.
#[test]
fn the_program_is_the_bash_half_on_unix_and_the_batch_file_on_windows() {
    let script = Path::new("/r/.agents/skills/testing-ui/scripts/container.cmd");
    assert_eq!(
        program_of(script, HostOs::Linux),
        "/r/.agents/skills/testing-ui/scripts/container.sh"
    );
    assert_eq!(
        program_of(script, HostOs::Macos),
        "/r/.agents/skills/testing-ui/scripts/container.sh"
    );
    let windows = Path::new(r"D:\r\.agents\skills\testing-ui\scripts\container.cmd");
    assert_eq!(
        program_of(windows, HostOs::Windows),
        r"D:\r\.agents\skills\testing-ui\scripts\container.cmd"
    );
}

#[test]
fn running_is_the_list_line_of_this_checkout() {
    let listed = "ui-1a2b3c4d  running  /Users/air/idea\n  run none\n  noVNC http://127.0.0.1:6111/vnc.html  password x\n\
                  ui-9f8e7d6c  running  /Users/air/other/\n  run none\n";
    assert!(running_in(listed, Path::new("/Users/air/idea")));
    assert!(running_in(listed, Path::new("/Users/air/other")));
    assert!(!running_in(listed, Path::new("/Users/air/idea2")));
    assert!(!running_in(listed, Path::new("/Users/air")));
    assert!(!running_in(
        "no containers of this tool are running\n",
        Path::new("/Users/air/idea")
    ));
    assert!(!running_in("", Path::new("/Users/air/idea")));
}

/// A checkout root with a space is one field, because the fields are split on the double-space separator.
#[test]
fn a_checkout_root_with_a_space_is_one_field() {
    let listed = "ui-1a2b3c4d  running  /Users/air/my idea\n";
    assert!(running_in(listed, Path::new("/Users/air/my idea")));
    assert!(!running_in(listed, Path::new("/Users/air/my")));
}

// --- what the container wrote ---------------------------------------------------------------------------------

/// Before a start, and after a stop, the port and the bearer are not there, and the description is `None`.
#[test]
fn a_container_that_was_not_started_has_no_port_and_no_description() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let settings = settings(root.path(), "/Users/air/idea", "/Users/air/.cache/bazel");
    for refusal in [control_port(&settings).unwrap_err(), bearer(&settings).unwrap_err()] {
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("container_linux_not_started", Exit::UNAVAILABLE)
        );
        assert!(refusal.message.contains("pool start"), "{}", refusal.message);
    }
    assert_eq!(backend(&settings).novnc().expect("a missing description is no refusal"), None);
}

/// The port file has no line break, as the script writes it, and the bearer file has one, as the guest writes it;
/// both read as one value. The description is the flat JSON of the guest, with keys this controller does not read.
#[test]
fn the_port_the_bearer_and_the_description_are_read_from_the_container_linux_root() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let settings = settings(root.path(), "/Users/air/idea", "/Users/air/.cache/bazel");
    let work = settings.container_linux_root.clone();
    std::fs::create_dir_all(&work).expect("the testing-ui root");
    std::fs::write(work.join("container.ctl_port"), "10111").expect("the port file");
    std::fs::write(work.join("container.ctl_bearer"), "0123456789abcdef0123456789abcdef\n").expect("the bearer file");
    std::fs::write(
        work.join("container.json"),
        r#"{"novnc": "http://127.0.0.1:6111/vnc.html?autoconnect=1", "novncPublic": "", "vncPassword": "s3cret"}"#,
    )
    .expect("the description file");
    assert_eq!(control_port(&settings).expect("the port reads"), 10111);
    assert_eq!(bearer(&settings).expect("the bearer reads"), "0123456789abcdef0123456789abcdef");
    assert_eq!(
        backend(&settings).novnc().expect("the description reads"),
        Some(NoVnc {
            url: "http://127.0.0.1:6111/vnc.html?autoconnect=1".to_owned(),
            password: "s3cret".to_owned(),
        })
    );

    std::fs::write(work.join("container.ctl_port"), "not a port").expect("the port file");
    let refusal = control_port(&settings).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("state_read_failed", Exit::FAILURE));
    assert!(refusal.message.contains("not a port"), "{}", refusal.message);
}

// --- the start record -------------------------------------------------------------------------------------------

/// The record names the argv a start uses, so it is current right after a start and stale when the argv moves: no
/// record is stale too.
#[test]
fn the_start_record_is_current_only_while_it_names_the_start_argv() {
    let root = tempfile::tempdir().expect("a temporary directory");
    let settings = settings(root.path(), "/Users/air/idea", "/Users/air/.cache/bazel");
    let backend = backend(&settings);
    std::fs::create_dir_all(settings.worker_dir(WORKER)).expect("the worker directory");
    assert!(!backend.start_record_is_current(WORKER).expect("no record reads"));

    backend.record_start(WORKER).expect("the record is written");
    assert!(backend.start_record_is_current(WORKER).expect("the record reads"));
    let recorded: Vec<String> =
        serde_json::from_slice(&std::fs::read(backend.start_record_path(WORKER)).expect("the record file")).expect("a JSON list");
    assert_eq!(recorded, backend.start_argv(WORKER).expect("the argv renders"));

    std::fs::write(backend.start_record_path(WORKER), r#"["start", "/elsewhere"]"#).expect("another record");
    assert!(!backend.start_record_is_current(WORKER).expect("the other record reads"));
}
