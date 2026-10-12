//! The `air-trace` binary: `pack`, `serve` and `plan`. The work is in the library.

use std::io;
use std::process::ExitCode;

fn main() -> ExitCode {
    // Not locked for the whole run: the tasks of `serve` write to the process's own streams.
    ExitCode::from(air_trace::run(
        std::env::args_os().skip(1),
        &mut io::stdin(),
        &mut io::stdout(),
        &mut io::stderr(),
    ))
}
