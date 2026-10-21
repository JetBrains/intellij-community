use std::collections::BTreeMap;
use std::path::Path;

use pretty_assertions::assert_eq;

use super::*;

/// The escapes of `Properties.store`, a comment, a key with an escaped `=`, the three separators and an empty value
/// read. So do a line that goes on, and a character outside the basic plane as a surrogate pair.
#[test]
fn a_properties_file_reads_as_java_writes_it() {
    let text = "#Build properties of Air Plugin UI Tests / 1\n\
                ! another comment\n\
                teamcity.serverUrl=https\\://bs-proxy.example\n\
                teamcity.auth.userId=TeamCityBuildId\\=1100310296\n\
                key\\=with\\ space = value\n\
                colon:value\n\
                spaced   value with  spaces \n\
                empty=\n\
                long=first \\\n    second\n\
                name=Caf\\u00E9 \\uD83D\\uDE00 \\u12 \\t|\n\
                \n";
    let expected: BTreeMap<String, String> = [
        ("colon", "value"),
        ("empty", ""),
        ("key=with space", "value"),
        ("long", "first second"),
        ("name", "Caf\u{e9} \u{1f600} u12 \t|"),
        ("spaced", "value with  spaces "),
        ("teamcity.auth.userId", "TeamCityBuildId=1100310296"),
        ("teamcity.serverUrl", "https://bs-proxy.example"),
    ]
    .into_iter()
    .map(|(key, value)| (key.to_owned(), value.to_owned()))
    .collect();
    assert_eq!(parse_properties(text), expected);
}

/// The two files of the agent, as build 1100310296 had them: the build's own properties, which name the file of the
/// configuration parameters.
fn agent_files(dir: &Path, build: &str) -> String {
    let configuration = dir.join("teamcity.config.parameters");
    fs::write(
        &configuration,
        "teamcity.build.id=1100310296\nteamcity.serverUrl=https\\://bs-proxy-eu-north-1.services.buildfarm.intellij.net\n",
    )
    .unwrap();
    let file = dir.join("teamcity.build.parameters");
    let escaped = configuration.to_string_lossy().replace('\\', "\\\\").replace(':', "\\:");
    fs::write(&file, format!("{build}teamcity.configuration.properties.file={escaped}\n")).unwrap();
    file.to_string_lossy().into_owned()
}

const BUILD_PROPERTIES: &str = "teamcity.buildConfName=Air Plugin UI Tests / 1\n\
                                teamcity.buildType.id=ijplatform_master_AirPluginUiTests_1\n";

fn of_step(variables: &[(&str, &str)]) -> Result<Option<Build>, String> {
    let variables: BTreeMap<String, String> = variables
        .iter()
        .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
        .collect();
    Build::of_step(&|name: &str| variables.get(name).cloned())
}

/// The build names its origin and the viewer page of a zip. The server is the root of `BUILD_URL`, and
/// `teamcity.serverUrl` without it.
#[test]
fn the_build_of_a_step_comes_from_the_agent_files() {
    let dir = tempfile::tempdir().unwrap();
    let file = agent_files(dir.path(), BUILD_PROPERTIES);
    let on_team_city = [
        ("TEAMCITY_VERSION", "2026.3 EAP (build 265562)"),
        (BUILD_PROPERTIES_FILE, file.as_str()),
    ];

    let build = of_step(&[
        on_team_city[0],
        on_team_city[1],
        (BUILD_URL, "https://buildserver.labs.intellij.net/build/1100310296"),
    ])
    .unwrap()
    .unwrap();
    assert_eq!(
        build,
        Build {
            server: "https://buildserver.labs.intellij.net".to_owned(),
            build_type_id: "ijplatform_master_AirPluginUiTests_1".to_owned(),
            id: "1100310296".to_owned(),
            name: "Air Plugin UI Tests / 1".to_owned(),
        }
    );
    assert_eq!(
        build.origin(),
        Origin {
            name: "Air Plugin UI Tests / 1".to_owned(),
            id: "1100310296".to_owned(),
            url: Some(
                "https://buildserver.labs.intellij.net/buildConfiguration/ijplatform_master_AirPluginUiTests_1/1100310296".to_owned()
            ),
        }
    );
    let page = "https://buildserver.labs.intellij.net/repository/download/ijplatform_master_AirPluginUiTests_1/1100310296:id/\
                air-traces.zip!/index.html";
    assert_eq!(
        build.viewer_link("ui_lane_test/iter-1/T/s.zip"),
        format!("{page}#/trace?zip=ui_lane_test/iter-1/T/s.zip")
    );
    // As `traceHash` of the viewer writes it: `URLSearchParams`, with each slash kept.
    assert_eq!(
        build.viewer_link("ui_lane_test/shard_1_of_2/iter-1/T/a b+c~d&e=é.zip"),
        format!("{page}#/trace?zip=ui_lane_test/shard_1_of_2/iter-1/T/a+b%2Bc%7Ed%26e%3D%C3%A9.zip")
    );

    let other_build_url = of_step(&[
        on_team_city[0],
        on_team_city[1],
        (BUILD_URL, "https://buildserver.labs.intellij.net/build/1"),
    ]);
    assert_eq!(
        other_build_url.unwrap().unwrap().server,
        "https://bs-proxy-eu-north-1.services.buildfarm.intellij.net"
    );
    assert_eq!(
        of_step(&on_team_city).unwrap().unwrap().server,
        "https://bs-proxy-eu-north-1.services.buildfarm.intellij.net"
    );
}

