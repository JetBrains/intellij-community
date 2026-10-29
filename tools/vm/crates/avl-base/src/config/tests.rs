use pretty_assertions::assert_eq;

use super::*;

const WORKSPACE: &str = "/repo/community/tools/vm";

/// Every test names its whole environment, so a default under test is a default and not something inherited
/// from the shell. `HOME` is always named: the runtime root and the Tart home are built from it.
fn env(pairs: &[(&str, &str)]) -> Environment {
    let mut environment = Environment::from_pairs([("HOME", "/Users/air")]);
    for (name, value) in pairs {
        environment.set(*name, *value);
    }
    environment
}

/// The host the tests of a Tart or a Parallels pool resolve on: this host, or a Linux host where this host is
/// Windows, which drives neither. So every such test runs on every host, and a macOS or a Linux host resolves as the
/// controller there does.
const POOL_HOST: HostOs = match HostOs::CURRENT {
    HostOs::Windows => HostOs::Linux,
    host => host,
};

fn tart_macos() -> Selection {
    Selection {
        backend: Backend::Tart,
        guest_os: GuestOs::Macos,
    }
}

fn parallels() -> Selection {
    Selection {
        backend: Backend::Parallels,
        guest_os: GuestOs::Macos,
    }
}

fn docker() -> Selection {
    Selection {
        backend: Backend::Docker,
        guest_os: GuestOs::Linux,
    }
}

/// macOS 15 on Apple silicon: the release a macOS host of these tests resolves on, unless a test names another. It
/// keeps the host engine.
const MACOS_15: MacosHost = MacosHost {
    major: 15,
    apple_silicon: true,
};

/// macOS 26 on Apple silicon: the first release that runs Apple `container` by default.
const MACOS_26: MacosHost = MacosHost {
    major: 26,
    apple_silicon: true,
};
fn container_linux() -> Selection {
    Selection {
        backend: Backend::ContainerLinux,
        guest_os: GuestOs::Linux,
    }
}

/// The memory of the host every test loads on: 64 GiB, which gives the Apple `container` pool its floor of two slots,
/// as `FIXTURE_HOST_MEMORY_MIB` of the host testkit does.
const HOST_MEMORY_MIB: u64 = 64 * 1024;

/// The facts of a host of the release `macos` and of [`HOST_MEMORY_MIB`].
const fn facts(macos: MacosHost) -> HostFacts {
    HostFacts {
        macos: Some(macos),
        memory_mib: Some(HOST_MEMORY_MIB),
    }
}

fn load(selection: Selection, environment: &Environment) -> Config {
    load_on(POOL_HOST, selection, environment)
}

fn load_on(host: HostOs, selection: Selection, environment: &Environment) -> Config {
    load_on_release(host, MACOS_15, selection, environment)
}

fn load_on_release(host: HostOs, macos: MacosHost, selection: Selection, environment: &Environment) -> Config {
    Config::load_on(host, facts(macos), selection, environment, Path::new(WORKSPACE))
        .unwrap_or_else(|refusal| panic!("the environment was refused on {host}: {refusal:?}"))
}

fn refuse_on(host: HostOs, selection: Selection, environment: &Environment) -> Refusal {
    refuse_on_release(host, MACOS_15, selection, environment)
}

fn refuse_on_release(host: HostOs, macos: MacosHost, selection: Selection, environment: &Environment) -> Refusal {
    match Config::load_on(host, facts(macos), selection, environment, Path::new(WORKSPACE)) {
        Ok(config) => panic!("the environment was accepted on {host}: {config:?}"),
        Err(refusal) => refusal,
    }
}

fn refuse(selection: Selection, environment: &Environment) -> Refusal {
    refuse_on(POOL_HOST, selection, environment)
}

// A Docker worker on a Linux host, so the engine is the host engine and the defaults are the same on every host.
#[test]
fn the_linux_defaults_are_the_documented_ones() {
    let config = load_on(HostOs::Linux, docker(), &env(&[]));
    let cases = [
        ("vm user", config.vm_user.as_str(), "admin"),
        ("vm home", &config.vm_home, "/home/admin"),
        ("vm data", &config.vm_data, "/home/admin/WorkerData"),
        ("vm tmp", &config.vm_tmp, "/home/admin/WorkerData/tmp"),
        ("vm node", &config.vm_node, "/usr/local/bin/node"),
        ("vm uid", &config.vm_uid, "1000"),
        ("guest display", &config.guest_display, ":88"),
        ("share mount", config.guest.share_mount, "/mnt/AirVmShares"),
        ("link flags", config.guest.link_flags, "-sfn"),
    ];
    for (what, got, want) in cases {
        assert_eq!(got, want, "{what}");
    }
    // Host paths carry the host's own separator, so they are built rather than written out.
    assert_eq!(config.tart_home, Path::new("/Users/air").join(".tart"));
    assert_eq!(config.runtime_root, HostOs::Linux.runtime_root(Path::new("/Users/air"), &env(&[])));
    assert_eq!(config.image_root, Path::new(WORKSPACE).join("provision"));
    assert_eq!(config.vm_root_disk_gb, 0);
    assert_eq!(config.workers, ["air-docker-1", "air-docker-2"]);
    assert_eq!(config.tart, None);
    assert_eq!(
        config.daemon,
        DaemonBudgets {
            port: 27_100,
            boot: Duration::from_mins(20),
            health: Duration::from_secs(180),
            active_execution: Duration::from_mins(30),
            progress_gap: Duration::from_secs(300),
        }
    );
}

// The runtime root keeps its on-disk name: the live pool state and the image pipeline's Packer state are under it.
// The Bazel root is the `startup:<os> --output_user_root` of community/common.bazelrc. One variable moves each.
#[test]
fn the_runtime_root_keeps_its_on_disk_name() {
    let home = Path::new("/Users/air");
    let cases = [
        (
            HostOs::Macos,
            home.join("Library/Application Support/JetBrains/macos-vm-ui-tests"),
            home.join("Library/Caches/JetBrains/MonorepoBazel"),
        ),
        (
            HostOs::Linux,
            home.join(".local/state/JetBrains/air-vm-ui-tests"),
            home.join(".cache/JetBrains/MonorepoBazel"),
        ),
        (
            HostOs::Windows,
            home.join("AppData").join("Local").join("JetBrains").join("air-vm-ui-tests"),
            PathBuf::from("C:/ProgramData/_bazel"),
        ),
    ];
    for (host, runtime_root, bazel_user_root) in cases {
        let config = load_on(host, docker(), &env(&[]));
        assert_eq!(config.runtime_root, runtime_root, "{host}");
        assert_eq!(config.configured_bazel_user_root(), bazel_user_root, "{host}");
        let moved = load_on(
            host,
            docker(),
            &env(&[("AIR_VM_RUNTIME_ROOT", "/state"), ("AIR_VM_BAZEL_USER_ROOT", "/bazel")]),
        );
        assert_eq!(moved.runtime_root, Path::new("/state"), "{host}");
        assert_eq!(moved.configured_bazel_user_root(), Path::new("/bazel"), "{host}");
    }
    // Config::load resolves the defaults of the current host.
    let current = Config::load(HostFacts::without_memory(), docker(), &env(&[]), Path::new(WORKSPACE)).unwrap();
    assert_eq!(current.runtime_root, load_on(HostOs::CURRENT, docker(), &env(&[])).runtime_root);
}

// A Windows host keeps the runtime root in its local application data, which `LOCALAPPDATA` names. The home falls
// back to `USERPROFILE` there, because only a POSIX shell sets `HOME` on Windows. A Unix host reads `HOME` only.
#[test]
fn a_windows_host_reads_its_own_home_and_application_data() {
    let windows = Environment::from_pairs([("USERPROFILE", r"C:\Users\air"), ("LOCALAPPDATA", r"D:\Local")]);
    let config = load_on(HostOs::Windows, docker(), &windows);
    assert_eq!(
        config.runtime_root,
        Path::new(r"D:\Local").join("JetBrains").join("air-vm-ui-tests")
    );
    assert_eq!(config.tart_home, Path::new(r"C:\Users\air").join(".tart"));
    // `HOME` comes first where a shell set it.
    let shell = Environment::from_pairs([("HOME", "/c/air"), ("USERPROFILE", r"C:\Users\air")]);
    assert_eq!(
        load_on(HostOs::Windows, docker(), &shell).tart_home,
        Path::new("/c/air").join(".tart")
    );

    let unset = Environment::from_pairs([("HOME", "")]);
    let refusal = refuse_on(HostOs::Windows, docker(), &unset);
    assert_eq!(refusal.code, "invalid_environment");
    assert!(
        refusal.message.starts_with("HOME and USERPROFILE are not set;"),
        "{}",
        refusal.message
    );
    let profile_only = Environment::from_pairs([("USERPROFILE", "/Users/air")]);
    for host in [HostOs::Macos, HostOs::Linux] {
        assert_eq!(
            refuse_on(host, docker(), &profile_only).message,
            "HOME is not set; the runtime root and the Tart home are built from it",
            "{host}"
        );
    }
}

