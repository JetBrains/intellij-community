//! The files that the tests of the preparation and the launch share: a dev distribution, a flags file, a project
//! archive, and the IDE root that the contexts go under.

use std::collections::BTreeMap;
use std::fs::{self, File};
use std::io::Write;
use std::os::unix::fs::PermissionsExt;
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

use avl_wire::ide::{self, IdeContext, IdeGcResult, IdeLaunch, IdeLaunched, IdePrepared, ProjectSource};
use avl_wire::supervisor::Phase;
use zip::write::SimpleFileOptions;

use super::launch::{self, Guest};
use super::{gc, prepare};
use crate::cli::{IdeGcArgs, RootArgs, RunArgs};
use crate::supervisor::{LaunchHost, Launcher, LiveSystem};
use crate::testing::agent_launcher;

pub(crate) const LAUNCH_KEY: &str = "key-1";
/// A property of every launch document, which the argument file must hold.
pub(crate) const PORT_PROPERTY: &str = "air.ui.test.http.port";
/// The `vmOptionsFilePath` of the fixture distribution, relative to the directory of its `product-info.json`.
pub(crate) const VM_OPTIONS_FILE_PATH: &str = if cfg!(target_os = "macos") {
    "../bin/idea64.vmoptions"
} else {
    "bin/idea64.vmoptions"
};

/// A guest that needs no display.
pub(crate) const NO_DISPLAY_CHECK: Guest = Guest {
    linux: false,
    display: None,
};

pub(crate) struct Fixture {
    directory: tempfile::TempDir,
}

impl Fixture {
    pub(crate) fn new() -> Self {
        let directory = tempfile::tempdir().unwrap();
        let root = directory.path();
        let bin = root.join("dist/bin");
        fs::create_dir_all(&bin).unwrap();
        // A data directory in `idea.properties` must lose to the one of the context.
        fs::write(
            bin.join("idea.properties"),
            "idea.config.path=/from/idea.properties\nfrom.idea.properties=1\n",
        )
        .unwrap();
        fs::write(bin.join("idea64.vmoptions"), "-Xmx2g\n-Dfrom.vmoptions=1\n").unwrap();
        let product_info = root.join("dist").join(launch::PRODUCT_INFO_FILE);
        fs::create_dir_all(product_info.parent().unwrap()).unwrap();
        fs::write(
            product_info,
            format!(
                r#"{{"launch":[{{"vmOptionsFilePath":"{VM_OPTIONS_FILE_PATH}","additionalJvmArguments":["-Dfrom.product.info=1"]}}]}}"#
            ),
        )
        .unwrap();
        fs::write(root.join("dist/core-classpath.txt"), "lib/a.jar\nlib/b.jar\n").unwrap();
        fs::write(root.join("dist.config"), "home.path=dist\nmain.class.name=com.intellij.idea.Main\n").unwrap();
        fs::write(root.join("ide.flags"), "-ea\n\n--add-opens=java.base/java.lang=ALL-UNNAMED\n").unwrap();
        write_project_archive(&root.join("project.zip"));
        Self { directory }
    }

    pub(crate) fn path(&self, relative: &str) -> PathBuf {
        self.directory.path().join(relative)
    }

    pub(crate) fn text(&self, relative: &str) -> String {
        self.path(relative).to_string_lossy().into_owned()
    }

    pub(crate) fn context(&self) -> PathBuf {
        self.path(&format!("ide/{LAUNCH_KEY}"))
    }

    pub(crate) fn context_document(&self, fresh: bool) -> IdeContext {
        IdeContext {
            schema_version: ide::SCHEMA_VERSION,
            ide_root: self.text("ide"),
            launch_key: LAUNCH_KEY.to_owned(),
            fresh,
            project: ProjectSource {
                archive: self.text("project.zip"),
                archive_root: "BookmarksTestProject".to_owned(),
                relocate_to: None,
            },
            disabled_plugin_ids: vec!["com.intellij.copyright".to_owned(), "org.jetbrains.junie".to_owned()],
        }
    }

    pub(crate) fn launch_document(&self, launch_name: &str, prepared: &IdePrepared) -> IdeLaunch {
        IdeLaunch {
            schema_version: ide::SCHEMA_VERSION,
            ide_root: self.text("ide"),
            launch_key: LAUNCH_KEY.to_owned(),
            dist_home: self.text("dist"),
            ide_config: self.text("dist.config"),
            java_home: self.text("jbr"),
            flags_file: self.text("ide.flags"),
            properties: BTreeMap::from([
                (PORT_PROPERTY.to_owned(), "17000".to_owned()),
                ("user.home".to_owned(), prepared.home_dir.clone()),
            ]),
            project_dir: prepared.project_dir.clone(),
            launch_name: launch_name.to_owned(),
            product_digest: "product-1".to_owned(),
        }
    }
}

