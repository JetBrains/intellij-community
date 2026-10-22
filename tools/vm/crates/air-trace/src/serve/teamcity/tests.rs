use std::cell::RefCell;
use std::collections::BTreeMap;

use pretty_assertions::assert_eq;

use super::*;

/// The REST answers recorded from the Air UI tests aggregator build 1100229901 and its chain.
fn recorded(name: &str) -> Vec<u8> {
    let path = avl_testkit::crate_path!(&format!("testdata/teamcity/{name}"));
    fs::read(&path).unwrap_or_else(|error| panic!("{}: {error}", path.display()))
}

/// A server that answers from a table of paths, and writes `content` for each download.
#[derive(Default)]
struct FakeRest {
    answers: BTreeMap<String, Vec<u8>>,
    content: Vec<u8>,
    gets: RefCell<Vec<String>>,
    downloads: RefCell<Vec<String>>,
}

impl FakeRest {
    fn recorded_chain() -> Self {
        let mut fake = Self::default();
        for id in [1_100_229_901_u64, 1_099_482_364, 1_099_482_408] {
            fake.answer(&build_path(id), recorded(&format!("build-{id}.json")));
        }
        fake
    }

    fn answer(&mut self, path: &str, body: impl Into<Vec<u8>>) {
        self.answers.insert(path.to_owned(), body.into());
    }
}

fn build_path(id: u64) -> String {
    format!("/app/rest/builds/id:{id}?fields={BUILD_FIELDS}")
}

fn artifacts_path(id: u64) -> String {
    format!("/app/rest/builds/id:{id}/artifacts/children/?fields=file(name,size)")
}

impl Rest for FakeRest {
    fn get(&self, path: &str) -> Result<Vec<u8>, Refusal> {
        self.gets.borrow_mut().push(path.to_owned());
        self.answers
            .get(path)
            .cloned()
            .ok_or_else(|| failed(format!("no answer for {path}")))
    }

    fn download(&self, path: &str, to: &Path) -> Result<(), Refusal> {
        self.downloads.borrow_mut().push(path.to_owned());
        fs::write(to, &self.content).map_err(|error| failed(error.to_string()))
    }

    fn program(&self) -> &'static str {
        "fake"
    }
}

/// The server of the recorded chain.
const SERVER: &str = "https://buildserver.labs.intellij.net";

/// A build is its id, or a build page of a server: by the last number of the path, or by `buildId=`. A server is
/// `https`, or `http` on a loopback host, and it is compared in lower case.
#[test]
fn a_build_is_an_id_or_the_url_of_its_page() {
    let parsed = |given: &str| parse_build(given, DEFAULT_TEAMCITY_URL).map_err(|refused| refused.message);
    let default = |id| {
        Ok(BuildRef {
            server: DEFAULT_TEAMCITY_URL.to_owned(),
            id,
        })
    };
    assert_eq!(parsed("1100229901"), default(1_100_229_901));
    assert_eq!(
        parsed(
            "https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_AirPluginUiTestsAggregator/1100229901?buildTab=tests"
        ),
        default(1_100_229_901)
    );
    assert_eq!(
        parsed("HTTPS://BuildServer.Labs.IntelliJ.net/build/1099482304/"),
        default(1_099_482_304)
    );
    assert_eq!(
        parsed("http://localhost:8111/viewLog.html?buildTypeId=x&buildId=42#tab"),
        Ok(BuildRef {
            server: "http://localhost:8111".to_owned(),
            id: 42,
        })
    );
    for loopback in ["http://127.0.0.1/build/3", "http://[::1]:8111/build/3"] {
        assert_eq!(parsed(loopback).map(|build| build.id), Ok(3), "{loopback}");
    }
    for refused in [
        "",
        "build-1",
        "https:///build/1",
        "https://host/buildConfiguration/x",
        "https://user@host/build/1",
    ] {
        let message = parsed(refused).expect_err(refused);
        assert!(message.starts_with(&format!("--teamcity {refused}: not a build id")), "{message}");
    }
    assert_eq!(
        parsed("http://tc.example/build/1"),
        Err("--teamcity http://tc.example/build/1: plain http is for a loopback host only; use https".to_owned())
    );
    assert_eq!(
        parsed("ftp://host/build/1"),
        Err("--teamcity ftp://host/build/1: not an http or https URL".to_owned())
    );

    assert_eq!(
        parse_server("https://BuildServer.labs.intellij.net/").map_err(|refused| refused.message),
        Ok(DEFAULT_TEAMCITY_URL.to_owned())
    );
    for refused in ["buildserver", "https://host/path", "http://tc.example", "https://"] {
        assert!(parse_server(refused).is_err(), "{refused}");
    }
}

