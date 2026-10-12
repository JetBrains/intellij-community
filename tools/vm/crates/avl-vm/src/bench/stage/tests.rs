use std::path::Path;

use avl_base::Reporter;
use avl_host_sys::lock::LockManager;
use avl_host_sys::{Ctx, Interrupts, Runner};
use pretty_assertions::assert_eq;

use super::{DistConfig, JBR_DIR, RECORD_FILE, Sources, parse_config, read_digest, stage, stays_inside};
use crate::bench::launch::OPENED_PACKAGES_FILE;

#[test]
fn reads_a_dist_config() {
    let config =
        parse_config("home.path=idea_dist.dist\nmain.class.name=com.intellij.idea.Main\nplatform.prefix=idea\nadditional.modules=\n")
            .expect("a config");
    assert_eq!(
        config,
        DistConfig {
            home: "idea_dist.dist".to_owned(),
            main_class: "com.intellij.idea.Main".to_owned(),
            platform_prefix: Some("idea".to_owned()),
            additional_modules: Vec::new(),
        }
    );
}

#[test]
fn reads_the_additional_modules_of_a_dist_config() {
    let config = parse_config(
        "home.path=jetbrains_light_all_plugins_dist.dist\nmain.class.name=com.intellij.idea.Main\nadditional.modules=intellij.markdown, intellij.json,,intellij.yaml \n",
    )
    .expect("a config");
    assert_eq!(
        config.additional_modules,
        vec!["intellij.markdown", "intellij.json", "intellij.yaml"]
    );
}

#[test]
fn refuses_a_config_the_launch_cannot_follow() {
    for (text, reason) in [
        ("main.class.name=M\n", "it has no home.path"),
        ("home.path=a.dist\n", "it has no main.class.name"),
        (
            "home.path=../a.dist\nmain.class.name=M\n",
            "its home.path ../a.dist is not the name of a sibling directory",
        ),
        ("home.path\n", "the line `home.path` is not key=value"),
    ] {
        assert_eq!(parse_config(text), Err(reason.to_owned()), "{text}");
    }
}

#[test]
fn a_link_stays_inside_its_tree_or_is_refused() {
    assert!(stays_inside(
        Path::new("legal/java.management.rmi"),
        Path::new("../java.base/LICENSE")
    ));
    assert!(stays_inside(Path::new("plugins/jcef"), Path::new("./cef_server.app/Contents")));
    assert!(!stays_inside(Path::new("legal"), Path::new("../../outside")));
    assert!(!stays_inside(Path::new(""), Path::new("..")));
    assert!(!stays_inside(Path::new("a"), Path::new("/absolute")));
}

/// The sources of a small distribution, a JBR and the opened packages under `root`.
fn fake_sources(root: &Path) -> Sources {
    let dist = root.join("bazel-bin/idea_dist.dist");
    std::fs::create_dir_all(dist.join("lib")).expect("a lib directory");
    std::fs::write(dist.join("lib/a.jar"), "a").expect("a jar");
    std::fs::write(dist.join("core-classpath.txt"), "lib/a.jar\n").expect("a class path");
    let config = root.join("bazel-bin/idea_dist.ide.config");
    std::fs::write(&config, "home.path=idea_dist.dist\nmain.class.name=com.intellij.idea.Main\n").expect("a config");
    let jbr = root.join("external/jbr");
    std::fs::create_dir_all(jbr.join("bin")).expect("a bin directory");
    std::fs::create_dir_all(jbr.join("legal/java.base")).expect("a legal directory");
    std::fs::write(jbr.join("bin/java"), "#!/bin/sh\n").expect("a java");
    std::fs::write(jbr.join("legal/java.base/LICENSE"), "license").expect("a license");
    #[cfg(unix)]
    {
        std::fs::create_dir_all(jbr.join("legal/java.rmi")).expect("a legal directory");
        std::os::unix::fs::symlink("../java.base/LICENSE", jbr.join("legal/java.rmi/LICENSE")).expect("a link");
    }
    let opened_packages = root.join("OpenedPackages.txt");
    std::fs::write(&opened_packages, "--add-opens=java.base/java.lang=ALL-UNNAMED\n").expect("the opened packages");
    Sources {
        target: "//build:idea_dist".to_owned(),
        dist,
        config,
        jbr,
        opened_packages,
    }
}

