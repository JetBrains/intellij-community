//! The guest half: the run supervisor, the lane IDE contexts, the image and guest verbs, and the agent binary.
//!
//! One static binary. The controller pushes it into the guest and invokes it over the one exec channel each
//! hypervisor offers. The reason it is a typed binary is that every silent defect the controller has had lived on
//! this side of the wire: a 64-bit content hash rounded by `JSON.parse`, an outcome string that two
//! hand-maintained lists disagreed about.
//!
//! The verbs do not collide: `start|supervise|status|active|log|cancel` are the run supervisor's,
//! `provision-image|validate-image` build the macOS golden image, `validate-guest` proves a Linux worker, and
//! `trace-pack-ready` zips the trace bundles that finished since the last pull with `air-trace pack`'s own code.
//! `relay` bridges its standard streams to a guest loopback port, which is how the controller reaches the UI daemon.
//! `read-file` copies one file to its standard output unchanged, which is how the controller pulls a file.
//! `runfiles-tree` builds the runfiles tree of a host MANIFEST, for a Windows host that writes no tree.
//! `ide-gc` stops the IDE runs under an IDE root and removes their old logs. A lane IDE process is a supervisor run.
//! `lane-daemon` serves the control channel of the lane daemon in the run slot: it owns the lane services and the lane
//! IDE, and it streams the records of each iteration. It prepares and launches the lane IDE in its own process.
//!
//! The image pair takes three named flags (`--macos-version --node-major --junie-version`), because the Packer
//! template `air-macos.pkr.hcl` passes them by name. `validate-guest` takes one positional argument, the X display.
//!
//! This crate is dispatch plus the verbs. The documents that cross to the host, and every verb's name, are declared
//! in `avl-wire`, where the host reads the same declaration; the pack report is `avl-trace-tools`'. Of the crates of
//! this workspace it depends on `avl-wire` and `avl-trace-tools` alone, never on `avl-base`, so the agent links no
//! controller code. `dev-launch` of the dev-dist tools composes the argument file of the lane IDE, the same composer
//! that writes the argument file of a dev launch row.

mod cli;
mod clock;
mod fs;
mod ide;
mod image;
mod lane;
mod linux;
mod read_file;
mod relay;
mod reply;
mod runfiles;
mod shape;
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