/// The token goes only to the server of `--teamcity-url`: another server, and a missing token, take the CLI.
#[test]
fn the_token_goes_only_to_the_server_it_is_for() {
    let with_token = |name: &str| (name == "TEAMCITY_TOKEN").then(|| "secret".to_owned());
    assert_eq!(client(SERVER, SERVER, &with_token).program(), "curl");
    assert_eq!(client("https://other.example", SERVER, &with_token).program(), "teamcity api");
    assert_eq!(client(SERVER, SERVER, &|_| None).program(), "teamcity api");
    let cli = TeamCityCli {
        server: "https://other.example".to_owned(),
    }
    .command("/app/rest/server");
    let removed = cli.get_envs().any(|(name, value)| name == "TEAMCITY_TOKEN" && value.is_none());
    assert!(removed, "the CLI of another server does not inherit the token");
}

/// The configuration that `curl` reads on its standard input is one header line. A quote and a backslash are
/// escaped, and a line break is dropped.
#[test]
fn the_curl_configuration_carries_the_token_as_one_header() {
    assert_eq!(curl_config("abc"), "header = \"Authorization: Bearer abc\"\n");
    assert_eq!(curl_config("a\"b\\c\nd"), "header = \"Authorization: Bearer a\\\"b\\\\cd\"\n");
}

/// The client of a build step gives `curl` the build's credentials as one `user` line on its standard input. The line
/// is escaped as the token is, and each request has a time limit.
#[cfg(unix)]
#[test]
fn the_agent_client_gives_curl_the_build_credentials_on_its_standard_input() {
    use std::os::unix::fs::PermissionsExt;
    let dir = tempfile::tempdir().unwrap();
    let fake = dir.path().join("curl");
    fs::write(&fake, "#!/bin/sh\nprintf 'args:'; printf ' %s' \"$@\"; printf '\\nstdin:'; cat\n").unwrap();
    fs::set_permissions(&fake, fs::Permissions::from_mode(0o755)).unwrap();
    let curl = agent_curl(fake, "https://proxy.example/", "TeamCityBuildId=1", "p\"w");
    let answer = String::from_utf8(curl.get("/app/rest/builds/id:1?fields=startDate").unwrap()).unwrap();
    let (args, stdin) = answer.split_once("\nstdin:").unwrap();
    assert!(!args.contains("p\"w") && !args.contains("TeamCityBuildId"), "{args}");
    assert!(args.contains(" --max-time 60 "), "{args}");
    assert!(
        args.ends_with(" https://proxy.example/app/rest/builds/id:1?fields=startDate"),
        "{args}"
    );
    assert_eq!(stdin, "user = \"TeamCityBuildId=1:p\\\"w\"\n");
}

/// `curl` gets the URL with `--globoff`, and the token on its standard input, never on its command line.
#[cfg(unix)]
#[test]
fn curl_gets_the_token_on_its_standard_input() {
    use std::os::unix::fs::PermissionsExt;
    let dir = tempfile::tempdir().unwrap();
    let fake = dir.path().join("curl");
    fs::write(&fake, "#!/bin/sh\nprintf 'args:'; printf ' %s' \"$@\"; printf '\\nstdin:'; cat\n").unwrap();
    fs::set_permissions(&fake, fs::Permissions::from_mode(0o755)).unwrap();
    let curl = Curl {
        program: fake,
        server: SERVER.to_owned(),
        config: curl_config("secret"),
        max_time: None,
    };
    let answer = String::from_utf8(curl.get("/app/rest/builds/id:1?fields=a(b)").unwrap()).unwrap();
    let (args, stdin) = answer.split_once("\nstdin:").unwrap();
    assert!(!args.contains("secret"), "{args}");
    assert!(args.contains(" --globoff "), "{args}");
    assert!(
        args.ends_with(" https://buildserver.labs.intellij.net/app/rest/builds/id:1?fields=a(b)"),
        "{args}"
    );
    assert_eq!(stdin, curl_config("secret"));
}

