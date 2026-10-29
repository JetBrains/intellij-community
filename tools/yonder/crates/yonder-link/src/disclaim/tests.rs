use std::process::{Child, Stdio};

use pretty_assertions::{assert_eq, assert_ne};

use super::*;

/// `pid_t responsibility_get_pid_responsible_for_pid(pid_t)` of libSystem.
fn responsible_for(pid: u32) -> libc::pid_t {
    type Responsible = unsafe extern "C" fn(libc::pid_t) -> libc::pid_t;
    // SAFETY: the name is a C string, and `RTLD_DEFAULT` searches every image of the process.
    let symbol = unsafe { libc::dlsym(libc::RTLD_DEFAULT, c"responsibility_get_pid_responsible_for_pid".as_ptr()) };
    assert!(!symbol.is_null(), "libSystem has no responsibility_get_pid_responsible_for_pid");
    // SAFETY: libSystem exports the symbol as the function of `Responsible`.
    let responsible = unsafe { std::mem::transmute::<*mut c_void, Responsible>(symbol) };
    let pid = libc::pid_t::try_from(pid).expect("a pid");
    // SAFETY: the call only reads the state of the process.
    unsafe { responsible(pid) }
}

fn sleeper(disclaimed: bool) -> Child {
    let mut command = Command::new("/bin/sleep");
    command.arg("30");
    if disclaimed {
        disclaim(&mut command).expect("disclaimed");
    }
    command.spawn().expect("a sleep")
}

fn end(mut child: Child) {
    child.kill().expect("killed");
    child.wait().expect("waited");
}

#[test]
fn a_disclaimed_child_is_responsible_for_itself_and_a_plain_one_is_not() {
    let plain = sleeper(false);
    let disclaimed = sleeper(true);
    let plain_pid = libc::pid_t::try_from(plain.id()).expect("a pid");
    let disclaimed_pid = libc::pid_t::try_from(disclaimed.id()).expect("a pid");
    assert_eq!(responsible_for(disclaimed.id()), disclaimed_pid);
    assert_eq!(responsible_for(plain.id()), responsible_for(std::process::id()));
    assert_ne!(responsible_for(plain.id()), plain_pid);
    end(plain);
    end(disclaimed);
}

#[test]
fn a_disclaimed_child_gets_its_arguments_and_its_pipes() {
    let mut command = Command::new("/bin/echo");
    command.args(["one", "two words"]).stdout(Stdio::piped());
    disclaim(&mut command).expect("disclaimed");
    let output = command.output().expect("an echo");
    assert_eq!(String::from_utf8_lossy(&output.stdout), "one two words\n");
    assert!(output.status.success());
}

#[test]
fn a_missing_program_fails_the_spawn() {
    let mut command = Command::new("/nonexistent/program");
    disclaim(&mut command).expect("disclaimed");
    let error = command.spawn().expect_err("missing");
    assert_eq!(error.kind(), io::ErrorKind::NotFound, "{error}");
}

#[test]
fn a_command_that_changes_the_environment_is_refused() {
    let mut command = Command::new("/bin/echo");
    command.env("YONDER_TEST", "1");
    assert_eq!(disclaim(&mut command), Err("the command changes the environment".to_owned()));
}

#[test]
fn a_program_without_a_path_is_refused() {
    assert_eq!(disclaim(&mut Command::new("nc")), Err("nc has no path".to_owned()));
}

#[test]
fn libsystem_has_the_disclaim() {
    assert_eq!(unavailable(), None);
}
