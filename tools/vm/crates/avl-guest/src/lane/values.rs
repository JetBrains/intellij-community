//! The values of one scenario: what each instruction answers, kept by name as an opaque JSON value, and what a later
//! instruction takes.
//!
//! The runner never reads a value, except the few that a host id uses. An IDE id gets each value it takes as a field
//! of its request, under the name of the value, and answers each value as a field of its answer. The vocabulary of the
//! suite states which values an id takes and answers. A part of an authored program states them on its instruction,
//! and it gets and answers them in one `values` object.

use std::collections::BTreeMap;

use avl_wire::program::IdSpec;
use serde_json::{Map, Value};

#[cfg(test)]
mod tests;

/// The address of the JetBrains Cloud emulator of the lane, which the runner answers before the first instruction.
pub(crate) const JETBRAINS_CLOUD_URL: &str = "jetBrainsCloudUrl";

/// The keys of the terminal Session View tabs that were open before the first instruction. The preparation answers it.
pub(crate) const TERMINAL_BASELINE: &str = "terminalBaseline";

/// The values of one scenario, by name.
#[derive(Debug, Default)]
pub(crate) struct Values {
    by_name: BTreeMap<String, Value>,
}

impl Values {
    /// The values of a scenario before its first instruction: the runner answers `runner`.
    pub(crate) fn new(runner: impl IntoIterator<Item = (String, Value)>) -> Self {
        Self {
            by_name: runner.into_iter().collect(),
        }
    }

    /// The value `name`, when an instruction answered it with a value that is not null.
    pub(crate) fn get(&self, name: &str) -> Option<&Value> {
        self.by_name.get(name).filter(|value| !value.is_null())
    }

    /// Keeps `value` under `name`, in place of an earlier answer.
    pub(crate) fn set(&mut self, name: &str, value: Value) {
        self.by_name.insert(name.to_owned(), value);
    }

    /// The value `name` that the instruction `label` takes. A missing value fails with a sentence that names the value
    /// and the instruction.
    pub(crate) fn require(&self, label: &str, name: &str) -> Result<&Value, String> {
        self.get(name)
            .ok_or_else(|| format!("the instruction '{label}' takes the value '{name}', and no earlier instruction answered it"))
    }

    /// The fields that the request of the IDE id `label` gets for its values: each value that `spec` takes, and each
    /// value that it may take and that exists.
    pub(crate) fn request_fields(&self, label: &str, spec: &IdSpec) -> Result<Map<String, Value>, String> {
        let mut fields = Map::new();
        for name in &spec.takes {
            fields.insert(name.clone(), self.require(label, name)?.clone());
        }
        for name in &spec.may_take {
            if let Some(value) = self.get(name) {
                fields.insert(name.clone(), value.clone());
            }
        }
        Ok(fields)
    }

    /// Keeps what the IDE id `label` answered: each value of `spec.answers`, null included, and each value of
    /// `spec.may_answer` that is not null. Any other field of the answer stays unread.
    pub(crate) fn keep_answers(&mut self, label: &str, spec: &IdSpec, answer: &Value) -> Result<(), String> {
        if spec.answers.is_empty() && spec.may_answer.is_empty() {
            return Ok(());
        }
        let Value::Object(fields) = answer else {
            return Err(format!(
                "the instruction '{label}' answers the values {:?}, and the IDE answered {answer}, which is no object",
                spec.answers
            ));
        };
        for name in &spec.answers {
            self.set(name, fields.get(name).cloned().unwrap_or(Value::Null));
        }
        for name in &spec.may_answer {
            if let Some(value) = fields.get(name).filter(|value| !value.is_null()) {
                self.set(name, value.clone());
            }
        }
        Ok(())
    }

    /// The `values` object of the part `label`: each value of `takes`. A missing one fails with a sentence that names
    /// the value and the part.
    pub(crate) fn part_values(&self, label: &str, takes: &[String]) -> Result<Map<String, Value>, String> {
        takes
            .iter()
            .map(|name| {
                self.get(name)
                    .cloned()
                    .map(|value| (name.clone(), value))
                    .ok_or_else(|| format!("the part '{label}' takes the value '{name}', and no earlier part answered it"))
            })
            .collect()
    }

    /// Keeps the values that the part `label` answered, which must be exactly the values that it `declared`.
    pub(crate) fn keep_part_values(&mut self, label: &str, answered: &Map<String, Value>, declared: &[String]) -> Result<(), String> {
        let mut answered_names: Vec<&str> = answered.keys().map(String::as_str).collect();
        let mut declared_names: Vec<&str> = declared.iter().map(String::as_str).collect();
        answered_names.sort_unstable();
        declared_names.sort_unstable();
        if answered_names != declared_names {
            return Err(format!(
                "the part '{label}' answered the values [{}], and it declares [{}]",
                answered.keys().cloned().collect::<Vec<_>>().join(", "),
                declared.join(", ")
            ));
        }
        for (name, value) in answered {
            self.set(name, value.clone());
        }
        Ok(())
    }
}