// A Windows host drives the two Linux containers, Docker and container-linux. The two macOS VM backends are refused
// by name before a setting is read, and a macOS or a Linux host drives them all.
#[test]
fn a_windows_host_drives_the_two_container_backends() {
    for selection in [tart_macos(), parallels()] {
        let refusal = refuse_on(HostOs::Windows, selection, &env(&[]));
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("unsupported_host_backend", Exit::USAGE),
            "{selection}"
        );
        assert_eq!(
            refusal.message,
            format!(
                "a windows host drives the Docker and the container-linux backends, and --backend {selection} needs a \
                 macOS or a Linux host; pass --backend docker or container-linux"
            )
        );
        for host in [HostOs::Macos, HostOs::Linux] {
            assert!(host.drives(selection.backend), "{host} {selection}");
        }
    }
    assert_eq!(load_on(HostOs::Windows, docker(), &env(&[])).backend, Backend::Docker);
    assert_eq!(
        load_on(HostOs::Windows, container_linux(), &env(&[])).backend,
        Backend::ContainerLinux
    );
}

// Every host that is given no backend runs the Docker pool and loads its settings: a Mac of macOS 15, a Linux host,
// which has no Tart, and a Windows host, which drives Docker only, each on the engine of its host environment.
#[test]
fn every_host_defaults_to_docker() {
    for (host, engine) in [
        (HostOs::Macos, DockerEngine::Host),
        (HostOs::Linux, DockerEngine::Host),
        (HostOs::Windows, DockerEngine::Host),
    ] {
        let config = load_on(host, Selection::DEFAULT, &env(&[]));
        assert_eq!(
            (config.backend, config.guest_os, config.docker_engine),
            (Backend::Docker, GuestOs::Linux, engine),
            "{host}"
        );
    }
}

// Windows compares environment names without case, so an override spelled `PATH` replaces the `Path` a Windows
// process inherits. A Unix host keeps two names that differ in case apart.
#[test]
fn a_windows_host_compares_variable_names_without_case() {
    use std::cmp::Ordering;
    let windows = HostOs::Windows;
    assert_eq!(windows.compare_variables("Path", "PATH"), Ordering::Equal);
    assert_eq!(windows.compare_variables("path", "PATHEXT"), Ordering::Less);
    assert_eq!(windows.compare_variables("TEMP", "tmp"), Ordering::Less);
    for host in [HostOs::Macos, HostOs::Linux] {
        assert_eq!(host.compare_variables("Path", "PATH"), Ordering::Greater);
        assert_eq!(host.compare_variables("PATH", "PATH"), Ordering::Equal);
    }
}

// A message names a host program by the last component of its path. On Windows either slash separates the
// components and the `.exe` suffix is dropped. A Unix host keeps a backslash and a suffix, which a file name there
// can hold.
#[test]
fn a_program_is_named_by_the_last_component_of_its_path() {
    let cases = [
        (HostOs::Windows, r"C:\Program Files\Docker\docker.exe", "docker"),
        (HostOs::Windows, "C:/tools/Git.EXE", "Git"),
        (HostOs::Windows, "docker", "docker"),
        (HostOs::Windows, ".exe", ""),
        (HostOs::Windows, "exe", "exe"),
        (HostOs::Windows, r"bin\täst.exe", "täst"),
        (HostOs::Linux, "/usr/bin/docker", "docker"),
        (HostOs::Linux, r"odd\name.exe", r"odd\name.exe"),
        (HostOs::Macos, "/opt/homebrew/bin/tart", "tart"),
        (HostOs::Macos, "prlctl", "prlctl"),
    ];
    for (host, program, name) in cases {
        assert_eq!(host.program_name(program), name, "{host} {program}");
    }
}

// The guest's architecture is nothing a selection names: Tart and Parallels run Apple-silicon guests, and a Docker
// worker runs the host's own architecture, x86_64 on any x86_64 host and arm64 on any arm64 host.
#[test]
fn the_guest_architecture_follows_the_backend_and_the_host() {
    for selection in [tart_macos(), parallels()] {
        assert_eq!(load(selection, &env(&[])).guest_arch, GuestArch::Arm64, "{selection:?}");
    }
    let docker = load(docker(), &env(&[])).guest_arch;
    assert_eq!(docker, GuestArch::of(Backend::Docker));
    let x86_64_host = cfg!(target_arch = "x86_64");
    assert_eq!(docker == GuestArch::X86_64, x86_64_host);
    assert_eq!(load(container_linux(), &env(&[])).guest_arch, docker);
    assert_eq!((GuestArch::Arm64.as_str(), GuestArch::Arm64.oci_arch()), ("aarch64", "arm64"));
    assert_eq!((GuestArch::X86_64.as_str(), GuestArch::X86_64.oci_arch()), ("x86_64", "amd64"));
}

// Each setting has one name: a spelling another controller once read is not read.
#[test]
fn a_retired_name_is_not_read() {
    let config = load(
        docker(),
        &env(&[
            ("VM_DATA", "/retired"),
            ("PARALLELS_VM_DATA", "/retired"),
            ("VM_USER", "retired"),
            ("TART_RUNTIME_ROOT", "/retired"),
            ("MACOS_VM_RUNTIME_ROOT", "/retired"),
            ("TART_BOOT_TIMEOUT", "1"),
            ("VM_CPU", "1"),
        ]),
    );
    assert_eq!(config.vm_data, "/home/admin/WorkerData");
    assert_eq!(config.vm_user, "admin");
    assert_eq!(config.runtime_root, load(docker(), &env(&[])).runtime_root);
    assert_eq!((config.boot_timeout_seconds, config.vm_cpu), (180, 8));
}

// The daemon budgets are read once with every other setting, so a malformed one refuses at load.
#[test]
fn the_daemon_budgets_come_from_the_environment() {
    let tuned = load(
        docker(),
        &env(&[
            ("AIR_VM_DAEMON_PORT", "9000"),
            ("AIR_VM_DAEMON_BOOT_TIMEOUT", "60"),
            ("AIR_VM_DAEMON_HEALTH_TIMEOUT", "240"),
            ("AIR_VM_DAEMON_EXECUTION_TIMEOUT", "900"),
            ("AIR_VM_DAEMON_PROGRESS_TIMEOUT", "30"),
        ]),
    );
    assert_eq!(
        tuned.daemon,
        DaemonBudgets {
            port: 9_000,
            boot: Duration::from_mins(1),
            health: Duration::from_secs(240),
            active_execution: Duration::from_mins(15),
            progress_gap: Duration::from_secs(30),
        }
    );
}

// Presentation is lenient: only `off` turns the viewer or the dashboard off, and an unknown theme asks the terminal.
#[test]
fn the_presentation_defaults_on_and_turns_off_by_name() {
    assert_eq!(Presentation::load(&env(&[])), Presentation::default());
    assert_eq!(
        Presentation::default(),
        Presentation {
            viewer: true,
            dashboard: true,
            theme: None
        }
    );
    let off = Presentation::load(&env(&[
        (VIEWER_OFF_VARIABLE, "off"),
        (DASHBOARD_OFF_VARIABLE, "off"),
        (THEME_VARIABLE, "dark"),
    ]));
    assert_eq!(
        off,
        Presentation {
            viewer: false,
            dashboard: false,
            theme: Some(Theme::Dark)
        }
    );
    let other = Presentation::load(&env(&[(VIEWER_OFF_VARIABLE, "no"), (THEME_VARIABLE, "solarized")]));
    assert!(other.viewer && other.theme.is_none(), "{other:?}");
    assert_eq!(Presentation::load(&env(&[(THEME_VARIABLE, "light")])).theme, Some(Theme::Light));
}

// The pins come from the image pipeline's own `versions.env`, embedded at compile time. This reads the same file
// at run time, so an `include_str!` path that points anywhere else fails here. It also checks every name the crate
// reads: `GOLDEN_VM`, `NODE_MAJOR` and `DOCKER_BASE_IMAGE`.
#[test]
fn the_pins_are_the_image_pipelines_own() {
    let path = avl_testkit::repo_path("tools/vm/provision/versions.env");
    let on_disk = std::fs::read_to_string(&path).unwrap_or_else(|error| panic!("cannot read {}: {error}", path.display()));
    assert_eq!(on_disk, pins::VERSIONS_ENV, "the embedded versions.env is another file");

    let parsed = pins::parse(&on_disk).expect("versions.env parses");
    assert!(!parsed.contains_key("LINUX_BASE_REFERENCE"), "the Tart Linux base is retired");
    assert!(!parsed.keys().any(|name| name.starts_with("LIMA_")), "the Lima engine is retired");
    assert_ne!(pins::tart_golden_vm(), "");
    assert!(pins::guest_node_major() > 0);
    // A Docker Hub reference, pinned by digest.
    let docker_base = pins::docker_base_image();
    assert!(
        docker_base.starts_with("ubuntu:") && docker_base.contains("@sha256:"),
        "{docker_base}"
    );
}

#[test]
fn an_unexpanded_reference_in_the_pins_is_refused() {
    pins::parse("A=${B}\nB=1\n").unwrap_err();
    let parsed = pins::parse("# comment\nB=1\nA=x${B}y\n").expect("parses");
    assert_eq!(parsed["A"], "x1y");
}

#[test]
fn the_macos_defaults_differ_only_where_they_should() {
    let config = load(tart_macos(), &env(&[]));
    assert_eq!((config.vm_home.as_str(), config.vm_uid.as_str()), ("/Users/admin", "501"));
    assert_eq!(
        config.vm_node,
        format!("/opt/homebrew/opt/node@{}/bin/node", pins::guest_node_major())
    );
    assert_eq!((config.vm_memory_mib, config.vm_root_disk_gb), (32_768, 120));
    assert_eq!(
        (config.guest.share_mount, config.guest.link_flags),
        ("/Volumes/AirVmShares", "-sfh")
    );
    assert_eq!(config.workers, ["air-macos-1", "air-macos-2"]);
    assert_eq!(config.golden_vm, pins::tart_golden_vm());

    // Parallels differs again, and only in the two places it was set up differently.
    let parallels = load(parallels(), &env(&[]));
    assert_eq!(
        (parallels.vm_user.as_str(), parallels.vm_node.as_str()),
        ("test", "/opt/homebrew/bin/node")
    );
    assert_eq!(parallels.workers, ["macOS"]);
}

