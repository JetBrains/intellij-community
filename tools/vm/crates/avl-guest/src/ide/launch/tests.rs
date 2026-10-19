use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use avl_wire::ide::{self, IdePrepared};
use avl_wire::supervisor::{AgentExit, EnvironmentPolicy, Outcome, Phase, Spec};
use pretty_assertions::assert_eq;

use super::*;
use crate::cli::CancelArgs;
use crate::ide::fixture::{FakeIde, Fixture, NO_DISPLAY_CHECK, PORT_PROPERTY, VM_OPTIONS_FILE_PATH, mode, prepared, wait_for_file};
use crate::ide::{CODE_IDE_RUNNING, live_run, prepare};
use crate::supervisor::{CONTEXT_UTF8_LOCALE, ENVIRONMENT_ALLOWLIST, LaunchHost, LiveSystem, child_path, has_utf8_locale};

/// A launcher that no refused launch reaches.
fn unused_launcher() -> Launcher {
    Launcher {
        self_exe: PathBuf::from("/nonexistent/vm-guest-agent"),
        host: LaunchHost::current(),
    }
}

fn refused_launch(document: &IdeLaunch) -> AgentRefusal {
    match launch(&LiveSystem, &unused_launcher(), &NO_DISPLAY_CHECK, document) {
        Ok(launched) => panic!("the launch was accepted: {launched:?}"),
        Err(refusal) => refusal,
    }
}

/// The arguments of an argument file that `dev_launch::argument_file_text` wrote.
fn read_arguments(path: &str) -> Vec<String> {
    fs::read_to_string(path)
        .unwrap()
        .lines()
        .map(|line| match line.strip_prefix('"').and_then(|quoted| quoted.strip_suffix('"')) {
            Some(quoted) => quoted.replace("\\\"", "\"").replace("\\\\", "\\"),
            None => line.to_owned(),
        })
        .collect()
}

fn position(arguments: &[String], argument: &str) -> usize {
    arguments
        .iter()
        .position(|candidate| candidate == argument)
        .unwrap_or_else(|| panic!("{argument} is not in {arguments:#?}"))
}

// The argument file holds the flags, the properties, the distribution, then the data paths of the context, then the
// class path and the main class.
#[test]
fn the_argument_file_puts_the_data_paths_of_the_context_last() {
    let fixture = Fixture::new();
    let context = prepared(&fixture.context_document(true));
    let document = fixture.launch_document("launch-1", &context);
    let paths = LaunchPaths::new(&document);
    let arguments = compose_arguments(&document, &paths).unwrap_or_else(|refusal| panic!("{refusal}"));
    let log = format!("{}/log/launch-1", context.context_dir);
    let dist = fixture.text("dist");
    let tail = [
        format!("-Didea.config.path={}", context.config_dir),
        format!("-Didea.system.path={}", context.system_dir),
        format!("-Didea.plugins.path={}", context.plugins_dir),
        format!("-Didea.log.path={log}"),
        format!("-Dsnapshots.path={log}/snapshots"),
        format!("-Didea.diagnostic.opentelemetry.file={log}/opentelemetry.json"),
        format!("-XX:ErrorFile={log}/jvm/java_error_in_idea_%p.log"),
        format!("-XX:HeapDumpPath={log}/heap-dump/heap-dump.hprof"),
        format!("-Xlog:gc*:file={log}/gcLog.log"),
        "-cp".to_owned(),
        format!("{dist}/lib/a.jar:{dist}/lib/b.jar"),
        "com.intellij.idea.Main".to_owned(),
    ];
    assert_eq!(arguments[arguments.len() - tail.len()..], tail);
    assert_eq!(arguments[..2], ["-ea", "--add-opens=java.base/java.lang=ALL-UNNAMED"]);
    let port = position(&arguments, &format!("-D{PORT_PROPERTY}=17000"));
    let home = position(&arguments, &format!("-Didea.home.path={dist}"));
    let from_properties = position(&arguments, "-Didea.config.path=/from/idea.properties");
    let own = position(&arguments, &tail[0]);
    assert!(port < home && home < from_properties && from_properties < own, "{arguments:#?}");
    for property in [
        "-Dfrom.vmoptions=1",
        "-Dfrom.product.info=1",
        "-Dfrom.idea.properties=1",
        &format!("-Djb.vmOptionsFile={dist}/bin/idea64.vmoptions"),
        &format!("-Duser.home={}", context.home_dir),
    ] {
        position(&arguments, property);
    }
}

