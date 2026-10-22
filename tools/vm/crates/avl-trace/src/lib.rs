//! Scenario traces: the lane protocol, OTLP, the bundle and the bridge routes.
//!
//! Three documents cross here:
//!
//! - The lane protocol ([protocol]). The lane writes one command per line to the recorder's standard input and reads
//!   the acks from its standard output. The lane is the lane daemon or `lane-run` of `avl-guest`, so both ends are
//!   Rust.
//! - The bundle ([bundle], [otlp]). The recorder writes it and the viewer in `plugins/air/docs` reads it.
//! - The bridge routes ([bridge]). The recorder asks the IDE's trace handler (`AirUiTraceHttpHandler`) for the
//!   facts, the Swing tree, a painted frame and the IDE's own spans.
//!
//! This crate declares each contract once. The golden files under the trace testdata pin the viewer to it: the
//! recorder replays `lane-transcript.ndjson` into `example.airtrace`, and the viewer's tests read that bundle.
//!
//! One rule differs from the daemon wire, and on purpose. There an unknown field is kept, because the host
//! republishes the daemon's records verbatim and a new field is how the daemon grows compatibly. Here the lane and
//! the recorder are built from one checkout, so an unknown field can only be a rename that one end missed. A recorder
//! that ignored it would write a bundle without the renamed fact, and nobody would notice until a failure needed it.
//! The decoders therefore refuse an unknown field just as they refuse an unknown op.
//!
//! The recorder links this crate and `avl-bridge`, the client of the bridge routes. `avl-trace-tools` holds the tools
//! over these documents, which the recorder does not run: the packer, the discovery of bundles and the viewer's URLs.

use std::fmt;

/// Declares a closed vocabulary: an enum whose variants are spelled by one word each on the wire, with the word
/// list, a lookup by word and `Display`. `avl-trace-tools` declares its own vocabularies with it, so the crate that
/// calls it needs `serde`.
#[macro_export]
macro_rules! vocabulary {
    (
        $(#[$meta:meta])*
        pub enum $name:ident {
            $($(#[$variant_meta:meta])* $variant:ident = $word:literal,)+
        }
    ) => {
        $(#[$meta])*
        #[derive(serde::Serialize, serde::Deserialize, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Debug)]
        pub enum $name {
            $($(#[$variant_meta])* #[serde(rename = $word)] $variant,)+
        }

        impl $name {
            /// Every word of the vocabulary, in declaration order.
            pub const ALL: &'static [$name] = &[$($name::$variant),+];

            /// The word this is spelled with on the wire.
            pub const fn as_str(self) -> &'static str {
                match self {
                    $($name::$variant => $word,)+
                }
            }

            /// The word's variant, or `None` for a word the vocabulary does not name.
            pub fn from_word(word: &str) -> Option<Self> {
                match word {
                    $($word => Some($name::$variant),)+
                    _ => None,
                }
            }
        }

        impl std::fmt::Display for $name {
            fn fmt(&self, formatter: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
                formatter.write_str(self.as_str())
            }
        }
    };
}

/// Returns early with an [Error] formatted like `format!`.
#[macro_export]
macro_rules! refuse {
    ($($argument:tt)*) => {
        return Err($crate::Error::new(format!($($argument)*)))
    };
}

pub mod bridge;
pub mod bundle;
pub mod otlp;
pub mod protocol;

#[cfg(test)]
mod testdata;

pub use bundle::{Capture, Manifest, Video};
pub use protocol::{Command, encode};

/// A document or a pack that this crate or `avl-trace-tools` refuses, with a sentence that says why.
///
/// The recorder answers a refused line with a negative ack carrying this sentence, and records it in the bundle,
/// so the message is the whole error: it already names the cause.
#[derive(Clone, PartialEq, Eq)]
pub struct Error(String);

impl Error {
    pub fn new(message: impl Into<String>) -> Self {
        Self(message.into())
    }

    pub fn message(&self) -> &str {
        &self.0
    }
}

impl fmt::Display for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        formatter.write_str(&self.0)
    }
}

impl fmt::Debug for Error {
    fn fmt(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
        fmt::Debug::fmt(&self.0, formatter)
    }
}

impl std::error::Error for Error {}

const fn is_false(value: &bool) -> bool {
    !*value
}

const fn is_zero_u32(value: &u32) -> bool {
    *value == 0
}

const fn is_zero_i64(value: &i64) -> bool {
    *value == 0
}
