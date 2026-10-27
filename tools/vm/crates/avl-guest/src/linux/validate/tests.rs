//! `validate-guest`, the sweep of the shared objects and the host probe of `lane-run`, against a stubbed worker.
//!
//! Every refusal below is reached in milliseconds on any host, against a stubbed loader, `xdpyinfo`, `xprop` and
//! `ldd`. Three cases could not have been written against the shell version at all: the **stale window manager
//! property** (`xprop` answering one thing on the root and another on the window it names), an **unresolved
//! shared object**, and a **musl guest** (one loader file present and the other absent).

use avl_wire::verb::AgentVerb;
use std::collections::{HashMap, VecDeque};
use std::fs;
use std::os::unix::fs::PermissionsExt;
use std::time::Duration;

use avl_wire::supervisor::AgentExit;
use pretty_assertions::assert_eq;

use super::*;
use crate::reply::verb_refusal_code;
use crate::step::StepError;
use crate::step::tests_support::exited;
use crate::testing::{ValidatorFixture, assert_damage_codes, assert_damage_mentions, assert_verb_usage_refused, run_agent};

/// The window the healthy stub's fluxbox owns. Mixed case on purpose: the two hops are compared against each other
/// and X tools are not consistent about hex case, which is why both lowercase.
const WINDOW: &str = "0x40000A";
const WINDOW_LOWER: &str = "0x40000a";

/// `air-linux-2`'s own loader line, measured on 2026-08-27: what is pinned is that the *real* wording is parsed.
const GLIBC_LINE: &str = "ld.so (Ubuntu GLIBC 2.39-0ubuntu8.8) stable release version 2.39.\n\
                          Copyright (C) 2024 Free Software Foundation, Inc.\n";

/// The label the fake worker answers the glibc loader's version probe under.
const GLIBC_LABEL: &str = "ld-linux-aarch64.so.1 --version";

const ABSENT: &str = "_NET_SUPPORTING_WM_CHECK:  not found.\n";

fn xprop_answer(window: &str) -> String {
    format!("{SUPPORTING_WM_CHECK_PROPERTY}(WINDOW): window id # {window}\n")
}

fn root_xprop() -> String {
    format!("{XPROP_TOOL} -root -display :88 {SUPPORTING_WM_CHECK_PROPERTY}")
}

fn self_xprop() -> String {
    format!("{XPROP_TOOL} -id {WINDOW_LOWER} -display :88 {SUPPORTING_WM_CHECK_PROPERTY}")
}

/// The guest's commands, stubbed by how a command reads: the tool's base name and its arguments with the distribution
/// reduced away. An unstubbed command panics rather than answering an error, which would surface as some
/// other check's refusal and let a case pass while testing something else.
struct Worker {
    distribution: PathBuf,
    /// The output of a command, or the exit code it fails with.
    replies: HashMap<String, Result<String, i32>>,
    /// Answers one command gives first, one per call, before the reply table answers the rest: a root window whose
    /// property is absent for the first few probes, which is the only shape the measured race has.
    queued: HashMap<String, VecDeque<String>>,
    calls: Vec<String>,
    environments: HashMap<String, Vec<(String, String)>>,
    paused: Vec<Duration>,
}

impl Worker {
    fn label(&self, argv: &[String]) -> String {
        let prefix = format!("{}/", self.distribution.display());
        let mut parts = vec![Path::new(&argv[0]).file_name().unwrap().to_string_lossy().into_owned()];
        parts.extend(
            argv[1..]
                .iter()
                .map(|argument| argument.strip_prefix(&prefix).unwrap_or(argument).to_owned()),
        );
        parts.join(" ")
    }

    fn reply(&mut self, label: &str, output: &str) {
        self.replies.insert(label.to_owned(), Ok(output.to_owned()));
    }

    fn fail_with(&mut self, label: &str, code: i32) {
        self.replies.insert(label.to_owned(), Err(code));
    }

    fn queue(&mut self, label: &str, outputs: &[&str]) {
        self.queued
            .insert(label.to_owned(), outputs.iter().map(ToString::to_string).collect());
    }

    /// The recorded `xprop` hops: two per pass when the root answers and one when it does not.
    fn xprop_hops(&self) -> usize {
        self.calls.iter().filter(|call| call.starts_with(&format!("{XPROP_TOOL} "))).count()
    }

    fn ran(&self, prefix: &str) -> bool {
        self.calls.iter().any(|call| call.starts_with(prefix))
    }
}

impl Runner for Worker {
    fn run(&mut self, step: &Step) -> Result<String, StepError> {
        let label = self.label(&step.argv);
        self.calls.push(label.clone());
        self.environments.insert(label.clone(), step.env.clone());
        if let Some(output) = self.queued.get_mut(&label).and_then(VecDeque::pop_front) {
            return Ok(output);
        }
        match self.replies.get(&label) {
            Some(Ok(output)) => Ok(output.clone()),
            Some(Err(code)) => Err(exited(step, *code)),
            None => panic!("the worker was asked to run {label:?}, which no stub answers"),
        }
    }

