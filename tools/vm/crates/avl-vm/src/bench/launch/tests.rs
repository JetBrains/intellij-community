use std::path::Path;

use pretty_assertions::assert_eq;

use super::{
    DYNAMIC_AGENT_OPTION, FORCE_MODAL_PROPERTY, INTEGRATION_TEST_PROPERTY, PRODUCT_INFO_FILE, Profiler, RunPlan,
    SUPPRESSED_PLUGINS_PROPERTY, TRUST_PROPERTY, argfile_text, class_load_option, dist_options, entry_options, idea_log_pid,
    measure_options, product_info_file, profile_stop_argv, quote_argument, sandbox_options, scheme_arguments, span_ended, thread_dump_argv,
    vm_options_file,
};
use crate::bench::arm::{Arm, ArmKind};
use crate::bench::profile::ProfileEvent;
use crate::bench::record::RunKind;
use crate::bench::session::{Project, Sandbox};
use crate::bench::stage::DistConfig;

#[test]
fn the_measure_options_select_the_sandbox_and_the_files_of_the_run() {
    let sandbox = Sandbox::new("/s/sandbox");
    let options = measure_options(Path::new("/s/run"), &sandbox, &Arm::NON_MODAL, None).expect("the options");
    assert_eq!(options[..3], sandbox_options(&sandbox)[..]);
    assert_eq!(options[0], "-Didea.config.path=/s/sandbox/config");
    for expected in [
        "-Didea.log.path=/s/run/log",
        "-Didea.log.perf.stats.file=/s/run/startup-stats.json",
        "-Didea.diagnostic.opentelemetry.file=/s/run/opentelemetry.json",
        "-Didea.log.class.list.file=/s/run/class-report.txt",
        "-Dplugin.classloader.debug=/s/run/plugin-classes.txt",
        "-Xlog:class+load,os+thread:file=/s/run/class-load.log:uptime,tid,tags",
        "-Dfus.internal.test.mode=true",
        "-Dnosplash=true",
        "-Didea.is.integration.test=true",
    ] {
        assert!(options.contains(&expected.to_owned()), "{expected}: {options:?}");
    }
    assert!(
        !options.iter().any(|option| option.contains("non.modal")),
        "the non-modal arm forces nothing"
    );
}

/// Every arm is an integration test, as every run of the IDE Starter is, and only a project start trusts its project.
#[test]
fn every_arm_is_an_integration_test_and_a_project_start_trusts_its_project() {
    let sandbox = Sandbox::new("/s/sandbox");
    let integration_test = format!("-D{INTEGRATION_TEST_PROPERTY}");
    let trust = format!("-D{TRUST_PROPERTY}");
    for arm in Arm::ALL {
        let options = measure_options(Path::new("/s/run"), &sandbox, &arm, None).expect("the options");
        assert_eq!(
            options.iter().filter(|option| **option == integration_test).count(),
            1,
            "{arm:?}: {options:?}"
        );
        assert_eq!(
            options.contains(&trust),
            matches!(arm.kind, ArmKind::Project | ArmKind::EmptyEditor | ArmKind::CargoSync),
            "{arm:?}: {options:?}"
        );
        assert_eq!(
            options.contains(&"-Dllm.show.ai.promotion.window.on.start=false".to_owned()),
            arm.kind == ArmKind::CargoSync,
            "the properties of RustRoverIndexingTest: {arm:?}"
        );
    }
}

/// A twin starts as the arm of its kind and adds the suppressed plugin. A plain arm suppresses none.
#[test]
fn a_twin_arm_suppresses_its_plugin_and_keeps_the_options_of_its_kind() {
    let sandbox = Sandbox::new("/s/sandbox");
    let suppressed = format!("-D{SUPPRESSED_PLUGINS_PROPERTY}=com.intellij.air");
    let plain = measure_options(Path::new("/s/run"), &sandbox, &Arm::PROJECT, None).expect("the options");
    let twin = measure_options(Path::new("/s/run"), &sandbox, &Arm::PROJECT.without("com.intellij.air"), None).expect("the options");
    assert!(
        !plain.iter().any(|option| option.contains(SUPPRESSED_PLUGINS_PROPERTY)),
        "{plain:?}"
    );
    let mut expected = plain;
    expected.push(suppressed);
    assert_eq!(twin, expected);
}

