use avl_host_testkit::{path_runner, quiet, refusal};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::sync::Arc;

use avl_base::format::words;
use avl_base::{Config, Exit, GuestArch, GuestOs};
use avl_host_sys::Ctx;
use avl_host_sys::guest::BazelHost;
use avl_wire::daemon::LABEL as DAEMON_LABEL;
use avl_wire::runtime::runfiles_root;
use pretty_assertions::assert_eq;
use tempfile::TempDir;

use super::*;
use crate::lane::testing::{pool, pool_backend, unresolved};

const DAEMON_RELATIVE: &str = "bazel-out/darwin_arm64-fastbuild/bin/plugins/air/tests/integration/ui/ui_daemon.runtime.json";

/// The fake Bazel, kept assertive: it refuses an invocation that bypasses the seam. The guest's config holds *build*
/// options, so an invocation that drops it is one Bazel keys its analysis cache differently for - and a lane that
/// silently built for the *other* guest is the defect the check guards. That failure is invisible otherwise: a
/// darwin descriptor declares `Contents/Home` as its Java-home suffix, that path does exist inside the macOS
/// tarball, so guest-local staging succeeds and the first complaint is an exec-format error from a daemon that
/// already reported itself started.
///
/// Written as a `bazel.cmd` in the fake repository root, exactly as the real wrapper is found: the controller runs
/// it through `/bin/sh` because the real one is the repo's shell/batch hybrid and has no shebang.
struct FakeBazelCmd {
    _root: TempDir,
    repo: PathBuf,
    /// What the second share exposes, so a descriptor under it is one the guest can see.
    bazel_user_root: PathBuf,
    /// What `info execution_root` answers, and what a relative `cquery` path is joined to.
    execution_root: PathBuf,
    /// The absolute path the fake's own `cquery` answer resolves to.
    descriptor: PathBuf,
    /// Every invocation, which is the only way to see a memo from outside.
    calls: PathBuf,
    /// The controller's own state directory. A real one is under the operator's `HOME`, which a suite may not write
    /// in - and a boot build's log lives there.
    runtime_root: PathBuf,
}

impl FakeBazelCmd {
    /// Writes the wrapper and stages the descriptor the guest would read. `guest_os` is the guest the fake was
    /// built *for*, which is what the refusal compares against - not the guest the caller happens to configure.
    fn new(guest_os: GuestOs) -> Self {
        let root = tempfile::tempdir().unwrap();
        let bazel_user_root = root.path().join("bazel-user-root");
        let execution_root = bazel_user_root.join("fakehash/execroot/_main");
        let descriptor = execution_root.join(DAEMON_RELATIVE);
        let fake = Self {
            repo: root.path().join("source"),
            calls: root.path().join("bazel-calls.txt"),
            runtime_root: root.path().join("runtime"),
            bazel_user_root,
            execution_root,
            descriptor,
            _root: root,
        };
        // The descriptor's *content* is `avl_wire::runtime`'s business; what this half decides is only the path,
        // and both it and its runfiles tree have to exist or the resolution refuses.
        fs::create_dir_all(fake.descriptor.parent().unwrap()).unwrap();
        fs::write(&fake.descriptor, "{}\n").unwrap();
        // A tree holds more than a MANIFEST.
        fs::create_dir_all(runfiles_root(&fake.descriptor).join("_main")).unwrap();
        fs::create_dir_all(&fake.repo).unwrap();
        let wanted = format!("--config=air-lane-{}", guest_os.as_str());
        let script = format!(
            r#"command="$1"
echo "$*" >> "{calls}"
guest_config=""
for argument in "$@"; do
  case "$argument" in
    --config=air-lane-*) guest_config="$guest_config$argument";;
  esac
done
if [ "$guest_config" != "{wanted}" ]; then
  echo "fake bazel: $command carries '$guest_config', expected {wanted}: $*" >&2
  exit 1
fi
case "$command" in
  build) echo "fake build: $*"; echo "fake build stderr" >&2; exit 0;;
  cquery) echo "{DAEMON_RELATIVE}";;
  info) echo "{execution_root}";;
  *) echo "unsupported fake bazel command: $*" >&2; exit 1;;
