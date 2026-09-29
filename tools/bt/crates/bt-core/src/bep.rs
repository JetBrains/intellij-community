//! The Build Event Protocol reader.
//!
//! Verified shapes (bazel 8, this repo): a `testResult` event carries {label, run, shard, attempt} in its id and
//! status + testActionOutput[{name, uri}] in its payload; test.xml/test.log URIs are absolute execroot paths that
//! out/bazel-testlogs symlinks to. A cache hit still emits a `testResult` with cachedLocally:true and URIs whose
//! files exist.
//!
//! `testSummary.totalRunCount` counts bazel RUNS, not JUnit test cases, so it can never be used as a test count.
//! Reading it as one is the false green this whole reader exists to prevent: a lane of 63 targets would report
//! "63 tests" and pass with every assertion in the repository unexecuted. The only source of a test count is
//! test.xml, which is why [`crate::result::collect_results`] reads the files rather than the summary.

use std::collections::BTreeMap;

use serde::Deserialize;
use serde::de::{DeserializeOwned, Deserializer};
use serde_json::{Map, Value};

use crate::paths::has_windows_drive;
use crate::runtime::Platform;

/// One test attempt as bazel reported it.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct BepAttempt {
    pub label: String,
    pub run: i64,
    pub shard: i64,
    pub attempt: i64,
    pub status: String,
    pub cached: bool,
    /// `xml_path` and `log_path` are absent when bazel reported no such artifact, which happens when the action
    /// never produced one: a distinct case from an artifact whose file is missing, and the two take different
    /// branches in [`crate::result::collect_results`].
    pub xml_path: Option<String>,
    pub log_path: Option<String>,
    pub duration_ms: u64,
}

/// Everything read out of one BEP file.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct BepSummary {
    pub attempts: Vec<BepAttempt>,
    pub targets: BTreeMap<String, String>,
    pub failed_to_build: Vec<String>,
    pub aborted: Vec<String>,
    pub action_stderr: Vec<String>,
    /// Bazel's own verdict, and `None` when the build never finished, which is exactly the case where a false
    /// `false` would look like an ordinary failure instead of a killed run.
    pub overall_success: Option<bool>,
    /// Distinguishes an empty file from a file that was never written. Both mean the results are not usable, and
    /// neither may be read as green.
    pub saw_any_event: bool,
}

/// A `file://` URI as a path in the platform's own dialect; anything else is left alone.
///
/// A malformed escape is left as it was: a path is still more useful than nothing, and the caller only ever prints
/// it or opens it.
pub(crate) fn uri_to_path(uri: &str, platform: Platform) -> String {
    let Some(rest) = uri.strip_prefix("file://") else {
        return uri.to_owned();
    };
    let decoded = percent_encoding::percent_decode_str(rest)
        .decode_utf8_lossy()
        .into_owned();
    match platform {
        Platform::Windows => windows_file_uri_path(&decoded),
        Platform::Darwin | Platform::Linux => decoded,
    }
}

/// The path part of a Windows `file://` URI: `/C:/x` is a drive path, `localhost/C:/x` the same one spelled with
/// a host, and `server/share/x` a UNC share.
fn windows_file_uri_path(path: &str) -> String {
    let path = path
        .strip_prefix("localhost")
        .filter(|rest| rest.starts_with('/'))
        .unwrap_or(path);
    let path = match path.strip_prefix('/') {
        Some(rest) if has_windows_drive(rest) => rest,
        _ => path,
    };
    if has_windows_drive(path) {
        return path.replace('/', "\\");
    }
    if !path.is_empty() && !path.starts_with('/') {
        return format!("//{path}").replace('/', "\\");
    }
    path.to_owned()
}

