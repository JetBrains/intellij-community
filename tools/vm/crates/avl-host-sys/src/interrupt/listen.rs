//! The operating system side of the interrupt service: what receives the two signals it answers.

use avl_base::{Exit, OrRefuse, Refusal};

use super::Signal;

#[cfg(unix)]
pub(super) use unix::Listener;
#[cfg(windows)]
pub(super) use windows::Listener;

/// A listener that cannot be installed is an internal error: the controller would not stop on Ctrl-C.
fn listen<T>(listening: std::io::Result<T>, name: &str) -> Result<T, Refusal> {
    listening.or_refuse("internal_error", Exit::FAILURE, || format!("cannot listen for {name}"))
}

#[cfg(unix)]
mod unix {
    use avl_base::Refusal;
    use tokio::signal::unix::{self as unix_signal, SignalKind};

    use super::{Signal, listen};

    /// SIGINT and SIGTERM.
    pub(in crate::interrupt) struct Listener {
        interrupt: unix_signal::Signal,
        terminate: unix_signal::Signal,
    }

    impl Listener {
        pub(in crate::interrupt) fn new() -> Result<Self, Refusal> {
            Ok(Self {
                interrupt: listen(unix_signal::signal(SignalKind::interrupt()), "SIGINT")?,
                terminate: listen(unix_signal::signal(SignalKind::terminate()), "SIGTERM")?,
            })
        }

        /// The next signal, or `None` when the runtime that listens has ended.
        pub(in crate::interrupt) async fn recv(&mut self) -> Option<Signal> {
            tokio::select! {
                Some(()) = self.interrupt.recv() => Some(Signal::Interrupt),
                Some(()) = self.terminate.recv() => Some(Signal::Terminate),
                else => None,
            }
        }
    }
}

#[cfg(windows)]
mod windows {
    use avl_base::Refusal;
    use tokio::signal::windows::{CtrlBreak, CtrlC, CtrlClose, CtrlShutdown};

    use super::{Signal, listen};

    /// Ctrl-C, which is SIGINT, and the console events that end a process, which are SIGTERM: Ctrl-Break, the close
    /// of the console window and a shutdown. Windows ends the process a few seconds after a close or a shutdown
    /// event whatever the handler does, so the cooperative stop has only that long.
    pub(in crate::interrupt) struct Listener {
        interrupt: CtrlC,
        terminate: CtrlBreak,
        close: CtrlClose,
        shutdown: CtrlShutdown,
    }

    impl Listener {
        pub(in crate::interrupt) fn new() -> Result<Self, Refusal> {
            use tokio::signal::windows::{ctrl_break, ctrl_c, ctrl_close, ctrl_shutdown};
            Ok(Self {
                interrupt: listen(ctrl_c(), "Ctrl-C")?,
                terminate: listen(ctrl_break(), "Ctrl-Break")?,
                close: listen(ctrl_close(), "the close of the console")?,
                shutdown: listen(ctrl_shutdown(), "a shutdown")?,
            })
        }

        /// The next signal, or `None` when the runtime that listens has ended.
        pub(in crate::interrupt) async fn recv(&mut self) -> Option<Signal> {
            tokio::select! {
                Some(()) = self.interrupt.recv() => Some(Signal::Interrupt),
                Some(()) = self.terminate.recv() => Some(Signal::Terminate),
                Some(()) = self.close.recv() => Some(Signal::Terminate),
                Some(()) = self.shutdown.recv() => Some(Signal::Terminate),
                else => None,
            }
        }
    }
}