esac
"#,
            calls = fake.calls.display(),
            execution_root = fake.execution_root.display(),
        );
        avl_testkit::fake_executable(&fake.repo, "bazel.cmd", &script).unwrap();
        fake
    }

    /// A config pointed at this fake, for whichever guest the caller wants to *configure*.
    fn settings(&self, guest_os: GuestOs) -> Config {
        self.settings_sharing(guest_os, &self.bazel_user_root)
    }

    fn settings_sharing(&self, guest_os: GuestOs, bazel_user_root: &Path) -> Config {
        let runtime_root = self.runtime_root.to_str().unwrap();
        let settings = unresolved(pool_backend(guest_os), guest_os, &[("AIR_VM_RUNTIME_ROOT", runtime_root)]);
        settings.set_host_paths(&self.repo, bazel_user_root).unwrap();
        settings
    }

    fn bazel(&self, guest_os: GuestOs) -> Arc<Bazel> {
        self.bazel_for(guest_os, GuestArch::Arm64)
    }

    /// [`Self::bazel`] with the guest's architecture pinned, for the guest an x86_64 host has.
    fn bazel_for(&self, guest_os: GuestOs, guest_arch: GuestArch) -> Arc<Bazel> {
        let mut settings = self.settings(guest_os);
        settings.guest_arch = guest_arch;
        Bazel::new(Arc::new(settings), path_runner(), quiet())
    }

    /// Every recorded command line, in order.
    fn invocations(&self) -> Vec<String> {
        fs::read_to_string(&self.calls)
            .unwrap_or_default()
            .lines()
            .filter(|line| !line.is_empty())
            .map(str::to_owned)
            .collect()
    }

    /// The first word of every recorded invocation, in order.
    fn commands(&self) -> Vec<String> {
        self.invocations()
            .iter()
            .filter_map(|line| line.split_whitespace().next().map(str::to_owned))
            .collect()
    }
}

/// Every invocation, not only `build`: `community/common.bazelrc` sets `build --nobuild_runfile_links`, and `cquery`
/// and `info` inherit that line, so a call site that forgets the guest's config differs from the build in a *build
/// option* - which makes Bazel discard the analysis of every configured target between the two.
///
/// The build options themselves live in the root `.bazelrc`, where `vm.cmd` and `trace.cmd` reach them by the same
/// config name. A literal one here would be a second spelling, and the wrappers' key would drift from it.
#[test]
fn every_invocation_carries_the_options_every_other_one_carries() {
    for command in ["build", "cquery", "info"] {
        for guest_os in [GuestOs::Macos, GuestOs::Linux] {
            let argv = host_bazel_argv(&pool(guest_os), command, &words(["//some:target"])).unwrap();
            let config = format!("--config=air-lane-{}", guest_os.as_str());
            for wanted in [
                command,
                &config,
                "--color=no",
                "--curses=no",
                // Not part of the analysis key, unlike the config's build options, and carried everywhere for exactly
                // that reason: a lane build must neither clear nor retarget `out/bazel-bin`.
                "--experimental_convenience_symlinks=ignore",
                "//some:target",
            ] {
                assert!(
                    argv.iter().any(|argument| argument == wanted),
                    "a {guest_os:?} {command} invocation dropped {wanted}: {argv:?}"
                );
            }
            assert!(
                !argv
                    .iter()
                    .any(|argument| argument.starts_with("--define") || argument.contains("build_runfile_links")),
                "a {guest_os:?} {command} invocation spells a build option the config owns: {argv:?}"
            );
            // Through the repo's wrapper and a shell, because the wrapper has no shebang.
            assert_eq!(argv[0], "/bin/sh");
            assert!(argv[1].ends_with("bazel.cmd"), "{argv:?}");
        }
    }
}

