use std::collections::BTreeSet;
use std::fs;
use std::path::Path;

use avl_testkit::traces::{TRANSCRIPT, example_bundle, example_file, lines};
use pretty_assertions::assert_eq;
use serde_json::{Value, json};

use super::*;
use crate::bridge::decode_tree;
use crate::otlp::{LogRecord, attr, decode_logs_line, event, lookup_int, lookup_str, span_id};
use crate::protocol::{Command, SnapCommand};
use crate::testdata::{decode_transcript, example_manifest};

#[test]
fn the_example_manifest_describes_the_transcript_it_came_from() {
    let manifest = example_manifest();
    let commands = decode_transcript(TRANSCRIPT);
    let (Command::Hello(hello), Command::Scenario(scenario), Some(Command::Done(done))) = (&commands[0], &commands[1], commands.last())
    else {
        panic!("the transcript does not run from hello and scenario to done");
    };
    assert_eq!(
        (manifest.run_id.as_str(), manifest.launcher),
        (hello.run_id.as_str(), hello.launcher)
    );
    assert_eq!(manifest.test_class, scenario.test_class);
    assert_eq!(manifest.scenario, scenario.name);
    assert_eq!(manifest.flow, scenario.flow);
    assert_eq!(manifest.lane, scenario.lane);
    assert_eq!(manifest.status, BundleStatus::from(done.status));
    // The bundle has no video on purpose, and says so rather than leaving the viewer to guess.
    assert_eq!(
        manifest.video,
        Video::None {
            reason: "fixture".to_owned()
        }
    );
}

/// The recorder writes the golden manifest with this encoder, so the encoder and the decoder are held to one file.
#[test]
fn the_golden_manifest_is_what_the_encoder_writes() {
    let encoded = encode_manifest(&example_manifest()).unwrap();
    assert_eq!(
        String::from_utf8(encoded).unwrap(),
        String::from_utf8(example_file(MANIFEST_FILE)).unwrap()
    );
    let mut truncated = example_manifest();
    truncated.status = BundleStatus::Truncated;
    assert!(
        encode_manifest(&truncated).is_err(),
        "a truncated manifest without its running span is encoded"
    );
}

#[test]
fn a_bundle_file_is_joined_one_segment_at_a_time() {
    let dir = Path::new("root").join("bundle");
    assert_eq!(bundle_file(&dir, &snap_tree_path(3)), dir.join(SNAP_DIR).join("0003.tree.json"));
    assert_eq!(bundle_file(&dir, MANIFEST_FILE), dir.join(MANIFEST_FILE));
}

fn valid_manifest() -> Value {
    json!({
        "schema": MANIFEST_SCHEMA, "runId": "run", "testClass": "AirExampleUiTest", "scenario": "example",
        "lane": "UI", "launcher": "bazel", "status": "passed", "startedAt": "2026-09-23T10:15:00.120Z",
        "durationMs": 1, "capture": {"source": "x11"},
        "video": {"codec": "h264", "file": VIDEO_FILE, "index": VIDEO_INDEX_FILE},
    })
}

fn decode(manifest: &Value) -> Result<Manifest, Error> {
    decode_manifest(&serde_json::to_vec(manifest).unwrap())
}

#[test]
fn manifest_validation_refuses_what_a_recorder_would_not_write() {
    let valid = decode(&valid_manifest()).expect("the valid manifest decodes");
    assert_eq!(serde_json::to_value(&valid).unwrap(), valid_manifest(), "the manifest round-trips");
    let mut truncated = valid_manifest();
    truncated["status"] = json!("truncated");
    truncated["runningSpan"] = json!(span_id(7));
    decode(&truncated).expect("a truncated manifest naming its running span decodes");

    type Mutation = fn(&mut Value);
    let refused: [(&str, Mutation); 17] = [
        ("another schema", |manifest| {
            manifest["schema"] = json!("air-trace/2");
        }),
        ("no scenario", |manifest| manifest["scenario"] = json!("")),
        ("an unknown launcher", |manifest| {
            manifest["launcher"] = json!("gradle");
        }),
        ("an unknown status", |manifest| {
            manifest["status"] = json!("running");
        }),
        ("truncated without a span", |manifest| {
            manifest["status"] = json!("truncated");
        }),
        ("a running span when it passed", |manifest| {
            manifest["runningSpan"] = json!(span_id(3));
        }),
        ("a running span that is no id", |manifest| {
            manifest["status"] = json!("truncated");
            manifest["runningSpan"] = json!("7");
        }),
        ("a start that is no time", |manifest| {
            manifest["startedAt"] = json!("23.09.2026 10:15");
        }),
        ("a negative duration", |manifest| {
            manifest["durationMs"] = json!(-1);
        }),
        ("an unknown capture", |manifest| {
            manifest["capture"]["source"] = json!("vnc");
        }),
        ("no capture and no reason", |manifest| {
            manifest["capture"] = json!({"source": "none"});
        }),
        ("a video without its file", |manifest| {
            manifest["video"] = json!({"codec": "h264", "index": VIDEO_INDEX_FILE});
        }),
        ("a video under another name", |manifest| {
            manifest["video"]["index"] = json!("frames.json");
        }),
        ("no video and no reason", |manifest| {
            manifest["video"] = json!({"codec": "none"});
        }),
        ("no video that names a file", |manifest| {
            manifest["video"] = json!({"codec": "none", "reason": "off", "file": VIDEO_FILE});
        }),
        ("an unknown codec", |manifest| {
            manifest["video"]["codec"] = json!("av1");
        }),
        ("an unknown field", |manifest| {
            manifest["snapshots"] = json!(17);
        }),
    ];
    for (label, mutate) in refused {
        let mut manifest = valid_manifest();
        mutate(&mut manifest);
        assert!(decode(&manifest).is_err(), "a manifest with {label} was accepted");
    }
}