// A Docker worker runs the Node of its image. An operator's `AIR_VM_NODE` still wins.
#[test]
fn the_linux_node_is_the_images_unless_it_is_overridden() {
    assert_eq!(load(docker(), &env(&[])).vm_node, "/usr/local/bin/node");
    let overridden = load(docker(), &env(&[("AIR_VM_NODE", "/usr/bin/node")]));
    assert_eq!(overridden.vm_node, "/usr/bin/node");
}

// A run secret stays off the persistent data volume: a Linux guest keeps it on tmpfs. A macOS guest has none, so its
// directory follows the run scratch. Nothing overrides it: `AIR_VM_RUN_SECRETS` may only repeat the derived path.
#[test]
fn a_linux_guest_keeps_run_secrets_on_tmpfs() {
    assert_eq!(load(docker(), &env(&[])).vm_run_secrets, "/dev/shm/air-run-secrets");
    let macos = load(tart_macos(), &env(&[]));
    assert_eq!(macos.vm_run_secrets, format!("{}/run-secrets", macos.vm_tmp));
    let moved_tmp = load(tart_macos(), &env(&[("AIR_VM_TMP", "/Volumes/scratch/tmp")]));
    assert_eq!(moved_tmp.vm_run_secrets, "/Volumes/scratch/tmp/run-secrets");
    let repeated = load(docker(), &env(&[("AIR_VM_RUN_SECRETS", "/dev/shm/air-run-secrets")]));
    assert_eq!(repeated.vm_run_secrets, "/dev/shm/air-run-secrets");
    // A lease release removes the directory whole, so every other path is refused, the guest's root among them.
    for refused in ["/run/air-secrets", "/", "//", "air-secrets", "/dev/shm/air-run-secrets/"] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_RUN_SECRETS", refused)]));
        assert_eq!(refusal.code, "invalid_environment", "{refused}");
        assert!(refusal.message.contains("AIR_VM_RUN_SECRETS"), "{}", refusal.message);
    }
}

// What `rm -rf` may be handed: the derived directory of the guest, and nothing a hand-built `Config` put in its place.
#[test]
fn only_the_derived_run_secrets_directory_is_removable() {
    let linux = load(docker(), &env(&[]));
    assert_eq!(linux.removable_run_secrets_dir().unwrap(), "/dev/shm/air-run-secrets");
    let macos = load(tart_macos(), &env(&[]));
    assert_eq!(macos.removable_run_secrets_dir().unwrap(), format!("{}/run-secrets", macos.vm_tmp));
    let refused = |config: &Config| {
        let refusal = config.removable_run_secrets_dir().unwrap_err();
        assert_eq!(refusal.code, "internal_error", "{}", refusal.message);
        assert!(refusal.message.contains("not removed"), "{}", refusal.message);
    };
    for directory in ["/", "/home/admin", "/dev/shm", "/dev/shm/air-run-secrets/..", "relative"] {
        refused(&Config {
            vm_run_secrets: directory.to_owned(),
            ..linux.clone()
        });
    }
    // The macOS form on a Linux guest is not the Linux one, and a macOS scratch that is relative or climbs out is not
    // the controller's either.
    refused(&Config {
        vm_run_secrets: format!("{}/run-secrets", linux.vm_tmp),
        ..linux
    });
    for vm_tmp in ["/Users/admin/..", "tmp"] {
        refused(&Config {
            vm_tmp: vm_tmp.to_owned(),
            vm_run_secrets: format!("{vm_tmp}/run-secrets"),
            ..macos.clone()
        });
    }
}

#[test]
fn an_empty_variable_is_unset() {
    let config = load(
        docker(),
        &env(&[
            ("AIR_VM_DATA", ""),
            ("AIR_VM_NETWORK", ""),
            ("AIR_VM_CPU", ""),
            ("AIR_VM_SUSPENDABLE", ""),
            ("AIR_VM_WORKERS", ""),
        ]),
    );
    assert_eq!(config.vm_data, "/home/admin/WorkerData");
    assert_eq!((config.vm_network.as_str(), config.vm_cpu, config.vm_suspendable), ("nat", 8, true));
    assert_eq!(config.workers.len(), 2);
}

// Bridged mode carries a second setting; an interface with no mode is carried and unused rather than refused.
#[test]
fn bridged_networking_carries_its_interface() {
    let config = load(
        tart_macos(),
        &env(&[("AIR_VM_NETWORK", "bridged"), ("AIR_VM_BRIDGED_INTERFACE", "en0")]),
    );
    assert_eq!(
        (config.vm_network.as_str(), config.vm_bridged_interface.as_deref()),
        ("bridged", Some("en0"))
    );
    let unused = load(tart_macos(), &env(&[("AIR_VM_BRIDGED_INTERFACE", "en0")]));
    assert_eq!(
        (unused.vm_network.as_str(), unused.vm_bridged_interface.as_deref()),
        ("nat", Some("en0"))
    );
}

#[test]
fn every_refusal_has_its_code_and_exit_status() {
    type Case<'a> = (&'a str, &'a [(&'a str, &'a str)], &'a str, &'a str);
    let cases: [Case<'_>; 20] = [
        (
            "no home to build the runtime root from",
            &[("HOME", "")],
            "invalid_environment",
            "HOME",
        ),
        (
            "a non-enumerated network",
            &[("AIR_VM_NETWORK", "host-only")],
            "invalid_environment",
            "softnet",
        ),
        (
            "bridged with no interface",
            &[("AIR_VM_NETWORK", "bridged")],
            "invalid_environment",
            "AIR_VM_BRIDGED_INTERFACE",
        ),
        (
            "malformed root-disk options",
            &[("AIR_VM_ROOT_DISK_OPTS", "caching=CACHED;sync")],
            "invalid_environment",
            "AIR_VM_ROOT_DISK_OPTS",
        ),
        (
            "a non-integer count",
            &[("AIR_VM_CPU", "many")],
            "invalid_environment",
            "AIR_VM_CPU",
        ),
        ("a zero count", &[("AIR_VM_CPU", "0")], "invalid_environment", "AIR_VM_CPU"),
        ("a negative count", &[("AIR_VM_CPU", "-1")], "invalid_environment", "AIR_VM_CPU"),
        (
            "a count past its ceiling",
            &[("AIR_VM_MAX_WORKERS", "17")],
            "invalid_environment",
            "must not exceed 16",
        ),
        (
            "a non-boolean",
            &[("AIR_VM_SUSPENDABLE", "maybe")],
            "invalid_environment",
            "boolean",
        ),
        (
            "the removed repository share",
            &[("AIR_VM_REPO_SHARE_NAME", "air-macos-repo")],
            "invalid_environment",
            "AIR_VM_REPO_SHARE_NAME is removed",
        ),
        (
            "an unsafe bazel share name",
            &[("AIR_VM_BAZEL_SHARE_NAME", "a/b")],
            "unsafe_name",
            "Bazel share name",
        ),
        (
            "an unsafe worker prefix",
            &[("AIR_VM_WORKER_PREFIX", "air docker")],
            "unsafe_name",
            "worker name prefix",
        ),
        (
            "an unsafe explicit worker",
            &[("AIR_VM_WORKERS", "air-docker-1,air docker 2")],
            "unsafe_name",
            "worker name",
        ),
        (
            "a malformed boot timeout",
            &[("AIR_VM_BOOT_TIMEOUT", "soon")],
            "invalid_environment",
            "AIR_VM_BOOT_TIMEOUT",
        ),
        (
            "a zero daemon health budget",
            &[("AIR_VM_DAEMON_HEALTH_TIMEOUT", "0")],
            "invalid_environment",
            "AIR_VM_DAEMON_HEALTH_TIMEOUT",
        ),
        (
            "a fractional daemon health budget",
            &[("AIR_VM_DAEMON_HEALTH_TIMEOUT", "1.5")],
            "invalid_environment",
            "AIR_VM_DAEMON_HEALTH_TIMEOUT",
        ),
        (
            "a float progress budget",
            &[("AIR_VM_DAEMON_PROGRESS_TIMEOUT", "8.0")],
            "invalid_environment",
            "AIR_VM_DAEMON_PROGRESS_TIMEOUT",
        ),
        (
            "a daemon port past the TCP range",
            &[("AIR_VM_DAEMON_PORT", "70000")],
            "invalid_environment",
            "AIR_VM_DAEMON_PORT",
        ),
        (
            "an empty explicit pool",
            &[("AIR_VM_WORKERS", ", ,")],
            "invalid_worker_pool",
            "names no workers",
        ),
        (
            "a duplicated explicit pool",
            &[("AIR_VM_WORKERS", "air-docker-1,air-docker-1")],
            "invalid_worker_pool",
            "distinct",
        ),
    ];
    // A Docker pool on a macOS host, which reads every variable of the table.
    for (what, pairs, code, mentions) in cases {
        let refusal = refuse_on(HostOs::Macos, docker(), &env(pairs));
        assert_eq!(refusal.code, code, "{what}: {}", refusal.message);
        assert_eq!(refusal.exit, Exit::USAGE, "{what}");
        assert!(refusal.message.contains(mentions), "{what}: {}", refusal.message);
    }
}

