//! The Bazel runtime descriptor a daemon is launched from, and the JVM @-file built out of it.
//!
//! The far end is Starlark: `plugins/air/tests/integration/air_ui_daemon_runtime.bzl` writes the document, and its
//! `_SCHEMA_VERSION` is [`SCHEMA_VERSION`]. A refusal here is a [`DescriptorError`], whose code is agent-facing.
//!
//! # Why the descriptor is validated fail-closed
//!
//! The descriptor is Bazel's half of the contract and this controller's only description of what to launch.
//! Every field it declares is either a path that will be staged or a flag that will be exec'd, so a field that is
//! absent, of the wrong type, or names a path outside the runfiles tree is a refusal here rather than a failure
//! later - later being a staged tree that looks healthy, or a JVM that has already told the controller it
//! started.

use std::collections::HashSet;
use std::fmt;
use std::path::{Path, PathBuf};

use serde::de::{self, Deserializer, MapAccess, Visitor};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::daemon::LABEL;

#[cfg(test)]
mod tests;

/// The version of the descriptor this controller reads. Unrelated to [`crate::daemon::PROTOCOL_VERSION`]: one
/// describes a build artifact, the other a running process. Bump it with `_SCHEMA_VERSION` in the `.bzl`.
pub const SCHEMA_VERSION: u32 = 5;

/// What the descriptor must declare itself to be.
///
/// Checked even though the file is read from a path this controller computed itself: the path is
/// `bazel cquery`'s answer for a label, and a rule whose implementation changed can publish a different document
/// at the same output path. Refusing an unexpected kind turns that into one message naming the label rather than
/// a stream of missing-field complaints about a file that was never this one.
pub const DESCRIPTOR_KIND: &str = "air-ui-daemon-runtime";

/// The refusals this module answers, each one a way the Bazel-owned contract can be wrong. Agent-facing.
pub mod code {
    /// Not a JSON document at all - usually a truncated write, or a Bazel action that failed after creating its
    /// output file.
    pub const NOT_JSON: &str = "daemon_runtime_descriptor_not_json";
    /// The guard that makes every other field's meaning safe to assume. A controller reading a descriptor one
    /// version ahead would find every field it knows and stage the tree wrongly.
    pub const SCHEMA_VERSION: &str = "daemon_runtime_schema_version_mismatch";
    /// A document of some other shape published at this label's output path.
    pub const KIND: &str = "daemon_runtime_kind_mismatch";
    /// A declared field that is absent or of the wrong type. One code on purpose: both mean the rule did not
    /// emit what this controller reads, and neither is actionable in a different way.
    pub const FIELD_INVALID: &str = "daemon_runtime_field_invalid";
    /// A list that is present, well-formed and empty: what a *filtered* rule emits. It stages a classpath that
    /// boots a JVM with nothing on it, whose first symptom is a NoClassDefFoundError naming the platform.
    pub const TIER_EMPTY: &str = "daemon_runtime_tier_empty";
    /// A logical path that is absolute or contains `..`. It is joined onto a runfiles root and then onto a
    /// *guest* staging root, so one that escapes is a write outside the tree this controller owns.
    pub const PATH_ESCAPES: &str = "daemon_runtime_path_escapes_runfiles";
    /// Two different files claiming one staged name. The stage would materialize whichever it reached last, and
    /// the digest would be computed over both.
    pub const DUPLICATE_LOGICAL_PATH: &str = "daemon_runtime_duplicate_logical_path";
    /// A descriptor built for a different guest; see [`super::parse_runtime_descriptor`].
    pub const PLATFORM_MISMATCH: &str = "daemon_runtime_platform_mismatch";
    /// A JVM flag that still names the runfiles tree at the point where it is written into an @-file. The host
    /// tree is not mounted in the guest, and the flag would silently name nothing.
    pub const FLAG_UNRESOLVED: &str = "daemon_runtime_flag_unresolved";
    /// A staged classpath that does not correspond to the descriptor's, entry for entry.
    pub const DAEMON_STAGE_INVALID: &str = "daemon_runtime_stage_invalid";
}

/// The token the descriptor's flags carry where the host runfiles root belongs.
const RUNFILES_ROOT_TOKEN: &str = "${RUNFILES_ROOT}";