/// The aggregator stands for the four shards that ran under its two composite builds, in the order of the snapshot
/// dependencies. A shard in the listing of its parent is not read again.
#[test]
fn a_composite_build_stands_for_the_builds_that_ran_under_it() {
    let fake = FakeRest::recorded_chain();
    let leaves = leaf_builds(&fake, 1_100_229_901).unwrap();
    let named: Vec<(u64, &str)> = leaves.iter().map(|build| (build.id, build.build_type.name.as_str())).collect();
    assert_eq!(
        named,
        [
            (1_099_482_304, "Air Plugin UI Tests / 1"),
            (1_099_482_358, "Air Plugin UI Tests / 2"),
            (1_099_482_381, "Air Plugin GUI Chat Tests / 1"),
            (1_099_482_393, "Air Plugin GUI Chat Tests / 2"),
        ]
    );
    assert_eq!(fake.gets.borrow().len(), 3, "{:?}", fake.gets.borrow());
    assert_eq!(
        leaves[0].origin(),
        Origin {
            name: "Air Plugin UI Tests / 1".to_owned(),
            id: "1099482304".to_owned(),
            url: Some(
                "https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_AirPluginUiTests_1/1099482304".to_owned()
            ),
        }
    );
    assert_eq!(
        leaves[0].describe(),
        "Air Plugin UI Tests / 1 #16335 (1099482304, finished success)"
    );

    let mut alone = FakeRest::default();
    alone.answer(
        &build_path(7),
        r#"{"id":7,"number":"1","composite":false,"buildType":{"id":"Shard","name":""}}"#,
    );
    let leaves = leaf_builds(&alone, 7).unwrap();
    assert_eq!(leaves.iter().map(|build| build.id).collect::<Vec<_>>(), [7]);
    assert_eq!(leaves[0].describe(), "Shard #1 (7)");
}