/// The fixture's own guarantee, because every case below depends on it.
#[tokio::test]
async fn the_fake_bazel_refuses_an_invocation_that_bypasses_the_seam() {
    let fake = FakeBazelCmd::new(GuestOs::Macos);
    let runner = path_runner();
    let wrapper = fake.repo.join("bazel.cmd");
    let wrapper = wrapper.to_str().unwrap();
    let run = |arguments: &[&str]| {
        let argv: Vec<String> = ["/bin/sh", wrapper]
            .iter()
            .chain(arguments)
            .map(|word| (*word).to_owned())
            .collect();
        let runner = runner.clone();
        async move {
            runner
                .capture(&Ctx::background(), &argv, &SpawnOptions::within(Duration::from_mins(1)))
                .await
                .unwrap()
        }
    };

    assert_eq!(run(&["build", "--config=air-lane-macos", "//x"]).await.exit_code, 0);
    let dropped = run(&["build", "//x"]).await;
    assert_eq!(dropped.exit_code, 1);
    assert!(dropped.stderr.contains("expected --config=air-lane-macos"), "{dropped:?}");
    // This fixture is the macOS guest, so the Linux config is as wrong as omitting the config.
    let wrong_guest = run(&["build", "--config=air-lane-linux", "//x"]).await;
    assert_eq!(wrong_guest.exit_code, 1);
    assert!(
        wrong_guest
            .stderr
            .contains("carries '--config=air-lane-linux', expected --config=air-lane-macos"),
        "{wrong_guest:?}"
    );
}

/// The lane build is what makes the guest agent fresh, so this guest's agent label is in the target set of the one
/// invocation, whichever guest is configured.
///
/// The defect it answers: nothing in the install path builds the agent. The guest setup resolves and installs under
/// the worker's lease-operation lock, so it can refuse a *missing* binary and cannot notice a stale one. Measured on
/// a Linux worker on 2026-08-25: a lane drove an agent built three days earlier from this same checkout and died in
/// `launch-prep` with exit 64.
///
/// One invocation and not two, which is why the labels are asserted on the recorded argv rather than only on
/// [`host_build_targets`]: a `bazel build` of the agent by itself repoints `out/bazel-bin` at the guest's own
/// configuration, and this working copy is shared.
#[tokio::test]
async fn the_lane_build_builds_this_guests_agent() {
    for (guest_os, guest_arch, agent) in [
        (GuestOs::Macos, GuestArch::Arm64, "@community//tools/vm:vm-guest-agent-darwin-arm64"),
        (GuestOs::Linux, GuestArch::Arm64, "@community//tools/vm:vm-guest-agent-linux-arm64"),
        (
            GuestOs::Linux,
            GuestArch::X86_64,
            "@community//tools/vm:vm-guest-agent-linux-x86_64",
        ),
    ] {
        // The guest's Node comes from its image, so no build stages one.
        let wanted = words([DAEMON_LABEL, agent]);
        assert_eq!(host_build_targets(guest_os, guest_arch, DAEMON_LABEL), wanted);

        let fake = FakeBazelCmd::new(guest_os);
        let log = fake.runtime_root.join("build.log");
        fs::create_dir_all(&fake.runtime_root).unwrap();
        fake.bazel_for(guest_os, guest_arch)
            .build(&Ctx::background(), &log, DAEMON_LABEL)
            .await
            .unwrap();
        let recorded = fake.invocations();
        assert_eq!(recorded.len(), 1, "a {guest_os:?} lane build ran {recorded:?}");
        for label in &wanted {
            assert!(recorded[0].contains(label.as_str()), "{label} not in {}", recorded[0]);
        }
    }
}

/// A **boot** builds what it installs into a guest, and nothing else: the agent. No lane label, because a boot
/// judges no test. The set is a subset of the lane build's, asserted as one here so the two cannot come to disagree
/// about which agent bytes a worker gets.
#[tokio::test]
async fn a_boot_builds_the_agent_and_no_lane() {
    for (guest_os, guest_arch) in [
        (GuestOs::Macos, GuestArch::Arm64),
        (GuestOs::Linux, GuestArch::Arm64),
        (GuestOs::Linux, GuestArch::X86_64),
    ] {
        let fake = FakeBazelCmd::new(guest_os);
        let settings = fake.settings(guest_os);
        let boot = guest_boot_targets(guest_os, guest_arch);
        let mut lane = words([DAEMON_LABEL]);
        lane.extend(boot.iter().cloned());
        assert_eq!(host_build_targets(guest_os, guest_arch, DAEMON_LABEL), lane);
        assert!(!boot.iter().any(|label| label == DAEMON_LABEL));
        assert_eq!(boot, [agent_label(guest_os, guest_arch)], "{guest_os:?} {guest_arch}");

        fake.bazel_for(guest_os, guest_arch)
            .build_guest_boot(&Ctx::background())
            .await
            .unwrap();

        let recorded = fake.invocations();
        assert_eq!(recorded.len(), 1, "{recorded:?}");
        for label in &boot {
            assert!(recorded[0].contains(label.as_str()), "{label} not in {}", recorded[0]);
        }
        assert!(!recorded[0].contains(DAEMON_LABEL), "{}", recorded[0]);
        // The log is pool-wide, because these outputs are identical for every worker of a guest.
        assert!(settings.guest_boot_build_log_path().is_file());
        // Nothing prepared the runtime root here, so the boot build created it - private, as a later pull insists.
        let mode = fs::metadata(&settings.runtime_root).unwrap().permissions().mode();
        assert_eq!(mode & 0o077, 0, "{guest_os:?}: runtime root mode {mode:o}");
    }
}