/// Why a descriptor, or the @-file built out of one, is refused.
///
/// `code` is one of [`code`], agent-facing. Every one of them is the far end of the contract being unreadable, so
/// a caller retries none of them.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct DescriptorError {
    pub code: &'static str,
    pub message: String,
}

impl DescriptorError {
    fn new(code: &'static str, message: impl Into<String>) -> Self {
        Self {
            code,
            message: message.into(),
        }
    }
}

impl fmt::Display for DescriptorError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl std::error::Error for DescriptorError {}

// --- the descriptor -------------------------------------------------------------------------------------------

/// One runfile the descriptor declares.
///
/// `exec_path` is where Bazel put it and `logical_path` is what it is called inside the runfiles tree - and the
/// second is the one that matters, because it is also the name the file is staged under on the guest. `owner` is
/// the label that produced it, and exists only to make a refusal name the rule a human has to go fix.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeFile {
    pub exec_path: String,
    pub logical_path: String,
    pub owner: String,
}

/// The daemon's classpath split by how often it changes.
///
/// `hot` is the test code, which changes every iteration and travels to the guest as individual jars keyed by
/// content digest. `stable` is everything else, staged as one generation. The split is the whole reason a warm
/// iteration is seconds rather than minutes, and it is declared by Bazel rather than guessed here.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct ClasspathTiers {
    pub hot: Vec<RuntimeFile>,
    pub stable: Vec<RuntimeFile>,
}

/// The share-backed IDE distribution: the two documents that name it, and the tree itself.
///
/// `fingerprint` is the whole identity of the distribution: the composer derives it from the component
/// manifests, the plugin classpath, the core classpath and the launch metadata.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct DevDist {
    pub config: RuntimeFile,
    pub fingerprint: RuntimeFile,
    pub home: RuntimeFile,
}

/// The runtime the guest unpacks, and the policy that says how.
///
/// `preloaded_only` says the guest may use only a JBR its image already holds. It is required rather than
/// defaulted: on this field the difference between "false" and "not stated" is a guest that downloads a runtime
/// on a worker that has no network.
#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Jbr {
    pub archive: RuntimeFile,
    /// Empty is legal: a Linux JBR's java is directly under the unpacked root.
    pub java_home_suffix: String,
    pub manifest: RuntimeFile,
    pub platform: String,
    pub preloaded_only: bool,
}

/// The Bazel-owned launch contract, after validation.
///
/// Unrelated to [`crate::stage::RuntimeManifest`] despite the overlap in vocabulary: this is what the *host
/// build* produced, and a manifest is what the *host asks the guest to materialize*. Only this one carries host
/// paths. It only ever exists as the answer of [`parse_runtime_descriptor`], which is why it has no `Deserialize`
/// of its own: a caller cannot decode one and skip the checks.
#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RuntimeDescriptor {
    /// Checked before the typed decode (a `5.0` is version 5), and set from the constant afterwards.
    pub schema_version: u32,
    pub kind: String,
    pub main_class: String,
    pub static_jvm_flags: Vec<String>,
    pub classpath: ClasspathTiers,
    pub dev_dist: DevDist,
    pub jbr: Jbr,
    pub data: Vec<RuntimeFile>,
}

/// The typed decode of a descriptor, before the semantic checks. Every section is an object or it is refused,
/// for the reason [`RuntimeFile`] spells out.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct DescriptorFields {
    #[serde(deserialize_with = "non_empty")]
    main_class: String,
    static_jvm_flags: Vec<String>,
    #[serde(deserialize_with = "object_only")]
    classpath: ClasspathTiers,
    #[serde(deserialize_with = "object_only")]
    dev_dist: DevDist,
    #[serde(deserialize_with = "object_only")]
    jbr: Jbr,
    data: Vec<RuntimeFile>,
}

/// Decodes `T` from a JSON object only; a derived struct would also accept an array positionally.
fn object_only<'de, D: Deserializer<'de>, T: Deserialize<'de>>(deserializer: D) -> Result<T, D::Error> {
    struct ObjectOnly<T>(std::marker::PhantomData<T>);

    impl<'de, T: Deserialize<'de>> Visitor<'de> for ObjectOnly<T> {
        type Value = T;

        fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
            formatter.write_str("an object")
        }

        fn visit_map<A: MapAccess<'de>>(self, map: A) -> Result<T, A::Error> {
            T::deserialize(de::value::MapAccessDeserializer::new(map))
        }
    }

    deserializer.deserialize_map(ObjectOnly(std::marker::PhantomData))
}

