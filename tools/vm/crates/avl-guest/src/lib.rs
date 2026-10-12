//! The guest half: the run supervisor, the runtime stager, the image and guest verbs, and the agent binary.
//!
//! One static binary. The controller pushes it into the guest and invokes it over the one exec channel each
//! hypervisor offers. The reason it is a typed binary is that every silent defect the controller has had lived on
//! this side of the wire: a 64-bit content hash rounded by `JSON.parse`, an outcome string that two
//! hand-maintained lists disagreed about.
//!
//! The verbs do not collide: `start|supervise|status|active|log|cancel` are the run supervisor's,
//! `stage|stage-check|launch-prep|gc` are the stager's, `provision-image|validate-image` build the macOS golden
//! image, `validate-guest` proves a Linux worker, and `trace-pack-ready` zips the trace bundles that finished since
//! the last pull with `air-trace pack`'s own code. `relay` bridges its standard streams to a guest loopback port,
//! which is how the controller reaches the UI daemon. `read-file` copies one file to its standard output unchanged,
//! which is how the controller pulls a file. `runfiles-tree` builds the runfiles tree of a host MANIFEST, for a
//! Windows host that writes no tree.
//!
//! The image pair takes three named flags (`--macos-version --node-major --junie-version`), because the Packer
//! template `air-macos.pkr.hcl` passes them by name. `validate-guest` takes a positional argv: a `:88` and an
//! absolute path are two shapes no transposition survives.
//!
//! This crate is dispatch plus the verbs. The documents that cross to the host, and every verb's name, are declared
//! in `avl-wire`, where the host reads the same declaration; the pack report is `avl-trace-tools`'. It depends on
//! `avl-wire` and `avl-trace-tools` alone, never on `avl-base`, so the agent links no controller code.

mod cli;
mod clock;
mod image;
mod linux;
mod read_file;
mod relay;
mod reply;
mod runfiles;
mod shape;
mod stage;
mod step;
mod supervisor;
#[cfg(test)]
mod testing;
mod tracepack;

use std::ffi::OsString;
use std::io::{Read, Write};

use reply::Streams;

/// The name of the binary, which a usage text and a refusal give.
const PROGRAM: &str = "vm-guest-agent";

/// Runs the agent, the whole program behind `main`: `args` are the arguments after the program name. It answers the
/// verb on the three streams, exactly one document on stdout on success or one failure envelope on stderr, and
/// answers the exit status.
///
/// `relay` reads the process's own standard input, whatever `stdin` is, because its input moves to a thread of its
/// own and a borrowed stream cannot move.
pub fn run(args: impl IntoIterator<Item = OsString>, stdin: &mut dyn Read, stdout: &mut dyn Write, stderr: &mut dyn Write) -> u8 {
    let args: Vec<OsString> = std::iter::once(OsString::from(PROGRAM)).chain(args).collect();
    let mut streams = Streams { stdin, stdout, stderr };
    match cli::parse(&args) {
        Ok(agent) => cli::dispatch(agent.verb, &mut streams),
        Err(error) => cli::answer_parse_error(&error, &args, &mut streams),
    }
}