/// The boot build is handed to the worker manager as a seam, and running it is the same build.
#[tokio::test]
async fn the_boot_seam_is_the_boot_build() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let builder = fake.bazel(GuestOs::Linux).boot_builder();

    builder(Ctx::background()).await.unwrap();

    assert_eq!(fake.commands(), ["build"]);
}

/// The build's log is the artifact a human debugs from, so it holds both of the child's streams.
#[tokio::test]
async fn a_build_logs_both_of_its_streams() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let log = fake.repo.join("build.log");
    let bazel = fake.bazel(GuestOs::Linux);
    bazel.build(&Ctx::background(), &log, "//some:target").await.unwrap();
    let content = fs::read_to_string(&log).unwrap();
    assert!(
        content.contains("fake build:") && content.contains("fake build stderr"),
        "the log is missing a stream: {content:?}"
    );
    // A previous run's log is this run's to replace, so a second build over the same path is not a refusal.
    bazel.build(&Ctx::background(), &log, "//some:target").await.unwrap();
}

/// A guest whose configuration the build did not select is the failure the fake's check stands for, and it has to
/// arrive as a refusal rather than as a descriptor for the wrong machine.
#[tokio::test]
async fn a_build_for_the_wrong_guest_fails_with_its_logs_tail() {
    // A fake built for macOS, asked for by a controller configured for a Linux guest.
    let fake = FakeBazelCmd::new(GuestOs::Macos);
    let log = fake.repo.join("build.log");

    let refused = refusal(fake.bazel(GuestOs::Linux).build(&Ctx::background(), &log, "//some:target").await);

    assert_eq!((refused.code.as_ref(), refused.exit), ("host_build_failed", Exit::BUILD_FAILED));
    assert_eq!(refused.exit.code(), 5);
    assert!(
        refused
            .message
            .contains("carries '--config=air-lane-linux', expected --config=air-lane-macos"),
        "the refusal carries no evidence from the log: {}",
        refused.message
    );
    let details = refused.details().unwrap();
    assert_eq!(details["exitCode"], 1);
    assert_eq!(details["label"], "//some:target");
    // The log survives the failure: the refusal carries 30 lines, the log carries the rest, and a human needs the
    // rest.
    assert!(log.is_file(), "the failed build's log was removed");
}

/// The refusal carries the log's tail, not the whole log: it ends up in an envelope an agent reads.
#[test]
fn a_failed_builds_refusal_carries_only_the_logs_tail() {
    let directory = tempfile::tempdir().unwrap();
    let log = directory.path().join("build.log");
    let lines: Vec<String> = (1..=40).map(|line| format!("line {line}")).collect();
    fs::write(&log, lines.join("\r\n")).unwrap();

    let tail = log_tail(&log).unwrap();

    assert_eq!(tail.lines().count(), BUILD_LOG_TAIL_LINES);
    assert!(tail.starts_with("line 11\n") && tail.ends_with("line 40"), "{tail:?}");
    assert_eq!(log_tail(&directory.path().join("missing.log")), None);
}

