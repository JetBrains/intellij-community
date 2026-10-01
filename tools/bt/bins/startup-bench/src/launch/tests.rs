use std::path::Path;

use pretty_assertions::assert_eq;

use super::{FIDELITY_FLAGS, measure_flags, profiler_jar, sandbox_flags, scheme_arguments, script_binary};
use crate::arm::Arm;
use crate::session::Sandbox;

fn fidelity() -> Vec<String> {
    FIDELITY_FLAGS.iter().map(|flag| (*flag).to_owned()).collect()
}

#[test]
fn every_flag_is_a_jvm_flag_and_the_fidelity_flags_come_last() {
    let sandbox = Sandbox::new("/s/sandbox");
    let flags = measure_flags(Path::new("/s/run"), &sandbox, Arm::NonModal, None, &fidelity());
    assert!(flags.iter().all(|flag| flag.starts_with("--jvm_flag=")), "{flags:?}");
    assert_eq!(flags[..3], sandbox_flags(&sandbox)[..]);
    assert_eq!(flags[0], "--jvm_flag=-Didea.config.path=/s/sandbox/config");
    assert!(flags.contains(&"--jvm_flag=-Didea.log.path=/s/run/log".to_owned()));
    assert!(flags.contains(&"--jvm_flag=-Dplugin.classloader.debug=/s/run/plugin-classes.txt".to_owned()));
    assert!(flags.contains(&"--jvm_flag=-Dfus.internal.test.mode=true".to_owned()));
    assert!(
        !flags.iter().any(|flag| flag.contains("non.modal")),
        "the non-modal arm forces nothing"
    );
    let tail: Vec<&str> = flags[flags.len() - FIDELITY_FLAGS.len()..].iter().map(String::as_str).collect();
    assert_eq!(
        tail,
        FIDELITY_FLAGS.iter().map(|flag| format!("--jvm_flag={flag}")).collect::<Vec<_>>()
    );
}

#[test]
fn the_modal_arm_forces_the_modal_screen_and_the_profile_adds_the_agent() {
    let flags = measure_flags(
        Path::new("/r"),
        &Sandbox::new("/b"),
        Arm::Modal,
        Some(Path::new("/lib/libasyncProfiler.dylib")),
        &[],
    );
    assert!(flags.contains(&"--jvm_flag=-Didea.force.disable.non.modal.welcome.screen=true".to_owned()));
    assert_eq!(
        flags.last().map(String::as_str),
        Some("--jvm_flag=-agentpath:/lib/libasyncProfiler.dylib=start,event=cpu,interval=1ms,threads,collapsed,file=/r/cpu.collapsed")
    );
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
fn reads_the_binary_of_a_bazel_run_script() {
    let script = "#!/bin/bash\ncd /x && \\\n  exec env \\\n    -u JAVA_RUNFILES \\\n  /out/bin/build/idea \"$@\"\n";
    assert_eq!(script_binary(script), Some(Path::new("/out/bin/build/idea").to_path_buf()));
    assert_eq!(script_binary(""), None);
}

#[test]
fn finds_the_profiler_jar_among_the_cquery_files() {
    let files = "bazel-out/x/idea-async-profiler.jar\n\
                 external/lib+http/file/async-profiler-4.5-0.jar\n\
                 external/lib+http/file/async-profiler-4.5-0-sources.jar\n";
    assert_eq!(profiler_jar(files), Some("external/lib+http/file/async-profiler-4.5-0.jar"));
    assert_eq!(profiler_jar("bazel-out/x/idea-async-profiler.jar\n"), None);
}
