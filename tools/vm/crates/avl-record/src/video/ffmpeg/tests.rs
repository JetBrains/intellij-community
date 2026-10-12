#[cfg(unix)]
use std::time::{Instant, UNIX_EPOCH};

#[cfg(unix)]
use avl_trace::bundle::VIDEO_INDEX_FILE;

use super::*;

#[test]
fn the_screen_argv_captures_the_main_screen_with_the_pointer() {
    let joined = screen_args(VIDEO_FILE).join(" ");
    for part in [
        "-f avfoundation",
        "-capture_cursor 1",
        "-i Capture screen 0:none",
        "-progress pipe:1",
        "-r 10",
        "-c:v libx264",
        "-g 10",
        "-bf 0",
        "+frag_keyframe+empty_moov+default_base_moof video.mp4",
    ] {
        assert!(joined.contains(part), "the screen argv lacks {part:?}: {joined}");
    }
}

#[cfg(unix)]
#[test]
fn find_prefers_path_then_the_usual_locations() {
    let (on_path, usual, empty) = (
        tempfile::tempdir().unwrap(),
        tempfile::tempdir().unwrap(),
        tempfile::tempdir().unwrap(),
    );
    let first = avl_testkit::fake_executable(on_path.path(), "ffmpeg", "exit 0\n").unwrap();
    let fallback = avl_testkit::fake_executable(usual.path(), "ffmpeg", "exit 0\n").unwrap();
    let usual_dir = usual.path().to_str().unwrap();
    assert_eq!(find_in(Some(on_path.path().as_os_str()), &[usual_dir]).unwrap(), first);
    assert_eq!(find_in(Some(empty.path().as_os_str()), &[usual_dir]).unwrap(), fallback);
    assert_eq!(find_in(None, &[usual_dir]).unwrap(), fallback);

    // A file that is not executable is not the ffmpeg to run.
    std::fs::write(empty.path().join("ffmpeg"), "not a program").unwrap();
    let missing = find_in(Some(empty.path().as_os_str()), &[empty.path().to_str().unwrap()]).unwrap_err();
    assert!(missing.to_string().starts_with("ffmpeg is not on PATH, nor in "), "{missing:#}");
}

/// A shell standing in for ffmpeg, started in `dir` like the real one, whose reports are dated at `now_ms`.
#[cfg(unix)]
fn shell(dir: &Path, script: &str, now_ms: u64) -> FfmpegRecording {
    let argv = ["/bin/sh", "-c", script].map(str::to_owned);
    let now = UNIX_EPOCH + Duration::from_millis(now_ms);
    FfmpegRecording::start(&argv, dir, Arc::new(move || now)).unwrap()
}

/// Waits until the reports counted `frames`, as a recording that ran for a while has.
#[cfg(unix)]
fn wait_for_frames(recording: &FfmpegRecording, frames: usize) {
    let deadline = Instant::now() + Duration::from_secs(5);
    while lock(&recording.progress.progress).frames != frames && Instant::now() < deadline {
        thread::sleep(Duration::from_millis(10));
    }
}

#[cfg(unix)]
#[test]
fn the_screen_index_is_dated_from_the_first_progress_report() {
    let dir = tempfile::tempdir().unwrap();
    let script = format!(
        "printf 'frame=0\\nprogress=continue\\nframe=5\\nprogress=continue\\n'; read key; printf 'frame=7\\nprogress=end\\n'; printf x > {VIDEO_FILE}"
    );
    let mut recording = shell(dir.path(), &script, 10_000);
    assert!(!recording.raw() && recording.take_sink().is_none());
    wait_for_frames(&recording, 5);
    let stopped = recording.stop().unwrap();
    assert_eq!(
        stopped,
        Stopped {
            frames: 7,
            first_frame_ms: 10_000 - 400
        }
    );
    assert!(dir.path().join(VIDEO_INDEX_FILE).exists());
}

#[cfg(unix)]
#[test]
fn an_ffmpeg_that_dies_says_why_and_leaves_no_index() {
    let dir = tempfile::tempdir().unwrap();
    let mut recording = shell(dir.path(), "echo 'Unknown encoder libx264' >&2; exit 1", 1000);
    let _ = recording.child.wait();
    let error = recording.stop().unwrap_err();
    assert!(format!("{error:#}").contains("Unknown encoder libx264"), "{error:#}");
    assert!(
        !dir.path().join(VIDEO_INDEX_FILE).exists(),
        "a video that does not exist has an index"
    );
}

#[cfg(unix)]
#[test]
fn an_ffmpeg_that_ignores_its_stop_is_killed() {
    let dir = tempfile::tempdir().unwrap();
    let mut recording = shell(dir.path(), "printf 'frame=3\\nprogress=continue\\n'; exec sleep 30", 1000);
    recording.grace = Duration::from_millis(200);
    wait_for_frames(&recording, 3);
    let started = Instant::now();
    let error = recording.stop().unwrap_err();
    assert!(started.elapsed() < Duration::from_secs(5), "stopping took {:?}", started.elapsed());
    assert!(format!("{error:#}").contains("killed"), "{error:#}");
}