/// A build whose log path cannot be opened is a refusal naming it, not a panic much later.
#[tokio::test]
async fn a_build_refuses_when_its_log_cannot_be_opened() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let log = fake.repo.join("no-such-directory/build.log");

    let refused = refusal(fake.bazel(GuestOs::Linux).build(&Ctx::background(), &log, "//some:target").await);

    assert_eq!(
        (refused.code.as_ref(), refused.exit),
        ("host_build_log_unavailable", Exit::CANT_CREATE)
    );
    assert!(fake.invocations().is_empty(), "bazel ran without a log");
}

/// `prepareBuild` asked Bazel twice for where one configured target's output landed - a `cquery` and an `info` - on
/// every daemon start, and that pair measured 7.3 s of a warm restart. Where an output lands is a function of the
/// label and the configuration, never of the build's result, so a second answer in the same process cannot differ
/// from the first.
#[tokio::test]
async fn the_descriptor_and_the_execution_root_are_resolved_once_per_process() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let bazel = fake.bazel(GuestOs::Linux);
    let ctx = Ctx::background();

    let resolved = bazel.runtime_descriptor(&ctx, DAEMON_LABEL).await.unwrap();
    assert_eq!(resolved, fake.descriptor);
    assert_eq!(bazel.runtime_descriptor(&ctx, DAEMON_LABEL).await.unwrap(), resolved);
    // The execution root the guest-agent resolver needs is the same answer rather than a second `info`.
    assert_eq!(bazel.execution_root(&ctx).await.unwrap(), fake.execution_root);
    assert_eq!(fake.commands(), ["cquery", "info"]);
}

/// A stale memo across a `bazel clean` is how a run stages a generation that is no longer there, and the descriptor
/// and its runfiles tree are removed independently - so both are re-validated on every hit.
#[tokio::test]
async fn a_wiped_output_tree_resolves_again_instead_of_answering_a_stale_path() {
    for removed in ["the descriptor", "its runfiles tree"] {
        let fake = FakeBazelCmd::new(GuestOs::Linux);
        let bazel = fake.bazel(GuestOs::Linux);
        let ctx = Ctx::background();
        bazel.runtime_descriptor(&ctx, DAEMON_LABEL).await.unwrap();
        if removed == "the descriptor" {
            fs::remove_file(&fake.descriptor).unwrap();
        } else {
            fs::remove_dir_all(runfiles_root(&fake.descriptor)).unwrap();
        }

        let refused = refusal(bazel.runtime_descriptor(&ctx, DAEMON_LABEL).await);

        assert_eq!(refused.code, "daemon_runtime_descriptor_missing", "with {removed} gone");
        // Asked again, rather than answered from the memo: one `cquery` per resolution. The execution root is still
        // asked once, because that memo is not invalidated by an output tree - a different output base would mean a
        // different server than the one this process has been talking to.
        assert_eq!(fake.commands(), ["cquery", "info", "cquery"], "with {removed} gone");
    }
}

/// A descriptor the guest cannot see through the share is refused here, where the message can say what to set,
/// rather than as a mount that fails inside the VM for a reason naming nothing.
#[tokio::test]
async fn a_descriptor_outside_the_share_is_refused() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let elsewhere = tempfile::tempdir().unwrap();
    let settings = fake.settings_sharing(GuestOs::Linux, elsewhere.path());
    let bazel = Bazel::new(Arc::new(settings), path_runner(), quiet());

    let refused = refusal(bazel.runtime_descriptor(&Ctx::background(), DAEMON_LABEL).await);

    assert_eq!(
        (refused.code.as_ref(), refused.exit),
        ("daemon_runtime_outside_share", Exit::DATA_ERR)
    );
    assert!(
        refused.message.contains("AIR_VM_BAZEL_USER_ROOT"),
        "the refusal does not say what to set: {}",
        refused.message
    );
}

/// A `cquery` that answers nothing about the descriptor is a refusal, not a joined-up empty path.
#[tokio::test]
async fn a_cquery_without_the_descriptor_is_refused() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);

    let refused = refusal(
        fake.bazel(GuestOs::Linux)
            .runtime_descriptor(&Ctx::background(), "//plugins/air/tests/integration/ui:other_target")
            .await,
    );

    assert_eq!(refused.code, "daemon_runtime_descriptor_missing");
}