// Only a Tart worker has a disk that the controller sizes: 120 GB by default, from 80 to 4000. The floor holds the
// macOS image and the writable state. A Docker pool does not read the setting, so a value that a shell still exports
// is not refused there.
#[test]
fn only_a_tart_worker_has_a_disk_size() {
    assert_eq!(load(tart_macos(), &env(&[])).vm_root_disk_gb, 120);
    assert_eq!(load(tart_macos(), &env(&[("AIR_VM_ROOT_DISK_GB", "80")])).vm_root_disk_gb, 80);
    for (value, mentions) in [("79", "at least 80"), ("4001", "4000"), ("many", "AIR_VM_ROOT_DISK_GB")] {
        let refusal = refuse(tart_macos(), &env(&[("AIR_VM_ROOT_DISK_GB", value)]));
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("invalid_environment", Exit::USAGE),
            "{value}"
        );
        assert!(refusal.message.contains(mentions), "{value}: {}", refusal.message);
    }
    for host in [HostOs::Macos, HostOs::Linux, HostOs::Windows] {
        for value in ["39", "4001", "many"] {
            let pool = load_on(host, docker(), &env(&[("AIR_VM_ROOT_DISK_GB", value)]));
            assert_eq!(pool.vm_root_disk_gb, 0, "{host} {value}");
        }
    }
}

// A count is a trimmed base-ten integer and nothing else: every other spelling is refused rather than
// reinterpreted as a different number.
#[test]
fn a_count_is_a_strict_base_ten_positive_integer() {
    for spelling in ["8.0", "0x10", "1e3", "0b1000", "0o10", "1_000", "Infinity", "+8"] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_CPU", spelling)]));
        assert_eq!(refusal.code, "invalid_environment", "AIR_VM_CPU={spelling}");
    }
    assert_eq!(load(docker(), &env(&[("AIR_VM_CPU", "  12  ")])).vm_cpu, 12);
}

// The two axes are not independent, and the pair is refused once here. Parallels needs an Aqua session, and Tart runs
// the macOS golden only, so each refuses a Linux guest, and the message says why.
#[test]
fn parallels_and_tart_refuse_a_linux_guest() {
    for (backend, reason) in [(Backend::Parallels, "Aqua session"), (Backend::Tart, "--backend docker")] {
        let refusal = refuse(
            Selection {
                backend,
                guest_os: GuestOs::Linux,
            },
            &env(&[]),
        );
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("unsupported_backend_operation", Exit::USAGE),
            "{backend:?}"
        );
        assert!(refusal.message.contains(reason), "{backend:?}: {}", refusal.message);
    }
}

#[test]
fn backend_parsing_is_one_flag_over_two_axes() {
    for (value, backend, guest_os) in [
        ("tart", Backend::Tart, GuestOs::Macos),
        ("parallels", Backend::Parallels, GuestOs::Macos),
        ("docker", Backend::Docker, GuestOs::Linux),
        ("container-linux", Backend::ContainerLinux, GuestOs::Linux),
    ] {
        let selection: Selection = value.parse().expect("a known backend");
        assert_eq!(selection, Selection { backend, guest_os });
        assert_eq!(selection.label(), value);
        assert_eq!(selection.to_string(), value);
    }
    // `linux` named the retired Tart Linux pool.
    for retired in ["vmware", "linux"] {
        let refusal = retired.parse::<Selection>().expect_err("an unknown backend");
        assert_eq!(refusal.code, "usage");
        // The refusal lists every spelling, so an operator who typed a wrong one reads the right one.
        assert_eq!(refusal.message, "--backend must be tart, parallels, docker or container-linux");
    }
    // One value rather than two defaults: falling back to the axes separately can compose a rejected pair.
    assert_eq!(Selection::DEFAULT, docker());
    assert_eq!(Selection::DEFAULT.label(), "docker");
    assert_eq!(Selection::default(), Selection::DEFAULT);
}

// A backend is written into a lease and a worker state file with the spelling `--backend` accepts, and reads back.
#[test]
fn a_backend_serializes_as_its_flag_spelling() {
    for (backend, spelling) in [
        (Backend::Tart, "tart"),
        (Backend::Parallels, "parallels"),
        (Backend::Docker, "docker"),
        (Backend::ContainerLinux, "container-linux"),
    ] {
        assert_eq!(backend.as_str(), spelling);
        assert_eq!(backend.to_string(), spelling);
        let json = serde_json::to_string(&backend).expect("a backend serializes");
        assert_eq!(json, format!("\"{spelling}\""));
        assert_eq!(serde_json::from_str::<Backend>(&json).expect("a backend reads back"), backend);
    }
    serde_json::from_str::<Backend>("\"testingui\"").expect_err("the lowercase spelling is not a backend");
}

#[test]
fn the_host_repo_refuses_to_be_read_before_it_is_resolved() {
    let config = load(docker(), &env(&[]));
    let refusal = config.host_repo().expect_err("an unresolved repository");
    assert_eq!(refusal.code, "host_paths_unresolved");
    assert!(refusal.message.contains("ensure_host_paths"), "{}", refusal.message);
    config.host_bazel_user_root().unwrap_err();
    // The configured value is still readable: it is what the resolver realpaths.
    let configured = POOL_HOST.bazel_user_root(Path::new("/Users/air"));
    assert_eq!(config.configured_bazel_user_root(), configured);

    // Resolving to nothing is refused rather than recorded.
    assert!(config.set_host_paths("", "/x").is_err());
    let root = configured.as_path();
    config.set_host_paths("/repo", root).expect("the paths are recorded");
    assert_eq!(config.host_repo(), Ok(Path::new("/repo")));
    assert_eq!(config.host_bazel_user_root(), Ok(Path::new(root)));
    // Set once: the same paths again are fine, other paths are refused rather than silently replacing them.
    config.set_host_paths("/repo", root).unwrap();
    let conflict = config.set_host_paths("/elsewhere", root).expect_err("other paths are refused");
    assert_eq!(conflict.code, "host_paths_conflict");
    assert_eq!(config.host_repo(), Ok(Path::new("/repo")));
}

// A Parallels worker's name comes from the user's own VM and could collide with a Tart slot, so its directory is
// keyed apart.
#[test]
fn a_parallels_worker_directory_is_keyed_apart() {
    let tart = load(tart_macos(), &env(&[]));
    let parallels = load(parallels(), &env(&[("AIR_VM_PARALLELS_VM", "air-macos-1")]));
    let workers = tart.runtime_root.join("workers");
    assert_eq!(tart.worker_dir("air-macos-1"), workers.join("air-macos-1"));
    assert_eq!(parallels.worker_dir("air-macos-1"), workers.join("parallels-air-macos-1"));
    let dir = tart.worker_dir("air-macos-1");
    assert_eq!(tart.pid_path("air-macos-1"), dir.join("tart.pid"));
    assert_eq!(tart.lease_path("air-macos-1"), dir.join("lease.json"));
    assert_eq!(tart.worker_provenance_path("air-macos-1"), dir.join("provenance.json"));
    assert_eq!(tart.suspended_state_path("air-macos-1"), dir.join("suspended.json"));
    assert_eq!(tart.tart_log_path("air-macos-1"), dir.join("tart.log"));
    assert_eq!(tart.guest_boot_build_log_path(), tart.runtime_root.join("guest-boot-build.log"));
}

#[test]
fn a_worker_outside_the_pool_is_refused() {
    let config = load(docker(), &env(&[]));
    config.require_pool_worker("air-docker-2").unwrap();
    let refusal = config.require_pool_worker("air-macos-1").expect_err("a worker from another pool");
    assert_eq!(refusal.code, "unknown_worker");
    // The message names the pool, so the reader does not have to guess which pool they reached.
    assert!(refusal.message.contains("air-docker-1, air-docker-2"), "{}", refusal.message);
}

#[test]
fn the_pool_names_are_distinct_per_guest() {
    let linux = load(docker(), &env(&[])).workers;
    let macos = load(tart_macos(), &env(&[])).workers;
    assert!(linux.iter().all(|name| !macos.contains(name)), "{linux:?} {macos:?}");
}

#[test]
fn an_explicit_pool_fixes_its_size() {
    let config = load(
        docker(),
        &env(&[("AIR_VM_WORKERS", " air-docker-7 , air-docker-9 "), ("AIR_VM_MAX_WORKERS", "16")]),
    );
    assert_eq!(config.workers, ["air-docker-7", "air-docker-9"]);
    let scaled = load(docker(), &env(&[("AIR_VM_MAX_WORKERS", "5")]));
    assert_eq!(scaled.workers.len(), 5);
    assert_eq!(scaled.workers[4], "air-docker-5");
}

/// A Docker pool on a macOS 26 host of `memory_mib`, which runs the Apple `container` engine.
fn container_pool(memory_mib: Option<u64>, pairs: &[(&str, &str)]) -> Config {
    let facts = HostFacts {
        macos: Some(MACOS_26),
        memory_mib,
    };
    Config::load_on(HostOs::Macos, facts, docker(), &env(pairs), Path::new(WORKSPACE))
        .unwrap_or_else(|refusal| panic!("the environment was refused: {refusal:?}"))
}

const GIB: u64 = 1024;

/// The variables of one case.
type Pairs<'a> = &'a [(&'a str, &'a str)];