/// Every arm writes the class-load log, from the thread of the asynchronous log, which stalls on a full buffer and
/// drops no class line. A space in the run directory is no problem, and a `:` is an error that names the run
/// directory.
#[test]
fn every_arm_writes_the_class_load_log_and_a_colon_in_the_run_directory_is_a_failure() {
    let sandbox = Sandbox::new("/s/sandbox");
    for arm in Arm::ALL {
        let options = measure_options(Path::new("/s/Application Support/run"), &sandbox, &arm, None).expect("the options");
        let class_log = options
            .iter()
            .position(|option| *option == "-Xlog:class+load,os+thread:file=/s/Application Support/run/class-load.log:uptime,tid,tags")
            .unwrap_or_else(|| panic!("{arm:?}: {options:?}"));
        assert_eq!(
            options[class_log - 2..class_log],
            ["-Xlog:async:stall", "-XX:AsyncLogBufferSize=32M"],
            "{arm:?}: the asynchronous log comes first"
        );
    }
    assert_eq!(
        argfile_text(&[class_load_option(Path::new("/s/a b")).expect("an option")]),
        "-Xlog:class+load,os+thread:file=/s/a\" \"b/class-load.log:uptime,tid,tags\n"
    );
    assert_eq!(
        measure_options(Path::new("/s/a:b/run"), &sandbox, &Arm::NON_MODAL, None),
        Err(
            "the run directory /s/a:b/run holds a `:`, which ends the file name of -Xlog:class+load; use a session directory without one"
                .to_owned()
        )
    );
}

/// A profiled run starts the agent with the JVM and allows the dynamic load of the stop. The event of the profile is
/// the one of `--profile-event`.
#[test]
fn the_modal_arm_forces_the_modal_screen_and_the_profile_adds_the_agent() {
    let profiler = |event| Profiler {
        library: "/lib/libasyncProfiler.dylib".into(),
        event,
    };
    let options = measure_options(
        Path::new("/r"),
        &Sandbox::new("/b"),
        &Arm::MODAL,
        Some(&profiler(ProfileEvent::Cpu)),
    )
    .expect("the options");
    assert!(options.contains(&format!("-D{FORCE_MODAL_PROPERTY}")));
    assert_eq!(
        options[options.len() - 2..],
        [
            DYNAMIC_AGENT_OPTION.to_owned(),
            "-agentpath:/lib/libasyncProfiler.dylib=start,event=cpu,interval=1ms,threads,collapsed,file=/r/cpu.collapsed".to_owned()
        ]
    );
    let wall = measure_options(
        Path::new("/r"),
        &Sandbox::new("/b"),
        &Arm::NON_MODAL,
        Some(&profiler(ProfileEvent::Wall)),
    )
    .expect("the options");
    assert_eq!(
        wall.last().map(String::as_str),
        Some("-agentpath:/lib/libasyncProfiler.dylib=start,event=wall,interval=1ms,threads,collapsed,file=/r/cpu.collapsed")
    );
    let plain = measure_options(Path::new("/r"), &Sandbox::new("/b"), &Arm::NON_MODAL, None).expect("the options");
    assert!(
        !plain.contains(&DYNAMIC_AGENT_OPTION.to_owned()),
        "a run without a profile loads no agent"
    );
}

/// The stop of the profile loads the library again into the IDE with `stop`, through `jcmd` of the generation. `jcmd`
/// joins its arguments, so a path with a space is quoted.
#[test]
fn the_profile_stops_at_the_gate_through_jcmd() {
    assert_eq!(
        profile_stop_argv(
            Path::new("/g/jbr/Contents/Home/bin/jcmd"),
            48213,
            Path::new("/s/Application Support/async-profiler/libasyncProfiler.dylib"),
            Path::new("/s/Application Support/non-modal-run-01/cpu-gate.collapsed"),
        ),
        [
            "/g/jbr/Contents/Home/bin/jcmd",
            "48213",
            "JVMTI.agent_load",
            "\"/s/Application Support/async-profiler/libasyncProfiler.dylib\"",
            "\"stop,file=/s/Application Support/non-modal-run-01/cpu-gate.collapsed,collapsed\"",
        ]
    );
}