// The production place of `product-info.json` comes first, and `bin/product-info.json` is the fallback.
#[test]
fn the_product_info_is_at_the_production_place_or_in_bin() {
    let directory = tempfile::tempdir().unwrap();
    let home = directory.path();
    let production = home.join(PRODUCT_INFO_FILE);
    let dev_build = home.join("bin/product-info.json");
    assert_eq!(product_info_file(home), production, "no file names the production place");
    fs::create_dir_all(home.join("bin")).unwrap();
    fs::write(&dev_build, "{}").unwrap();
    assert_eq!(product_info_file(home), dev_build);
    fs::create_dir_all(production.parent().unwrap()).unwrap();
    fs::write(&production, "{}").unwrap();
    assert_eq!(product_info_file(home), production);
}

// The flags file cannot move a data path.
#[test]
fn a_flag_that_states_a_data_path_is_refused() {
    let fixture = Fixture::new();
    let context = prepared(&fixture.context_document(true));
    fs::write(fixture.path("ide.flags"), "-ea\n-Didea.system.path=/elsewhere\n").unwrap();
    let refusal = refused_launch(&fixture.launch_document("launch-1", &context));
    assert_eq!(
        (refusal.code.as_ref(), refusal.exit),
        ("guest_ide_launch_failed", AgentExit::Refused)
    );
    assert!(refusal.message.contains("idea.system.path"), "{}", refusal.message);
    for flag in ["-Didea.log.path", "-XX:ErrorFile=/x", "-XX:HeapDumpPath=/x", "-Xlog:gc:file=/x"] {
        assert!(owned_option(flag).is_some(), "{flag}");
    }
    for flag in ["-Didea.logger=x", "-Xmx2g", "-ea"] {
        assert_eq!(owned_option(flag), None, "{flag}");
    }
}

// `product-info.json` names the main vmoptions file, so a second file in `bin` does not count.
#[test]
fn the_product_info_names_the_vm_options_file() {
    let fixture = Fixture::new();
    let context = prepared(&fixture.context_document(true));
    fs::write(fixture.path("dist/bin/jetbrains_client64.vmoptions"), "-Dfrom.frontend=1\n").unwrap();
    let document = fixture.launch_document("launch-1", &context);
    let arguments = compose_arguments(&document, &LaunchPaths::new(&document)).unwrap_or_else(|refusal| panic!("{refusal}"));
    position(&arguments, "-Dfrom.vmoptions=1");
    assert!(!arguments.iter().any(|argument| argument == "-Dfrom.frontend=1"), "{arguments:#?}");

    let product_info = fixture.path("dist").join(PRODUCT_INFO_FILE);
    fs::write(&product_info, r#"{"launch":[]}"#).unwrap();
    let refusal = refused_launch(&fixture.launch_document("launch-1", &context));
    assert!(refusal.message.contains("states no launch"), "{}", refusal.message);
    fs::write(&product_info, r#"{"launch":[{"vmOptionsFilePath":"../../idea.vmoptions"}]}"#).unwrap();
    let refusal = refused_launch(&fixture.launch_document("launch-1", &context));
    assert!(refusal.message.contains("is not a file of the home"), "{}", refusal.message);
}

// The path resolves by its text against the directory of the production place, as the launcher resolves it.
#[test]
fn the_vm_options_path_resolves_in_the_home() {
    assert_eq!(
        resolve_in_home("Resources", "../bin/idea.vmoptions").as_deref(),
        Some("bin/idea.vmoptions")
    );
    assert_eq!(resolve_in_home("", "bin/idea64.vmoptions").as_deref(), Some("bin/idea64.vmoptions"));
    assert_eq!(
        resolve_in_home("", "./bin//idea64.vmoptions").as_deref(),
        Some("bin/idea64.vmoptions")
    );
    assert_eq!(
        resolve_in_home(
            PRODUCT_INFO_FILE.rsplit_once('/').map_or("", |(directory, _)| directory),
            VM_OPTIONS_FILE_PATH
        )
        .as_deref(),
        Some("bin/idea64.vmoptions")
    );
    for refused in [
        "../bin/idea64.vmoptions",
        "/bin/idea64.vmoptions",
        "bin\\idea64.vmoptions",
        "bin/..",
    ] {
        assert_eq!(resolve_in_home("", refused), None, "{refused}");
    }
}

// A launch needs the layout of a preparation: the context and the project.
#[test]
fn a_launch_on_a_context_that_is_not_prepared_is_refused() {
    let fixture = Fixture::new();
    let context = IdePrepared {
        context_dir: fixture.text("ide/key-1"),
        config_dir: fixture.text("ide/key-1/config"),
        system_dir: fixture.text("ide/key-1/system"),
        plugins_dir: fixture.text("ide/key-1/plugins"),
        project_dir: fixture.text("ide/key-1/project/BookmarksTestProject"),
        home_dir: fixture.text("ide/key-1/home"),
        bin_dir: fixture.text("ide/key-1/bin"),
    };
    let refusal = refused_launch(&fixture.launch_document("launch-1", &context));
    assert!(refusal.message.contains("prepare the context first"), "{}", refusal.message);
    assert!(!fixture.context().join("log").exists());
}

// Only the agent's own environment names the display.
#[test]
fn a_linux_guest_needs_a_display() {
    let linux = |display: Option<&str>| Guest {
        linux: true,
        display: display.map(Into::into),
    };
    let refusal = require_display(&linux(None)).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), (CODE_DISPLAY_MISSING, AgentExit::Refused));
    require_display(&linux(Some(""))).unwrap_err();
    require_display(&linux(Some(":88"))).unwrap();
    require_display(&NO_DISPLAY_CHECK).unwrap();
}

