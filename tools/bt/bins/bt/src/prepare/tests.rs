//! Every credential here is a fake value of a fake file of the fake runtime. The fake answers each command by the
//! first rule whose text the command holds.

use pretty_assertions::assert_eq;

use super::*;
use bt_core::fake::{FakeRuntime, HOME_DIR, refusal};

use crate::execute::execute_with;
use crate::remote::fake::{FakeLink, hello, host_refused};
use crate::remote::{HostRefusal, code};
use crate::ssh::SSH_OPTIONS;

const LOCAL_MASTER: &str = "1111111111111111111111111111111111111111";
const HOST_MASTER: &str = "2222222222222222222222222222222222222222";

const BAZELRC_TEXT: &str = "import %workspace%/build/remote_cache.bazelrc\n";
const CACHE_RC_TEXT: &str = "common --remote_cache=https://cache.example.test\n";
const JAR_REPOSITORIES_TEXT: &str = r#"<remote-repository>
  <option name="id" value="intellij-dependencies" />
  <option name="url" value="https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies" />
</remote-repository>"#;
const LOCAL_NETRC: &str = "machine cache.example.test login me password fake-local-secret\n";
const LOCAL_SETTINGS: &str = "<settings>\n  <servers>\n    <server>\n      <id>intellij-dependencies</id>\n      <password>fake-token-secret</password>\n    </server>\n  </servers>\n</settings>\n";
/// The marker name that the fake hash gives the fake token.
const MARKER: &str = "c60dbf6914b9127db260";

fn answered(exit_code: i32, output: &str) -> SpawnResult {
    SpawnResult {
        exit_code,
        output: output.to_owned(),
    }
}

/// A checkout and a host on which every step is ready.
fn ready() -> FakeRuntime {
    let fake = FakeRuntime::new([
        (".bazelrc", BAZELRC_TEXT),
        ("build/remote_cache.bazelrc", CACHE_RC_TEXT),
        (".idea/jarRepositories.xml", JAR_REPOSITORIES_TEXT),
    ]);
    fake.put_absolute(&format!("{HOME_DIR}/.m2/settings.xml"), LOCAL_SETTINGS);
    fake.put_absolute(&format!("{HOME_DIR}/.netrc"), LOCAL_NETRC);
    let rules = [
        ("LogLevel=ERROR studio true", answered(0, "")),
        ("if [ -d", answered(0, &format!("{LOCAL_MASTER}\n"))),
        ("config --type=bool --get core.fsmonitor", answered(0, "true\n")),
        (
            "git rev-parse --verify -q refs/heads/master",
            answered(0, &format!("{LOCAL_MASTER}\n")),
        ),
        ("case \":$PATH:\"", answered(0, "on\n")),
        (
            "command -v git",
            answered(0, "git yes\nXcode yes\nrustup yes\ndocker no\ntmux no\n"),
        ),
        ("awk", answered(0, "cache.example.test\n")),
        ("sed -n", answered(0, "intellij-dependencies\n")),
        ("ls -1 Library", answered(0, &format!("{MARKER}\n"))),
        ("shasum -a 256", answered(0, &format!("{MARKER}0123456789abcdef  -\n"))),
        // 2100-01-01: a token that has not expired.
        ("stat -f %m", answered(0, "4102444800\n")),
        ("curl", answered(0, "404")),
        ("hostname", answered(0, "dev-mac\n")),
    ];
    fake.state()
        .spawn_rules
        .extend(rules.into_iter().map(|(text, result)| (text.to_owned(), result)));
    fake
}

/// Puts a rule before the rules of [`ready`].
fn answer_first(fake: &FakeRuntime, text: &str, result: SpawnResult) {
    fake.state().spawn_rules.insert(0, (text.to_owned(), result));
}

/// A link to an agent that answers, and that `bd-host setup` installs.
fn ready_link() -> FakeLink {
    let link = FakeLink::default();
    link.answer_hello(Ok(hello()));
    link.answer_setup(Ok("copied bd-host to studio\nlaunchd restarted\n".to_owned()));
    link
}

fn prepare(argv: &[&str], fake: &FakeRuntime) -> Outcome {
    prepare_with(argv, fake, &ready_link())
}