#[test]
fn format_manifest_time_writes_utc_milliseconds() {
    let instant: jiff::Timestamp = "2026-09-23T12:15:00.12+02:00".parse().unwrap();
    assert_eq!(format_manifest_time(instant), "2026-09-23T10:15:00.120Z");
    assert_eq!(
        format_manifest_time(jiff::Timestamp::from_millisecond(0).unwrap()),
        "1970-01-01T00:00:00.000Z"
    );
}

#[test]
fn sanitize_name_keeps_the_portable_characters_only() {
    let long = "a".repeat(200);
    let cases = [
        ("rename-closed-project-session", "rename-closed-project-session"),
        ("AirRenameSessionGeneratedFlowUiTest", "AirRenameSessionGeneratedFlowUiTest"),
        ("com.intellij.air.AirExampleUiTest", "com.intellij.air.AirExampleUiTest"),
        (
            "iter-6f1c2a9e-3b4d-4f7a-9c1e-2d8b5a7e0c41-3",
            "iter-6f1c2a9e-3b4d-4f7a-9c1e-2d8b5a7e0c41-3",
        ),
        ("send a prompt / wait: reply", "send_a_prompt___wait__reply"),
        ("..", "_"),
        ("../escape", "_escape"),
        (".hidden", "hidden"),
        ("", "_"),
        ("Überprüfung", "_berpr_fung"),
        (long.as_str(), &long[..120]),
    ];
    for (name, expected) in cases {
        assert_eq!(sanitize_name(name), expected, "sanitize_name({name:?})");
    }
}

#[test]
fn bundle_locations() {
    let dir = bundle_dir(Path::new("/traces"), "iter-1", "AirExampleUiTest", "a/b");
    assert_eq!(dir, Path::new("/traces").join("iter-1").join("AirExampleUiTest").join("a_b"));
    assert_eq!(bundle_path("iter-1", "AirExampleUiTest", "a/b"), "iter-1/AirExampleUiTest/a_b");
    assert_eq!(
        (snap_image_path(3).as_str(), snap_tree_path(12).as_str()),
        ("snap/0003.webp", "snap/0012.tree.json")
    );
}