fn write_project_archive(path: &Path) {
    let mut writer = zip::ZipWriter::new(File::create(path).unwrap());
    let options = SimpleFileOptions::default().compression_method(zip::CompressionMethod::Stored);
    for (name, content) in [
        ("BookmarksTestProject/src/Main.java", "class Main {}\n"),
        ("BookmarksTestProject/.idea/misc.xml", "<project/>\n"),
    ] {
        writer.start_file(name, options).unwrap();
        writer.write_all(content.as_bytes()).unwrap();
    }
    writer.finish().unwrap();
}

pub(crate) fn prepared(document: &IdeContext) -> IdePrepared {
    prepare::prepare(&LiveSystem, document).unwrap_or_else(|refusal| panic!("the preparation was refused: {refusal}"))
}

pub(crate) fn mode(path: &str) -> u32 {
    fs::metadata(path).unwrap().permissions().mode() & 0o777
}

/// The text of `path` once a writer has published it whole, ended with a line break.
pub(crate) fn wait_for_file(path: &Path) -> String {
    let until = Instant::now() + Duration::from_secs(10);
    while Instant::now() < until {
        if let Ok(text) = fs::read_to_string(path)
            && text.ends_with('\n')
        {
            return text;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    panic!("{} was not written", path.display());
}

/// A context whose IDE is a fake `java` that records its argv and its environment and stays. The agent runs with a
/// canary, a credential and a channel variable in its environment, which the IDE must not get.
pub(crate) struct FakeIde {
    pub fixture: Fixture,
    /// The `bin` directory of the fake JVM, where the fake `java` writes `argv.txt` and `env.txt`.
    pub tools: PathBuf,
    launcher: Launcher,
}

impl FakeIde {
    /// The fake IDE, or `None` when the host has no `/bin/ps` or `/bin/sleep`.
    pub(crate) fn new() -> Option<Self> {
        if !Path::new("/bin/ps").is_file() || !Path::new("/bin/sleep").is_file() {
            eprintln!("skipped: /bin/ps or /bin/sleep is not on this host");
            return None;
        }
        let fixture = Fixture::new();
        let tools = fixture.path("jbr/bin");
        fs::create_dir_all(&tools).unwrap();
        avl_testkit::fake_executable(
            &tools,
            "java",
            "dir=\"$(dirname \"$0\")\"\nprintenv > \"$dir/env.txt\"\nprintf '%s\\n' \"$@\" > \"$dir/argv.tmp\"\n\
             mv \"$dir/argv.tmp\" \"$dir/argv.txt\"\nexec /bin/sleep 60\n",
        )
        .unwrap();
        let agent = agent_launcher(&fixture.path(""));
        let with_canary = avl_testkit::fake_executable(
            &fixture.path(""),
            "agent-with-canary",
            &format!(
                "export AIR_CANARY=1 TART_VM_TOKEN=channel-secret ANTHROPIC_API_KEY=vendor-secret CODEX_HOME=/elsewhere\n\
                 exec '{}' \"$@\"\n",
                agent.display()
            ),
        )
        .unwrap();
        let launcher = Launcher {
            self_exe: with_canary,
            host: LaunchHost::current(),
        };
        Some(Self { fixture, tools, launcher })
    }

    /// Prepares the context and launches `launch_name` on it.
    pub(crate) fn launch(&self, launch_name: &str, fresh: bool) -> (IdePrepared, IdeLaunched) {
        let context = prepared(&self.fixture.context_document(fresh));
        let launched = launch::launch(
            &LiveSystem,
            &self.launcher,
            &NO_DISPLAY_CHECK,
            &self.fixture.launch_document(launch_name, &context),
        )
        .unwrap_or_else(|refusal| panic!("the launch was refused: {refusal}"));
        assert_eq!(launched.run.phase, Phase::Running, "{launched:?}");
        (context, launched)
    }

    pub(crate) fn gc(&self, stop_all: bool, keep_product: Option<&str>, keep_logs: u32) -> IdeGcResult {
        gc::collect(
            &LiveSystem,
            &IdeGcArgs {
                root: self.fixture.path("ide"),
                stop_all,
                keep_product: keep_product.map(str::to_owned),
                keep_logs,
                grace_ms: 5_000,
            },
        )
        .unwrap()
    }

    pub(crate) fn run(&self, launched: &IdeLaunched) -> RunArgs {
        RunArgs {
            root: RootArgs {
                root: self.fixture.context(),
            },
            run_id: launched.run.run_id.clone(),
        }
    }
}