    fn pause(&mut self, duration: Duration) {
        self.paused.push(duration);
    }
}

struct Fixture {
    _root: tempfile::TempDir,
    validator: GuestValidator<Worker>,
}

impl ValidatorFixture for Fixture {
    /// A worker every check passes on, with a distribution of two shared objects that resolve. Every case is this,
    /// minus one thing. The loaders are real files in a temporary directory, because the check stats them.
    fn healthy() -> Self {
        let root = tempfile::tempdir().unwrap();
        let distribution = root.path().join("dist");
        let args = ValidateGuestArgs { display: ":88".to_owned() };
        let worker = Worker {
            distribution,
            replies: HashMap::new(),
            queued: HashMap::new(),
            calls: Vec::new(),
            environments: HashMap::new(),
            paused: Vec::new(),
        };
        let mut validator = GuestValidator::new(&args, worker);
        validator.loaders = LoaderPaths {
            glibc: root.path().join("ld-linux-aarch64.so.1"),
            musl: root.path().join("ld-musl-aarch64.so.1"),
        };
        let mut fixture = Self { _root: root, validator };
        fixture.stage_glibc_loader();
        fixture
            .worker()
            .reply(XDPYINFO_TOOL, "screen #0:\n  dimensions: 1920x1080 pixels\n");
        fixture.worker().reply(&root_xprop(), &xprop_answer(WINDOW));
        // The self-reference, answered in the other hex case, which both hops lowercase before comparing.
        fixture.worker().reply(&self_xprop(), &xprop_answer(&WINDOW.to_uppercase()));
        fixture.stage_shared_object(
            "lib/libskiko-linux-arm64.so",
            "\tlinux-vdso.so.1 (0x0000ffff)\n\tlibEGL.so.1 => /lib/aarch64-linux-gnu/libEGL.so.1 (0x0000ffff)\n",
        );
        fixture.stage_shared_object(
            "jbr/lib/libjcef.so",
            "\tlibgtk-3.so.0 => /lib/aarch64-linux-gnu/libgtk-3.so.0 (0x0000ffff)\n",
        );
        fixture
    }

    fn refusal(&mut self, case: &str) -> AgentRefusal {
        self.validate().expect_err(case)
    }
}

impl Fixture {
    fn worker(&mut self) -> &mut Worker {
        &mut self.validator.runner
    }

    fn distribution(&self) -> PathBuf {
        self.validator.runner.distribution.clone()
    }

    fn stage_glibc_loader(&mut self) {
        fs::write(&self.validator.loaders.glibc, "\x7fELF").unwrap();
        self.worker().reply(GLIBC_LABEL, GLIBC_LINE);
    }

    /// Nothing stubs a command for the musl loader: the check stats it and never runs it.
    fn stage_musl_loader(&self) {
        fs::write(&self.validator.loaders.musl, "\x7fELF").unwrap();
    }

    fn remove_glibc_loader(&self) {
        fs::remove_file(&self.validator.loaders.glibc).unwrap();
    }

    /// Writes one file into the distribution and stubs the `ldd` that will read it.
    fn stage_shared_object(&mut self, relative: &str, ldd_output: &str) {
        let path = self.distribution().join(relative);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, "\x7fELF").unwrap();
        self.worker().reply(&format!("{LDD_TOOL} {relative}"), ldd_output);
    }

    fn empty_distribution(&self) {
        fs::remove_dir_all(self.distribution()).unwrap();
    }

    fn validate(&mut self) -> Result<GuestReport, AgentRefusal> {
        self.validator.validate()
    }

    /// The sweep of the shared objects of the distribution, with the runner of the worker.
    fn sweep(&mut self) -> Result<usize, AgentRefusal> {
        let distribution = self.distribution();
        check_shared_objects(&mut self.validator.runner, &distribution)
    }

    fn probe_host(&mut self) -> Result<Vec<String>, AgentRefusal> {
        let distribution = self.distribution();
        self.validator.probe_host(&distribution)
    }

    fn calls(&self) -> &[String] {
        &self.validator.runner.calls
    }
}

// --- the happy path -------------------------------------------------------------------------------------------

// The report names the C library, the display and the window manager. The self-check runs no `ldd`: the lane daemon
// sweeps the shared objects of its distribution at its own boot.
#[test]
fn a_healthy_worker_is_validated_and_reports_what_it_found() {
    let mut fixture = Fixture::healthy();
    let report = fixture.validate().unwrap();
    assert_eq!(
        report,
        GuestReport {
            glibc: "2.39".to_owned(),
            display: ":88".to_owned(),
            window_manager: WINDOW_LOWER.to_owned(),
            window_manager_probes: 1,
        }
    );
    // A warm worker pays the wait's first probe and nothing else.
    assert_eq!(fixture.validator.runner.paused, []);
    let encoded = serde_json::to_value(&report).unwrap();
    assert_eq!(
        encoded,
        serde_json::json!({"glibc": "2.39", "display": ":88", "windowManager": WINDOW_LOWER,
                           "windowManagerProbes": 1})
    );
    assert!(!fixture.validator.runner.ran(&format!("{LDD_TOOL} ")), "{:?}", fixture.calls());
}

