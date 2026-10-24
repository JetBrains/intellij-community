use pretty_assertions::assert_eq;

use super::*;

/// Each option that bounds a dead link is in the one list, and a call never joins a multiplexed master: a mux client
/// ignores its own keepalive options and waits for its master.
#[test]
fn every_host_call_bounds_a_dead_link_on_its_own_connection() {
    for option in [
        "BatchMode=yes",
        "ConnectTimeout=15",
        "ServerAliveInterval=15",
        "ServerAliveCountMax=4",
        "ControlMaster=no",
        "ControlPath=none",
    ] {
        let at = SSH_OPTIONS.iter().position(|word| *word == option);
        assert!(at.is_some_and(|at| at > 0 && SSH_OPTIONS[at - 1] == "-o"), "{option}");
    }
    let argv = ssh("studio", "true");
    assert_eq!(argv.len(), SSH_OPTIONS.len() + 3);
    assert_eq!(argv[argv.len() - 2..], [OsString::from("studio"), OsString::from("true")]);
    assert_eq!(ssh_git_config(), format!("core.sshCommand=ssh {}", SSH_OPTIONS.join(" ")));
}

#[test]
fn a_host_path_keeps_its_home_outside_the_quotes() {
    assert_eq!(host_path_word("~/projects/idea 1"), "~/'projects/idea 1'");
    assert_eq!(host_path_word("/abs"), "'/abs'");
    assert_eq!(push_url("studio", "~/projects/idea-1"), "studio:projects/idea-1");
    assert_eq!(push_url("studio", "/w/idea"), "studio:/w/idea");
}