/// The PID of the IDE is the line that its start writes into `idea.log`. A log without it, or with another value, has
/// none.
#[test]
fn the_pid_of_the_ide_comes_from_its_log() {
    let log = "2026-10-01 15:18:10,201 [    283]   INFO - #c.i.p.i.b.StartupUtil - JVM: 25.0.2+10-b432.48 (OpenJDK 64-Bit Server VM)\n\
               2026-10-01 15:18:10,201 [    283]   INFO - #c.i.p.i.b.StartupUtil - PID: 48213\n\
               2026-10-01 15:18:10,202 [    284]   INFO - #c.i.p.i.b.StartupUtil - Timezone: Europe/Berlin (+02:00)\n";
    assert_eq!(idea_log_pid(log), Some(48213));
    assert_eq!(
        idea_log_pid("2026-10-01 15:18:10,201 [    283]   INFO - #c.i.p.i.b.StartupUtil - OS: macOS (26.7)\n"),
        None
    );
    assert_eq!(idea_log_pid("2026-10-01 15:18:10,201 [    283]   INFO - #c - PID: none\n"), None);
}

#[test]
fn the_prime_run_of_a_project_opens_its_first_file_and_a_measured_run_restores_it() {
    let sandbox = Sandbox::new("/s/project-sandbox");
    let project = Project {
        name: "markdown".to_owned(),
        file: "README.md".into(),
    };
    let plan = |kind| RunPlan {
        arm: Arm::PROJECT,
        kind,
        project: Some(project.paths(&sandbox)),
        open_project: None,
    };
    assert_eq!(
        plan(RunKind::Prime).program_arguments(),
        vec![
            "/s/project-sandbox/projects/markdown",
            "/s/project-sandbox/projects/markdown/README.md"
        ]
    );
    assert_eq!(
        plan(RunKind::Measured).program_arguments(),
        vec!["/s/project-sandbox/projects/markdown"]
    );
    let welcome = RunPlan {
        arm: Arm::OPEN_PROJECT,
        kind: RunKind::Measured,
        project: None,
        open_project: Some(Path::new("/p")),
    };
    assert!(
        welcome.program_arguments().is_empty(),
        "open-project starts the IDE without a project"
    );

    let options = measure_options(Path::new("/r"), &sandbox, &Arm::PROJECT, None).expect("the options");
    assert!(options.contains(&format!("-D{TRUST_PROPERTY}")), "{options:?}");
    assert!(!options.contains(&format!("-D{FORCE_MODAL_PROPERTY}")));
    let welcome_options = measure_options(Path::new("/r"), &sandbox, &Arm::NON_MODAL, None).expect("the options");
    assert!(!welcome_options.contains(&format!("-D{TRUST_PROPERTY}")), "{welcome_options:?}");
}

/// Every run of the empty-editor arm opens the project directory alone, so the IDE neither saves nor restores an
/// editor. The arm takes the properties of a project start.
#[test]
fn every_run_of_the_empty_editor_arm_opens_the_project_without_a_file() {
    let sandbox = Sandbox::new("/s/empty-editor-sandbox");
    let project = Project {
        name: "markdown".to_owned(),
        file: "README.md".into(),
    };
    for kind in [RunKind::Prime, RunKind::Measured] {
        let plan = RunPlan {
            arm: Arm::EMPTY_EDITOR,
            kind,
            project: Some(project.paths(&sandbox)),
            open_project: None,
        };
        assert_eq!(
            plan.program_arguments(),
            vec!["/s/empty-editor-sandbox/projects/markdown"],
            "{kind:?}"
        );
    }
    let options = measure_options(Path::new("/r"), &sandbox, &Arm::EMPTY_EDITOR, None).expect("the options");
    assert!(options.contains(&format!("-D{TRUST_PROPERTY}")), "{options:?}");
    assert!(!options.contains(&format!("-D{FORCE_MODAL_PROPERTY}")));
}