/// A host that already failed a cheap check is not swept: a display that does not answer is a host no amount of
/// `ldd` will rescue.
#[test]
fn the_shared_object_sweep_runs_only_after_the_cheap_checks_passed() {
    let mut fixture = healthy_host();
    fixture.worker().fail_with(XDPYINFO_TOOL, 1);
    fixture.probe_host().unwrap_err();
    assert!(!fixture.validator.runner.ran(&format!("{LDD_TOOL} ")), "{:?}", fixture.calls());
}

// --- the argument contract ------------------------------------------------------------------------------------

/// Exactly one value, the display, checked before anything is read. A wrong argv is a wiring defect in the
/// controller's `validate_argv` and refuses as usage. A controller that still passes the retired runtime root, or the
/// retired four values, is a wiring defect too, and not a checked boot.
#[test]
fn the_validate_argv_is_exactly_one_checked_value() {
    let cases: [(&str, &[&str]); 6] = [
        ("no arguments", &[]),
        ("the retired runtime root", &[":88", "/data/daemon-runtime"]),
        ("a relative second argument", &[":88", "daemon-runtime"]),
        ("the retired four-value argv", &[":88", "/node/bin/node", "/ide", "24"]),
        ("a display with no colon", &["88"]),
        ("a display with a trailing letter", &[":88x"]),
    ];
    assert_verb_usage_refused(AgentVerb::ValidateGuest, &cases);
}

/// **The validator holds no package list**, and a trailing argument is how one would arrive: it is refused, and
/// the refusal names what arrived.
#[test]
fn the_validator_takes_no_package_list() {
    let answered = run_agent(&[AgentVerb::ValidateGuest.as_str(), ":88", "libegl1", "libgtk-3-0t64"], b"");
    assert_eq!((answered.exit, answered.code()), (64, "usage".to_owned()));
    let message = answered.failure()["error"]["message"].as_str().unwrap().to_owned();
    assert!(message.contains("libegl1"), "{message}");
}

// --- every refusal path ---------------------------------------------------------------------------------------

#[test]
fn every_guest_check_refuses_with_its_own_code() {
    type Damage = fn(&mut Fixture);
    let cases: [(&str, Damage, &str); 10] = [
        (
            "the X server is not serving the display",
            |fixture| fixture.worker().fail_with(XDPYINFO_TOOL, 1),
            "linux_display_not_answering",
        ),
        (
            "the root window has no supporting-WM property",
            |fixture| fixture.worker().reply(&root_xprop(), ABSENT),
            "linux_window_manager_missing",
        ),
        (
            "xprop itself did not run",
            |fixture| fixture.worker().fail_with(&root_xprop(), 1),
            "linux_window_manager_missing",
        ),
        // The hop that distinguishes a live fluxbox from a root property left behind by one that exited.
        (
            "the support window does not answer for itself",
            |fixture| fixture.worker().reply(&self_xprop(), ABSENT),
            "linux_window_manager_stale",
        ),
        (
            "the support window points at some other window",
            |fixture| {
                fixture.worker().reply(&self_xprop(), &xprop_answer("0x600011"));
            },
            "linux_window_manager_stale",
        ),
        (
            "the support window no longer exists",
            |fixture| {
                fixture.worker().fail_with(&self_xprop(), 1);
            },
            "linux_window_manager_stale",
        ),
        // The C library, whose absence used to arrive as a display refusal naming Xvfb.
        (
            "neither dynamic loader is on the guest",
            |fixture| fixture.remove_glibc_loader(),
            "linux_glibc_missing",
        ),
        (
            "the guest is a musl guest",
            |fixture| {
                fixture.remove_glibc_loader();
                fixture.stage_musl_loader();
            },
            "linux_glibc_missing",
        ),
        (
            "the glibc loader does not run",
            |fixture| {
                fixture.worker().fail_with(GLIBC_LABEL, 127);
            },
            "linux_glibc_missing",
        ),
        // A file at that path that answers something else is not the glibc loader, whatever its name is.
        (
            "the glibc loader reports no release of its own",
            |fixture| {
                fixture.worker().reply(GLIBC_LABEL, "BusyBox v1.36.1\n");
            },
            "linux_glibc_missing",
        ),
    ];
    assert_damage_codes(&cases);
}