/// An integer that proto3 JSON may quote.
///
/// Both spellings are needed: proto3 encodes an int64 field as a *string*, so `testAttemptDurationMillis` arrives
/// quoted from a real bazel while the int32 ids arrive bare. Reading only one of the two would silently zero every
/// duration and make the cost line report "slowest" as whichever target came first.
#[expect(
    clippy::cast_possible_truncation,
    reason = "a float spelling of an integer field keeps its whole part; `as` saturates out-of-range values"
)]
fn lenient_integer<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Option<i64>, D::Error> {
    Ok(match Value::deserialize(deserializer)? {
        Value::Number(number) => number
            .as_i64()
            .or_else(|| number.as_f64().map(|float| float as i64)),
        Value::String(text) => {
            let text = text.trim();
            text.parse::<i64>()
                .ok()
                .or_else(|| text.parse::<f64>().ok().map(|float| float as i64))
        }
        _ => None,
    })
}

/// A flag that is `true` only when it says so. proto3 omits a false bool, so absence is `false`, and so is any
/// value that is not a bool.
fn is_true<'de, D: Deserializer<'de>>(deserializer: D) -> Result<bool, D::Error> {
    Ok(matches!(
        Value::deserialize(deserializer)?,
        Value::Bool(true)
    ))
}

/// A string that is absent when it is not a string.
fn lenient_string<'de, D: Deserializer<'de>>(deserializer: D) -> Result<Option<String>, D::Error> {
    Ok(match Value::deserialize(deserializer)? {
        Value::String(text) => Some(text),
        _ => None,
    })
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct Labelled {
    #[serde(deserialize_with = "lenient_string")]
    label: Option<String>,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct TestResultId {
    #[serde(deserialize_with = "lenient_string")]
    label: Option<String>,
    #[serde(deserialize_with = "lenient_integer")]
    run: Option<i64>,
    #[serde(deserialize_with = "lenient_integer")]
    shard: Option<i64>,
    #[serde(deserialize_with = "lenient_integer")]
    attempt: Option<i64>,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "camelCase")]
struct TestResultPayload {
    #[serde(deserialize_with = "lenient_string")]
    status: Option<String>,
    #[serde(deserialize_with = "is_true")]
    cached_locally: bool,
    execution_info: Option<Value>,
    #[serde(deserialize_with = "lenient_integer")]
    test_attempt_duration_millis: Option<i64>,
    test_action_output: Option<Value>,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "camelCase")]