// The launch checks its document itself, so the lane daemon that calls it gets the check too. A property that names a
// data directory of the context is refused by its name, and so is a JVM that is not an absolute path. A refused launch
// writes no file.
#[test]
fn a_launch_document_that_names_a_data_directory_is_refused() {
    let fixture = Fixture::new();
    let context = prepared(&fixture.context_document(true));
    for owned in OWNED_PROPERTIES {
        let mut document = fixture.launch_document("launch-1", &context);
        document.properties.insert(owned.to_owned(), "/elsewhere".to_owned());
        let refusal = refused_launch(&document);
        assert_eq!(
            (refusal.code.as_ref(), refusal.exit),
            ("guest_ide_launch_failed", AgentExit::Refused),
            "{owned}"
        );
        assert!(
            refusal.message.contains(&format!("the property {owned} names a data directory")),
            "{}",
            refusal.message
        );
    }
    let mut document = fixture.launch_document("launch-1", &context);
    document.java_home = "jbr".to_owned();
    let refusal = refused_launch(&document);
    assert_eq!(refusal.code.as_ref(), "guest_ide_launch_failed");
    assert!(refusal.message.contains("javaHome"), "{}", refusal.message);
    for file in ["log", "ide-jvm.args", "launch.json"] {
        assert!(!fixture.context().join(file).exists(), "a refused launch wrote {file}");
    }
}

/// The names that the shell of the fake `java` sets itself, so the environment that it prints holds them too.
const SHELL_VARIABLES: [&str; 4] = ["PWD", "OLDPWD", "SHLVL", "_"];

/// The environment that the fake `java` printed, without the variables of its own shell.
fn printed_environment(text: &str) -> BTreeMap<String, String> {
    text.lines()
        .filter_map(|line| line.split_once('='))
        .filter(|(name, _)| !SHELL_VARIABLES.contains(name))
        .map(|(name, value)| (name.to_owned(), value.to_owned()))
        .collect()
}

