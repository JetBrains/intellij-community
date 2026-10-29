use std::path::{Path, PathBuf};

use avl_base::{Refusal, Reporter};
use avl_host_sys::lock::LockManager;
use avl_host_sys::{Ctx, Interrupts, Runner};
use pretty_assertions::assert_eq;

use super::{DistConfig, Generation, RECORD_FILE, Sources, java_in, java_name, open, parse_config, read_digest, stage, stays_inside};
use crate::bench::launch::OPENED_PACKAGES_FILE;
use crate::bench::session::GitInfo;

/// The checkout that built the sources of a test.
fn built() -> GitInfo {
    GitInfo {
        commit: "3f337093836d2000000000000000000000000000".to_owned(),
        dirty: true,
    }
}

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
fn reads_a_dist_config_with_the_home_below_the_dist_directory() {
    let config = parse_config("home.path=idea_dist.dist/Contents\nmain.class.name=com.intellij.idea.Main\n").expect("a config");
    assert_eq!(config.home, "idea_dist.dist/Contents");
    assert_eq!(config.dist_name(), "idea_dist.dist");
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
            "its home.path ../a.dist is neither a sibling directory nor a directory in one",
        ),
        (
            "home.path=a.dist/Contents/MacOS\nmain.class.name=M\n",
            "its home.path a.dist/Contents/MacOS is neither a sibling directory nor a directory in one",
        ),
        (
            "home.path=a.dist/\nmain.class.name=M\n",
            "its home.path a.dist/ is neither a sibling directory nor a directory in one",
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

#[test]
fn the_java_of_a_home_is_the_one_of_its_jbr() {
    assert_eq!(
        java_in(Path::new("/g/idea_dist.dist/Contents")),
        Path::new("/g/idea_dist.dist/Contents/jbr/Contents/Home/bin").join(java_name())
    );
}

/// The sources of a small distribution with its JBR, and the opened packages, under `root`.
fn fake_sources(root: &Path) -> Sources {
    let dist = root.join("bazel-bin/idea_dist.dist");
    std::fs::create_dir_all(dist.join("lib")).expect("a lib directory");
    std::fs::write(dist.join("lib/a.jar"), "a").expect("a jar");
    std::fs::write(dist.join("core-classpath.txt"), "lib/a.jar\n").expect("a class path");
    let config = root.join("bazel-bin/idea_dist.ide.config");
    std::fs::write(&config, "home.path=idea_dist.dist\nmain.class.name=com.intellij.idea.Main\n").expect("a config");
    let jbr = dist.join("jbr/Contents/Home");
    std::fs::create_dir_all(jbr.join("bin")).expect("a bin directory");
    std::fs::create_dir_all(jbr.join("legal/java.base")).expect("a legal directory");
    std::fs::write(jbr.join("bin").join(java_name()), "#!/bin/sh\n").expect("a java");
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
        opened_packages,
    }
}

/// The fake sources of [`fake_sources`] in a temporary directory, the runtime root beside them, and the collaborators of
/// a stage.
struct Fixture {
    _dir: tempfile::TempDir,
    sources: Sources,
    runtime: PathBuf,
    _runner: Runner,
    locks: LockManager,
    reporter: Reporter,
}

impl Fixture {
    fn new() -> Self {
        let dir = tempfile::tempdir().expect("a temporary directory");
        let sources = fake_sources(dir.path());
        let runtime = dir.path().join("runtime");
        let runner = Runner::new(std::env::vars(), Interrupts::detached());
        let locks = LockManager::new(runner.clone());
        let (reporter, _, _) = Reporter::in_memory("vm");
        Self {
            _dir: dir,
            sources,
            runtime,
            _runner: runner,
            locks,
            reporter,
        }
    }

    /// Stages the sources into the runtime root, as the checkout `git` built them.
    async fn stage(&self, git: &GitInfo) -> Result<Generation, Refusal> {
        stage(&Ctx::background(), &self.locks, &self.runtime, &self.sources, git, &self.reporter).await
    }

    /// The count of the entries of the generations directory, the temporaries of a stage among them.
    fn generation_entries(&self) -> usize {
        std::fs::read_dir(self.runtime.join("bench/generations"))
            .expect("the generations")
            .count()
    }
}

/// Asserts that `refusal` refuses the distribution with a message that contains `part`.
fn assert_unsupported(refusal: &Refusal, part: &str) {
    assert_eq!(refusal.code, "bench_dist_unsupported");
    assert!(refusal.message.contains(part), "{}", refusal.message);
}

