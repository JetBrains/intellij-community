//! `vm-guest-agent`: everything the host controller needs to run inside a worker VM. The work is in the library.

use std::io;
use std::process::ExitCode;

fn main() -> ExitCode {
    // Each standard stream is locked for each use, not for the whole run. `relay` reads standard input on a thread of
    // its own, the threads of `lane-daemon` write their log on standard error, and the run thread and the watchdog
    // thread of `lane-run` write the run stream on standard output. A lock that this thread holds for the whole run
    // blocks each of them for ever.
    ExitCode::from(avl_guest::run(
        std::env::args_os().skip(1),
        &mut io::stdin(),
        &mut io::stdout(),
        &mut io::stderr(),
    ))
}