// On the Apple `container` engine the workers get at most a quarter of the host memory, from 2 slots to 16.
#[test]
fn the_container_pool_follows_the_host_memory() {
    let cases: [(Option<u64>, Pairs<'_>, usize); 7] = [
        (Some(128 * GIB), &[], 4),
        (Some(256 * GIB), &[], 8),
        (Some(64 * GIB), &[], 2),
        (Some(32 * GIB), &[], 2),
        (Some(1024 * GIB), &[], 16),
        (None, &[], 2),
        // Larger workers give fewer slots.
        (Some(128 * GIB), &[("AIR_VM_MEMORY_MB", "16384")], 2),
    ];
    for (memory_mib, pairs, slots) in cases {
        let config = container_pool(memory_mib, pairs);
        assert_eq!(config.docker_engine, DockerEngine::AppleContainer);
        assert_eq!(config.workers.len(), slots, "{memory_mib:?} {pairs:?}");
        assert_eq!(config.host_memory_mib, memory_mib);
    }
    let sized = container_pool(Some(128 * GIB), &[]);
    assert_eq!(sized.workers[3], "air-docker-4");
    assert_eq!(sized.pool_rule.to_string(), "host 128 GiB, 8 GiB per worker");
    assert_eq!(container_pool(None, &[]).pool_rule, PoolRule::UnknownHostMemory);
}

// `AIR_VM_MAX_WORKERS` and `AIR_VM_WORKERS` win over the host memory rule.
#[test]
fn a_pool_setting_wins_over_the_host_memory() {
    let max = container_pool(Some(128 * GIB), &[("AIR_VM_MAX_WORKERS", "3")]);
    assert_eq!((max.workers.len(), max.pool_rule), (3, PoolRule::MaxWorkers));
    assert_eq!(max.pool_rule.to_string(), "AIR_VM_MAX_WORKERS");
    let named = container_pool(Some(128 * GIB), &[("AIR_VM_WORKERS", "air-docker-1")]);
    assert_eq!((named.workers.len(), named.pool_rule), (1, PoolRule::Named));
}

// A container on the host engine has no cap, so that pool keeps 2 slots on a large host: a named engine and an older
// Mac alike. A Tart pool keeps 2 slots too.
#[test]
fn only_the_container_pool_follows_the_host_memory() {
    let named = container_pool(Some(128 * GIB), &[("DOCKER_HOST", "unix:///var/run/docker.sock")]);
    assert_eq!(named.docker_engine, DockerEngine::Host);
    assert_eq!((named.workers.len(), named.pool_rule), (2, PoolRule::Default));
    let older_facts = HostFacts {
        macos: Some(MACOS_15),
        memory_mib: Some(128 * GIB),
    };
    let older = Config::load_on(HostOs::Macos, older_facts, docker(), &env(&[]), Path::new(WORKSPACE)).unwrap();
    assert_eq!(older.docker_engine, DockerEngine::Host);
    assert_eq!((older.workers.len(), older.pool_rule), (2, PoolRule::Default));
    let facts = HostFacts {
        macos: Some(MACOS_26),
        memory_mib: Some(128 * GIB),
    };
    let tart = Config::load_on(HostOs::Macos, facts, tart_macos(), &env(&[]), Path::new(WORKSPACE)).unwrap();
    assert_eq!(tart.workers.len(), 2);
}

// An idle Apple `container` worker stops an hour after its release by default. Seconds, zero included, or `off`
// change it, and a malformed value is refused. Every other engine and backend has no idle stop.
#[test]
fn the_idle_stop_is_the_container_engines_alone() {
    let pool = |pairs: &[(&str, &str)]| container_pool(Some(128 * GIB), pairs).idle_stop;
    assert_eq!(pool(&[]), Some(IDLE_STOP_DEFAULT));
    assert_eq!(IDLE_STOP_DEFAULT, Duration::from_secs(3_600));
    assert_eq!(pool(&[("AIR_VM_IDLE_STOP", "60")]), Some(Duration::from_secs(60)));
    assert_eq!(pool(&[("AIR_VM_IDLE_STOP", "0")]), Some(Duration::ZERO));
    assert_eq!(pool(&[("AIR_VM_IDLE_STOP", "off")]), None);
    assert_eq!(
        pool(&[("AIR_VM_IDLE_STOP", "60"), ("DOCKER_HOST", "unix:///var/run/docker.sock")]),
        None
    );
    assert_eq!(load(docker(), &env(&[("AIR_VM_IDLE_STOP", "60")])).idle_stop, None);
    assert_eq!(load(tart_macos(), &env(&[])).idle_stop, None);
    for malformed in ["-1", "1h", "1.5", "never"] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_IDLE_STOP", malformed)]));
        assert_eq!(refusal.code, "invalid_environment", "{malformed}");
        assert!(refusal.message.contains("AIR_VM_IDLE_STOP"), "{}", refusal.message);
    }
}

// A run exports nothing by default. `failed` and `all` name what it exports, always into the export directory of the
// checkout, and any other value is refused.
#[test]
fn the_export_is_off_unless_the_environment_names_it() {
    let settings = load(docker(), &env(&[]));
    assert_eq!(settings.export, Export::Off);
    assert_eq!(settings.export_root, Path::new("/repo/out/air/vm-export"));
    assert_eq!(load(docker(), &env(&[("AIR_VM_EXPORT", "failed")])).export, Export::Failed);
    assert_eq!(load(docker(), &env(&[("AIR_VM_EXPORT", "all")])).export, Export::All);
    let refusal = refuse(docker(), &env(&[("AIR_VM_EXPORT", "yes")]));
    assert_eq!(refusal.code, "invalid_environment");
    assert!(refusal.message.contains("AIR_VM_EXPORT"), "{}", refusal.message);
}

#[test]
fn the_default_slots_are_a_pure_rule() {
    assert_eq!(default_docker_slots(Some(128 * GIB), 8_192), 4);
    assert_eq!(default_docker_slots(Some(128 * GIB), 0), MAX_POOL_SLOTS);
    assert_eq!(default_docker_slots(Some(u64::MAX), 1), MAX_POOL_SLOTS);
    assert_eq!(default_docker_slots(None, 8_192), DEFAULT_POOL_SLOTS);
    assert_eq!(
        PoolRule::HostMemory {
            host_mib: 7_680,
            worker_mib: 2_048
        }
        .to_string(),
        "host 7.5 GiB, 2 GiB per worker"
    );
}

// The set of guests is closed, so a guest with no profile cannot be constructed; its spelling is a usage
// refusal, and every profile field is set.
#[test]
fn a_guest_with_no_profile_is_refused() {
    let refusal = "windows".parse::<GuestOs>().expect_err("no windows profile");
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("usage", Exit::USAGE));
    for os in [GuestOs::Macos, GuestOs::Linux] {
        let profile = os.profile();
        assert_eq!(profile.os, os);
        for field in [profile.share_mount, profile.chown, profile.link_flags] {
            assert!(!field.is_empty(), "{os}: {profile:?}");
        }
        assert_eq!(os.as_str().parse::<GuestOs>(), Ok(os));
    }
}

// Only a macOS guest has the VirtioFS device. Its mount binaries are absolute, because the remount sweep runs under
// `sudo -H`, whose `secure_path` on a macOS guest has no `/sbin`.
#[test]
fn only_the_macos_guest_has_a_virtiofs_device_with_absolute_binaries() {
    assert_eq!(GuestOs::Linux.profile().virtiofs, None);
    let virtiofs = GuestOs::Macos.profile().virtiofs.expect("the macOS guest has the device");
    for field in [virtiofs.mounted_filesystem, virtiofs.mount_shares] {
        assert!(!field.is_empty(), "{virtiofs:?}");
    }
    assert!(
        virtiofs.mount_binary.starts_with('/') && virtiofs.umount_binary.starts_with('/'),
        "{virtiofs:?}"
    );
}

// First refusal wins, in the order the readers run, so one invalid environment produces one message.
#[test]
fn the_first_refusal_wins() {
    let refusal = refuse(
        docker(),
        &env(&[
            ("AIR_VM_NETWORK", "bridged"),
            ("AIR_VM_ROOT_DISK_OPTS", "NOPE;"),
            ("AIR_VM_CPU", "many"),
        ]),
    );
    assert!(refusal.message.contains("AIR_VM_NETWORK"), "{}", refusal.message);
}

// Root-disk options are concatenated into a `tart run` argv, so a shell metacharacter is an injection site.
#[test]
fn root_disk_options_accept_only_tarts_own_spelling() {
    for value in ["caching=cached,sync=none", "sync=none", "caching", ""] {
        let result = Config::load_on(
            POOL_HOST,
            facts(MACOS_15),
            tart_macos(),
            &env(&[("AIR_VM_ROOT_DISK_OPTS", value)]),
            Path::new("/repo"),
        );
        assert!(result.is_ok(), "{value:?} was refused: {result:?}");
    }
    for value in ["caching=Cached", "sync=none;rm -rf /", "sync = none", "caching,,sync"] {
        let result = Config::load_on(
            POOL_HOST,
            facts(MACOS_15),
            tart_macos(),
            &env(&[("AIR_VM_ROOT_DISK_OPTS", value)]),
            Path::new("/repo"),
        );
        assert!(result.is_err(), "{value:?} was accepted");
    }
}