/// `shard` and `flake` fan out over the workers of one guest, so both memos are reached from several tasks at once.
/// A memo published only after the call would let each of them pay for it.
#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn the_memos_are_safe_under_a_fan_out() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let bazel = fake.bazel(GuestOs::Linux);
    let mut workers = tokio::task::JoinSet::new();
    for _ in 0..8 {
        let bazel = Arc::clone(&bazel);
        workers.spawn(async move {
            let ctx = Ctx::background();
            bazel.runtime_descriptor(&ctx, DAEMON_LABEL).await.unwrap();
            bazel.execution_root(&ctx).await.unwrap();
        });
    }
    while let Some(joined) = workers.join_next().await {
        joined.unwrap();
    }
    assert_eq!(fake.commands(), ["cquery", "info"]);
}

/// The trait the guest setup declares so that a test of the agent install never spawns Bazel. This is its one
/// implementation.
#[tokio::test]
async fn this_is_the_guest_packages_bazel_host() {
    let fake = FakeBazelCmd::new(GuestOs::Linux);
    let host: Arc<dyn BazelHost> = fake.bazel(GuestOs::Linux);
    let ctx = Ctx::background();
    // A `--output=starlark` form the guest agent's resolver may need, passed through untouched.
    let stdout = host
        .query(
            &ctx,
            "cquery",
            &words([
                "--output=starlark",
                "--starlark:expr=[f.path for f in target.files.to_list()][0]",
                "//x",
            ]),
        )
        .await
        .unwrap();
    assert_eq!(stdout.trim(), DAEMON_RELATIVE);
    assert_eq!(host.execution_root(&ctx).await.unwrap(), fake.execution_root);
    // And nothing that builds. A lease-only operation - `lease acquire`, `lease release`, `status`, `exec`, `pull` -
    // installs the agent through this trait and builds nothing, deliberately: each of them holds the worker's
    // lease-operation lock, where a build would spend minutes, and none of them judges a test. The trait has no
    // build method at all, and this is the runtime half of the same sentence.
    assert_eq!(fake.commands(), ["cquery", "info"]);
}

/// The summary is read from the lines Bazel writes at the end of a build. The action cache hits are cached but are
/// not processes, the internal processes did not run, and every other strategy executed its actions.
#[test]
fn the_build_summary_is_read_from_the_end_of_the_log() {
    let directory = tempfile::tempdir().unwrap();
    let log = directory.path().join("host-build.log");
    fs::write(
        &log,
        [
            "[7,684 / 7,686] Composing dev distribution @@//build:idea_air_lane_dist_linux; 2s disk-cache, darwin-sandbox",
            "INFO: Found 3 targets...",
            "INFO: Elapsed time: 240.675s, Critical Path: 187.67s",
            "INFO: 1025 processes: 6651 action cache hit, 1 disk cache hit, 32 remote cache hit, 22 internal, \
             34 darwin-sandbox, 936 worker.",
            "INFO: Build completed successfully, 1025 total actions",
            "",
        ]
        .join("\r\n"),
    )
    .unwrap();
    assert_eq!(
        read_build_summary(&log),
        Some(BuildSummary {
            processes: 1025,
            ran: 970,
            cached: 6684,
            critical_path_ms: 187_670,
        })
    );

    // A no-op build has one process line without a strategy list's cache hits.
    fs::write(&log, "INFO: 1 process: 1 internal.\n").unwrap();
    assert_eq!(
        read_build_summary(&log),
        Some(BuildSummary {
            processes: 1,
            ..BuildSummary::default()
        })
    );

    // A build that Bazel never started has no summary.
    fs::write(&log, "ERROR: Skipping '//a:b': no such package\n").unwrap();
    assert_eq!(read_build_summary(&log), None);
    assert_eq!(read_build_summary(&directory.path().join("missing.log")), None);
}

/// The follower reports the last line a build wrote, which is what the build is doing now.
#[test]
fn the_last_log_line_is_the_last_non_empty_one() {
    let directory = tempfile::tempdir().unwrap();
    let log = directory.path().join("host-build.log");
    assert_eq!(last_log_line(&log), None);
    fs::write(&log, "first\r\n[1 / 2] compiling\r\n\r\n  \n").unwrap();
    assert_eq!(last_log_line(&log).as_deref(), Some("[1 / 2] compiling"));
}
