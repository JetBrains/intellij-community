//! Every credential here is a fake value of a fake file of the fake runtime.

use pretty_assertions::assert_eq;

use super::*;
use bt_core::fake::{FakeRuntime, HOME_DIR};
use bt_core::runtime::SpawnResult;

const BAZELRC_TEXT: &str = "import %workspace%/build/remote_cache.bazelrc\ncommon --experimental_remote_scrubbing_config=x.cfg\n";
const CACHE_RC_TEXT: &str = "# Remote Caching\ncommon --remote_cache=https://cache.example.test\ncommon:ci --remote_upload_local_results=true\nbuild --bes_backend=grpcs://events.example.test:443\n";

const JAR_REPOSITORIES_TEXT: &str = r#"<project version="4">
  <component name="RemoteRepositoriesConfiguration">
    <remote-repository>
      <option name="id" value="central-proxy" />
      <option name="url" value="https://cache-redirector.jetbrains.com/repo1.maven.org/maven2" />
    </remote-repository>
    <remote-repository>
      <option name="id" value="intellij-dependencies" />
      <option name="url" value="https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies" />
    </remote-repository>
  </component>
</project>"#;

fn ok(output: &str) -> SpawnResult {
    SpawnResult {
        exit_code: 0,
        output: output.to_owned(),
    }
}

#[test]
fn the_services_come_from_the_bazelrc_and_its_imports() {
    let fake = FakeRuntime::new([(".bazelrc", BAZELRC_TEXT), ("build/remote_cache.bazelrc", CACHE_RC_TEXT)]);
    let found = services(&fake);
    assert_eq!(
        found,
        [
            Service {
                option: "--remote_cache".to_owned(),
                host: "cache.example.test".to_owned(),
            },
            Service {
                option: "--bes_backend".to_owned(),
                host: "events.example.test".to_owned(),
            },
        ]
    );
    assert_eq!(service_hosts(&found), ["cache.example.test", "events.example.test"]);
    assert_eq!(services(&FakeRuntime::new([])), []);
}

#[test]
fn the_space_repositories_are_the_ones_on_the_package_host() {
    let fake = FakeRuntime::new([(".idea/jarRepositories.xml", JAR_REPOSITORIES_TEXT)]);
    assert_eq!(space_repository_ids(&fake), ["intellij-dependencies"]);
}

#[test]
fn a_netrc_entry_is_replaced_in_place_and_the_other_entries_stay() {
    let host = "machine other.test login o password other-secret\n\nmachine cache.test\n  login old\n  password old-secret\n\nmachine last.test login l password last-secret\n";
    let local = "machine cache.test login new password new-secret\n";
    let (merged, outcome) = merge(&NetrcFormat, host, local, "cache.test");
    assert_eq!(outcome, Merged::Written);
    assert_eq!(
        merged,
        "machine other.test login o password other-secret\n\nmachine cache.test login new password new-secret\n\nmachine last.test login l password last-secret\n"
    );
    assert_eq!(merge(&NetrcFormat, &merged, local, "cache.test").1, Merged::Current);
}

#[test]
fn a_netrc_entry_is_appended_when_the_host_has_none() {
    let local = "machine a.test login a password a-secret\nmachine cache.test login c password c-secret\n";
    let (merged, outcome) = merge(&NetrcFormat, "machine other.test login o password p", local, "cache.test");
    assert_eq!(outcome, Merged::Written);
    assert_eq!(
        merged,
        "machine other.test login o password p\nmachine cache.test login c password c-secret\n"
    );
    assert_eq!(merge(&NetrcFormat, "", "machine x.test", "cache.test").1, Merged::NoLocal);
}

#[test]
fn a_maven_server_is_replaced_appended_or_given_a_new_file() {
    let local = "<settings>\n  <servers>\n    <server>\n      <id>repo</id>\n      <password>new-secret</password>\n    </server>\n  </servers>\n</settings>\n";
    let entry = "<server>\n      <id>repo</id>\n      <password>new-secret</password>\n    </server>";

    let host = "<settings>\n  <servers>\n    <server><id>other</id></server>\n    <server><id>repo</id><password>old-secret</password></server>\n  </servers>\n</settings>\n";
    let (replaced, outcome) = merge(&SettingsFormat, host, local, "repo");
    assert_eq!(outcome, Merged::Written);
    assert_eq!(
        replaced,
        format!("<settings>\n  <servers>\n    <server><id>other</id></server>\n    {entry}\n  </servers>\n</settings>\n")
    );

    let (appended, _) = merge(
        &SettingsFormat,
        "<settings>\n  <servers>\n  </servers>\n</settings>\n",
        local,
        "repo",
    );
    assert_eq!(
        appended,
        format!("<settings>\n  <servers>\n    {entry}\n  </servers>\n</settings>\n")
    );

    let (created, _) = merge(&SettingsFormat, "", local, "repo");
    assert_eq!(
        created,
        format!("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<settings>\n  <servers>\n    {entry}\n  </servers>\n</settings>\n")
    );

    assert_eq!(merge(&SettingsFormat, "<broken/>", local, "repo").1, Merged::NoPlace);
}

/// The merged text travels on the stdin of ssh, never on a command line, and no line of the answer holds a secret.
#[test]
fn a_copy_writes_the_merged_file_through_the_stdin_and_says_no_secret() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(&format!("{HOME_DIR}/.netrc"), "machine cache.test login me password local-secret\n");
    fake.state()
        .spawn_rules
        .push(("cat .netrc".to_owned(), ok("machine other.test login o password other-secret\n")));
    let names = ["cache.test".to_owned(), "absent.test".to_owned()];
    let lines = copy_entries(&fake, "studio", "7", NETRC, &names, &NetrcFormat);
    assert_eq!(lines, ["synced netrc entry for cache.test", "no local netrc entry for absent.test"]);

    let state = fake.state();
    assert_eq!(
        state.inputs,
        ["machine other.test login o password other-secret\nmachine cache.test login me password local-secret\n"]
    );
    let write = state.spawned.last().expect("the write").clone();
    let expected = crate::ssh::ssh(
        "studio",
        "umask 077 && cat > .netrc.bt-remote-7 && chmod 600 .netrc.bt-remote-7 && mv -f .netrc.bt-remote-7 .netrc",
    );
    assert_eq!(
        write,
        expected.iter().map(|word| word.to_string_lossy().into_owned()).collect::<Vec<_>>()
    );
    let said = format!("{lines:?} {:?} {:?} {:?}", state.stdout, state.stderr, state.spawned);
    assert!(!said.contains("secret"), "{said}");
}

/// An equal file is not written again.
#[test]
fn a_current_entry_writes_nothing() {
    let fake = FakeRuntime::new([]);
    fake.put_absolute(&format!("{HOME_DIR}/.netrc"), "machine cache.test login me password same\n");
    fake.state()
        .spawn_rules
        .push(("cat .netrc".to_owned(), ok("machine cache.test login me password same\n")));
    let lines = copy_entries(&fake, "studio", "7", NETRC, &["cache.test".to_owned()], &NetrcFormat);
    assert_eq!(lines, ["current netrc entry for cache.test"]);
    assert!(fake.state().inputs.is_empty());
}

#[test]
fn the_write_creates_the_directory_of_a_nested_file() {
    assert_eq!(
        atomic_write_command(SETTINGS_XML, "7"),
        "umask 077 && mkdir -p .m2 && cat > .m2/settings.xml.bt-remote-7 && chmod 600 .m2/settings.xml.bt-remote-7 && mv -f .m2/settings.xml.bt-remote-7 .m2/settings.xml"
    );
}