struct TestSummaryPayload {
    #[serde(deserialize_with = "lenient_string")]
    overall_status: Option<String>,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct Success {
    #[serde(deserialize_with = "is_true")]
    success: bool,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct Aborted {
    #[serde(deserialize_with = "lenient_string")]
    reason: Option<String>,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct Action {
    #[serde(deserialize_with = "is_true")]
    success: bool,
    stderr: Option<Value>,
}

#[derive(Deserialize, Default)]
#[serde(default, rename_all = "camelCase")]
struct Finished {
    #[serde(deserialize_with = "is_true")]
    overall_success: bool,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct Uri {
    #[serde(deserialize_with = "lenient_string")]
    uri: Option<String>,
}

#[derive(Deserialize, Default)]
#[serde(default)]
struct ActionOutput {
    #[serde(deserialize_with = "lenient_string")]
    name: Option<String>,
    #[serde(deserialize_with = "lenient_string")]
    uri: Option<String>,
}

/// The member `key` of an event, decoded when it is an object. A member of any other shape is treated as absent,
/// so one odd field costs that event's reading, never the whole file's.
fn object<T: DeserializeOwned>(event: &Map<String, Value>, key: &str) -> Option<T> {
    match event.get(key)? {
        value @ Value::Object(_) => T::deserialize(value).ok(),
        _ => None,
    }
}

fn has_object(event: &Map<String, Value>, key: &str) -> bool {
    matches!(event.get(key), Some(Value::Object(_)))
}

/// Reads a BEP NDJSON stream.
///
/// Told its platform rather than reading the host's, because bazel writes an artifact URI in the dialect of the
/// host it ran on and this reader must answer for either one from either one. That keeps the suite hermetic: the
/// fake declares the platform, so a case reads the same on every machine.
pub fn parse_bep(lines: impl IntoIterator<Item = String>, platform: Platform) -> BepSummary {
    let mut summary = BepSummary::default();
    let empty = Map::new();
    for line in lines {
        let trimmed = line.trim();
        if trimmed.is_empty() {
            continue;
        }
        // A truncated final line is expected when bazel is killed mid-write, and it says nothing about the events
        // that were written whole. Skipping it is the difference between reporting the run and reporting
        // infrastructure.
        let Ok(event) = serde_json::from_str::<Map<String, Value>>(trimmed) else {
            continue;
        };
        summary.saw_any_event = true;
        let id = match event.get("id") {
            Some(Value::Object(id)) => id,
            _ => &empty,
        };

        // Bazel announces an event by its id alone before it sends it; only an id with its payload is a result.
        if let (Some(identity), Some(payload)) = (
            object::<TestResultId>(id, "testResult"),
            object::<TestResultPayload>(&event, "testResult"),
        ) {
            summary
                .attempts
                .push(read_attempt(identity, payload, platform));
            continue;
        }

        if let (Some(identity), Some(payload)) = (
            object::<Labelled>(id, "testSummary"),
            object::<TestSummaryPayload>(&event, "testSummary"),
        ) {
            summary.targets.insert(
                identity.label.unwrap_or_default(),
                payload.overall_status.unwrap_or_else(no_status),
            );
            continue;
        }

        // proto3 omits a false bool, so failure is "not explicitly true", never `success == false`.
        if let (Some(identity), Some(completed)) = (
            object::<Labelled>(id, "targetCompleted"),
            object::<Success>(&event, "completed"),
        ) && !completed.success
        {
            summary
                .failed_to_build
                .push(identity.label.unwrap_or_default());
            continue;
        }

        if let Some(aborted) = object::<Aborted>(&event, "aborted") {
            summary
                .aborted
                .push(aborted.reason.unwrap_or_else(|| "UNKNOWN".to_owned()));
            continue;
        }

        if has_object(id, "actionCompleted")
            && let Some(action) = object::<Action>(&event, "action")
            && !action.success
        {
            // Best effort only: bazel deletes bazel-out/_tmp/actions/stderr-* as the build finishes, so the captured
            // bazel output is the reliable source for compiler diagnostics.
            if let Some(uri) = action
                .stderr
                .and_then(|stderr| Uri::deserialize(stderr).ok())
                .and_then(|stderr| stderr.uri)
            {
                summary.action_stderr.push(uri_to_path(&uri, platform));
            }
            continue;
        }

        if has_object(id, "buildFinished")
            && let Some(finished) = object::<Finished>(&event, "finished")
        {
            summary.overall_success = Some(finished.overall_success);
        }
    }
    summary
}

fn no_status() -> String {
    "NO_STATUS".to_owned()
}

fn read_attempt(
    identity: TestResultId,
    payload: TestResultPayload,
    platform: Platform,
) -> BepAttempt {
    let outputs: Vec<Value> = match payload.test_action_output {
        Some(Value::Array(outputs)) => outputs,
        _ => Vec::new(),
    };
    let find = |name: &str| {
        outputs
            .iter()
            .filter_map(|output| match output {
                Value::Object(_) => ActionOutput::deserialize(output).ok(),
                _ => None,
            })
            .find(|output| output.name.as_deref() == Some(name))
            .and_then(|output| output.uri)
            .map(|uri| uri_to_path(&uri, platform))
    };
    let cached_remotely = || {
        #[derive(Deserialize, Default)]
        #[serde(default, rename_all = "camelCase")]
        struct ExecutionInfo {
            #[serde(deserialize_with = "is_true")]
            cached_remotely: bool,
        }
        match &payload.execution_info {
            Some(info @ Value::Object(_)) => {
                ExecutionInfo::deserialize(info).is_ok_and(|info| info.cached_remotely)
            }
            _ => false,
        }
    };
    BepAttempt {
        label: identity.label.unwrap_or_default(),
        run: identity.run.unwrap_or(1),
        shard: identity.shard.unwrap_or(1),
        attempt: identity.attempt.unwrap_or(1),
        status: payload.status.clone().unwrap_or_else(no_status),
        cached: payload.cached_locally || cached_remotely(),
        xml_path: find("test.xml"),
        log_path: find("test.log"),
        duration_ms: payload
            .test_attempt_duration_millis
            .map_or(0, |millis| u64::try_from(millis).unwrap_or(0)),
    }
}

#[cfg(test)]
mod tests;