fn prepare_with(argv: &[&str], fake: &FakeRuntime, link: &FakeLink) -> Outcome {
    let mut full = vec!["--host", "studio", "--prepare"];
    full.extend(argv);
    execute_with(full, fake, link).unwrap_or_else(|failure| panic!("{argv:?} refused: {failure:?}"))
}

fn spawned_text(fake: &FakeRuntime) -> Vec<String> {
    fake.spawned().iter().map(|command| command.join(" ")).collect()
}

#[track_caller]
fn assert_spawned(fake: &FakeRuntime, text: &str) {
    let spawned = spawned_text(fake);
    assert!(spawned.iter().any(|command| command.contains(text)), "no {text:?} in {spawned:#?}");
}

#[test]
fn a_ready_host_reports_ok_for_each_step_and_changes_nothing() {
    let fake = ready();
    let outcome = prepare(&["--dry-run"], &fake);
    assert_eq!(
        outcome.text,
        [
            "ok      ssh: studio answers in batch mode",
            "ok      repository: master of ~/projects/idea-1 is at 111111111111",
            "ok      repository: core.fsmonitor is on in ~/projects/idea-1",
            "ok      PATH: ~/.local/bin is on the PATH of the login shell",
            "ok      tool: git is there",
            "ok      tool: Xcode is there",
            "ok      tool: rustup is there",
            "absent  tool: docker is not there; only some lanes need it",
            "absent  tool: tmux is not there; only some lanes need it",
            "ok      agent: bd-host 0.37.0 answers on 192.168.1.24:7742 (macos aarch64)",
            "ok      netrc: ~/.netrc on studio has an entry for cache.example.test: true",
            "ok      Maven: ~/.m2/settings.xml on studio has all 1 Space servers",
            "ok      token markers: studio has the expiry marker of the token of intellij-dependencies",
            "ok      remote cache: cache.example.test answers 404 on studio",
        ]
        .join("\n")
    );
    assert_eq!(outcome.exit_code, exit::GREEN);
    assert_eq!(
        fake.state().inputs,
        ["fake-token-secret"],
        "only the hash read the token, on its stdin"
    );
}

#[test]
fn an_unreachable_host_stops_with_the_ssh_hint() {
    let fake = ready();
    answer_first(
        &fake,
        "LogLevel=ERROR studio true",
        answered(255, "ssh: connect to host studio port 22: No route to host\n"),
    );
    let outcome = prepare(&[], &fake);
    assert_eq!(outcome.exit_code, exit::INFRA);
    assert!(
        outcome
            .text
            .starts_with("missing ssh: studio does not answer in batch mode (ssh: connect"),
        "{}",
        outcome.text
    );
    assert!(outcome.text.contains("AddressFamily inet"), "{}", outcome.text);
    assert!(outcome.text.contains("IdentitiesOnly yes"), "{}", outcome.text);
    assert_eq!(fake.spawned().len(), 1);
}

#[test]
fn a_missing_repository_is_created_and_given_the_master_history() {
    let fake = ready();
    answer_first(&fake, "if [ -d", answered(0, "absent\n"));
    answer_first(&fake, "count-objects", answered(0, "count: 1\nsize-pack: 2097152\n"));
    let outcome = prepare(&[], &fake);
    assert!(
        outcome
            .text
            .contains("done    repository: created ~/projects/idea-1 on studio at 111111111111"),
        "{}",
        outcome.text
    );
    assert_spawned(
        &fake,
        "git init -q ~/'projects/idea-1' && git -C ~/'projects/idea-1' config feature.manyFiles true",
    );
    assert_spawned(
        &fake,
        "config core.untrackedCache true && git -C ~/'projects/idea-1' config core.fsmonitor true",
    );
    assert!(
        !spawned_text(&fake).iter().any(|command| command.contains("--get core.fsmonitor")),
        "a new repository is not read again"
    );
    assert_spawned(
        &fake,
        &format!("push --progress studio:projects/idea-1 {LOCAL_MASTER}:refs/heads/sync"),
    );
    assert_spawned(&fake, "git -C ~/'projects/idea-1' checkout -q -B master sync");
    let warned = fake.state().stderr.join("\n");
    assert!(warned.contains("about 2048 MiB"), "{warned}");
}