/// The poll of a span gate: no trace, a trace without the span, then a trace that the running IDE still writes, with
/// the span. The IDE writes a span when it ends.
#[test]
fn the_span_poll_passes_once_the_trace_holds_the_span() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let run = dir.path();
    let name = "air.emptyState.createComponent";
    assert!(!span_ended(run, name), "no trace");
    let head = r#"{"data":[{"traceID":"t","spans":[{"traceID":"t","spanID":"1","operationName":"bootstrap","processID":"p1","startTime":1000,"duration":10}"#;
    std::fs::write(run.join("opentelemetry.json"), format!("{head},")).expect("a trace");
    assert!(!span_ended(run, name), "a trace without the span");
    std::fs::write(
        run.join("opentelemetry.json"),
        format!(
            r#"{head},{{"traceID":"t","spanID":"2","operationName":"{name}: scheduled","processID":"p1","startTime":2000,"duration":5}},"#
        ),
    )
    .expect("a trace");
    assert!(!span_ended(run, name), "a helper twin is not the span");
    std::fs::write(
        run.join("opentelemetry.json"),
        format!(r#"{head},{{"traceID":"t","spanID":"3","operationName":"{name}","processID":"p1","startTime":2000,"duration":900}},"#),
    )
    .expect("a trace");
    assert!(span_ended(run, name), "a truncated trace with the span");
}

#[test]
fn the_scheme_run_writes_into_the_template() {
    assert_eq!(
        scheme_arguments(&Sandbox::new("/t")),
        vec![
            "buildEventsScheme",
            "--outputFile=/t/config/event-log-metadata/fus/test-events-scheme.json",
            "--recorderId=FUS",
            "--testEventScheme=true",
        ]
    );
}

#[test]
fn an_argument_is_quoted_as_the_starter_quotes_it() {
    assert_eq!(quote_argument("-Da=b"), "-Da=b");
    assert_eq!(
        quote_argument("-Dp=/Library/Application Support/x"),
        "-Dp=/Library/Application\" \"Support/x"
    );
    assert_eq!(
        quote_argument("-Djdk.http.auth.tunneling.disabledSchemes=\"\""),
        "-Djdk.http.auth.tunneling.disabledSchemes=\"\\\"\"\"\\\"\""
    );
    assert_eq!(argfile_text(&["a".to_owned(), "b c".to_owned()]), "a\nb\" \"c\n");
}

/// The `vmOptionsFilePath` of [`fake_dist`], relative to the directory of its `product-info.json`.
const VM_OPTIONS_FILE_PATH: &str = if cfg!(target_os = "macos") {
    "../bin/idea.vmoptions"
} else {
    "bin/idea.vmoptions"
};

/// A distribution with the files that the launch reads.
fn fake_dist(root: &Path) -> std::path::PathBuf {
    let dist = root.join("idea_dist.dist");
    std::fs::create_dir_all(dist.join("bin")).expect("a bin directory");
    std::fs::write(dist.join("bin/idea.vmoptions"), "-Xmx2048m\n\n# a comment\n-ea\n").expect("the vmoptions");
    let product_info = dist.join(PRODUCT_INFO_FILE);
    std::fs::create_dir_all(product_info.parent().expect("a parent")).expect("the directory of the product info");
    std::fs::write(
        product_info,
        format!(
            r#"{{"launch":[{{"vmOptionsFilePath":"{VM_OPTIONS_FILE_PATH}","additionalJvmArguments":["-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader","-Xbootclasspath/a:{}/lib/nio-fs.jar"]}}]}}"#,
            super::home_macro()
        ),
    )
    .expect("the product info");
    dist
}

/// The path resolves by its text against the directory of the production place, as the launcher resolves it.
#[test]
fn the_vm_options_file_resolves_in_the_distribution() {
    let dist = Path::new("/g/idea_dist.dist");
    assert_eq!(
        vm_options_file(dist, VM_OPTIONS_FILE_PATH).expect("the file"),
        dist.join("bin").join("idea.vmoptions")
    );
    for refused in ["../../idea.vmoptions", "/bin/idea.vmoptions", "bin\\idea.vmoptions"] {
        let error = vm_options_file(dist, refused).expect_err(refused);
        assert!(format!("{error:#}").ends_with("is not a file of /g/idea_dist.dist"), "{error:#}");
    }
}