#[test]
fn a_name_is_what_a_shell_carries_unquoted() {
    for name in ["air-docker-1", "run.2026_09", "A-z.0_9"] {
        assert!(validate_name(name, "worker name").is_ok(), "{name}");
    }
    for name in ["", "../escape", "a b", "a/b", "semi;colon", "ünicode"] {
        let refusal = validate_name(name, "run id").expect_err(name);
        assert_eq!((refusal.code.as_ref(), refusal.exit), ("unsafe_name", Exit::USAGE));
        assert!(refusal.message.starts_with("run id contains"), "{}", refusal.message);
    }
}

// `get` treats an empty value as unset, but a child's environment is built from `pairs`, and a child that was given
// `NO_COLOR=` must see it as given.
#[test]
fn the_pairs_keep_an_empty_value() {
    let environment = Environment::from_pairs([("NO_COLOR", ""), ("HOME", "/Users/air")]);
    assert_eq!(environment.get("NO_COLOR"), None);
    let mut pairs: Vec<(String, String)> = environment.pairs().collect();
    pairs.sort();
    assert_eq!(
        pairs,
        [("HOME".to_owned(), "/Users/air".to_owned()), ("NO_COLOR".to_owned(), String::new())]
    );
}

// --- the Docker pool -----------------------------------------------------------------------------------------

/// A Docker worker has the Linux guest's account and paths, and a pool of two containers, as a Tart pool has two
/// workers, unless the operator asks for another size.
#[test]
fn the_docker_defaults_are_the_linux_guests_with_two_slots() {
    let config = load(docker(), &env(&[]));
    assert_eq!((config.backend, config.guest_os), (Backend::Docker, GuestOs::Linux));
    assert_eq!(config.workers, ["air-docker-1", "air-docker-2"]);
    assert_eq!(
        (
            config.vm_user.as_str(),
            config.vm_uid.as_str(),
            config.vm_home.as_str(),
            config.vm_data.as_str(),
        ),
        ("admin", "1000", "/home/admin", "/home/admin/WorkerData")
    );
    assert_eq!(config.guest.share_mount, "/mnt/AirVmShares");
    // A macOS host resolves the pinned CLI; another host runs `docker` on `PATH`.
    assert_eq!(config.docker, (POOL_HOST != HostOs::Macos).then(|| PathBuf::from("docker")));
    assert_eq!(config.docker_image, "air-ui-worker");
    assert_eq!(config.docker_base_image, pins::docker_base_image());
    // The image is pulled from the JetBrains registry by default, and never pushed by default.
    assert_eq!(
        config.docker_registry.as_deref(),
        Some("registry.jetbrains.team/p/ij/containers-public")
    );
    assert!(!config.docker_push);
    // A container has no disk that the controller sizes.
    assert_eq!(config.vm_root_disk_gb, 0);
    assert_eq!(config.vm_screen, None);
    // A slot of its own directory, so a container cannot share state with a Tart slot of the same name.
    assert_eq!(config.worker_key("air-docker-1"), "docker-air-docker-1");
    let root = config.runtime_root.clone();
    assert_eq!(
        config.docker_create_record_path("air-docker-1"),
        root.join("workers/docker-air-docker-1/docker-create.json")
    );
    assert_eq!(config.docker_build_log_path(), root.join("docker-build.log"));
    assert_eq!(config.docker_pull_log_path(), root.join("docker-pull.log"));
    assert_eq!(config.docker_push_log_path(), root.join("docker-push.log"));
    assert_eq!(config.docker_image_record_path(), root.join("docker-image.json"));
    // The Tart pool keeps its own defaults.
    assert_eq!(load(tart_macos(), &env(&[])).workers, ["air-macos-1", "air-macos-2"]);
}

#[test]
fn a_docker_pool_reads_its_own_settings_and_bounds() {
    let config = load(
        docker(),
        &env(&[
            ("DOCKER_BIN", "/opt/orbstack/bin/docker"),
            ("AIR_VM_DOCKER_IMAGE", "registry.example/air/ui-worker"),
            ("AIR_VM_MAX_WORKERS", "3"),
            ("AIR_VM_SCREEN", "2560x1440x24"),
        ]),
    );
    assert_eq!(config.docker.as_deref(), Some(Path::new("/opt/orbstack/bin/docker")));
    assert_eq!(config.docker_engine, DockerEngine::Host);
    assert_eq!(config.docker_image, "registry.example/air/ui-worker");
    assert_eq!(config.workers, ["air-docker-1", "air-docker-2", "air-docker-3"]);
    assert_eq!(config.vm_screen.as_deref(), Some("2560x1440x24"));

    let refusal = refuse(docker(), &env(&[("AIR_VM_MAX_WORKERS", "17")]));
    assert_eq!(refusal.code, "invalid_environment");
    // A tag or a capital is refused: the controller appends the tag, and Docker refuses capitals.
    for image in ["air-ui-worker:latest", "Air-UI", "air//worker", "/air"] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_DOCKER_IMAGE", image)]));
        assert_eq!(refusal.code, "invalid_environment", "{image}");
    }
    // The screen grammar of `air-display`: three digit groups joined by `x`.
    for screen in [
        "1920x1080",
        "1920x1080x24x1",
        "x1080x24",
        "1920xx24",
        "1920x1080x",
        "1920X1080X24",
        "wide",
    ] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_SCREEN", screen)]));
        assert_eq!(refusal.code, "invalid_environment", "{screen}");
        assert!(refusal.message.contains("AIR_VM_SCREEN"), "{}", refusal.message);
    }
    assert_eq!(config.docker_image_lock_path(), config.runtime_root.join("docker-image.lock"));
}

/// The registry is a path the controller prefixes to the image repository, so it takes a host with an optional
/// port and path components, and `off` turns the pull off. An empty value is unset, which is the default, so
/// `off` is the one spelling that disables it. A push with the registry off has nowhere to go.
#[test]
fn the_docker_registry_is_a_registry_path_or_off() {
    for (value, want) in [
        ("registry.example", Some("registry.example")),
        ("localhost:5000", Some("localhost:5000")),
        ("localhost:5000/air", Some("localhost:5000/air")),
        (
            "registry.example/p/ij/containers-public",
            Some("registry.example/p/ij/containers-public"),
        ),
        ("off", None),
        ("", Some("registry.jetbrains.team/p/ij/containers-public")),
    ] {
        let config = load(docker(), &env(&[("AIR_VM_DOCKER_REGISTRY", value)]));
        assert_eq!(config.docker_registry.as_deref(), want, "{value:?}");
    }
    for value in [
        "Registry.Example",
        "registry.example/",
        "/air",
        "registry.example//air",
        "registry.example:port",
        "registry.example:",
        "registry.example/air:latest",
    ] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_DOCKER_REGISTRY", value)]));
        assert_eq!(refusal.code, "invalid_environment", "{value}");
        assert!(refusal.message.contains("AIR_VM_DOCKER_REGISTRY"), "{}", refusal.message);
    }
    let config = load(docker(), &env(&[("AIR_VM_DOCKER_PUSH", "1")]));
    assert!(config.docker_push);
    let refusal = refuse(docker(), &env(&[("AIR_VM_DOCKER_PUSH", "yes"), ("AIR_VM_DOCKER_REGISTRY", "off")]));
    assert_eq!(refusal.code, "invalid_environment");
    assert!(refusal.message.contains("AIR_VM_DOCKER_PUSH"), "{}", refusal.message);
    let refusal = refuse(docker(), &env(&[("AIR_VM_DOCKER_PUSH", "maybe")]));
    assert_eq!(refusal.code, "invalid_environment");
}

/// The file mirror is an http or https URL the controller appends a path to, and `off` turns the mirror pull off. On
/// the Apple `container` engine a push goes to the mirror, so it needs the mirror and not the registry. The token is
/// read, and its `Debug` hides the value.
#[test]
fn the_image_mirror_is_a_url_or_off() {
    let config = load(docker(), &env(&[]));
    assert_eq!(config.image_mirror.as_deref(), Some(IMAGE_MIRROR_DEFAULT));
    assert_eq!(config.image_mirror_token, None);
    assert_eq!(config.host_curl, "curl");
    for (value, want) in [
        ("https://mirror.example/files", Some("https://mirror.example/files")),
        ("http://localhost:8080", Some("http://localhost:8080")),
        ("off", None),
    ] {
        let config = load(docker(), &env(&[("AIR_VM_IMAGE_MIRROR", value)]));
        assert_eq!(config.image_mirror.as_deref(), want, "{value:?}");
    }
    for value in [
        "mirror.example/files",
        "https://",
        "https://mirror.example/",
        "https:///files",
        "https://a b",
        "ftp://x",
    ] {
        let refusal = refuse(docker(), &env(&[("AIR_VM_IMAGE_MIRROR", value)]));
        assert_eq!(refusal.code, "invalid_environment", "{value}");
        assert!(refusal.message.contains("AIR_VM_IMAGE_MIRROR"), "{}", refusal.message);
    }

    let apple = |pairs: &[(&str, &str)]| {
        let mut all = vec![("AIR_VM_DOCKER_PUSH", "1")];
        all.extend_from_slice(pairs);
        env(&all)
    };
    let config = load_on_release(HostOs::Macos, MACOS_26, docker(), &apple(&[("AIR_VM_DOCKER_REGISTRY", "off")]));
    assert!(config.runs_container_engine() && config.docker_push);
    let refusal = refuse_on_release(HostOs::Macos, MACOS_26, docker(), &apple(&[("AIR_VM_IMAGE_MIRROR", "off")]));
    assert_eq!(refusal.code, "invalid_environment");
    assert!(refusal.message.contains("AIR_VM_IMAGE_MIRROR=off"), "{}", refusal.message);
    // The other engines keep the registry rule.
    let refusal = refuse_on(
        HostOs::Macos,
        docker(),
        &env(&[("AIR_VM_DOCKER_PUSH", "1"), ("AIR_VM_DOCKER_REGISTRY", "off")]),
    );
    assert!(refusal.message.contains("AIR_VM_DOCKER_REGISTRY=off"), "{}", refusal.message);

    let config = load(
        docker(),
        &env(&[("AIR_VM_IMAGE_MIRROR_TOKEN", "secret-token"), ("AIR_VM_HOST_CURL", "/usr/bin/curl")]),
    );
    assert_eq!(config.image_mirror_token.as_ref().map(Secret::expose), Some("secret-token"));
    assert_eq!(config.host_curl, "/usr/bin/curl");
    let printed = format!("{config:?}");
    assert!(!printed.contains("secret-token") && printed.contains("Secret(..)"), "{printed}");
}