#[tokio::test]
async fn a_stage_clones_the_sources_read_only_and_a_second_stage_reuses_them() {
    let fixture = Fixture::new();
    let (sources, runtime) = (&fixture.sources, &fixture.runtime);
    let generation = fixture.stage(&built()).await.expect("a generation");
    assert!(!generation.reused);
    assert_eq!(generation.root, runtime.join("bench/generations").join(&generation.digest));
    assert_eq!(generation.dist, generation.root.join("idea_dist.dist"));
    assert_eq!(generation.home, generation.dist);
    assert_eq!(generation.java, java_in(&generation.home));
    assert!(generation.java.is_file(), "the clone of the distribution holds its JBR");
    assert!(!generation.root.join("jbr").exists(), "no JBR beside the distribution");
    assert_eq!(read_digest(&generation.root).expect("the record"), generation.digest);
    let jar = generation.dist.join("lib/a.jar");
    assert_eq!(std::fs::read_to_string(&jar).expect("the clone"), "a");
    assert!(std::fs::metadata(&jar).expect("the clone").permissions().readonly());
    assert!(generation.root.join(OPENED_PACKAGES_FILE).is_file());
    #[cfg(unix)]
    assert_eq!(
        std::fs::read_link(generation.dist.join("jbr/Contents/Home/legal/java.rmi/LICENSE")).expect("the link"),
        PathBuf::from("../java.base/LICENSE")
    );
    let leftovers: Vec<String> = std::fs::read_dir(runtime.join("bench/generations"))
        .expect("the generations")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    assert_eq!(leftovers, vec![generation.digest.clone()], "no temporary stays");

    let again = fixture.stage(&built()).await.expect("a generation");
    assert!(again.reused);
    assert_eq!(again.digest, generation.digest);

    std::fs::write(sources.dist.join("lib/a.jar"), "changed").expect("a rebuild");
    let rebuilt = fixture.stage(&built()).await.expect("a generation");
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
    let fixture = Fixture::new();
    std::os::unix::fs::symlink("/etc/hosts", fixture.sources.dist.join("lib/out.jar")).expect("a link");
    let refusal = fixture.stage(&built()).await.expect_err("a refusal");
    assert_unsupported(&refusal, "links to /etc/hosts");
    assert_eq!(fixture.generation_entries(), 0);
}

/// Moves the home of the fake distribution of `sources` to `Contents`, as a macOS app has it.
fn move_home_to_contents(sources: &Sources) {
    std::fs::create_dir_all(sources.dist.join("Contents")).expect("the home");
    for name in ["lib", "core-classpath.txt", "jbr"] {
        std::fs::rename(sources.dist.join(name), sources.dist.join("Contents").join(name)).expect("a move");
    }
    std::fs::write(
        &sources.config,
        "home.path=idea_dist.dist/Contents\nmain.class.name=com.intellij.idea.Main\n",
    )
    .expect("a config");
}

// A macOS app has its home in `Contents`. The generation clones the whole `.dist` directory, starts from the home, and
// takes the JBR of the home.
#[tokio::test]
async fn a_stage_starts_from_the_home_that_the_config_names_below_the_dist_directory() {
    let fixture = Fixture::new();
    move_home_to_contents(&fixture.sources);
    let generation = fixture.stage(&built()).await.expect("a generation");
    assert_eq!(generation.dist, generation.root.join("idea_dist.dist"));
    assert_eq!(generation.home, generation.dist.join("Contents"));
    assert_eq!(std::fs::read_to_string(generation.home.join("lib/a.jar")).expect("the clone"), "a");
    assert_eq!(
        generation.java,
        generation.dist.join("Contents/jbr/Contents/Home/bin").join(java_name())
    );
    assert!(generation.java.is_file(), "the clone of the distribution holds its JBR");
}

/// A distribution without `jbr/Contents/Home/bin/java` in its home is refused with the path, and so is a home without
/// `core-classpath.txt`. Neither stages a generation.
#[tokio::test]
async fn a_distribution_without_its_jbr_or_its_class_path_is_refused() {
    let fixture = Fixture::new();
    move_home_to_contents(&fixture.sources);
    let java = fixture.sources.dist.join("Contents/jbr/Contents/Home/bin").join(java_name());
    std::fs::remove_file(&java).expect("no java");
    let refusal = fixture.stage(&built()).await.expect_err("a refusal");
    assert_unsupported(&refusal, &format!("{} is not a file", java.display()));

    std::fs::remove_file(fixture.sources.dist.join("Contents/core-classpath.txt")).expect("no class path");
    let refusal = fixture.stage(&built()).await.expect_err("a refusal");
    assert_unsupported(&refusal, "has no core-classpath.txt");
    assert_eq!(fixture.generation_entries(), 0);
}

