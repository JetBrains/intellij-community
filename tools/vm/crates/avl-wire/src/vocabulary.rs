//! Closed vocabularies: a word on the wire that is one of a fixed set, spelled once.

use std::fmt;

/// A word that is not one of a vocabulary's declared spellings.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct UnknownWord {
    pub vocabulary: &'static str,
    pub word: String,
}

impl fmt::Display for UnknownWord {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?} is not a declared {}", self.word, self.vocabulary)
    }
}

impl std::error::Error for UnknownWord {}

/// Declares a fieldless enum whose variants are spelled on the wire as the given literals.
///
/// The enum gets `ALL` (every word in declaration order - the order is contract data for some of them),
/// `as_str`, `Display`, `FromStr`, and serde impls that read and write the spelling. An unknown word is a
/// decode error, so a reader that must keep unknown words (a run outcome) is written by hand instead.
macro_rules! vocabulary {
    (
        $(#[$meta:meta])*
        $vis:vis enum $name:ident {
            $( $(#[$variant_meta:meta])* $variant:ident = $text:literal ),+ $(,)?
        }
    ) => {
        $(#[$meta])*
        #[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
        $vis enum $name {
            $( $(#[$variant_meta])* $variant ),+
        }

        impl $name {
            /// Every word, in declaration order.
            pub const ALL: &'static [$name] = &[$($name::$variant),+];

            /// The word as the wire spells it.
            pub const fn as_str(self) -> &'static str {
                match self {
                    $($name::$variant => $text),+
                }
            }
        }

        impl ::std::fmt::Display for $name {
            fn fmt(&self, f: &mut ::std::fmt::Formatter<'_>) -> ::std::fmt::Result {
                f.write_str(self.as_str())
            }
        }

        impl ::std::str::FromStr for $name {
            type Err = $crate::vocabulary::UnknownWord;

            fn from_str(text: &str) -> ::std::result::Result<Self, Self::Err> {
                match text {
                    $($text => Ok($name::$variant),)+
                    _ => Err($crate::vocabulary::UnknownWord {
                        vocabulary: stringify!($name),
                        word: text.to_owned(),
                    }),
                }
            }
        }

        impl ::serde::Serialize for $name {
            fn serialize<S: ::serde::Serializer>(&self, serializer: S) -> ::std::result::Result<S::Ok, S::Error> {
                serializer.serialize_str(self.as_str())
            }
        }

        impl<'de> ::serde::Deserialize<'de> for $name {
            fn deserialize<D: ::serde::Deserializer<'de>>(deserializer: D) -> ::std::result::Result<Self, D::Error> {
                let text = <::std::string::String as ::serde::Deserialize>::deserialize(deserializer)?;
                text.parse().map_err(|_| {
                    <D::Error as ::serde::de::Error>::unknown_variant(&text, &[$($text),+])
                })
            }
        }
    };
}
