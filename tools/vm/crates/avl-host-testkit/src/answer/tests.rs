use std::time::Duration;

use avl_base::format::words;
use pretty_assertions::assert_eq;

use super::*;

// A router routes on what actually runs, whichever wrappers production code put in front of it.
#[test]
fn the_effective_argv_skips_every_wrapper_production_code_builds() {
    let agent = "/vm/data/state/vm-guest-agent";
    for wrapped in [
        words(["/usr/bin/sudo", "-H", "-u", "admin", agent, "stage", "/x"]),
        words(["/usr/bin/sudo", "-H", agent, "stage", "/x"]),
        words([
            "/usr/bin/sudo",
            "-H",
            "-u",
            "admin",
            "/usr/bin/setsid",
            "--wait",
            "/usr/bin/env",
            "DISPLAY=:88",
            "/usr/bin/env",
            "IJ_PRIVATE_PACKAGES_AUTHORIZER_SKIP=true",
            agent,
            "stage",
            "/x",
        ]),
        words([
            "/bin/launchctl",
            "asuser",
            "501",
            "/usr/bin/sudo",
            "-H",
            "-u",
            "admin",
            agent,
            "stage",
            "/x",
        ]),
    ] {
        assert_eq!(effective_argv(&wrapped), words([agent, "stage", "/x"]), "{wrapped:?}");
    }
    assert!(effective_argv(&words(["/usr/bin/sudo", "-H"])).is_empty());
}

// The agent routes by its subcommand and every other program by its basename; the handler sees the stripped argv.
#[test]
fn verbs_route_the_agent_by_subcommand_and_the_rest_by_basename() {
    let agent = "/vm/data/state/vm-guest-agent";
    let verbs = Verbs::new(agent);
    verbs.on("gc", handler(|argv, _| Ok(said(&argv.join(" ")))));
    verbs.on("df", answer_exit(3));
    let options = SpawnOptions::within(Duration::from_mins(1));
    let gc = verbs
        .route(&words(["/usr/bin/sudo", "-H", "-u", "admin", agent, "gc"]), &options)
        .unwrap();
    assert_eq!(gc.stdout, format!("{agent} gc"));
    assert_eq!(verbs.route(&words(["/bin/df", "-k"]), &options).unwrap().exit_code, 3);
    assert_eq!(verbs.route(&words(["/bin/ls"]), &options).unwrap(), Captured::default());
}