// Written by hand for one reason: a derived struct also accepts a JSON *array* positionally, so `["a","b","c"]`
// would decode as a file. A declared file is an object or it is refused.
impl<'de> Deserialize<'de> for RuntimeFile {
    fn deserialize<D: Deserializer<'de>>(deserializer: D) -> Result<Self, D::Error> {
        #[derive(Deserialize)]
        #[serde(rename_all = "camelCase")]
        struct Fields {
            #[serde(deserialize_with = "non_empty")]
            exec_path: String,
            #[serde(deserialize_with = "non_empty")]
            logical_path: String,
            #[serde(deserialize_with = "non_empty")]
            owner: String,
        }

        struct ObjectOnly;

        impl<'de> Visitor<'de> for ObjectOnly {
            type Value = RuntimeFile;

            fn expecting(&self, formatter: &mut fmt::Formatter<'_>) -> fmt::Result {
                formatter.write_str("a runtime file object")
            }

            fn visit_map<A: MapAccess<'de>>(self, map: A) -> Result<RuntimeFile, A::Error> {
                let Fields {
                    exec_path,
                    logical_path,
                    owner,
                } = Fields::deserialize(de::value::MapAccessDeserializer::new(map))?;
                Ok(RuntimeFile {
                    exec_path,
                    logical_path,
                    owner,
                })
            }
        }

        deserializer.deserialize_map(ObjectOnly)
    }
}

/// A string that must say something: an empty main class execs a JVM with no argv tail, and an empty path or
/// owner names nothing.
fn non_empty<'de, D: Deserializer<'de>>(deserializer: D) -> Result<String, D::Error> {
    let value = String::deserialize(deserializer)?;
    if value.is_empty() {
        return Err(de::Error::custom("is an empty string"));
    }
    Ok(value)
}

fn descriptor_error(code: &'static str, descriptor_path: &str, detail: &str) -> DescriptorError {
    DescriptorError::new(
        code,
        format!("invalid Air daemon runtime descriptor {descriptor_path}: {detail}; rebuild {LABEL}"),
    )
}

