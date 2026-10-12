//! The suite's fixtures: the committed suite documents, a checkout that holds them, and the contract's golden
//! example bundle.
//!
//! Every expectation is read out of the committed documents, not written down: a test that pinned "rename-session
//! has one scenario" would be a second copy of the catalog, and it would go stale the first time the generator
//! moved.

use std::collections::BTreeMap;
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Output};

use bt_core::catalog::{SuiteDocument, parse_suite_document};
use tempfile::TempDir;

/// Where the Air lane table says the suite documents are.
fn flow_profile_dir() -> &'static str {
    avl_affected::bridge::install_fixture();
    avl_affected::air_area()
        .catalog()
        .expect("the Air lane table names a catalog")
        .flow_profile_dir
}

/// Every committed suite document, by file name, or `None` where this run cannot reach them.
///
/// Under Bazel they arrive as the `flow-profiles` module's test resource jar, named by `AIR_FLOW_PROFILES_JAR`. Only
/// `//plugins/air/tests/integration/vm-contract:air-trace-plan-test` of the ultimate root has the jar, so the test of
/// this crate in the community module skips. Under cargo they are read where the generator writes them in the ultimate
/// checkout, and a community checkout skips.
pub(super) fn committed_documents() -> Option<BTreeMap<String, Vec<u8>>> {
    let mut documents = BTreeMap::new();
    if let Some(rlocation) = std::env::var_os("AIR_FLOW_PROFILES_JAR").filter(|value| !value.is_empty()) {
        let jar = avl_testkit::rlocation(&rlocation.to_string_lossy());
        let file = fs::File::open(&jar).unwrap_or_else(|error| panic!("open {}: {error}", jar.display()));
        let mut archive = zip::ZipArchive::new(file).expect("the resource jar is a zip");
        for index in 0..archive.len() {
            let mut entry = archive.by_index(index).expect("a jar entry");
            let name = entry.name().to_owned();
            let Some(base) = name.strip_prefix("flow-profiles/") else {
                continue;
            };
            if !base.ends_with(".json") || base.contains('/') {
                continue;
            }
            let mut content = Vec::new();
            entry.read_to_end(&mut content).expect("read a jar entry");
            documents.insert(base.to_owned(), content);
        }
    } else {
        let Some(root) = avl_testkit::ultimate_root() else {
            println!("skipped: AIR_FLOW_PROFILES_JAR is unset and no ultimate checkout is around the community module");
            return None;
        };
        let directory = root.join(flow_profile_dir());
        let Ok(entries) = fs::read_dir(&directory) else {
            println!(
                "skipped: AIR_FLOW_PROFILES_JAR is unset and {} is not readable",
                directory.display()
            );
            return None;
        };
        for entry in entries.filter_map(Result::ok) {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.ends_with(".json") {
                documents.insert(name, fs::read(entry.path()).expect("read a suite document"));
            }
        }
    }
    assert!(!documents.is_empty(), "no committed suite document was found");
    Some(documents)
}

/// Returns from the test, saying why, when the committed documents are out of reach.
macro_rules! documents_or_skip {
    () => {
        match $crate::plan::tests::fixture::committed_documents() {
            Some(documents) => documents,
            None => return,
        }
    };
}
pub(crate) use documents_or_skip;

/// Every committed document, decoded by `bt`'s reader, in file-name order. The decoding is pinned by `bt`'s own
/// tests; what this suite holds the planner to is its answers over the documents.
pub(super) fn suite_documents(documents: &BTreeMap<String, Vec<u8>>) -> Vec<SuiteDocument> {
    documents
        .iter()
        .map(|(name, content)| parse_suite_document(content).unwrap_or_else(|error| panic!("{name}: {error}")))
        .collect()
}

/// The controller's spelling of a document's lane, as `vm.cmd --lane` takes it.
pub(super) fn lane_word(document: &SuiteDocument) -> &'static str {
    avl_affected::bridge::install_fixture();
    document
        .lane_name(avl_affected::air_area().lanes())
        .unwrap_or_else(|| panic!("{} declares no UI lane", document.suite))
}

/// A checkout holding the committed suite documents where the generator writes them, and nothing else until a test
/// adds it. The path is resolved: on macOS the temporary directory is reached through `/var`, a link to
/// `/private/var`, and the join answers paths relative to the resolved root.
pub(super) struct Repo {
    _dir: TempDir,
    pub root: PathBuf,
}

pub(super) fn fixture_repo(documents: &BTreeMap<String, Vec<u8>>) -> Repo {
    let dir = TempDir::new().expect("a temporary checkout");
    let root = fscopy::resolve_links(dir.path()).expect("resolve the temporary checkout");
    let repo = Repo { _dir: dir, root };
    for (name, content) in documents {
        write_bytes(&repo.root, &format!("{}/{name}", flow_profile_dir()), content);
    }
    repo
}

impl Repo {
    /// Writes one file into the checkout, creating its directories, and answers its path.
    pub(super) fn write(&self, relative: &str, content: &str) -> PathBuf {
        write_bytes(&self.root, relative, content.as_bytes())
    }
}

pub(super) fn write_bytes(root: &Path, relative: &str, content: &[u8]) -> PathBuf {
    let path = relative.split('/').fold(root.to_path_buf(), |path, segment| path.join(segment));
    fs::create_dir_all(path.parent().expect("a file has a parent")).expect("create directories");
    fs::write(&path, content).expect("write a fixture file");
    path
}

/// Runs git in a fixture checkout with an identity of its own, so no user configuration is needed.
pub(super) fn run_git(repo: &Path, argv: &[&str]) -> std::io::Result<Output> {
    Command::new("git")
        .arg("-C")
        .arg(repo)
        .args(["-c", "user.email=plan@example.invalid", "-c", "user.name=plan"])
        .args(argv)
        .output()
}

/// An archive of files by entry name, in a directory of its own.
pub(super) struct Zip {
    _dir: TempDir,
    pub path: PathBuf,
}

pub(super) fn zip_of(entries: &[(&str, &[u8])]) -> Zip {
    let dir = TempDir::new().expect("a temporary directory");
    let path = dir.path().join("traces.zip");
    let mut writer = zip::ZipWriter::new(fs::File::create(&path).expect("create the zip"));
    let mut sorted = entries.to_vec();
    sorted.sort_by_key(|(name, _)| *name);
    for (name, content) in sorted {
        writer
            .start_file(name, zip::write::SimpleFileOptions::default())
            .expect("start a zip entry");
        writer.write_all(content).expect("write a zip entry");
    }
    writer.finish().expect("finish the zip");
    Zip { _dir: dir, path }
}