/// A distribution that exists and cannot be *read* is not an empty one. Skipped as root, which may open a mode-0000
/// directory.
#[test]
fn an_unreadable_distribution_is_not_read_as_an_empty_one() {
    if nix::unistd::geteuid().is_root() {
        eprintln!("skipped: a root process may read a mode-0000 directory, so the walk cannot be made to fail");
        return;
    }
    let mut fixture = Fixture::healthy();
    let root = fixture.distribution();
    fs::set_permissions(&root, fs::Permissions::from_mode(0o000)).unwrap();
    let result = fixture.sweep();
    fs::set_permissions(&root, fs::Permissions::from_mode(0o755)).unwrap();
    let refusal = result.unwrap_err();
    assert_eq!(refusal.code, DISTRIBUTION_UNREADABLE_CODE);
    assert_eq!(refusal.exit, AgentExit::Refused);
    assert!(refusal.message.contains(&*root.to_string_lossy()), "{}", refusal.message);
}

/// A distribution whose shared objects resolve passes, and the sweep answers how many objects `ldd` read. A library
/// that the guest does not have is refused with its own code and the status of every guest check.
#[test]
fn the_sweep_refuses_a_shared_object_whose_library_the_guest_does_not_have() {
    let mut fixture = Fixture::healthy();
    assert_eq!(fixture.sweep().unwrap(), 2);

    fixture.worker().reply(
        "ldd lib/libskiko-linux-arm64.so",
        "\tlinux-vdso.so.1 (0x0000ffff)\n\tlibEGL.so.1 => not found\n",
    );
    let refusal = fixture.sweep().unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        (SHARED_OBJECT_UNRESOLVED_CODE, AgentExit::Refused)
    );
    assert_eq!(SHARED_OBJECT_UNRESOLVED_CODE, "linux_shared_object_unresolved");
}

/// The unresolved-object refusal names **both** halves: the object that cannot be loaded (which feature stops
/// working) and the library it wanted (the searchable half), the distribution, and where the repair goes.
#[test]
fn an_unresolved_shared_object_names_the_object_and_the_library_that_wanted_it() {
    let mut fixture = Fixture::healthy();
    fixture.worker().reply(
        "ldd lib/libskiko-linux-arm64.so",
        "\tlinux-vdso.so.1 (0x0000ffff)\n\tlibEGL.so.1 => not found\n\tlibGLdispatch.so.0 => not found\n",
    );
    let refusal = fixture.sweep().unwrap_err();
    let distribution = fixture.distribution().display().to_string();
    for fragment in [
        "libskiko-linux-arm64.so",
        "libEGL.so.1",
        "libGLdispatch.so.0",
        distribution.as_str(),
        "GUEST_PACKAGES",
        "crates/avl-vm/src/worker/docker.rs",
    ] {
        assert!(refusal.message.contains(fragment), "{fragment}: {}", refusal.message);
    }
}

/// A refusal names what it looked at and what it found. The stale case says the property is a leftover rather
/// than that a window manager is missing: one is a worker to recycle, the other a unit to look at.
#[test]
fn a_guest_refusal_names_what_it_checked_and_what_it_found() {
    type Damage = fn(&mut Fixture);
    let cases: [(&str, Damage, &[&str]); 5] = [
        (
            "a display that does not answer",
            |fixture| fixture.worker().fail_with(XDPYINFO_TOOL, 1),
            &[":88", XDPYINFO_TOOL, XVFB_PROCESS],
        ),
        (
            "no window manager at all",
            |fixture| fixture.worker().reply(&root_xprop(), ABSENT),
            &[":88", SUPPORTING_WM_CHECK_PROPERTY, FLUXBOX_PROCESS],
        ),
        (
            "a stale window manager property",
            |fixture| {
                fixture.worker().reply(&self_xprop(), &xprop_answer("0x600011"));
            },
            &[WINDOW_LOWER, "0x600011", "exited"],
        ),
        // A musl guest is told it is one, by name, which is why the musl loader is statted rather than run.
        (
            "a musl guest",
            |fixture| {
                fixture.remove_glibc_loader();
                fixture.stage_musl_loader();
            },
            &["musl", "glibc", "DOCKER_BASE_IMAGE"],
        ),
        (
            "no dynamic loader at all",
            |fixture| fixture.remove_glibc_loader(),
            &["glibc", "DOCKER_BASE_IMAGE"],
        ),
    ];
    assert_damage_mentions(&cases);
}

// --- a distribution without a shared object ------------------------------------------------------------------

