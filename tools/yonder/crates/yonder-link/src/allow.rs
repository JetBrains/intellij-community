//! The allow list of an agent: the fingerprints of the clients it accepts.
//!
//! One client per line: the fingerprint, then a space and a name for a person. A line that starts with `#` and an
//! empty line are skipped. The agent reads the file at each handshake, so `yonder prepare` adds a client without a
//! restart.

use std::collections::BTreeMap;
use std::fs;
use std::io;
use std::path::Path;

use crate::identity::Fingerprint;

#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct AllowList {
    clients: BTreeMap<Fingerprint, String>,
}

impl AllowList {
    /// The list in `path`. A missing file is an empty list, so an agent without clients refuses every connection.
    pub fn read(path: &Path) -> io::Result<Self> {
        match fs::read_to_string(path) {
            Ok(text) => Self::parse(&text).map_err(|line| {
                io::Error::new(
                    io::ErrorKind::InvalidData,
                    format!("{}: line {line} is not `<fingerprint> <name>`", path.display()),
                )
            }),
            Err(error) if error.kind() == io::ErrorKind::NotFound => Ok(Self::default()),
            Err(error) => Err(io::Error::new(error.kind(), format!("cannot read {}: {error}", path.display()))),
        }
    }

    /// The list of a text, or the 1-based number of the first line that is not `<fingerprint> <name>`.
    pub fn parse(text: &str) -> Result<Self, usize> {
        let mut clients = BTreeMap::new();
        for (index, line) in text.lines().enumerate() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let (fingerprint, name) = line.split_once(' ').unwrap_or((line, ""));
            let Ok(fingerprint) = fingerprint.parse::<Fingerprint>() else {
                return Err(index + 1);
            };
            clients.insert(fingerprint, name.trim().to_owned());
        }
        Ok(Self { clients })
    }

    /// The name of an allowed client, or `None`.
    pub fn name(&self, fingerprint: &Fingerprint) -> Option<&str> {
        self.clients.get(fingerprint).map(String::as_str)
    }

    /// Adds or renames a client. Answers whether the list changed. Refuses a name that [`check_name`] refuses.
    pub fn add(&mut self, fingerprint: Fingerprint, name: &str) -> io::Result<bool> {
        check_name(name)?;
        Ok(self.clients.insert(fingerprint, name.to_owned()).as_deref() != Some(name))
    }

    /// The text of the file, one client per line in fingerprint order.
    pub fn text(&self) -> String {
        self.clients
            .iter()
            .map(|(fingerprint, name)| format!("{fingerprint} {name}\n"))
            .collect()
    }
}

/// Refuses a client name with a control character. A line break would start a second line of the file, which can
/// name another fingerprint.
pub fn check_name(name: &str) -> io::Result<()> {
    if name.chars().any(char::is_control) {
        return Err(io::Error::new(
            io::ErrorKind::InvalidInput,
            format!("the client name {name:?} has a control character"),
        ));
    }
    Ok(())
}

#[cfg(test)]
mod tests;
