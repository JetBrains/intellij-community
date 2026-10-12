//! What the trace goldens decode to, as every module's tests read them. The files themselves are
//! [`avl_testkit::traces`]'.

use avl_testkit::traces;

use crate::bundle::{MANIFEST_FILE, Manifest, decode_manifest};
use crate::protocol::{Command, decode_command};

pub(crate) fn example_manifest() -> Manifest {
    decode_manifest(&traces::example_file(MANIFEST_FILE)).expect("the golden manifest decodes")
}

pub(crate) fn decode_transcript(name: &str) -> Vec<Command> {
    traces::file_lines(name)
        .iter()
        .enumerate()
        .map(|(index, line)| decode_command(line).unwrap_or_else(|error| panic!("{name} line {}: {error}", index + 1)))
        .collect()
}