/// A distribution with no shared object has nothing to sweep: the sweep answers 0 and runs no `ldd`. The probe of a
/// host says so in a note, because "resolved everything" and "there was nothing to resolve" are the same success.
#[test]
fn a_distribution_without_a_shared_object_sweeps_nothing_and_the_probe_notes_it() {
    type Prepare = fn(&Fixture);
    let cases: [(&str, Prepare); 2] = [
        ("the distribution does not exist", |fixture| {
            fixture.empty_distribution();
        }),
        ("the distribution exists and holds no shared object", |fixture| {
            fixture.empty_distribution();
            let java = fixture.distribution().join("jbr/bin/java");
            fs::create_dir_all(java.parent().unwrap()).unwrap();
            fs::write(java, "#!/bin/sh\n").unwrap();
        }),
    ];
    for (name, prepare) in cases {
        let mut fixture = healthy_host();
        prepare(&fixture);
        assert_eq!(fixture.sweep().expect(name), 0, "{name}");
        assert!(!fixture.validator.runner.ran(&format!("{LDD_TOOL} ")), "{name}");
        let notes = fixture.probe_host().expect(name);
        let wanted = format!(
            "the distribution {} holds no shared object, so ldd checked nothing",
            fixture.distribution().display()
        );
        assert_eq!(notes, [wanted], "{name}");
    }
}

// --- the C library --------------------------------------------------------------------------------------------

/// The C library is checked **first**: every other step runs a glibc program, so nothing else is even attempted on
/// a guest that cannot load one.
#[test]
fn the_c_library_is_checked_before_anything_that_needs_it() {
    let mut fixture = Fixture::healthy();
    fixture.remove_glibc_loader();
    let refusal = fixture.validate().unwrap_err();
    assert_eq!(refusal.code, "linux_glibc_missing", "{}", refusal.message);
    assert!(fixture.calls().is_empty(), "{:?}", fixture.calls());
}

/// Only the trailing `version 2.39` is read: the parenthesised half is the distribution's packaging string.
#[test]
fn the_glibc_release_is_read_from_what_the_loader_prints() {
    let cases = [
        (GLIBC_LINE, "2.39"),
        ("ld.so (GNU libc) stable release version 2.31.\n", "2.31"),
        ("ld.so (GNU libc) stable release version 2.40.1.\n", "2.40.1"),
    ];
    for (printed, release) in cases {
        let mut fixture = Fixture::healthy();
        fixture.worker().reply(GLIBC_LABEL, printed);
        assert_eq!(fixture.validate().unwrap().glibc, release, "{printed:?}");
    }
}

/// The loaders are the ones of the architecture this agent was built for, because the controller installs the
/// agent build of the guest's architecture. The four paths are pinned here, so a changed one is a visible change.
#[test]
fn the_loaders_follow_the_agents_own_architecture() {
    use crate::linux::{AARCH64_LOADERS, Loaders, X86_64_LOADERS};

    fn fields(loaders: &Loaders) -> [&'static str; 3] {
        [loaders.glibc, loaders.musl, loaders.jbr_platform]
    }

    let defaults = LoaderPaths::default();
    assert_eq!(defaults.glibc, PathBuf::from(LOADERS.glibc));
    assert_eq!(defaults.musl, PathBuf::from(LOADERS.musl));
    let expected = if std::env::consts::ARCH == "x86_64" {
        &X86_64_LOADERS
    } else {
        &AARCH64_LOADERS
    };
    assert_eq!(fields(&LOADERS), fields(expected), "{}", std::env::consts::ARCH);
    assert_eq!(
        fields(&AARCH64_LOADERS),
        ["/lib/ld-linux-aarch64.so.1", "/lib/ld-musl-aarch64.so.1", "linux_aarch64"]
    );
    assert_eq!(
        fields(&X86_64_LOADERS),
        ["/lib64/ld-linux-x86-64.so.2", "/lib/ld-musl-x86_64.so.1", "linux_x64"]
    );
}

/// The musl loader is statted and never run: musl's `ldd` *is* the loader and answers a usage block.
#[test]
fn the_musl_loader_is_never_run() {
    let mut fixture = Fixture::healthy();
    fixture.remove_glibc_loader();
    fixture.stage_musl_loader();
    fixture.validate().unwrap_err();
    assert!(
        !fixture.calls().iter().any(|call| call.contains("ld-musl")),
        "{:?}",
        fixture.calls()
    );
}

// --- the sweep ------------------------------------------------------------------------------------------------

/// A soname the swept tree carries a file for is not a base-image gap: measured on `air-linux-2`, every
/// `jbr/lib/*.so` reports `libjvm.so => not found`, and `libjvm.so` is in the tree one directory below.
#[test]
fn a_soname_the_tree_ships_is_not_a_base_image_gap() {
    let mut fixture = Fixture::healthy();
    fixture.empty_distribution();
    fixture.stage_shared_object("jbr/lib/server/libjvm.so", "\tlibc.so.6 => /lib/libc.so.6 (0x0000ffff)\n");
    for name in ["libjava.so", "libjcef.so", "libawt_xawt.so"] {
        fixture.stage_shared_object(&format!("jbr/lib/{name}"), "\tlibjvm.so => not found\n");
    }
    assert_eq!(fixture.sweep().unwrap(), 4);
}