/// A `bazel.logs.zip` is downloaded once: a copy of the listed size is used as it is. A download of another size is
/// refused and leaves no copy, and a build without the artifact has no root.
#[test]
fn the_logs_of_a_build_are_downloaded_once() {
    let cache = tempfile::tempdir().unwrap();
    let build: Build = serde_json::from_str(r#"{"id":5,"number":"9","buildType":{"id":"T","name":"Shard / 1"}}"#).unwrap();
    let mut fake = FakeRest {
        content: b"zip bytes".to_vec(),
        ..FakeRest::default()
    };
    fake.answer(
        &artifacts_path(5),
        r#"{"file":[{"name":"Xvfb.out","size":3},{"name":"bazel.logs.zip","size":9}]}"#,
    );
    let lines = RefCell::new(Vec::new());
    let log = |line: String| lines.borrow_mut().push(line);
    let target = build_dir(cache.path(), SERVER, 5).join(LOGS_ARTIFACT);
    let hash = &hex::encode(Sha256::digest(SERVER.as_bytes()))[..8];
    assert_eq!(target.parent().unwrap().file_name().unwrap(), format!("tc-{hash}-5").as_str());
    assert_ne!(
        build_dir(cache.path(), "https://other.example", 5),
        build_dir(cache.path(), SERVER, 5)
    );

    assert_eq!(
        fetch_logs(&fake, &build, SERVER, cache.path(), &log).unwrap(),
        Fetched::Downloaded(target.clone())
    );
    assert_eq!(fs::read(&target).unwrap(), b"zip bytes");
    assert_eq!(
        fetch_logs(&fake, &build, SERVER, cache.path(), &log).unwrap(),
        Fetched::Cached(target.clone())
    );
    assert_eq!(
        *fake.downloads.borrow(),
        ["/app/rest/builds/id:5/artifacts/files/bazel.logs.zip"],
        "the second call reads the copy"
    );
    assert_eq!(*lines.borrow(), ["    Shard / 1 #9 (5): downloading bazel.logs.zip, 1 MB"]);
    assert_eq!(
        fs::read_dir(target.parent().unwrap()).unwrap().count(),
        1,
        "no temporary file is left"
    );

    let recorded_shard: Build = serde_json::from_str(r#"{"id":1099482304,"number":"16335"}"#).unwrap();
    fake.answer(&artifacts_path(1_099_482_304), recorded("artifacts-1099482304.json"));
    let refused = fetch_logs(&fake, &recorded_shard, SERVER, cache.path(), &log).unwrap_err();
    assert_eq!(
        refused.message,
        "build 1099482304: the download of bazel.logs.zip has 9 bytes, the listing says 19091215"
    );
    let shard_dir = build_dir(cache.path(), SERVER, 1_099_482_304);
    assert_eq!(fs::read_dir(&shard_dir).unwrap().count(), 0, "a refused download leaves no copy");

    fake.answer(&artifacts_path(6), r#"{"file":[{"name":"Xvfb.out","size":3}]}"#);
    let bare: Build = serde_json::from_str(r#"{"id":6}"#).unwrap();
    assert_eq!(fetch_logs(&fake, &bare, SERVER, cache.path(), &log).unwrap(), Fetched::Missing);
}

/// Each build that ran and published its logs is one root, with the build as its origin. A build whose logs do not
/// download is a root with its error, and the other builds still serve.
#[test]
fn each_build_with_logs_is_a_root_with_its_origin() {
    let cache = tempfile::tempdir().unwrap();
    let mut fake = FakeRest {
        content: b"zip".to_vec(),
        ..FakeRest::recorded_chain()
    };
    fake.answer(&artifacts_path(1_099_482_304), r#"{"file":[{"name":"bazel.logs.zip","size":3}]}"#);
    fake.answer(&artifacts_path(1_099_482_358), r#"{"file":[]}"#);
    fake.answer(&artifacts_path(1_099_482_381), r#"{"file":[{"name":"bazel.logs.zip","size":3}]}"#);
    let fake = std::rc::Rc::new(fake);
    let servers = RefCell::new(Vec::new());
    let client = |server: &str| -> Box<dyn Rest> {
        servers.borrow_mut().push(server.to_owned());
        Box::new(Shared(std::rc::Rc::clone(&fake)))
    };
    let lines = RefCell::new(Vec::new());
    let roots = teamcity_roots(
        &["https://buildserver.labs.intellij.net/build/1100229901".to_owned()],
        "https://unused.example",
        cache.path(),
        &client,
        &|line| lines.borrow_mut().push(line),
    )
    .unwrap();
    assert_eq!(*servers.borrow(), [SERVER]);
    let named: Vec<(String, PathBuf, Option<String>)> = roots
        .iter()
        .map(|root| (root.origin.as_ref().unwrap().name.clone(), root.path.clone(), root.error.clone()))
        .collect();
    let logs = |id| build_dir(cache.path(), SERVER, id).join(LOGS_ARTIFACT);
    assert_eq!(
        named,
        [
            ("Air Plugin UI Tests / 1".to_owned(), logs(1_099_482_304), None),
            ("Air Plugin GUI Chat Tests / 1".to_owned(), logs(1_099_482_381), None),
            (
                "Air Plugin GUI Chat Tests / 2".to_owned(),
                logs(1_099_482_393),
                Some(format!("no answer for {}", artifacts_path(1_099_482_393)))
            ),
        ]
    );
    assert!(roots.iter().all(|root| root.kind == RootKind::TeamCity));
    let lines = lines.borrow();
    assert_eq!(lines[0], "  teamcity 1100229901 (fake): 4 build(s) ran");
    assert!(
        lines.contains(&"    Air Plugin UI Tests / 2 #15232 (1099482358, finished success): no bazel.logs.zip".to_owned()),
        "{lines:?}"
    );

    let unresolved = teamcity_roots(&["1".to_owned()], SERVER, cache.path(), &client, &|_| {}).unwrap();
    assert_eq!(unresolved.len(), 1);
    assert_eq!(
        unresolved[0].error.as_deref(),
        Some(format!("no answer for {}", build_path(1)).as_str())
    );
    let refused = teamcity_roots(&["1".to_owned(), "not-a-build".to_owned()], SERVER, cache.path(), &client, &|_| {});
    assert_eq!(refused.unwrap_err().exit, Exit::Usage, "a value that is no build refuses the serve");
}

/// One fake behind several clients.
struct Shared(std::rc::Rc<FakeRest>);

impl Rest for Shared {
    fn get(&self, path: &str) -> Result<Vec<u8>, Refusal> {
        self.0.get(path)
    }

    fn download(&self, path: &str, to: &Path) -> Result<(), Refusal> {
        self.0.download(path, to)
    }

    fn program(&self) -> &'static str {
        self.0.program()
    }
}

/// A blank `TEAMCITY_TOKEN` is no token. A missing program is a refusal that says what to install.
#[test]
fn a_blank_token_is_no_token_and_a_missing_program_says_what_to_install() {
    let missing = run_program(Command::new("air-trace-no-such-program"), None, true, "probe").unwrap_err();
    assert_eq!(missing.code, TEAMCITY_FAILED);
    assert!(
        missing.message.starts_with("cannot run air-trace-no-such-program: "),
        "{}",
        missing.message
    );
    let mut cli = Command::new("teamcity");
    cli.env("PATH", "");
    let missing = run_program(cli, None, true, "probe").unwrap_err();
    assert!(
        missing.message.ends_with("or set TEAMCITY_TOKEN for the server of --teamcity-url"),
        "{}",
        missing.message
    );

    let variable = |value: &'static str| move |name: &str| (name == "TEAMCITY_TOKEN").then(|| value.to_owned());
    assert_eq!(token(&variable(" secret\n")), Some("secret".to_owned()));
    assert_eq!(token(&variable("  ")), None, "a blank token picks the CLI");
    assert_eq!(token(&|_| None), None);
}