#[test]
fn video_index_decodes_and_refuses_disorder() {
    let index = decode_video_index(br#"{"frameRate":10,"framesMs":[1000,1100,1200]}"#).unwrap();
    assert_eq!(
        index,
        VideoIndex {
            frame_rate: 10,
            frames_ms: vec![1000, 1100, 1200]
        }
    );
    for document in [
        r#"{"frameRate":0,"framesMs":[]}"#,
        r#"{"frameRate":10,"framesMs":[1100,1000]}"#,
        r#"{"frameRate":10,"framesMs":[],"codec":"h264"}"#,
    ] {
        assert!(
            decode_video_index(document.as_bytes()).is_err(),
            "the index {document} was accepted"
        );
    }
}

/// The example's snapshot records in the order they were written.
fn snapshot_records() -> Vec<LogRecord> {
    lines(&example_bundle().join(LOGS_FILE))
        .iter()
        .flat_map(|line| decode_logs_line(line).unwrap().resource_logs)
        .flat_map(|resource| resource.scope_logs)
        .flat_map(|scope| scope.log_records)
        .filter(|record| record.event_name == event::SNAPSHOT)
        .collect()
}

/// Every snapshot the transcript asks for has a record, in order, with its phase, and every record names a
/// lossless WebP and a tree that are in the bundle. A viewer built against this bundle can therefore rely on each
/// of those.
///
/// A snapshot of an unchanged screen names the previous picture's file, and `snap/` holds only the files some
/// record names. The example has such repeats, so the viewer's tests see two snapshots share one picture.
#[test]
fn every_snapshot_has_its_picture_and_its_tree() {
    let snaps: Vec<SnapCommand> = decode_transcript(TRANSCRIPT)
        .into_iter()
        .filter_map(|command| match command {
            Command::Snap(snap) => Some(snap),
            _ => None,
        })
        .collect();
    let records = snapshot_records();
    assert_eq!(records.len(), snaps.len(), "one snapshot record per snap command");
    let mut named = BTreeSet::new();
    let mut previous = String::new();
    let mut repeats = 0;
    for (index, (record, snap)) in records.iter().zip(&snaps).enumerate() {
        let ordinal = u32::try_from(index + 1).unwrap();
        assert_eq!(lookup_int(&record.attributes, attr::SNAPSHOT_ORDINAL), Some(i64::from(ordinal)));
        assert_eq!(
            lookup_str(&record.attributes, attr::SNAPSHOT_PHASE),
            Some(snap.phase.as_str()),
            "snapshot {ordinal}"
        );
        assert_eq!(record.span_id, span_id(snap.span), "snapshot {ordinal}");
        let image = lookup_str(&record.attributes, attr::SNAPSHOT_IMAGE).unwrap_or_default().to_owned();
        let tree = lookup_str(&record.attributes, attr::SNAPSHOT_TREE).unwrap_or_default().to_owned();
        if image == previous {
            repeats += 1;
        } else {
            assert_eq!(
                image,
                snap_image_path(ordinal),
                "snapshot {ordinal} names neither its own picture nor the previous one"
            );
        }
        assert_eq!(tree, snap_tree_path(ordinal));
        require_lossless_webp(&image, &example_file(&image));
        decode_tree(&example_file(&tree)).unwrap_or_else(|error| panic!("{tree}: {error}"));
        named.insert(image.clone());
        named.insert(tree);
        previous = image;
    }
    assert!(
        repeats > 0,
        "no snapshot of the example repeats the previous picture, so no reader is tested on a shared file"
    );
    let present: BTreeSet<String> = fs::read_dir(example_bundle().join(SNAP_DIR))
        .unwrap()
        .map(|entry| format!("{SNAP_DIR}/{}", entry.unwrap().file_name().to_string_lossy()))
        .collect();
    assert_eq!(present, named, "{SNAP_DIR} holds exactly the files the snapshots name");
}

/// Checks the RIFF container and the VP8L chunk: the header a browser's decoder reads first, and the chunk that
/// makes the picture lossless rather than a VP8 approximation of the screen.
fn require_lossless_webp(name: &str, content: &[u8]) {
    assert!(
        content.len() >= 21 && &content[0..4] == b"RIFF" && &content[8..12] == b"WEBP",
        "{name} is not a RIFF WEBP file"
    );
    let size = u32::from_le_bytes(content[4..8].try_into().unwrap());
    assert_eq!(
        usize::try_from(size).unwrap(),
        content.len() - 8,
        "{name} declares the wrong RIFF size"
    );
    assert!(&content[12..16] == b"VP8L" && content[20] == 0x2f, "{name} is not lossless WebP");
}

#[test]
fn the_example_log_slice_is_the_idea_log_format() {
    for (index, line) in lines(&example_bundle().join(IDEA_LOG_FILE)).iter().enumerate() {
        // `2026-09-23 10:15:00,251 [ 812034]   INFO - #category - message`, the platform's layout.
        let layout =
            line.len() >= 24 && line[4] == b'-' && line[10] == b' ' && line[19] == b',' && line.windows(4).any(|window| window == b" - #");
        assert!(
            layout,
            "line {} of {IDEA_LOG_FILE} is not an idea.log line: {}",
            index + 1,
            String::from_utf8_lossy(line)
        );
    }
}

fn allure_result(sources: &[&str]) -> AllureResult {
    let attachment = |source: &&str| AllureAttachment {
        name: "Before".to_owned(),
        source: (*source).to_owned(),
        mime: "image/webp".to_owned(),
    };
    AllureResult {
        uuid: derived_uuid(&["run-1", "AirExampleUiTest/example", "2026-09-23T10:15:00.000Z"]),
        history_id: allure_history_id("AirExampleUiTest.example"),
        full_name: "AirExampleUiTest.example".to_owned(),
        name: "example".to_owned(),
        status: AllureStatus::Broken,
        status_details: AllureStatusDetails::default(),
        stage: AllureStage::Finished,
        start: 1_790_158_500_000,
        stop: 1_790_158_501_000,
        labels: vec![Label {
            name: ALLURE_BUNDLE_LABEL.to_owned(),
            value: "AirExampleUiTest/example".to_owned(),
        }],
        steps: vec![AllureStep {
            name: "Open the chat".to_owned(),
            status: AllureStatus::Passed,
            status_details: AllureStatusDetails::default(),
            stage: AllureStage::Finished,
            start: 1_790_158_500_100,
            stop: 1_790_158_500_900,
            steps: Vec::new(),
            attachments: sources.iter().map(attachment).collect(),
        }],
        attachments: Vec::new(),
    }
}

/// A bundle status maps to the Allure status a report shows: an aborted or a truncated bundle is broken.
#[test]
fn the_allure_status_of_each_bundle_status() {
    let mapped: Vec<(&str, &str)> = BundleStatus::ALL
        .iter()
        .map(|status| (status.as_str(), AllureStatus::from(*status).as_str()))
        .collect();
    assert_eq!(
        mapped,
        [
            ("passed", "passed"),
            ("failed", "failed"),
            ("aborted", "broken"),
            ("truncated", "broken")
        ]
    );
    assert_eq!(AllureStatus::from(Status::Aborted), AllureStatus::Broken);
}

/// A derived uuid is a version 8 RFC 9562 UUID, the same for the same parts, and the history id hashes the full name.
#[test]
fn derived_ids_are_stable() {
    let uuid = derived_uuid(&["run-1", "AirExampleUiTest/example"]);
    assert_eq!(uuid, derived_uuid(&["run-1", "AirExampleUiTest/example"]));
    assert_ne!(uuid, derived_uuid(&["run-1", "AirExampleUiTest/example.2"]));
    // The separator keeps the parts apart, so a moved boundary is another uuid.
    assert_ne!(derived_uuid(&["ab", "c"]), derived_uuid(&["a", "bc"]));
    let groups: Vec<usize> = uuid.split('-').map(str::len).collect();
    assert_eq!(groups, [8, 4, 4, 4, 12]);
    assert_eq!(&uuid[14..15], "8", "{uuid} is not version 8");
    assert!(matches!(&uuid[19..20], "8" | "9" | "a" | "b"), "{uuid} is not the RFC 9562 variant");
    let history = allure_history_id("AirExampleUiTest.example");
    assert_eq!(history, hex::encode(Sha256::digest("AirExampleUiTest.example")));
    assert!(history.len() == 64 && history.bytes().all(|byte| matches!(byte, b'0'..=b'9' | b'a'..=b'f')));
    assert_eq!(allure_result_file(&uuid), format!("{uuid}-result.json"));
    assert_eq!(allure_attachment_file(&uuid, "webp"), format!("{uuid}-attachment.webp"));
    assert_eq!(
        allure_results_dir(Path::new("/root"), "run 1"),
        Path::new("/root").join("run_1").join(ALLURE_RESULTS_DIR)
    );
}

/// A result reads back as it was written, without the empty details, and a reader refuses a result the recorder
/// would not write.
#[test]
fn an_allure_result_round_trips_and_refuses_what_the_recorder_never_writes() {
    let result = allure_result(&["0c62081c-1bed-87ac-b44a-5c9a5ec24117-attachment.webp"]);
    let document = encode_allure_result(&result).unwrap();
    let text = String::from_utf8(document.clone()).unwrap();
    assert!(!text.contains("statusDetails"), "empty details are written: {text}");
    assert!(text.contains(r#""type": "image/webp""#), "{text}");
    assert_eq!(decode_allure_result(&document).unwrap(), result);
    assert_eq!(result.bundle(), Some("AirExampleUiTest/example"));
    assert_eq!(result.sources(), ["0c62081c-1bed-87ac-b44a-5c9a5ec24117-attachment.webp"]);

    let refused = |result: &AllureResult| encode_allure_result(result).unwrap_err().to_string();
    assert!(refused(&allure_result(&["../video.mp4"])).contains("not a file of its directory"));
    assert!(refused(&allure_result(&[".hidden"])).contains("not a file of its directory"));
    let mut unbundled = allure_result(&[]);
    unbundled.labels.clear();
    assert!(refused(&unbundled).contains("no traceBundle label"));
    let mut backwards = allure_result(&[]);
    backwards.stop = backwards.start - 1;
    assert!(refused(&backwards).contains("before its start"));
    let unknown = text.replacen(r#""uuid""#, r#""links": [], "uuid""#, 1);
    assert!(
        decode_allure_result(unknown.as_bytes())
            .unwrap_err()
            .to_string()
            .contains("unknown field `links`")
    );
}