fn collaborators() -> (Runner, LockManager, Reporter) {
    let runner = Runner::new(std::env::vars(), Interrupts::detached());
    let locks = LockManager::new(runner.clone());
    let (reporter, _, _) = Reporter::in_memory("vm");
    (runner, locks, reporter)
}

#[tokio::test]
async fn a_stage_clones_the_sources_read_only_and_a_second_stage_reuses_them() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let sources = fake_sources(dir.path());
    let runtime = dir.path().join("runtime");
    let (_runner, locks, reporter) = collaborators();
    let ctx = Ctx::background();

    let generation = stage(&ctx, &locks, &runtime, &sources, &reporter).await.expect("a generation");
    assert!(!generation.reused);
    assert_eq!(generation.root, runtime.join("bench/generations").join(&generation.digest));
    assert_eq!(generation.dist, generation.root.join("idea_dist.dist"));
    assert_eq!(generation.java, generation.root.join(JBR_DIR).join("bin").join(super::java_name()));
    assert_eq!(read_digest(&generation.root).expect("the record"), generation.digest);
    let jar = generation.dist.join("lib/a.jar");
    assert_eq!(std::fs::read_to_string(&jar).expect("the clone"), "a");
    assert!(std::fs::metadata(&jar).expect("the clone").permissions().readonly());
    assert!(generation.root.join(OPENED_PACKAGES_FILE).is_file());
    #[cfg(unix)]
    assert_eq!(
        std::fs::read_link(generation.root.join("jbr/legal/java.rmi/LICENSE")).expect("the link"),
        std::path::PathBuf::from("../java.base/LICENSE")
    );
    let leftovers: Vec<String> = std::fs::read_dir(runtime.join("bench/generations"))
        .expect("the generations")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    assert_eq!(leftovers, vec![generation.digest.clone()], "no temporary stays");

    let again = stage(&ctx, &locks, &runtime, &sources, &reporter).await.expect("a generation");
    assert!(again.reused);
    assert_eq!(again.digest, generation.digest);

    std::fs::write(sources.dist.join("lib/a.jar"), "changed").expect("a rebuild");
    let rebuilt = stage(&ctx, &locks, &runtime, &sources, &reporter).await.expect("a generation");
    assert!(!rebuilt.reused);
    assert_ne!(rebuilt.digest, generation.digest);
    assert_eq!(
        std::fs::read_to_string(generation.dist.join("lib/a.jar")).expect("the first generation"),
        "a",
        "a rebuild leaves the first generation as it was"
    );
    assert!(generation.root.join(RECORD_FILE).is_file());
}

#[cfg(unix)]
#[tokio::test]
async fn a_link_out_of_the_tree_is_refused_and_leaves_no_temporary() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let sources = fake_sources(dir.path());
    std::os::unix::fs::symlink("/etc/hosts", sources.dist.join("lib/out.jar")).expect("a link");
    let runtime = dir.path().join("runtime");
    let (_runner, locks, reporter) = collaborators();
    let refusal = stage(&Ctx::background(), &locks, &runtime, &sources, &reporter)
        .await
        .expect_err("a refusal");
    assert_eq!(refusal.code, "bench_dist_unsupported");
    assert!(refusal.message.contains("links to /etc/hosts"), "{}", refusal.message);
    let left = std::fs::read_dir(runtime.join("bench/generations"))
        .expect("the generations")
        .count();
    assert_eq!(left, 0);
}

#[tokio::test]
async fn a_config_that_names_another_home_is_refused() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let sources = fake_sources(dir.path());
    std::fs::write(&sources.config, "home.path=other.dist\nmain.class.name=M\n").expect("a config");
    let (_runner, locks, reporter) = collaborators();
    let refusal = stage(&Ctx::background(), &locks, &dir.path().join("runtime"), &sources, &reporter)
        .await
        .expect_err("a refusal");
    assert_eq!(refusal.code, "bench_dist_unsupported");
    assert!(refusal.message.contains("names the home other.dist"), "{}", refusal.message);
}