#[test]
fn an_existing_repository_without_fsmonitor_gets_it_and_a_dry_run_only_says_so() {
    let fake = ready();
    answer_first(&fake, "--get core.fsmonitor", answered(0, "false\n"));
    let outcome = prepare(&[], &fake);
    assert!(
        outcome
            .text
            .contains("done    repository: turned on core.fsmonitor in ~/projects/idea-1 on studio (it was false)"),
        "{}",
        outcome.text
    );
    assert_spawned(&fake, "git -C ~/'projects/idea-1' config core.fsmonitor true");

    let unset = ready();
    answer_first(&unset, "--get core.fsmonitor", answered(1, ""));
    let outcome = prepare(&["--dry-run"], &unset);
    assert!(
        outcome
            .text
            .contains("missing repository: core.fsmonitor is unset in ~/projects/idea-1; --prepare turns it on"),
        "{}",
        outcome.text
    );
    assert_eq!(outcome.exit_code, exit::INFRA);
    assert!(
        !spawned_text(&unset)
            .iter()
            .any(|command| command.contains("config core.fsmonitor true"))
    );
}

#[test]
fn a_clean_master_that_is_an_ancestor_moves_and_another_one_stays() {
    let fake = ready();
    answer_first(&fake, "if [ -d", answered(0, &format!("{HOST_MASTER}\n")));
    answer_first(&fake, "merge-base --is-ancestor", answered(0, ""));
    answer_first(&fake, "status --porcelain", answered(0, ""));
    let outcome = prepare(&[], &fake);
    assert!(
        outcome
            .text
            .contains("done    repository: moved master of studio from 222222222222 to 111111111111"),
        "{}",
        outcome.text
    );
    assert_spawned(&fake, "git -C ~/'projects/idea-1' merge -q --ff-only sync");

    let diverged = ready();
    answer_first(&diverged, "if [ -d", answered(0, &format!("{HOST_MASTER}\n")));
    answer_first(&diverged, "merge-base --is-ancestor", answered(1, ""));
    let outcome = prepare(&[], &diverged);
    assert!(
        outcome
            .text
            .contains("missing repository: master 222222222222 of studio is not an ancestor"),
        "{}",
        outcome.text
    );
    assert!(!spawned_text(&diverged).iter().any(|command| command.contains("refs/heads/sync")));

    let dirty = ready();
    answer_first(&dirty, "if [ -d", answered(0, &format!("{HOST_MASTER}\n")));
    answer_first(&dirty, "merge-base --is-ancestor", answered(0, ""));
    answer_first(&dirty, "status --porcelain", answered(0, " M a.txt\n"));
    let outcome = prepare(&[], &dirty);
    assert!(outcome.text.contains("has local changes, so its master stays"), "{}", outcome.text);
}