/// Tree-wide, so the `libjvm.so` of the JBR excuses an object in another directory of the distribution.
#[test]
fn a_soname_shipped_anywhere_in_the_distribution_covers_every_object() {
    let mut fixture = Fixture::healthy();
    fixture.empty_distribution();
    fixture.stage_shared_object("jbr/lib/server/libjvm.so", "\tlibc.so.6 => /lib/libc.so.6 (0x0000ffff)\n");
    fixture.stage_shared_object("plugins/native/lib/libjava.so", "\tlibjvm.so => not found\n");
    fixture.sweep().unwrap();
}

/// What the tree ships must not mask what the base image owes: the JBR's own SwiftShader `libEGL.so` differs from
/// `libegl1`'s `libEGL.so.1` by the version suffix alone.
#[test]
fn a_soname_the_base_image_must_supply_is_not_excused_by_the_tree() {
    let mut fixture = Fixture::healthy();
    fixture.empty_distribution();
    fixture.stage_shared_object("jbr/lib/libEGL.so", "\tlibc.so.6 => /lib/libc.so.6 (0x0000ffff)\n");
    fixture.stage_shared_object("lib/libskiko-linux-arm64.so", "\tlibEGL.so.1 => not found\n");
    let refusal = fixture.sweep().unwrap_err();
    assert_eq!(refusal.code, SHARED_OBJECT_UNRESOLVED_CODE, "{}", refusal.message);
    assert!(refusal.message.contains("libEGL.so.1"), "{}", refusal.message);
}

/// Both spellings of a shared object's name (`*.so`, `*.so.*`), regular files only; everything else in a 4 GB
/// distribution is skipped. `notes.so.txt` is swept because `*.so.*` matches it, as the script's `find` did.
#[test]
fn the_sweep_reads_shared_objects_and_nothing_else() {
    let mut fixture = Fixture::healthy();
    fixture.empty_distribution();
    let resolved = "\tlibc.so.6 => /lib/libc.so.6 (0x0000ffff)\n";
    for name in ["lib/libjvm.so", "lib/libEGL.so.1", "lib/libfoo.so.1.2.3", "lib/notes.so.txt"] {
        fixture.stage_shared_object(name, resolved);
    }
    let root = fixture.distribution();
    for name in ["lib/app.jar", "bin/idea.sh", "plugins/readme.sonnet"] {
        let path = root.join(name);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(path, "not a shared object").unwrap();
    }
    // A symlink and a directory named like a shared object are not regular files.
    std::os::unix::fs::symlink(root.join("lib/libjvm.so"), root.join("lib/liblink.so")).unwrap();
    fs::create_dir_all(root.join("lib/libdir.so")).unwrap();
    assert_eq!(fixture.sweep().unwrap(), 4);
}

/// The distribution of a guest runfiles tree is one link to a directory of the share. The sweep follows that link and
/// reads the objects below it, so a linked distribution is no empty one.
#[test]
fn a_distribution_that_is_a_link_is_swept_through_the_link() {
    let mut fixture = Fixture::healthy();
    let target = fixture.distribution().with_file_name("share-dist");
    fs::rename(fixture.distribution(), &target).unwrap();
    std::os::unix::fs::symlink(&target, fixture.distribution()).unwrap();
    assert_eq!(find_shared_objects(&fixture.distribution()).unwrap().len(), 2);
    assert_eq!(fixture.sweep().unwrap(), 2);
}

/// `ldd`'s own failure is not a refusal: it fails for a file that is not an ELF object, which is not this check's
/// business.
#[test]
fn an_ldd_that_cannot_read_a_file_is_not_a_refusal() {
    let mut fixture = Fixture::healthy();
    fixture.worker().fail_with("ldd jbr/lib/libjcef.so", 1);
    assert_eq!(fixture.sweep().unwrap(), 2);
}

/// Deduplicated and ordered (`sort -u`): the same broken worker names the same libraries on two boots.
#[test]
fn unresolved_sonames_are_deduplicated_and_ordered() {
    let output = [
        "\tlibgtk-3.so.0 => not found",
        "\tlibEGL.so.1 => not found",
        "\tlibc.so.6 => /lib/libc.so.6 (0x0000ffff)",
        "\tlibEGL.so.1 => not found",
        "\tlibatspi.so.0 => not found",
    ]
    .join("\n");
    assert_eq!(unresolved_sonames(&output), ["libEGL.so.1", "libatspi.so.0", "libgtk-3.so.0"]);
    assert_eq!(
        unresolved_sonames("\tlibc.so.6 => /lib/libc.so.6 (0x0000ffff)\n"),
        Vec::<String>::new()
    );
}

