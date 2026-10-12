use std::path::Path;

use pretty_assertions::assert_eq;

use super::{
    FORCE_MODAL_PROPERTY, PROJECT_PROPERTIES, RunPlan, argfile_text, class_load_option, dist_options, entry_options, measure_options,
    quote_argument, sandbox_options, scheme_arguments, span_ended,
};
use crate::bench::arm::Arm;
use crate::bench::record::RunKind;
use crate::bench::session::{Project, Sandbox};
use crate::bench::stage::DistConfig;

#[test]
fn the_measure_options_select_the_sandbox_and_the_files_of_the_run() {
    let sandbox = Sandbox::new("/s/sandbox");
    let options = measure_options(Path::new("/s/run"), &sandbox, Arm::NonModal, None).expect("the options");
    assert_eq!(options[..3], sandbox_options(&sandbox)[..]);
    assert_eq!(options[0], "-Didea.config.path=/s/sandbox/config");
    for expected in [
        "-Didea.log.path=/s/run/log",
        "-Didea.log.perf.stats.file=/s/run/startup-stats.json",
        "-Didea.diagnostic.opentelemetry.file=/s/run/opentelemetry.json",
        "-Didea.log.class.list.file=/s/run/class-report.txt",
        "-Dplugin.classloader.debug=/s/run/plugin-classes.txt",
        "-Xlog:class+load:file=/s/run/class-load.log:uptime,tags",
        "-Dfus.internal.test.mode=true",
        "-Dnosplash=true",
    ] {
        assert!(options.contains(&expected.to_owned()), "{expected}: {options:?}");
    }
    assert!(
        !options.iter().any(|option| option.contains("non.modal")),
        "the non-modal arm forces nothing"
    );
}

/// Every arm writes the class-load log. A space in the run directory is no problem, and a `:` is an error that names
/// the run directory.
#[test]
fn every_arm_writes_the_class_load_log_and_a_colon_in_the_run_directory_is_a_failure() {
    let sandbox = Sandbox::new("/s/sandbox");
    for arm in Arm::ALL {
        let options = measure_options(Path::new("/s/Application Support/run"), &sandbox, arm, None).expect("the options");
        assert!(
            options.contains(&"-Xlog:class+load:file=/s/Application Support/run/class-load.log:uptime,tags".to_owned()),
            "{arm:?}: {options:?}"
        );
    }
    assert_eq!(
        argfile_text(&[class_load_option(Path::new("/s/a b")).expect("an option")]),
        "-Xlog:class+load:file=/s/a\" \"b/class-load.log:uptime,tags\n"
    );
    assert_eq!(
        measure_options(Path::new("/s/a:b/run"), &sandbox, Arm::NonModal, None),
        Err(
            "the run directory /s/a:b/run holds a `:`, which ends the file name of -Xlog:class+load; use a session directory without one"
                .to_owned()
        )
    );
}

#[test]
fn the_modal_arm_forces_the_modal_screen_and_the_profile_adds_the_agent() {
    let options = measure_options(
        Path::new("/r"),
        &Sandbox::new("/b"),
        Arm::Modal,
        Some(Path::new("/lib/libasyncProfiler.dylib")),
    )
    .expect("the options");
    assert!(options.contains(&format!("-D{FORCE_MODAL_PROPERTY}")));
    assert_eq!(
        options.last().map(String::as_str),
        Some("-agentpath:/lib/libasyncProfiler.dylib=start,event=cpu,interval=1ms,threads,collapsed,file=/r/cpu.collapsed")
    );
}

#[test]
fn the_prime_run_of_a_project_opens_its_first_file_and_a_measured_run_restores_it() {
    let sandbox = Sandbox::new("/s/project-sandbox");
    let project = Project {
        name: "markdown".to_owned(),
        file: "README.md".into(),
    };
    let plan = |kind| RunPlan {
        arm: Arm::Project,
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
        arm: Arm::OpenProject,
        kind: RunKind::Measured,
        project: None,
        open_project: Some(Path::new("/p")),
    };
    assert!(
        welcome.program_arguments().is_empty(),
        "open-project starts the IDE without a project"
    );

    let options = measure_options(Path::new("/r"), &sandbox, Arm::Project, None).expect("the options");
    for property in PROJECT_PROPERTIES {
        assert!(options.contains(&format!("-D{property}")), "{property}");
    }
    assert!(!options.contains(&format!("-D{FORCE_MODAL_PROPERTY}")));
    let welcome_options = measure_options(Path::new("/r"), &sandbox, Arm::NonModal, None).expect("the options");
    for property in PROJECT_PROPERTIES {
        assert!(!welcome_options.contains(&format!("-D{property}")), "{property}");
    }
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
            arm: Arm::EmptyEditor,
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
    let options = measure_options(Path::new("/r"), &sandbox, Arm::EmptyEditor, None).expect("the options");
    for property in PROJECT_PROPERTIES {
        assert!(options.contains(&format!("-D{property}")), "{property}");
    }
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

/// A distribution with the files that the launch reads.
fn fake_dist(root: &Path) -> std::path::PathBuf {
    let dist = root.join("idea_dist.dist");
    std::fs::create_dir_all(dist.join("bin")).expect("a bin directory");
    std::fs::write(dist.join("bin/idea.vmoptions"), "-Xmx2048m\n\n# a comment\n-ea\n").expect("the vmoptions");
    std::fs::write(
        dist.join("bin/product-info.json"),
        format!(
            r#"{{"launch":[{{"additionalJvmArguments":["-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader","-Xbootclasspath/a:{}/lib/nio-fs.jar"]}}]}}"#,
            super::home_macro()
        ),
    )
    .expect("the product info");
    dist
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
    std::fs::write(dist.join("bin/other.vmoptions"), "").expect("a second vmoptions");
    let error = dist_options(&dist, &config, Path::new("/repo")).expect_err("two vmoptions");
    assert!(format!("{error:#}").ends_with("has no single *.vmoptions file"), "{error:#}");
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
