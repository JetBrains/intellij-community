//! The TeamCity service messages of one line, which the controller and `lane-run` both write.

/// The environment variable that a TeamCity agent sets for every build step.
pub const TEAMCITY_VERSION: &str = "TEAMCITY_VERSION";

/// Whether `variable` names a TeamCity build: [`TEAMCITY_VERSION`] is set and not blank.
pub fn is_team_city(variable: impl Fn(&str) -> Option<String>) -> bool {
    variable(TEAMCITY_VERSION).is_some_and(|version| !version.trim().is_empty())
}

/// One service message: `##teamcity[<name> key='value' ...]`, or `##teamcity[<name> 'value']` for a message of one
/// value.
pub fn message(name: &str, attributes: &[(&str, &str)], value: Option<&str>) -> String {
    let mut line = format!("##teamcity[{name}");
    if let Some(value) = value {
        line.push_str(" '");
        line.push_str(&escape(value));
        line.push('\'');
    }
    for (key, value) in attributes {
        line.push(' ');
        line.push_str(key);
        line.push_str("='");
        line.push_str(&escape(value));
        line.push('\'');
    }
    line.push(']');
    line
}

/// The `publishArtifacts` message that publishes the host file `file` into the artifact directory `target`.
pub fn publish_artifacts(file: &str, target: &str) -> String {
    message("publishArtifacts", &[], Some(&format!("{file} => {target}")))
}

/// The `buildProblem` message of `description`. TeamCity fails the build for it, also when each failed case is muted,
/// and it keeps one problem per `identity`.
pub fn build_problem(description: &str, identity: &str) -> String {
    message("buildProblem", &[("description", description), ("identity", identity)], None)
}

/// Escapes a value as TeamCity reads it: `|` before each of `|`, `'`, `[` and `]`, `|n` and `|r` for the line ends,
/// and `|x`, `|l` and `|p` for the next line, the line separator and the paragraph separator of Unicode.
fn escape(value: &str) -> String {
    let mut escaped = String::with_capacity(value.len());
    for character in value.chars() {
        match character {
            '|' => escaped.push_str("||"),
            '\'' => escaped.push_str("|'"),
            '[' => escaped.push_str("|["),
            ']' => escaped.push_str("|]"),
            '\n' => escaped.push_str("|n"),
            '\r' => escaped.push_str("|r"),
            '\u{0085}' => escaped.push_str("|x"),
            '\u{2028}' => escaped.push_str("|l"),
            '\u{2029}' => escaped.push_str("|p"),
            _ => escaped.push(character),
        }
    }
    escaped
}

#[cfg(test)]
mod tests;
