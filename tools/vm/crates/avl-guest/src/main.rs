//! `vm-guest-agent`: everything the host controller needs to run inside a worker VM. The work is in the library.

use std::io;
use std::process::ExitCode;

fn main() -> ExitCode {
    let (stdout, stderr) = (io::stdout(), io::stderr());
    // Standard input is locked for each read, not for the whole run: `relay` reads it on a thread of its own, and a
    // lock this thread holds blocks that thread for ever.
    ExitCode::from(avl_guest::run(
        std::env::args_os().skip(1),
        &mut io::stdin(),
        &mut stdout.lock(),
        &mut stderr.lock(),
    ))
}