#[test]
fn a_path_without_the_local_bin_gets_it_in_zshenv() {
    let fake = ready();
    answer_first(&fake, "case \":$PATH:\"", answered(0, "off\n"));
    let outcome = prepare(&[], &fake);
    assert!(
        outcome.text.contains("done    PATH: added ~/.local/bin to the PATH in ~/.zshenv"),
        "{}",
        outcome.text
    );
    assert_spawned(&fake, r#"printf '%s\n' 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.zshenv"#);
}

/// Without a terminal and without `--yes`, nothing is copied, so an agent cannot start the copy.
#[test]
fn the_credential_copy_refuses_without_a_terminal() {
    let fake = ready();
    answer_first(&fake, "awk", answered(0, ""));
    answer_first(&fake, "curl", answered(0, "401"));
    let outcome = prepare(&[], &fake);
    assert!(
        outcome
            .text
            .contains("missing netrc: ~/.netrc on studio has an entry for cache.example.test: false"),
        "{}",
        outcome.text
    );
    assert!(
        outcome.text.contains(
            "missing credentials: the stdin is not a terminal, so nothing is copied; run `./community/tools/bt.cmd --host studio --prepare` in a terminal"
        ),
        "{}",
        outcome.text
    );
    assert!(
        outcome
            .text
            .contains("missing remote cache: cache.example.test answers 401 on studio"),
        "{}",
        outcome.text
    );
    assert_eq!(outcome.exit_code, exit::INFRA);
    let state = fake.state();
    assert_eq!(state.questions, ["Copy to studio: the ~/.netrc entries of cache.example.test"]);
    assert_eq!(state.inputs, ["fake-token-secret"], "nothing was written; the hash read the token");
}

#[test]
fn a_no_at_the_terminal_copies_nothing() {
    let fake = ready();
    answer_first(&fake, "awk", answered(0, ""));
    fake.state().confirm_answer = Some(false);
    let outcome = prepare(&[], &fake);
    assert!(
        outcome.text.contains("missing credentials: not copied: the answer was no"),
        "{}",
        outcome.text
    );
    assert_eq!(fake.state().inputs, ["fake-token-secret"]);
}

/// `--yes` copies the missing entries and markers. No output and no command line holds a secret: the merged file
/// travels on the stdin.
#[test]
fn yes_copies_the_missing_entries_and_says_no_secret() {
    let fake = ready();
    answer_first(&fake, "awk", answered(0, "other.example.test\n"));
    answer_first(&fake, "ls -1 Library", answered(0, ""));
    answer_first(
        &fake,
        "cat .netrc",
        answered(0, "machine other.example.test login o password fake-other-secret\n"),
    );
    let outcome = prepare(&["--yes"], &fake);
    assert!(
        outcome
            .text
            .contains("done    credentials: synced netrc entry for cache.example.test"),
        "{}",
        outcome.text
    );
    assert!(
        outcome.text.contains("done    credentials: copied 1 token expiry markers"),
        "{}",
        outcome.text
    );
    assert_spawned(
        &fake,
        &format!(
            "scp -pq {} {HOME_DIR}/{TOKEN_MARKERS}/{MARKER} studio:{TOKEN_MARKERS}/",
            SSH_OPTIONS.join(" ")
        ),
    );
    let state = fake.state();
    assert!(state.questions.is_empty(), "--yes asks nothing");
    assert_eq!(
        state.inputs,
        [
            "fake-token-secret",
            "machine other.example.test login o password fake-other-secret\nmachine cache.example.test login me password fake-local-secret\n"
        ]
    );
    let said = format!("{} {:?} {:?} {:?}", outcome.text, state.stdout, state.stderr, state.spawned);
    assert!(!said.contains("secret"), "{said}");
}

/// A host that lacks an entry this machine does not have either gets nothing for it, and the report says so.
#[test]
fn a_missing_local_entry_is_reported_by_name() {
    let fake = ready();
    answer_first(&fake, "awk", answered(0, ""));
    fake.put_absolute(
        &format!("{HOME_DIR}/.netrc"),
        "machine unrelated.test login u password fake-secret\n",
    );
    answer_first(&fake, "cat .netrc", answered(0, ""));
    let outcome = prepare(&["--yes"], &fake);
    assert!(
        outcome
            .text
            .contains("missing credentials: no local netrc entry for cache.example.test"),
        "{}",
        outcome.text
    );
    assert_eq!(fake.state().inputs, ["fake-token-secret"], "no file was written");
}

/// The agent step runs `bd-host setup` with the repository and the name of this client, then asks for a hello. bt
/// copies no binary itself: the host needs no `bd`, and `bd-host setup` copies `bd-host`.
#[test]
fn the_agent_is_installed_through_bd_host_setup() {
    let fake = ready();
    let link = ready_link();
    let outcome = prepare_with(&[], &fake, &link);
    assert!(
        outcome.text.contains(
            "done    agent: copied bd-host to studio; launchd restarted; bd-host 0.37.0 answers on 192.168.1.24:7742 (macos aarch64)"
        ),
        "{}",
        outcome.text
    );
    assert_eq!(outcome.exit_code, exit::GREEN);
    assert_eq!(link.calls(), ["setup studio", "hello studio"]);
    let setups = link.setups();
    assert_eq!(
        (setups[0].alias.as_str(), setups[0].repo.as_str(), setups[0].name.as_str()),
        ("studio", "~/projects/idea-1", "bt on dev-mac")
    );
    let spawned = spawned_text(&fake);
    assert!(
        !spawned
            .iter()
            .any(|command| command.contains(".local/bin/bd") || command.contains("bazel")),
        "{spawned:#?}"
    );

    let link = ready_link();
    let outcome = prepare_with(&["--host-repo", "/w/idea"], &ready(), &link);
    assert_eq!(outcome.exit_code, exit::GREEN, "{}", outcome.text);
    assert_eq!(link.setups()[0].repo, "/w/idea");
}

#[test]
fn a_failed_setup_or_a_silent_agent_is_missing() {
    let link = ready_link();
    link.answer_setup(Err(host_refused(
        code::NOT_INSTALLED,
        "no bd-host on the PATH and none at ~/.local/bin/bd-host; run `bd install` of Branch Deck",
    )));
    let outcome = prepare_with(&[], &ready(), &link);
    assert!(
        outcome
            .text
            .contains("missing agent: no bd-host on the PATH and none at ~/.local/bin/bd-host; run `bd install`"),
        "{}",
        outcome.text
    );
    assert_eq!(outcome.exit_code, exit::INFRA);
    assert_eq!(link.calls(), ["setup studio"]);

    let link = ready_link();
    link.answer_hello(Err(HostRefusal::new("unreachable", 255, "no address of studio answers")));
    let outcome = prepare_with(&[], &ready(), &link);
    assert!(
        outcome.text.contains(
            "missing agent: copied bd-host to studio; launchd restarted, but the agent does not answer: no address of studio answers"
        ),
        "{}",
        outcome.text
    );
}

/// An agent of another build answers, and the report names the upgrade.
#[test]
fn an_agent_of_another_build_names_the_upgrade() {
    let link = ready_link();
    link.answer_hello(Ok(HostHello {
        same_build: false,
        ..hello()
    }));
    let outcome = prepare_with(&["--dry-run"], &ready(), &link);
    assert!(
        outcome.text.contains(
            "ok      agent: bd-host 0.37.0 answers on 192.168.1.24:7742 (macos aarch64), another build than here; `bd-host upgrade studio` replaces it"
        ),
        "{}",
        outcome.text
    );
}

/// A dry run asks the agent for its hello and installs nothing.
#[test]
fn a_dry_run_without_an_agent_says_that_prepare_installs_it() {
    let fake = ready();
    let link = FakeLink::default();
    link.answer_hello(Err(host_refused("no_host", "no host file for studio")));
    let outcome = prepare_with(&["--dry-run"], &fake, &link);
    assert!(
        outcome
            .text
            .contains("missing agent: no host file for studio; --prepare installs it"),
        "{}",
        outcome.text
    );
    assert_eq!(link.calls(), ["hello studio"]);
}

#[test]
fn prepare_needs_a_host() {
    let failure = refusal(execute_with(["--prepare"], &ready(), &FakeLink::default()));
    assert_eq!(failure.exit, exit::USAGE);
}

/// A local token whose marker is in the past, or that has no marker, must be renewed here first. Nothing of it is
/// copied, because the host would ask for a browser login anyway.
#[test]
fn an_expired_or_unmarked_local_token_asks_for_a_renewal_first() {
    let expired = ready();
    answer_first(&expired, "stat -f %m", answered(0, "0\n"));
    answer_first(&expired, "ls -1 Library", answered(0, ""));
    let outcome = prepare(&["--yes"], &expired);
    assert!(
        outcome
            .text
            .contains("missing token markers: the local token of intellij-dependencies has expired; renew it first"),
        "{}",
        outcome.text
    );
    assert!(
        !outcome.text.contains("token expiry markers"),
        "nothing is copied: {}",
        outcome.text
    );

    let unmarked = ready();
    answer_first(&unmarked, "stat -f %m", answered(1, "stat: No such file or directory\n"));
    let outcome = prepare(&["--dry-run"], &unmarked);
    assert!(
        outcome
            .text
            .contains("missing token markers: this machine has no expiry marker for the token of intellij-dependencies"),
        "{}",
        outcome.text
    );
}