/// The refusal shows ten objects and says how many it did not show.
#[test]
fn a_long_unresolved_list_is_capped_and_says_so() {
    let mut fixture = Fixture::healthy();
    fixture.empty_distribution();
    for index in 0..MAX_REPORTED_UNRESOLVED + 5 {
        fixture.stage_shared_object(&format!("lib/lib{index:02}.so"), "\tlibc.so.6 => not found\n");
    }
    let refusal = fixture.sweep().unwrap_err();
    assert!(refusal.message.contains("(and 5 more)"), "{}", refusal.message);
    assert_eq!(refusal.message.matches(" needs ").count(), MAX_REPORTED_UNRESOLVED);
    // The first ten in order, so two boots of the same worker name the same objects.
    assert!(refusal.message.contains("lib/lib00.so needs"), "{}", refusal.message);
    assert!(!refusal.message.contains("lib/lib10.so"), "{}", refusal.message);
}

// --- the window-manager wait ----------------------------------------------------------------------------------

/// The measured first boot: fluxbox has not taken the display for the first few probes, and then has. The report
/// gives the probe it answered on, and the wait pauses one interval fewer than it probes.
#[test]
fn a_window_manager_that_registers_on_a_later_probe_is_waited_for() {
    let mut fixture = Fixture::healthy();
    fixture.worker().queue(&root_xprop(), &[ABSENT, ABSENT, ABSENT]);
    let report = fixture.validate().unwrap();
    assert_eq!(report.window_manager_probes, 4);
    assert_eq!(report.window_manager, WINDOW_LOWER);
    assert_eq!(fixture.validator.runner.paused, [WAIT_INTERVAL; 3]);
    // Three passes that found nothing made the root hop alone, and the fourth made both.
    assert_eq!(fixture.validator.runner.xprop_hops(), 5);
}

/// A window manager that never registers refuses after the whole budget, says how long it waited, names the unit,
/// and nothing after it runs.
#[test]
fn a_window_manager_that_never_registers_refuses_after_the_budget() {
    let mut fixture = Fixture::healthy();
    fixture.worker().reply(&root_xprop(), ABSENT);
    let refusal = fixture.validate().unwrap_err();
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("linux_window_manager_missing", AgentExit::Refused)
    );
    for fragment in [":88", SUPPORTING_WM_CHECK_PROPERTY, FLUXBOX_PROCESS, "60 s"] {
        assert!(refusal.message.contains(fragment), "{fragment}: {}", refusal.message);
    }
    let worker = &fixture.validator.runner;
    assert_eq!(worker.xprop_hops(), WAIT_ATTEMPTS as usize);
    assert_eq!(worker.paused.len(), WAIT_ATTEMPTS as usize - 1);
    assert!(!worker.ran(&format!("{LDD_TOOL} ")), "{:?}", worker.calls);
}

/// **A stale property must not be waited out**: absent means fluxbox has not taken the display yet, stale means it
/// ran and exited, which no amount of waiting improves.
#[test]
fn a_stale_window_manager_property_refuses_without_waiting() {
    type Damage = fn(&mut Fixture);
    let cases: [(&str, Damage); 3] = [
        ("the support window does not answer for itself", |fixture| {
            fixture.worker().reply(&self_xprop(), ABSENT);
        }),
        ("the support window points at some other window", |fixture| {
            fixture.worker().reply(&self_xprop(), &xprop_answer("0x600011"));
        }),
        ("the support window no longer exists", |fixture| {
            fixture.worker().fail_with(&self_xprop(), 1);
        }),
    ];
    for (name, damage) in cases {
        let mut fixture = Fixture::healthy();
        damage(&mut fixture);
        let refusal = fixture.validate().expect_err(name);
        assert_eq!(refusal.code, "linux_window_manager_stale", "{name}");
        let worker = &fixture.validator.runner;
        assert!(worker.paused.is_empty(), "{name}: waited {:?}", worker.paused);
        assert_eq!(worker.xprop_hops(), 2, "{name}");
        assert!(refusal.message.contains("exited"), "{name}: {}", refusal.message);
    }
}

/// A property that goes stale after a few absent probes is refused on the pass that sees it, not waited out.
#[test]
fn a_property_that_goes_stale_during_the_wait_is_refused_on_that_pass() {
    let mut fixture = Fixture::healthy();
    fixture.worker().queue(&root_xprop(), &[ABSENT, ABSENT]);
    fixture.worker().reply(&self_xprop(), &xprop_answer("0x600011"));
    let refusal = fixture.validate().unwrap_err();
    assert_eq!(refusal.code, "linux_window_manager_stale", "{}", refusal.message);
    assert_eq!(fixture.validator.runner.paused.len(), 2);
}

// --- the window-manager probe ---------------------------------------------------------------------------------