#[tokio::test]
async fn a_config_that_names_another_home_is_refused() {
    let fixture = Fixture::new();
    std::fs::write(&fixture.sources.config, "home.path=other.dist\nmain.class.name=M\n").expect("a config");
    let refusal = fixture.stage(&built()).await.expect_err("a refusal");
    assert_unsupported(&refusal, "names the home other.dist");
}

/// The record of a stage names the commit of the checkout that built the bytes, and `open` finds the generation by a
/// prefix of its digest with that commit. A reused generation keeps the commit of its first stage, whose bytes are the
/// same.
#[tokio::test]
async fn a_generation_names_its_commit_and_opens_by_a_prefix_of_its_digest() {
    let fixture = Fixture::new();
    let runtime = &fixture.runtime;
    let generation = fixture.stage(&built()).await.expect("a generation");
    let staged = open(runtime, &generation.digest[..8]).expect("the generation");
    assert_eq!(
        staged.generation,
        Generation {
            reused: true,
            ..generation.clone()
        }
    );
    assert_eq!((staged.target.as_str(), staged.git), ("//build:idea_dist", Some(built())));

    let later = GitInfo {
        commit: "b0b0b0b0b0b01111111111111111111111111111".to_owned(),
        dirty: false,
    };
    let again = fixture.stage(&later).await.expect("a generation");
    assert!(again.reused);
    assert_eq!(open(runtime, &generation.digest).expect("the generation").git, Some(built()));
}

/// Writes a published generation `digest` under `generations` with the record of a stage from before the commit, the
/// config copy and the JBR of the distribution.
fn fake_generation(generations: &Path, digest: &str) {
    let root = generations.join(digest);
    std::fs::create_dir_all(&root).expect("a generation");
    let record = serde_json::json!({
        "digest": digest,
        "target": "//build:idea_dist",
        "distName": "idea_dist.dist",
        "sources": {"dist": "/b/idea_dist.dist", "config": "/b/idea_dist.ide.config", "jbr": "/b/jbr", "opened_packages": "/r/OpenedPackages.txt"},
        "files": 1,
        "bytes": 1,
        "stagedAt": "2026-10-01T13:15:00Z",
    });
    std::fs::write(root.join(RECORD_FILE), record.to_string()).expect("a record");
    std::fs::write(
        root.join("ide.config"),
        "home.path=idea_dist.dist\nmain.class.name=com.intellij.idea.Main\n",
    )
    .expect("a config");
    let java = java_in(&root.join("idea_dist.dist"));
    std::fs::create_dir_all(java.parent().expect("a bin")).expect("a bin directory");
    std::fs::write(&java, "").expect("a java");
}

/// A prefix that no generation has, or that several have, is refused with the candidates. An unfinished stage is no
/// candidate, and a generation from before the record of the commit opens without one.
#[test]
fn an_unknown_or_ambiguous_prefix_is_refused_with_the_candidates() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let runtime = dir.path();
    let refusal = open(runtime, "ab").expect_err("no generation");
    assert_eq!(refusal.code, "no_generation");
    assert_eq!(refusal.exit, avl_base::Exit::USAGE);
    assert!(
        refusal.message.ends_with("has a digest that starts with ab; it has none"),
        "{}",
        refusal.message
    );

    let generations = runtime.join("bench/generations");
    for digest in ["ab12", "ab34", "cd56"] {
        fake_generation(&generations, digest);
    }
    std::fs::create_dir_all(generations.join(".stage-ab99-1")).expect("an unfinished stage");
    let refusal = open(runtime, "ab").expect_err("two generations");
    assert_eq!(refusal.code, "usage");
    assert_eq!(
        refusal.message,
        "2 generations have a digest that starts with ab: ab12, ab34; name a longer prefix"
    );
    let refusal = open(runtime, "ff").expect_err("no such generation");
    assert!(
        refusal
            .message
            .ends_with("has a digest that starts with ff; its generations are ab12, ab34, cd56"),
        "{}",
        refusal.message
    );
    let old = open(runtime, "cd").expect("a generation");
    assert_eq!((old.generation.digest.as_str(), old.git), ("cd56", None));
    assert_eq!(old.generation.dist, generations.join("cd56/idea_dist.dist"));
}

/// A generation of the first layout has its JBR beside the distribution, not in it. `open` refuses it with the path
/// of the missing `java`.
#[test]
fn a_generation_of_the_earlier_layout_is_refused() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let generations = dir.path().join("bench/generations");
    fake_generation(&generations, "ab12");
    let java = java_in(&generations.join("ab12/idea_dist.dist"));
    std::fs::remove_file(&java).expect("no java");
    let refusal = open(dir.path(), "ab").expect_err("an earlier layout");
    assert_unsupported(&refusal, &format!("has no JBR at {}", java.display()));
}
