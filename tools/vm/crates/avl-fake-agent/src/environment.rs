//! The environment of the agent process, read once at the start.

use std::collections::BTreeMap;

/// Every variable of the process environment, by name. A name or a value that is not UTF-8 is read lossily.
#[derive(Clone, Debug, Default)]
pub(crate) struct Environment {
    variables: BTreeMap<String, String>,
}

impl Environment {
    /// The environment of this process.
    pub(crate) fn of_process() -> Self {
        Self {
            variables: std::env::vars_os()
                .map(|(name, value)| (name.to_string_lossy().into_owned(), value.to_string_lossy().into_owned()))
                .collect(),
        }
    }

    /// An environment of `pairs` only, for a test.
    #[cfg(test)]
    pub(crate) fn of(pairs: &[(&str, &str)]) -> Self {
        Self {
            variables: pairs
                .iter()
                .map(|(name, value)| ((*name).to_owned(), (*value).to_owned()))
                .collect(),
        }
    }

    /// The value of `name`.
    pub(crate) fn get(&self, name: &str) -> Option<&str> {
        self.variables.get(name).map(String::as_str)
    }

    /// The value of `name`, when it has a character other than white space.
    pub(crate) fn non_blank(&self, name: &str) -> Option<&str> {
        self.get(name).filter(|value| !value.trim().is_empty())
    }

    /// Every variable, in the order of the names.
    pub(crate) const fn variables(&self) -> &BTreeMap<String, String> {
        &self.variables
    }
}