/// Off TeamCity there is no build. On TeamCity, a file that is not set or does not read, and a property that is
/// missing, say why.
#[test]
fn a_build_that_the_files_do_not_name_says_why() {
    assert_eq!(of_step(&[(BUILD_PROPERTIES_FILE, "/nowhere")]), Ok(None));
    assert_eq!(of_step(&[("TEAMCITY_VERSION", " ")]), Ok(None));

    let version = ("TEAMCITY_VERSION", "2026.3");
    assert_eq!(of_step(&[version]), Err(format!("{BUILD_PROPERTIES_FILE} is not set")));

    let dir = tempfile::tempdir().unwrap();
    let missing = dir.path().join("missing.properties");
    let refused = of_step(&[version, (BUILD_PROPERTIES_FILE, &*missing.to_string_lossy())]).unwrap_err();
    assert!(refused.starts_with(&format!("cannot read {}: ", missing.display())), "{refused}");

    let file = agent_files(dir.path(), "teamcity.buildConfName=Air Plugin UI Tests / 1\n");
    assert_eq!(
        of_step(&[version, (BUILD_PROPERTIES_FILE, file.as_str())]),
        Err(format!("the build properties of {file} name no teamcity.buildType.id"))
    );
}

/// `BUILD_URL` names the root of the server also with a slash at its end.
#[test]
fn the_build_page_may_end_in_a_slash() {
    let dir = tempfile::tempdir().unwrap();
    let file = agent_files(dir.path(), BUILD_PROPERTIES);
    let variables = [
        ("TEAMCITY_VERSION", "2026.3"),
        (BUILD_PROPERTIES_FILE, file.as_str()),
        (BUILD_URL, "https://buildserver.labs.intellij.net/build/1100310296/"),
    ];
    assert_eq!(
        of_step(&variables).unwrap().unwrap().server,
        "https://buildserver.labs.intellij.net"
    );
}

/// A server that is not the root of `BUILD_URL` says so, with the server that the links use.
#[test]
fn a_server_that_is_not_the_root_of_the_build_page_says_so() {
    let dir = tempfile::tempdir().unwrap();
    let file = agent_files(dir.path(), BUILD_PROPERTIES);
    let on_team_city = [("TEAMCITY_VERSION", "2026.3"), (BUILD_PROPERTIES_FILE, file.as_str())];
    let build = of_step(&on_team_city).unwrap().unwrap();
    let variables: BTreeMap<String, String> = BTreeMap::new();
    assert_eq!(
        build.server_fallback(&|name: &str| variables.get(name).cloned()),
        Some(
            "BUILD_URL does not name the page of build 1100310296, so the links and the origin use teamcity.serverUrl \
             https://bs-proxy-eu-north-1.services.buildfarm.intellij.net"
                .to_owned()
        )
    );
    let page = |name: &str| (name == BUILD_URL).then(|| "https://buildserver.labs.intellij.net/build/1100310296".to_owned());
    assert_eq!(build.server_fallback(&page), None);
}

/// A server that answers each path from a table.
struct FakeRest(BTreeMap<String, Vec<u8>>);

impl Rest for FakeRest {
    fn get(&self, path: &str) -> Result<Vec<u8>, crate::Refusal> {
        self.0
            .get(path)
            .cloned()
            .ok_or_else(|| crate::Refusal::new("teamcity_failed", crate::Exit::Broken, format!("no answer for {path}")))
    }

    fn download(&self, path: &str, _to: &Path) -> Result<(), crate::Refusal> {
        Err(crate::Refusal::new(
            "teamcity_failed",
            crate::Exit::Broken,
            format!("no download of {path}"),
        ))
    }

    fn program(&self) -> &'static str {
        "fake"
    }
}

/// The start of a build is the `startDate` of its REST answer, as the server of build 1100310296 writes it. It moves by
/// the skew of the clocks, which the `currentTime` of the server says, and it counts from two minutes earlier. An
/// answer without a time, and no answer, say why.
#[test]
fn the_start_of_a_build_is_its_start_date_in_the_clock_of_this_machine() {
    let start_path = "/app/rest/builds/id:1100310296?fields=startDate";
    let server_path = "/app/rest/server?fields=currentTime";
    let answer = |start: &str, server: &str| {
        FakeRest(BTreeMap::from([
            (start_path.to_owned(), start.as_bytes().to_vec()),
            (server_path.to_owned(), server.as_bytes().to_vec()),
        ]))
    };
    let time = |text: &str| SystemTime::from(text.parse::<jiff::Timestamp>().unwrap());
    // This machine is 30 s ahead of the server.
    let here = || time("2026-10-21T10:00:30Z");
    let rest = answer(
        "{\n  \"startDate\": \"20261021T095000+0000\"\n}",
        "{\"currentTime\": \"20261021T100000+0000\"}",
    );
    let start = start_from(&rest, "1100310296", &here).unwrap();
    assert_eq!(start.since, time("2026-10-21T09:48:30Z"));
    assert_eq!(
        start.note,
        "build 1100310296 started at 2026-10-21T09:50:00Z by the clock of its server, which is 30 s behind this one; \
         a test.xml from 2026-10-21T09:48:30Z on counts"
    );

    let refused = start_from(&answer("{}", "{}"), "1100310296", &here).unwrap_err();
    assert_eq!(refused, format!("the REST answer of {start_path} names no startDate"));
    let refused = start_from(&answer("{\"startDate\": \"yesterday\"}", "{}"), "1100310296", &here).unwrap_err();
    assert!(
        refused.starts_with(&format!("the startDate \"yesterday\" of {start_path} does not read: ")),
        "{refused}"
    );
    let refused = start_from(&answer("{\"startDate\": \"20261021T095000+0000\"}", "{}"), "1100310296", &here).unwrap_err();
    assert_eq!(refused, format!("the REST answer of {server_path} names no currentTime"));
    assert_eq!(
        start_from(&answer("{}", "{}"), "7", &here).unwrap_err(),
        "no answer for /app/rest/builds/id:7?fields=startDate"
    );
}