/// Both hops carry the display twice: in the environment and as `xprop`'s own `-display`. The window manager script of
/// `LinuxIdeDistribution.linuxCommandLine` passes the environment. The display probe gets the environment alone, which
/// is all `xdpyinfo` reads.
#[test]
fn both_window_manager_hops_are_told_the_display_twice() {
    let mut fixture = Fixture::healthy();
    fixture.validate().unwrap();
    let worker = &fixture.validator.runner;
    let display = vec![("DISPLAY".to_owned(), ":88".to_owned())];
    let hops: Vec<&String> = worker
        .calls
        .iter()
        .filter(|call| call.starts_with(&format!("{XPROP_TOOL} ")))
        .collect();
    assert_eq!(hops, [&root_xprop(), &self_xprop()]);
    for hop in hops {
        assert!(hop.contains("-display :88"), "{hop}");
        assert_eq!(worker.environments[hop], display, "{hop}");
    }
    assert_eq!(worker.environments[XDPYINFO_TOOL], display);
}

/// The verb's own refusal code, and the verb end to end on this host, which is no provisioned worker: whichever
/// check refuses, it is a `linux_` code under this verb with the family's status.
#[test]
fn the_validate_guest_verb_refuses_under_its_own_name() {
    assert_eq!(verb_refusal_code(AgentVerb::ValidateGuest), "guest_validate_guest_failed");
    let answered = run_agent(&[AgentVerb::ValidateGuest.as_str(), ":8765"], b"");
    assert_eq!(answered.exit, 70, "{}", answered.stderr);
    assert!(answered.code().starts_with("linux_"), "{}", answered.stderr);
    assert_eq!(answered.failure()["command"], AgentVerb::ValidateGuest.as_str());
}

// --- the host probe of lane-run ---------------------------------------------------------------------------------

/// A host of `lane-run` that has what the lane IDE needs: the healthy worker with `git` on its PATH.
fn healthy_host() -> Fixture {
    let mut fixture = Fixture::healthy();
    fixture.worker().reply("git --version", "git version 2.43.0\n");
    fixture
}

// A host with an answering display, a window manager, the libraries of the distribution, git and the screen of a lane
// guest passes the probe with no note.
#[test]
fn a_host_that_has_what_the_lane_ide_needs_passes_the_probe_with_no_note() {
    let mut fixture = healthy_host();

    assert_eq!(fixture.probe_host().unwrap(), Vec::<String>::new());
    assert!(fixture.worker().ran("git --version"));
}

// A screen smaller than the 1920x1080 of a lane guest, such as the Xvfb of a TeamCity agent at 1024x768, is a note and
// not a refusal. An answer that names no dimensions is a note too.
#[test]
fn a_smaller_screen_is_a_note_and_not_a_refusal() {
    let mut fixture = healthy_host();
    fixture.worker().reply(
        XDPYINFO_TOOL,
        "screen #0:\n  dimensions:    1024x768 pixels (271x203 millimeters)\n",
    );
    assert_eq!(
        fixture.probe_host().unwrap(),
        [
            "the screen of the display :88 is 1024x768, smaller than the 1920x1080 of a lane guest, so a window of the \
          lane IDE can lay out differently"
        ]
    );

    fixture.worker().reply(XDPYINFO_TOOL, "name of display:    :88\n");
    assert_eq!(
        fixture.probe_host().unwrap(),
        ["xdpyinfo names no screen dimensions of the display :88"]
    );
}

// A host without git is refused by name before the lane IDE starts, because the merge-conflict and worktree scenarios
// spawn it.
#[test]
fn a_host_without_git_is_refused_by_name() {
    let mut fixture = Fixture::healthy();
    fixture.worker().fail_with("git --version", 127);

    let refusal = fixture.probe_host().unwrap_err();

    assert_eq!(refusal.code, GIT_MISSING_CODE);
    assert!(
        refusal
            .message
            .starts_with("this host cannot run the lane IDE: `git --version` fails"),
        "{}",
        refusal.message
    );
}

// A host whose display has no window manager, or whose distribution links a library that the host lacks, is refused
// with the code of the same check of a worker. The message names the host, and the refusal of a library names the
// first missing one. The probe stops there and asks for no git.
#[test]
fn a_host_without_a_window_manager_or_a_library_is_refused_with_the_code_of_the_guest_check() {
    let mut fixture = healthy_host();
    fixture.worker().reply(&root_xprop(), ABSENT);
    let refusal = fixture.probe_host().unwrap_err();
    assert_eq!(refusal.code, "linux_window_manager_missing");
    assert!(
        refusal.message.starts_with("this host cannot run the lane IDE: "),
        "{}",
        refusal.message
    );

    let mut fixture = healthy_host();
    fixture.stage_shared_object("lib/libskiko-linux-arm64.so", "\tlibEGL.so.1 => not found\n");
    let refusal = fixture.probe_host().unwrap_err();
    assert_eq!(refusal.code, "linux_shared_object_unresolved");
    assert!(
        refusal.message.starts_with("this host cannot run the lane IDE: ") && refusal.message.contains("needs libEGL.so.1"),
        "{}",
        refusal.message
    );
    assert!(!fixture.worker().ran("git"), "the probe stops at the first missing requirement");
}