/// The production place of `product-info.json` comes first, and `bin/product-info.json` is the fallback.
#[test]
fn the_product_info_is_at_the_production_place_or_in_bin() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let dist = dir.path();
    let production = dist.join(PRODUCT_INFO_FILE);
    let dev_build = dist.join("bin").join("product-info.json");
    assert_eq!(product_info_file(dist), production, "no file names the production place");
    std::fs::create_dir_all(dist.join("bin")).expect("a bin directory");
    std::fs::write(&dev_build, "{}").expect("the product info in bin");
    assert_eq!(product_info_file(dist), dev_build);
    std::fs::create_dir_all(production.parent().expect("a parent")).expect("the directory of the product info");
    std::fs::write(&production, "{}").expect("the product info");
    assert_eq!(product_info_file(dist), production);
}

#[test]
fn the_dist_options_are_the_vmoptions_the_product_info_and_the_starter_options() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let dist = fake_dist(dir.path());
    let config = DistConfig {
        home: "idea_dist.dist".to_owned(),
        main_class: "com.intellij.idea.Main".to_owned(),
        platform_prefix: Some("idea".to_owned()),
        additional_modules: vec!["intellij.markdown".to_owned()],
    };
    let options = dist_options(&dist, &config, Path::new("/repo")).expect("the options");
    let home = dist.display();
    assert_eq!(
        options,
        vec![
            "-Xmx2048m".to_owned(),
            "-ea".to_owned(),
            format!("-Djb.vmOptionsFile={home}/bin/idea.vmoptions"),
            "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader".to_owned(),
            format!("-Xbootclasspath/a:{home}/lib/nio-fs.jar"),
            "-Didea.use.dev.build.server=true".to_owned(),
            "-Didea.ui.icons.svg.disk.cache=false".to_owned(),
            "-Didea.is.internal=true".to_owned(),
            "-Ddev.build.dir=idea_dist.dist".to_owned(),
            "-Didea.dev.project.root=/repo".to_owned(),
            format!("-Didea.home.path={home}"),
            format!("-Didea.properties.file={home}/bin/idea.properties"),
            "-Didea.platform.prefix=idea".to_owned(),
        ]
    );
    // `product-info.json` names the main vmoptions file, so a second file in `bin` does not count.
    std::fs::write(dist.join("bin/jetbrains_client.vmoptions"), "-Dfrom.frontend=1\n").expect("a second vmoptions");
    assert_eq!(dist_options(&dist, &config, Path::new("/repo")).expect("the options"), options);
    std::fs::write(dist.join(PRODUCT_INFO_FILE), r#"{"launch":[]}"#).expect("a product info without a launch");
    let error = dist_options(&dist, &config, Path::new("/repo")).expect_err("no launch");
    assert!(format!("{error:#}").ends_with("states no launch"), "{error:#}");
}

#[test]
fn the_entry_resolves_the_class_path_in_the_distribution() {
    let dist = Path::new("/g/idea_dist.dist");
    let opened = "--add-opens=java.base/java.lang=ALL-UNNAMED\n--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED\n";
    let entry = entry_options(dist, opened, "lib/a.jar\nlib/b.jar\n", "com.intellij.idea.Main").expect("the entry");
    let separator = if cfg!(windows) { ";" } else { ":" };
    let mut expected = vec!["--add-opens=java.base/java.lang=ALL-UNNAMED".to_owned()];
    if cfg!(windows) {
        expected.push("--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED".to_owned());
    }
    expected.extend([
        "-classpath".to_owned(),
        [dist.join("lib/a.jar"), dist.join("lib/b.jar")]
            .map(|path| path.display().to_string())
            .join(separator),
        "com.intellij.idea.Main".to_owned(),
    ]);
    assert_eq!(entry, expected);
    #[cfg(unix)]
    {
        let error = entry_options(dist, "", "/out/bazel-bin/x.jar\n", "Main").expect_err("an entry outside");
        assert!(format!("{error:#}").contains("outside the distribution"), "{error:#}");
    }
    let error = entry_options(dist, "", "\n", "Main").expect_err("an empty class path");
    assert!(format!("{error:#}").ends_with("is empty"), "{error:#}");
}

/// The thread dump after the gate is `jcmd <pid> Thread.print`.
#[test]
fn the_thread_dump_is_jcmd_thread_print() {
    assert_eq!(
        thread_dump_argv(Path::new("/jbr/bin/jcmd"), 51234),
        ["/jbr/bin/jcmd", "51234", "Thread.print"]
    );
}