/// A container shares the kernel of the engine's Linux VM, so there is no macOS guest to put in it. Refused here,
/// once, like the Parallels Linux pairing, for the Docker container and for the testing-ui container alike.
#[test]
fn a_docker_backend_with_a_macos_guest_is_refused() {
    for backend in [Backend::Docker, Backend::ContainerLinux] {
        let refusal = refuse(
            Selection {
                backend,
                guest_os: GuestOs::Macos,
            },
            &env(&[]),
        );
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("unsupported_backend_operation", Exit::USAGE),
            "{backend}"
        );
        assert!(refusal.message.contains("--backend tart"), "{backend}: {}", refusal.message);
    }
    assert!(
        refuse(
            Selection {
                backend: Backend::ContainerLinux,
                guest_os: GuestOs::Macos,
            },
            &env(&[]),
        )
        .message
        .starts_with("a container-linux worker is a Linux container;")
    );
}

// --- the testing-ui container ----------------------------------------------------------------------------------

/// The testing-ui container is the skill's: its `ubuntu` account, its Xvnc on `:1`, and one slot. The writable state
/// lives on the container disk under the account's home, as on every other backend. The two settings that name the
/// skill's script and its output root default under the checkout that holds the skill directory, and every default
/// yields to its variable.
#[test]
fn the_container_linux_defaults_are_the_skills_container() {
    let config = load(container_linux(), &env(&[]));
    assert_eq!((config.backend, config.guest_os), (Backend::ContainerLinux, GuestOs::Linux));
    assert_eq!(config.workers, ["container-linux-1"]);
    assert_eq!(
        (
            config.vm_user.as_str(),
            config.vm_uid.as_str(),
            config.vm_home.as_str(),
            config.vm_data.as_str(),
        ),
        ("ubuntu", "1000", "/home/ubuntu", "/home/ubuntu/WorkerData")
    );
    assert_eq!(config.vm_tmp, "/home/ubuntu/WorkerData/tmp");
    assert_eq!(config.vm_runs_root, "/home/ubuntu/WorkerData/state/ui-runs");
    assert_eq!(config.vm_agent, "/home/ubuntu/WorkerData/state/vm-guest-agent");
    assert_eq!(config.guest_display, ":1");
    // A container has no disk of its own to grow, and the slot has a directory of its own under the runtime root.
    assert_eq!(config.vm_root_disk_gb, 0);
    assert_eq!(config.worker_key("container-linux-1"), "container-linux-container-linux-1");
    let checkout = Path::new("/repo");
    assert_eq!(
        config.container_linux_script,
        checkout.join(".agents/skills/testing-ui/scripts/container.cmd")
    );
    assert_eq!(config.container_linux_root, checkout.join("out/testing-ui"));
    // The published host port of the daemon derives from the checkout: off the skill's own ranges, and stable.
    assert!((12_000..20_000).contains(&config.daemon_host_port), "{}", config.daemon_host_port);
    assert_eq!(config.daemon_host_port, load(container_linux(), &env(&[])).daemon_host_port);
    // The Docker pool keeps its own defaults.
    let docker = load(docker(), &env(&[]));
    assert_eq!((docker.vm_user.as_str(), docker.guest_display.as_str()), ("admin", ":88"));

    let moved = load(
        container_linux(),
        &env(&[
            ("AIR_VM_USER", "tester"),
            ("AIR_VM_DATA", "/work/elsewhere"),
            ("AIR_VM_DISPLAY", ":2"),
            ("AIR_VM_DAEMON_HOST_PORT", "15555"),
        ]),
    );
    assert_eq!(
        (moved.vm_user.as_str(), moved.vm_home.as_str(), moved.vm_data.as_str()),
        ("tester", "/home/tester", "/work/elsewhere")
    );
    assert_eq!(moved.guest_display, ":2");
    assert_eq!(moved.daemon_host_port, 15_555);
}

/// The profile follows the selection: a Docker guest gets the profile of its OS, and the testing-ui container gets
/// the Linux spellings, its shares at their host paths, and no root.
#[test]
fn the_profile_follows_the_selection() {
    assert_eq!(docker().profile(), GuestOs::Linux.profile());
    assert!(docker().profile().privileged);
    for selection in [tart_macos(), parallels()] {
        assert_eq!(selection.profile(), GuestOs::Macos.profile(), "{selection}");
        assert!(selection.profile().privileged, "{selection}");
    }
    let profile = container_linux().profile();
    let linux = GuestOs::Linux.profile();
    assert_eq!(
        (profile.os, profile.privileged, profile.shares_at_host_paths),
        (GuestOs::Linux, false, true)
    );
    assert_eq!(
        (
            profile.share_mount,
            profile.chown,
            profile.link_flags,
            profile.virtiofs,
            linux.shares_at_host_paths
        ),
        (linux.share_mount, linux.chown, linux.link_flags, linux.virtiofs, false)
    );
    // The loaded settings carry the selection's profile, not the OS one.
    assert_eq!(load(container_linux(), &env(&[])).guest, profile);
    assert_eq!(load(docker(), &env(&[])).guest, linux);
}

/// The skill's script runs one container, so the pool has one slot, and a request for more is refused by the name
/// of the variable and of the backend. A Docker pool still scales.
#[test]
fn a_container_linux_pool_has_one_slot() {
    assert_eq!(load(container_linux(), &env(&[])).workers, ["container-linux-1"]);
    assert_eq!(
        load(container_linux(), &env(&[("AIR_VM_MAX_WORKERS", "1")])).workers,
        ["container-linux-1"]
    );
    let refusal = refuse(container_linux(), &env(&[("AIR_VM_MAX_WORKERS", "2")]));
    assert_eq!((refusal.code.as_ref(), refusal.exit), ("invalid_worker_pool", Exit::USAGE));
    assert!(
        refusal.message.contains("AIR_VM_MAX_WORKERS") && refusal.message.contains("container-linux"),
        "{}",
        refusal.message
    );
    assert_eq!(load(docker(), &env(&[("AIR_VM_MAX_WORKERS", "2")])).workers.len(), 2);
}

// --- the Docker engine -----------------------------------------------------------------------------------------

/// The engine rule: a variable that names an engine wins on every host. With neither set, a macOS 15 host keeps the
/// host engine with the pinned CLI, and another host runs `docker` on `PATH` against the engine it has.
#[test]
fn the_docker_engine_follows_the_environment_and_the_host() {
    let docker_on_path = Some(PathBuf::from("docker"));
    let named = Some(PathBuf::from("/opt/orbstack/bin/docker"));
    type Case<'a> = (HostOs, &'a [(&'a str, &'a str)], Option<PathBuf>);
    let cases: [Case<'_>; 9] = [
        (HostOs::Macos, &[], None),
        (HostOs::Macos, &[("DOCKER_BIN", "/opt/orbstack/bin/docker")], named.clone()),
        (HostOs::Macos, &[("DOCKER_HOST", "unix:///var/run/docker.sock")], None),
        (HostOs::Linux, &[], docker_on_path.clone()),
        (HostOs::Linux, &[("DOCKER_BIN", "/opt/orbstack/bin/docker")], named.clone()),
        (HostOs::Linux, &[("DOCKER_HOST", "tcp://engine:2375")], docker_on_path.clone()),
        (HostOs::Windows, &[], docker_on_path.clone()),
        (HostOs::Windows, &[("DOCKER_BIN", "/opt/orbstack/bin/docker")], named),
        (
            HostOs::Windows,
            &[("DOCKER_HOST", "npipe:////./pipe/docker_engine")],
            docker_on_path,
        ),
    ];
    for (host, pairs, program) in cases {
        let config = load_on(host, docker(), &env(pairs));
        assert_eq!(config.docker_engine, DockerEngine::Host, "{host} {pairs:?}");
        assert_eq!(config.docker, program, "{host} {pairs:?}");
        assert!(!config.runs_container_engine(), "{host} {pairs:?}");
        assert_eq!(
            config.docker_host.as_deref(),
            pairs.iter().find(|(name, _)| *name == "DOCKER_HOST").map(|(_, value)| *value),
            "{host} {pairs:?}"
        );
    }
}

