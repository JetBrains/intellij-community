//! The helpers that the suites of `lane` share: the launch inputs of an IDE root, a context plugin file, the
//! lane-context fixtures and the token check of a fake IDE.

use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};

use avl_testkit::http::{Request, Response};
use avl_trace::bridge::TOKEN_HEADER;

use crate::lane::ide::ContextPlugin;
use crate::lane::inputs::{LaunchInputs, TraceInputs};
use crate::lane::package::sha256_hex;

/// The launch inputs of an IDE whose files are under `root`, with no file behind them.
pub(crate) fn launch_inputs(root: &Path) -> LaunchInputs {
    LaunchInputs {
        ide_root: root.join("ide"),
        home: root.join("home"),
        dist_home: root.join("dist"),
        ide_config: root.join("dist.properties"),
        java_home: root.join("dist/jbr"),
        flags_file: root.join("flags.txt"),
        project_archive: root.join("project.zip"),
        project_root: "plain-project".to_owned(),
        runtimes: Vec::new(),
        distribution_plugins: BTreeSet::new(),
        trace: TraceInputs::default(),
    }
}

/// The launch inputs of an IDE at the guest paths of a lane worker, with no file behind them.
pub(crate) fn guest_launch_inputs() -> LaunchInputs {
    LaunchInputs {
        ide_root: "/vm/data/ide".into(),
        home: "/vm/home".into(),
        dist_home: "/d".into(),
        ide_config: "/d.properties".into(),
        java_home: "/d/jbr".into(),
        flags_file: "/f".into(),
        project_archive: "/p.zip".into(),
        ..launch_inputs(Path::new("/"))
    }
}

/// A context plugin file `destination` whose bytes are `content`, written into `sources`.
pub(crate) fn context_plugin(sources: &Path, destination: &str, content: &str) -> ContextPlugin {
    let source = sources.join(destination.replace('/', "_"));
    fs::write(&source, content).unwrap();
    ContextPlugin {
        destination: destination.to_owned(),
        sha256: sha256_hex(content.as_bytes()),
        source,
    }
}

/// The fixture `name` of `testdata/lane-context`, as an absolute path.
pub(crate) fn lane_context_testdata(name: &str) -> PathBuf {
    let path = avl_testkit::crate_path!(&format!("testdata/lane-context/{name}"));
    std::path::absolute(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// The answer of a fake IDE to a request without the test token `token`: the refusal of the IDE, 401 with a
/// `SecurityException`. A request with the token gets `None`.
pub(crate) fn token_refusal(request: &Request, token: &str) -> Option<Response> {
    (request.header(TOKEN_HEADER) != Some(token))
        .then(|| Response::json(401, r#"{"failure":{"type":"java.lang.SecurityException","message":"bad token"}}"#))
}