/// Reads and fail-closed validates the Bazel-owned daemon launch contract.
///
/// `expected_jbr_platform` is the *guest's*, and checking it here is what keeps the wrong configuration from being
/// staged silently. A darwin descriptor handed to a Linux worker declares `Contents/Home` as its Java-home suffix,
/// and that path does exist inside the macOS tarball, so staging succeeds and the first complaint is an
/// exec-format error from a daemon that has already been told it started.
///
/// The version and the kind are read before the typed decode, so a document of another vintage is named as such
/// rather than as a list of fields it lacks.
pub fn parse_runtime_descriptor(
    content: &[u8],
    descriptor_path: &Path,
    expected_jbr_platform: &str,
) -> Result<RuntimeDescriptor, DescriptorError> {
    let shown = descriptor_path.display().to_string();
    let fail = |code: &'static str, detail: &str| descriptor_error(code, &shown, detail);

    let document: Value = serde_json::from_slice(content).map_err(|error| fail(code::NOT_JSON, &format!("not JSON ({error})")))?;
    // A number literal, compared as a number: `5.0` is version 5, and `"5"` is not a version at all.
    let version = document.get("schemaVersion");
    if version.and_then(Value::as_f64) != Some(f64::from(SCHEMA_VERSION)) {
        return Err(fail(
            code::SCHEMA_VERSION,
            &format!(
                "schemaVersion is {}, expected {SCHEMA_VERSION}",
                version.map_or_else(|| "absent".to_owned(), Value::to_string)
            ),
        ));
    }
    let kind = document.get("kind");
    if kind.and_then(Value::as_str) != Some(DESCRIPTOR_KIND) {
        return Err(fail(
            code::KIND,
            &format!("kind is {}", kind.map_or_else(|| "absent".to_owned(), Value::to_string)),
        ));
    }

    let fields: DescriptorFields = serde_path_to_error::deserialize(&document).map_err(|error| {
        let path = error.path().to_string();
        let detail = if path == "." {
            error.inner().to_string()
        } else {
            format!("{path}: {}", error.inner())
        };
        fail(code::FIELD_INVALID, &detail)
    })?;
    let descriptor = RuntimeDescriptor {
        schema_version: SCHEMA_VERSION,
        kind: DESCRIPTOR_KIND.to_owned(),
        main_class: fields.main_class,
        static_jvm_flags: fields.static_jvm_flags,
        classpath: fields.classpath,
        dev_dist: fields.dev_dist,
        jbr: fields.jbr,
        data: fields.data,
    };

    if descriptor.classpath.hot.is_empty() || descriptor.classpath.stable.is_empty() {
        return Err(fail(code::TIER_EMPTY, "both classpath tiers must be non-empty"));
    }
    if descriptor.jbr.platform != expected_jbr_platform {
        // Not phrased as an invalid descriptor: this document is well-formed and internally consistent, and the
        // mistake was made one level up, by a build that did not select the guest's configuration.
        return Err(DescriptorError::new(
            code::PLATFORM_MISMATCH,
            format!(
                "{shown} declares a {} JBR and distribution, but this worker runs a {expected_jbr_platform} \
                 guest; the host build did not select the guest's configuration",
                descriptor.jbr.platform
            ),
        ));
    }
    if let Some(field) = labelled_files(&descriptor)
        .into_iter()
        .find_map(|(field, file)| escapes(&file.logical_path).then_some(field))
    {
        return Err(fail(code::PATH_ESCAPES, &format!("{field}.logicalPath escapes the runfiles root")));
    }

    // The order of this list decides which duplicate a message names when there are two.
    let staged = descriptor
        .classpath
        .hot
        .iter()
        .chain(&descriptor.classpath.stable)
        .chain(&descriptor.data)
        .chain([
            &descriptor.dev_dist.config,
            &descriptor.dev_dist.fingerprint,
            &descriptor.dev_dist.home,
            &descriptor.jbr.archive,
            &descriptor.jbr.manifest,
        ]);
    if let Some(duplicate) = first_duplicate(staged) {
        return Err(fail(code::DUPLICATE_LOGICAL_PATH, &format!("duplicate logical path {duplicate}")));
    }
    Ok(descriptor)
}

/// Every declared file with the field that declared it, for a refusal that names the field.
fn labelled_files(descriptor: &RuntimeDescriptor) -> Vec<(String, &RuntimeFile)> {
    fn listed_in<'a>(name: &str, files: &'a [RuntimeFile]) -> Vec<(String, &'a RuntimeFile)> {
        files
            .iter()
            .enumerate()
            .map(|(index, file)| (format!("{name}[{index}]"), file))
            .collect()
    }
    let mut labelled = listed_in("classpath.hot", &descriptor.classpath.hot);
    labelled.extend(listed_in("classpath.stable", &descriptor.classpath.stable));
    labelled.extend(listed_in("data", &descriptor.data));
    labelled.extend([
        ("devDist.config".to_owned(), &descriptor.dev_dist.config),
        ("devDist.fingerprint".to_owned(), &descriptor.dev_dist.fingerprint),
        ("devDist.home".to_owned(), &descriptor.dev_dist.home),
        ("jbr.archive".to_owned(), &descriptor.jbr.archive),
        ("jbr.manifest".to_owned(), &descriptor.jbr.manifest),
    ]);
    labelled
}

/// Checked as components rather than with a substring search: a jar legitimately called `foo..bar.jar` contains
/// `..` and escapes nothing, and refusing it would be a build nobody could fix.
fn escapes(logical_path: &str) -> bool {
    logical_path.starts_with('/') || logical_path.split('/').any(|component| component == "..")
}

fn first_duplicate<'a>(files: impl IntoIterator<Item = &'a RuntimeFile>) -> Option<&'a str> {
    let mut seen = HashSet::new();
    files
        .into_iter()
        .map(|file| file.logical_path.as_str())
        .find(|logical_path| !seen.insert(*logical_path))
}

