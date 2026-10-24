//! The identity of one end: a self-signed certificate and its key, and the fingerprint that the other end pins.

use std::fmt;
use std::fs;
use std::io::{self, Write as _};
use std::path::{Path, PathBuf};
use std::str::FromStr;

use rustls::pki_types::{CertificateDer, PrivateKeyDer, PrivatePkcs8KeyDer};
use serde::{Deserialize, Deserializer, Serialize, Serializer};

/// The file of the private key, PKCS#8 DER, in the identity directory.
const KEY_FILE: &str = "identity.key";

/// The file of the certificate, DER, in the identity directory.
const CERT_FILE: &str = "identity.crt";

/// The SHA-256 of the DER of a certificate. It is the name of an end: a client pins the fingerprint of its host, and an
/// agent keeps the fingerprints of its clients in its allow list.
#[derive(Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub struct Fingerprint([u8; 32]);

impl Fingerprint {
    pub fn of(cert_der: &[u8]) -> Self {
        let digest = ring::digest::digest(&ring::digest::SHA256, cert_der);
        let mut bytes = [0u8; 32];
        bytes.copy_from_slice(digest.as_ref());
        Self(bytes)
    }

    /// The first 12 hex digits, for a message.
    pub fn short(&self) -> String {
        self.to_string()[..12].to_owned()
    }
}

impl fmt::Display for Fingerprint {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        self.0.iter().try_for_each(|byte| write!(f, "{byte:02x}"))
    }
}

impl fmt::Debug for Fingerprint {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Fingerprint({self})")
    }
}

/// The refusal of a text that is not 64 hex digits.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct BadFingerprint(pub String);

impl fmt::Display for BadFingerprint {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "a fingerprint is 64 hex digits, not {:?}", self.0)
    }
}

impl std::error::Error for BadFingerprint {}

impl FromStr for Fingerprint {
    type Err = BadFingerprint;

    fn from_str(text: &str) -> Result<Self, Self::Err> {
        let refused = || BadFingerprint(text.to_owned());
        if text.len() != 64 || !text.is_ascii() {
            return Err(refused());
        }
        let mut bytes = [0u8; 32];
        for (index, byte) in bytes.iter_mut().enumerate() {
            let Ok(parsed) = u8::from_str_radix(&text[index * 2..index * 2 + 2], 16) else {
                return Err(refused());
            };
            *byte = parsed;
        }
        Ok(Self(bytes))
    }
}

impl Serialize for Fingerprint {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        serializer.collect_str(self)
    }
}

impl<'de> Deserialize<'de> for Fingerprint {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        let text = String::deserialize(deserializer)?;
        text.parse().map_err(serde::de::Error::custom)
    }
}

/// The certificate and the key of one end.
#[derive(Clone)]
pub struct Identity {
    cert_der: Vec<u8>,
    key_der: Vec<u8>,
}

impl fmt::Debug for Identity {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Identity({})", self.fingerprint())
    }
}

impl Identity {
    /// A new identity: an ECDSA P-256 key and a self-signed certificate for the name `yonder`. The certificate never
    /// expires for yonder, because each end checks the fingerprint and not the dates.
    pub fn generate() -> io::Result<Self> {
        let key = rcgen::KeyPair::generate().map_err(io::Error::other)?;
        let params = rcgen::CertificateParams::new(vec![SERVER_NAME.to_owned()]).map_err(io::Error::other)?;
        let cert = params.self_signed(&key).map_err(io::Error::other)?;
        Ok(Self {
            cert_der: cert.der().to_vec(),
            key_der: key.serialize_der(),
        })
    }

    /// The identity in `dir`, created there when it is missing. The files have mode 0600 on Unix.
    pub fn load_or_create(dir: &Path) -> io::Result<Self> {
        if dir.join(CERT_FILE).exists() {
            return Self::load(dir);
        }
        let identity = Self::generate()?;
        fs::create_dir_all(dir)?;
        write_private(&dir.join(KEY_FILE), &identity.key_der)?;
        write_private(&dir.join(CERT_FILE), &identity.cert_der)?;
        Ok(identity)
    }

    /// The identity in `dir`.
    pub fn load(dir: &Path) -> io::Result<Self> {
        let read = |name: &str| {
            let path = dir.join(name);
            fs::read(&path).map_err(|error| io::Error::new(error.kind(), format!("cannot read {}: {error}", path.display())))
        };
        Ok(Self {
            cert_der: read(CERT_FILE)?,
            key_der: read(KEY_FILE)?,
        })
    }

    pub fn fingerprint(&self) -> Fingerprint {
        Fingerprint::of(&self.cert_der)
    }

    pub(crate) fn cert_chain(&self) -> Vec<CertificateDer<'static>> {
        vec![CertificateDer::from(self.cert_der.clone())]
    }

    pub(crate) fn private_key(&self) -> PrivateKeyDer<'static> {
        PrivateKeyDer::Pkcs8(PrivatePkcs8KeyDer::from(self.key_der.clone()))
    }
}

/// The name in the certificate of every end, and the server name of every connection. The name proves nothing: the
/// fingerprint does.
pub(crate) const SERVER_NAME: &str = "yonder";

/// Writes a file that only its owner can read, through a temporary file beside it, so a reader never sees a part.
fn write_private(path: &Path, bytes: &[u8]) -> io::Result<()> {
    let temp = PathBuf::from(format!("{}.tmp-{}", path.display(), std::process::id()));
    let mut options = fs::OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    std::os::unix::fs::OpenOptionsExt::mode(&mut options, 0o600);
    let written = options.open(&temp).and_then(|mut file| {
        file.write_all(bytes)?;
        file.sync_all()
    });
    match written.and_then(|()| fs::rename(&temp, path)) {
        Ok(()) => Ok(()),
        Err(error) => {
            let _ = fs::remove_file(&temp);
            Err(io::Error::new(error.kind(), format!("cannot write {}: {error}", path.display())))
        }
    }
}

#[cfg(test)]
mod tests;