/// The version rule of a macOS host that names no engine: macOS 26 or newer on Apple silicon runs Apple `container`.
/// An older macOS, an Intel Mac and a Mac of unknown release keep the host engine. `DOCKER_BIN` and `DOCKER_HOST`
/// name the host engine on every release. ADR 0224 records the rule.
#[test]
fn the_macos_version_chooses_the_engine() {
    let intel_26 = MacosHost {
        apple_silicon: false,
        ..MACOS_26
    };
    let macos_27 = MacosHost { major: 27, ..MACOS_26 };
    type Case<'a> = (MacosHost, &'a [(&'a str, &'a str)], DockerEngine);
    let cases: [Case<'_>; 7] = [
        (MACOS_26, &[], DockerEngine::AppleContainer),
        (macos_27, &[], DockerEngine::AppleContainer),
        (MACOS_15, &[], DockerEngine::Host),
        // Apple builds `container` for Apple silicon only.
        (intel_26, &[], DockerEngine::Host),
        (MACOS_26, &[("DOCKER_HOST", "unix:///var/run/docker.sock")], DockerEngine::Host),
        (MACOS_26, &[("DOCKER_BIN", "/opt/orbstack/bin/docker")], DockerEngine::Host),
        (MACOS_15, &[("DOCKER_BIN", "/opt/orbstack/bin/docker")], DockerEngine::Host),
    ];
    for (macos, pairs, engine) in cases {
        let config = load_on_release(HostOs::Macos, macos, docker(), &env(pairs));
        assert_eq!(config.docker_engine, engine, "{macos:?} {pairs:?}");
        assert_eq!(
            config.runs_container_engine(),
            engine == DockerEngine::AppleContainer,
            "{macos:?} {pairs:?}"
        );
    }
    // A macOS host whose release is unknown keeps the host engine.
    let unknown = Config::load_on(HostOs::Macos, HostFacts::default(), docker(), &env(&[]), Path::new(WORKSPACE)).unwrap();
    assert_eq!(unknown.docker_engine, DockerEngine::Host);
    // The release takes no part on another host.
    for host in [HostOs::Linux, HostOs::Windows] {
        assert_eq!(
            load_on_release(host, MACOS_26, docker(), &env(&[])).docker_engine,
            DockerEngine::Host,
            "{host}"
        );
    }
    // A macOS 26 worker gets the memory of one container, and no disk.
    let config = load_on_release(HostOs::Macos, MACOS_26, docker(), &env(&[]));
    assert_eq!((config.vm_memory_mib, config.vm_root_disk_gb), (CONTAINER_WORKER_MEMORY_MIB, 0));
    assert_eq!(CONTAINER_MACOS_MAJOR, 26);
    assert_eq!(
        (DockerEngine::Host.as_str(), DockerEngine::AppleContainer.as_str()),
        ("host", "container")
    );
    // The engine is a fact of the Docker backend only: a Tart pool on such a Mac runs no Apple `container`.
    assert!(!load_on_release(HostOs::Macos, MACOS_26, tart_macos(), &env(&[])).runs_container_engine());
}

/// The settings keep the release of a macOS host, which the gate of the Apple `container` engine reads. Another host
/// keeps no release.
#[test]
fn the_settings_keep_the_macos_release() {
    let older = load_on_release(HostOs::Macos, MACOS_15, docker(), &env(&[]));
    assert_eq!((older.docker_engine, older.macos), (DockerEngine::Host, Some(MACOS_15)));
    assert_eq!(load_on_release(HostOs::Linux, MACOS_26, docker(), &env(&[])).macos, None);
}

/// A macOS host reads its release from its version file, and another host has none.
#[test]
fn the_host_reads_its_own_release() {
    let release = MacosHost::read();
    if HostOs::CURRENT == HostOs::Macos {
        let release = release.expect("a macOS host names its release");
        assert!(release.major >= 11, "{release:?}");
        assert_eq!(release.apple_silicon, cfg!(target_arch = "aarch64"));
    } else {
        assert_eq!(release, None);
    }
}

/// The release comes from `ProductVersion` of `SystemVersion.plist`, in the XML that macOS 27.0.1 writes.
#[test]
fn the_macos_release_is_the_major_of_the_product_version() {
    let plist = |version: &str| {
        format!(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<plist version=\"1.0\">\n<dict>\n\t<key>ProductBuildVersion</key>\n\
             \t<string>26A434</string>\n\t<key>ProductUserVisibleVersion</key>\n\t<string>{version}</string>\n\
             \t<key>ProductVersion</key>\n\t<string>{version}</string>\n</dict>\n</plist>\n"
        )
    };
    assert_eq!(product_major(&plist("27.0.1")), Some(27));
    assert_eq!(product_major(&plist("26.0")), Some(26));
    assert_eq!(product_major(&plist("15.7.3")), Some(15));
    assert_eq!(product_major(&plist("26")), Some(26));
    assert_eq!(product_major(&plist("")), None);
    assert_eq!(product_major(&plist("x.1")), None);
    assert_eq!(product_major("<plist><dict></dict></plist>"), None);
}

/// `AIR_VM_DOCKER_ENGINE` is removed. A set value of any word is refused on every host and every backend, and the
/// message says that the macOS version chooses the engine. An empty value reads as unset.
#[test]
fn the_removed_engine_variable_is_refused() {
    for host in [HostOs::Macos, HostOs::Linux, HostOs::Windows] {
        for value in ["container", "lima", "orbstack"] {
            let refusal = refuse_on(host, docker(), &env(&[("AIR_VM_DOCKER_ENGINE", value)]));
            assert_eq!(
                (refusal.code.as_ref(), refusal.exit),
                ("invalid_environment", Exit::USAGE),
                "{host} {value}"
            );
            for named in [
                "AIR_VM_DOCKER_ENGINE is removed",
                "the macOS version chooses the engine",
                "DOCKER_HOST",
            ] {
                assert!(refusal.message.contains(named), "{named:?} in {}", refusal.message);
            }
        }
    }
    let refusal = refuse_on_release(
        HostOs::Macos,
        MACOS_26,
        tart_macos(),
        &env(&[("AIR_VM_DOCKER_ENGINE", "container")]),
    );
    assert_eq!(refusal.code, "invalid_environment");
    let empty = load_on_release(HostOs::Macos, MACOS_26, docker(), &env(&[("AIR_VM_DOCKER_ENGINE", "")]));
    assert_eq!(empty.docker_engine, DockerEngine::AppleContainer);
}

/// `AIR_VM_LIMA_HOME` is removed with the Lima engine. A set value is refused on every host and every backend, and
/// the message says that the variable is removed. An empty value reads as unset.
#[test]
fn the_removed_lima_home_is_refused() {
    let lima_home = [("AIR_VM_LIMA_HOME", "/Users/air/.local/state/JetBrains/air-vm-ui-tests/lima")];
    for host in [HostOs::Macos, HostOs::Linux, HostOs::Windows] {
        let refusal = refuse_on(host, docker(), &env(&lima_home));
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("invalid_environment", Exit::USAGE),
            "{host}"
        );
        assert!(
            refusal.message.starts_with("AIR_VM_LIMA_HOME is removed with the Lima engine"),
            "{}",
            refusal.message
        );
    }
    assert_eq!(refuse(tart_macos(), &env(&lima_home)).code, "invalid_environment");
    load_on(HostOs::Macos, docker(), &env(&[("AIR_VM_LIMA_HOME", "")]));
}

/// On the Apple `container` engine the memory is per worker, the CPUs are those of a Linux worker, no disk is sized,
/// and `CONTAINER_BIN` and `AIR_VM_DNS` are read.
#[test]
fn a_container_worker_has_its_own_memory_and_takes_a_nameserver() {
    let config = load_on_release(HostOs::Macos, MACOS_26, docker(), &env(&[]));
    assert_eq!(
        (config.vm_cpu, config.vm_memory_mib, config.vm_root_disk_gb),
        (8, CONTAINER_WORKER_MEMORY_MIB, 0)
    );
    assert_eq!(CONTAINER_WORKER_MEMORY_MIB, 8_192);
    assert_eq!(config.vm_dns, None);
    assert_eq!(config.container_system_log_path(), config.runtime_root.join("container-system.log"));
    let named = load_on_release(
        HostOs::Macos,
        MACOS_26,
        docker(),
        &env(&[
            ("CONTAINER_BIN", "/usr/local/bin/container"),
            ("AIR_VM_DNS", "10.0.0.53"),
            ("AIR_VM_MEMORY_MB", "6144"),
        ]),
    );
    assert!(named.runs_container_engine());
    assert_eq!(named.container.as_deref(), Some(Path::new("/usr/local/bin/container")));
    assert_eq!(named.vm_dns.as_deref(), Some("10.0.0.53"));
    assert_eq!(named.vm_memory_mib, 6_144);
    let refusal = refuse_on_release(HostOs::Macos, MACOS_26, docker(), &env(&[("AIR_VM_DNS", "dns.example")]));
    assert_eq!(refusal.code, "invalid_environment");
    assert!(refusal.message.contains("AIR_VM_DNS"), "{}", refusal.message);
    assert_eq!(CONTAINER_LABEL, "@community//tools/vm:air_container_darwin_arm64");
    assert_eq!(label_target(CONTAINER_LABEL), "air_container_darwin_arm64");
}

/// The labels name the aliases of the repositories of `docker.MODULE.bazel`, one per host architecture, and the target
/// of each alias is the repository name.
#[test]
fn the_pinned_docker_cli_labels_follow_the_architecture() {
    assert_eq!(docker_cli_label(GuestArch::Arm64), "@community//tools/vm:air_docker_darwin_arm64");
    assert_eq!(docker_cli_label(GuestArch::X86_64), "@community//tools/vm:air_docker_darwin_x86_64");
    assert_eq!(label_target(TART_LABEL), "air_tart");
}