/// Where the runfiles tree of a descriptor sits: `<descriptor>.runfiles`.
///
/// Derived from the descriptor's own path rather than from `RUNFILES_DIR`: this controller spawns the daemon
/// itself instead of going through a `bazel run` launcher, so no runfiles environment exists to read.
pub fn runfiles_root(descriptor_path: &Path) -> PathBuf {
    let mut root = descriptor_path.as_os_str().to_owned();
    root.push(".runfiles");
    PathBuf::from(root)
}

/// Where one declared runfile actually is on the host.
pub fn runtime_file_path(runfiles_root: &Path, file: &RuntimeFile) -> PathBuf {
    runfiles_root.join(&file.logical_path)
}

// --- the daemon @-file ----------------------------------------------------------------------------------------

/// What a lane contributes to the daemon's own @-file.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct LaunchOptions {
    /// The guest-local directory the JVM writes temporaries into. Named explicitly because the default is the
    /// guest's shared `/tmp`, and two workers on one host would then collide there.
    pub test_tmp_dir: String,
    pub extra_flags: Vec<String>,
}

/// Every @-file token that precedes the classpath: the tmpdir, the descriptor's static flags with their runfiles
/// root substituted, and the lane's own.
///
/// These are exactly the tokens that cross to the guest in `stage::ArgFileRequest::prefix` - the guest
/// appends `-cp` and the main class and renders the file itself. [`daemon_launch_arg_file`] is built from this
/// rather than restating it: a second spelling of this order would be a launch whose digest the controller
/// computed over tokens it did not send.
pub fn daemon_launch_prefix(
    descriptor: &RuntimeDescriptor,
    runfiles_root: &Path,
    options: &LaunchOptions,
) -> Result<Vec<String>, DescriptorError> {
    let unresolved = |detail: &str| descriptor_error(code::FLAG_UNRESOLVED, "<loaded>", detail);
    let root = runfiles_root
        .to_str()
        .ok_or_else(|| unresolved("the runfiles root is not UTF-8, so no JVM flag can name it"))?;
    let mut prefix = Vec::with_capacity(descriptor.static_jvm_flags.len() + options.extra_flags.len() + 1);
    prefix.push(format!("-Djava.io.tmpdir={}", options.test_tmp_dir));
    for flag in &descriptor.static_jvm_flags {
        let resolved = flag.replace(RUNFILES_ROOT_TOKEN, root);
        // After the substitution, so the only way to trip it is a runfiles root that itself names the token - not
        // a paranoid case: the root is derived from a descriptor path a caller supplied, and shipping the literal
        // token to a JVM is a flag that names a directory nothing created.
        if resolved.contains(RUNFILES_ROOT_TOKEN) {
            return Err(unresolved("a JVM flag still contains an unresolved RUNFILES_ROOT token"));
        }
        prefix.push(resolved);
    }
    prefix.extend(options.extra_flags.iter().cloned());
    Ok(prefix)
}

/// The exact JVM @-file of the daemon, from the declared flags and the staged, guest-local stable classpath.
///
/// Still built in full even though the guest writes the file: these bytes are what the digest the guest's reply
/// is held against is taken over (`stage::ArgFileRequest::sha256`).
pub fn daemon_launch_arg_file(
    descriptor: &RuntimeDescriptor,
    runfiles_root: &Path,
    stable_classpath: &[String],
    options: &LaunchOptions,
) -> Result<String, DescriptorError> {
    // Counted rather than trusted: the staged list comes back from the guest, and a stage that materialized fewer
    // jars than it was asked for would produce a classpath that boots and then fails to find a class, which reads
    // as a test failure rather than as a broken stage.
    if stable_classpath.len() != descriptor.classpath.stable.len() {
        return Err(DescriptorError::new(
            code::DAEMON_STAGE_INVALID,
            format!(
                "staged classpath has {} entries, expected {}",
                stable_classpath.len(),
                descriptor.classpath.stable.len()
            ),
        ));
    }
    let mut tokens = daemon_launch_prefix(descriptor, runfiles_root, options)?;
    tokens.push("-cp".to_owned());
    tokens.push(stable_classpath.join(":"));
    tokens.push(descriptor.main_class.clone());
    Ok(crate::stage::arg_file_text(&tokens))
}
