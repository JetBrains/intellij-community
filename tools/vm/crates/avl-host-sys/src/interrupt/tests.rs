use pretty_assertions::assert_eq;

use super::*;

/// The tests that start a child and raise a real signal. They drive `/bin/sleep` and read the signal that ended it.
#[cfg(unix)]
mod unix {
    use std::process::Stdio;
    use std::time::Duration;

    use nix::sys::signal as nix_signal;
    use pretty_assertions::assert_eq;

    use super::*;

    /// A `sleep 30` in its own process group, the shape of every child the runner starts.
    fn sleeper() -> tokio::process::Child {
        let mut command = tokio::process::Command::new("/bin/sleep");
        command.arg("30").stdin(Stdio::null()).kill_on_drop(true);
        ProcessGroup::prepare(&mut command);
        command.spawn().expect("sleep starts")
    }

    async fn ended_by(child: &mut tokio::process::Child) -> Option<i32> {
        use std::os::unix::process::ExitStatusExt;
        let status = tokio::time::timeout(Duration::from_secs(10), child.wait())
            .await
            .expect("the signal did not reach the group")
            .unwrap();
        status.signal()
    }

    fn group(child: &tokio::process::Child) -> ProcessGroup {
        ProcessGroup::attach(child)
            .expect("the group takes the child")
            .expect("the child runs")
    }

    // The first signal is recorded by name, cancels the root token, and reaches every registered group - and only the
    // registered ones.
    #[tokio::test]
    async fn the_first_signal_cancels_and_reaches_every_registered_group() {
        let interrupts = Interrupts::detached();
        let ctx = interrupts.context();
        let mut registered = sleeper();
        let mut unregistered = sleeper();
        let mut dropped = sleeper();
        let _registration = interrupts.register_group(&group(&registered));
        drop(interrupts.register_group(&group(&dropped)));

        assert!(interrupts.deliver(Signal::Interrupt));
        assert_eq!(interrupts.received(), Some(Signal::Interrupt));
        assert!(ctx.is_cancelled());
        assert_eq!(ended_by(&mut registered).await, Some(libc::SIGINT));
        for child in [&mut unregistered, &mut dropped] {
            assert!(
                tokio::time::timeout(Duration::from_millis(300), child.wait()).await.is_err(),
                "a group nobody registered was signalled"
            );
        }

        // A later signal changes nothing here: the name recorded is the one that ended the run.
        assert!(!interrupts.deliver(Signal::Terminate));
        assert_eq!(interrupts.received().map(Signal::name), Some("SIGINT"));
    }

    // A group registered after the signal was spawned by an operation that had not looked at its token yet; it must
    // not outlive the interrupt either.
    #[tokio::test]
    async fn a_group_registered_after_the_signal_is_signalled_at_once() {
        let interrupts = Interrupts::detached();
        interrupts.deliver(Signal::Terminate);
        let mut late = sleeper();
        let _registration = interrupts.register_group(&group(&late));
        assert_eq!(ended_by(&mut late).await, Some(libc::SIGTERM));
    }

    // The installed service answers a real signal. SIGTERM is raised once and only once in this process: a second
    // one is the operator's "stop now", which exits.
    #[tokio::test]
    async fn the_installed_service_answers_a_real_signal() {
        let interrupts = Interrupts::install().expect("the handlers install");
        let again = Interrupts::install().unwrap();
        assert!(Arc::ptr_eq(&interrupts.0, &again.0), "a second install made a second service");
        let mut child = sleeper();
        let _registration = interrupts.register_group(&group(&child));

        nix_signal::raise(nix_signal::Signal::SIGTERM).unwrap();
        tokio::time::timeout(Duration::from_secs(10), interrupts.token().cancelled())
            .await
            .expect("the signal was not answered");
        assert_eq!(interrupts.received(), Some(Signal::Terminate));
        assert_eq!(ended_by(&mut child).await, Some(libc::SIGTERM));
    }
}

#[test]
fn a_signal_is_named_and_exits_as_a_shell_reports_it() {
    assert_eq!(Signal::Interrupt.name(), "SIGINT");
    assert_eq!(Signal::Terminate.to_string(), "SIGTERM");
    // A second Ctrl-C leaves with the status a shell shows for it.
    assert_eq!(Signal::Interrupt.exit_status(), 130);
    assert_eq!(Signal::Terminate.exit_status(), 143);
}

// The listener belongs to the runtime that installed it. An install after that runtime ended listens again, for
// the same service: a handle taken earlier must not be left with nothing that will ever interrupt it.
#[test]
fn an_install_after_its_runtime_ended_listens_again_for_the_same_service() {
    static SLOT: Mutex<Option<Installed>> = Mutex::new(None);
    let runtime = || tokio::runtime::Builder::new_current_thread().enable_all().build().unwrap();
    let first = runtime();
    let service = first.block_on(async { install_into(&SLOT) }).unwrap();
    drop(first);
    assert!(lock(&SLOT).as_ref().unwrap().listener.is_finished());

    let second = runtime();
    let again = second.block_on(async { install_into(&SLOT) }).unwrap();
    assert!(Arc::ptr_eq(&service.0, &again.0), "a new service was made");
    assert!(!lock(&SLOT).as_ref().unwrap().listener.is_finished());
}
