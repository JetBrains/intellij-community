//! The fixed shapes the verbs check in an argument or in a tool's answer: a display and a release.
//!
//! They are written by hand, because a regex engine would be the largest part of the agent, and the controller
//! reinstalls the agent on every worker when its bytes change.

#[cfg(test)]
mod tests;

/// Whether `value` is one or more ASCII digits.
pub(crate) fn is_digits(value: &str) -> bool {
    !value.is_empty() && value.bytes().all(|byte| byte.is_ascii_digit())
}

/// The release that follows the first `version ` with one, `N.N` or `N.N.N`, such as `2.39` of
/// `ld.so (Ubuntu GLIBC 2.39-0ubuntu8.8) stable release version 2.39.`.
pub(crate) fn release_after_version(text: &str) -> Option<&str> {
    text.match_indices("version ")
        .find_map(|(at, word)| leading_release(&text[at + word.len()..]))
}

/// The `N.N` or `N.N.N` at the start of `text`, the third group when it is there.
fn leading_release(text: &str) -> Option<&str> {
    let digits_from = |start: usize| text.as_bytes()[start..].iter().take_while(|byte| byte.is_ascii_digit()).count();
    let dot_at = |at: usize| text.as_bytes().get(at) == Some(&b'.');
    let major = digits_from(0);
    if major == 0 || !dot_at(major) {
        return None;
    }
    let minor = digits_from(major + 1);
    if minor == 0 {
        return None;
    }
    let mut end = major + 1 + minor;
    if dot_at(end) {
        let patch = digits_from(end + 1);
        if patch > 0 {
            end += 1 + patch;
        }
    }
    Some(&text[..end])
}