/// The launch starts the IDE as a supervisor run: `java @<argument file> <project>` in a closed environment. The
/// context then refuses a preparation and a launch, `ide-gc` keeps the IDE of the same product and stops it
/// otherwise, and `cancel --thread-dump` takes the dump with a fake `jcmd` before the TERM.
#[test]
fn the_prepared_context_runs_the_ide_as_a_supervisor_run() {
    let Some(ide) = FakeIde::new() else {
        return;
    };
    let (fixture, tools) = (&ide.fixture, &ide.tools);
    let jcmd = avl_testkit::fake_executable(tools, "jcmd", "echo \"jcmd $*\"\n").unwrap();
    let context = fixture.context();
    let context_text = context.to_string_lossy().into_owned();

    let (prepared_context, launched) = ide.launch("launch-1", true);
    assert_eq!(
        (launched.log_dir.as_str(), launched.arg_file.as_str()),
        (
            format!("{context_text}/log/launch-1").as_str(),
            format!("{context_text}/ide-jvm.args").as_str()
        )
    );
    assert_eq!(
        wait_for_file(&tools.join("argv.txt")),
        format!("@{}\n{}\n", launched.arg_file, prepared_context.project_dir)
    );
    assert_eq!(mode(&launched.arg_file), 0o600);
    for directory in ["jvm", "heap-dump", "snapshots"] {
        assert_eq!(mode(&format!("{}/{directory}", launched.log_dir)), 0o700, "{directory}");
    }
    let arguments = read_arguments(&launched.arg_file);
    assert_eq!(arguments.last().map(String::as_str), Some("com.intellij.idea.Main"));
    position(&arguments, &format!("-D{PORT_PROPERTY}=17000"));
    let record_path = context.join("launch.json");
    assert_eq!(mode(&record_path.to_string_lossy()), 0o600);
    let record = ide::decode_launch_record(&fs::read(&record_path).unwrap()).unwrap();
    assert_eq!(
        (
            record.launch_name.as_str(),
            record.product_digest.as_str(),
            record.arg_file.as_str()
        ),
        ("launch-1", "product-1", launched.arg_file.as_str())
    );
    assert_eq!(record.java_home, fixture.text("jbr"));

    // The environment is the layout of the context, the allowlist of the agent's own environment and a UTF-8 locale, and
    // nothing else. Under Bazel the test has no locale, so the IDE gets the UTF-8 one.
    let environment = printed_environment(&fs::read_to_string(tools.join("env.txt")).unwrap());
    let mut expected: BTreeMap<String, String> = ENVIRONMENT_ALLOWLIST
        .iter()
        .filter_map(|name| std::env::var(name).ok().map(|value| ((*name).to_owned(), value)))
        .collect();
    let inherited = expected.iter().map(|(name, value)| (name.into(), value.into())).collect();
    if !has_utf8_locale(&inherited) {
        expected.insert("LC_ALL".to_owned(), CONTEXT_UTF8_LOCALE.to_owned());
    }
    expected.insert("HOME".to_owned(), prepared_context.home_dir.clone());
    expected.insert(
        "PATH".to_owned(),
        format!("{}:{}", prepared_context.bin_dir, child_path(LaunchHost::current())),
    );
    expected.insert("IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP".to_owned(), "true".to_owned());
    assert_eq!(environment, expected);
    assert!(
        environment["PATH"].starts_with(&format!("{context_text}/bin:")),
        "{}",
        environment["PATH"]
    );
    for absent in [
        "CODEX_HOME",
        "TART_VM_TOKEN",
        "ANTHROPIC_API_KEY",
        "AIR_CANARY",
        "AVL_GUEST_AGENT_ARGC",
    ] {
        assert!(!environment.contains_key(absent), "{absent}");
    }

    // The spec records the policy, and no variable crosses the argv.
    let spec_text = fs::read_to_string(context.join("run-ide-launch-1/spec.json")).unwrap();
    assert!(spec_text.contains(r#""policy":"context""#), "{spec_text}");
    let spec: Spec = serde_json::from_str(&spec_text).unwrap();
    assert_eq!(spec.environment, EnvironmentPolicy::Context { context_dir: context_text });
    assert_eq!(
        spec.argv,
        [
            format!("{}/java", tools.display()),
            format!("@{}", launched.arg_file),
            prepared_context.project_dir.clone()
        ]
    );
    assert!(spec.argv.iter().all(|word| !word.contains('=')), "{:?}", spec.argv);
    assert_eq!(spec.cwd, fixture.text("dist"));
    assert_eq!(launched.run.argv, spec.argv);

    let refusal = prepare::prepare(&LiveSystem, &fixture.context_document(false)).unwrap_err();
    assert_eq!((refusal.code.as_ref(), refusal.exit), (CODE_IDE_RUNNING, AgentExit::Refused));
    assert!(refusal.message.contains("run-ide-launch-1"), "{}", refusal.message);
    let refusal = refused_launch(&fixture.launch_document("launch-2", &prepared_context));
    assert_eq!((refusal.code.as_ref(), refusal.exit), (CODE_IDE_RUNNING, AgentExit::Refused));
    assert!(refusal.message.contains("run-ide-launch-1"), "{}", refusal.message);

    let kept = ide.gc(false, Some("product-1"), 5);
    assert_eq!((kept.kept.len(), kept.stopped.len()), (1, 0), "{kept:?}");
    assert_eq!(kept.kept[0].run_id, "run-ide-launch-1");

    let finished = supervisor::cancel(
        &LiveSystem,
        &CancelArgs {
            run: ide.run(&launched),
            grace_ms: 5_000,
            thread_dump: Some(jcmd),
        },
    )
    .unwrap();
    assert_eq!((finished.phase, finished.outcome), (Phase::Finished, Some(Outcome::Canceled)));
    let dumps: Vec<PathBuf> = fs::read_dir(&launched.log_dir)
        .unwrap()
        .map(|entry| entry.unwrap().path())
        .filter(|path| path.file_name().unwrap().to_string_lossy().starts_with("threadDump-before-kill-"))
        .collect();
    assert_eq!(dumps.len(), 1, "{dumps:?}");
    assert_eq!(
        fs::read_to_string(&dumps[0]).unwrap(),
        format!("jcmd {} Thread.print\n", launched.run.pid.unwrap())
    );
    let supervisor_log = fs::read_to_string(context.join("run-ide-launch-1/supervisor.log")).unwrap();
    assert!(supervisor_log.contains("thread dump of pid"), "{supervisor_log}");

    fs::remove_file(tools.join("argv.txt")).unwrap();
    ide.launch("launch-2", false);
    wait_for_file(&tools.join("argv.txt"));
    let stopped = ide.gc(false, Some("product-2"), 5);
    assert_eq!((stopped.kept.len(), stopped.stopped.len()), (0, 1), "{stopped:?}");
    assert_eq!(stopped.stopped[0].run_id, "run-ide-launch-2");
    assert_eq!(live_run(&LiveSystem, &context).unwrap(), None);
}

// The caller stops an orphan IDE and prepares the context again with `fresh`. The new launch gets new data
// directories, and the log and the run record of the stopped IDE stay until `ide-gc` trims them.
#[test]
fn a_fresh_preparation_keeps_the_evidence_of_a_stopped_ide() {
    let Some(ide) = FakeIde::new() else {
        return;
    };
    let context = ide.fixture.context();
    let (first_context, first) = ide.launch("launch-1", true);
    wait_for_file(&ide.tools.join("argv.txt"));
    let marker = format!("{}/options/other.xml", first_context.config_dir);
    fs::create_dir_all(Path::new(&marker).parent().unwrap()).unwrap();
    fs::write(&marker, "earlier").unwrap();
    let stopped = ide.gc(true, None, 5);
    assert_eq!((stopped.kept.len(), stopped.stopped.len()), (0, 1), "{stopped:?}");

    let (second_context, second) = ide.launch("launch-2", true);
    assert_eq!(second_context.config_dir, first_context.config_dir);
    assert!(!Path::new(&marker).exists(), "a fresh preparation kept the config directory");
    assert!(
        Path::new(&first.log_dir).is_dir(),
        "a fresh preparation deleted the log of the stopped IDE"
    );
    assert!(context.join("run-ide-launch-1/state.json").is_file());
    ide.gc(true, None, 5);

    let trimmed = ide.gc(false, None, 1);
    assert_eq!(
        trimmed.removed,
        vec![first.log_dir, context.join("run-ide-launch-1").to_string_lossy().into_owned()]
    );
    assert!(Path::new(&second.log_dir).is_dir());
}

/// The publish renames a private temporary file onto the destination, so the IDE reads a whole file or none. An
/// in-place write would keep the mode 0644 of the destination, and a publish that left its temporary file would leave
/// two files.
#[test]
fn a_publish_replaces_the_file_atomically_and_privately() {
    use std::os::unix::fs::{MetadataExt, PermissionsExt};

    let root = tempfile::tempdir().unwrap();
    let destination = root.path().join(ARG_FILE);
    fs::write(&destination, "the previous launch\n").unwrap();
    fs::set_permissions(&destination, fs::Permissions::from_mode(0o644)).unwrap();
    let inode = fs::metadata(&destination).unwrap().ino();

    publish_privately(&destination, b"the next launch").unwrap();
    assert_eq!(fs::read_to_string(&destination).unwrap(), "the next launch");
    let info = fs::metadata(&destination).unwrap();
    assert_eq!(info.permissions().mode() & 0o777, 0o600);
    assert_ne!(info.ino(), inode, "the previous file was written over in place");
    assert_eq!(fs::read_dir(root.path()).unwrap().count(), 1, "the publish left a temporary file");

    // A destination that no file can be renamed onto fails, and still leaves no temporary file.
    let blocked = root.path().join("occupied");
    fs::create_dir_all(blocked.join("child")).unwrap();
    publish_privately(&blocked, b"refused").unwrap_err();
    assert_eq!(
        fs::read_dir(root.path()).unwrap().count(),
        2,
        "a failed publish left a temporary file"
    );
}
