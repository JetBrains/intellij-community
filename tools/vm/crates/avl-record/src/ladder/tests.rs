use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Instant;

use super::*;
use crate::testing::{ScriptedClock, SharedBuffer};

/// The ladder reads X only where it may and where the IDE draws to it, and gives up on a display for a minute, not
/// for the rest of the session.
#[test]
fn the_ladder_reads_x_only_where_allowed_and_where_the_ide_draws_to_it() {
    let t0 = 1_790_158_500_000;
    let clock = ScriptedClock::at_ms(t0);
    let opened = Arc::new(AtomicUsize::new(0));
    let mut ladder = SystemLadder::new(Diagnostics::new(SharedBuffer::default()), clock.clock(), "linux".to_owned());
    ladder.open_x11 = {
        let opened = opened.clone();
        Box::new(move |display| {
            opened.fetch_add(1, Ordering::SeqCst);
            anyhow::bail!("cannot connect to the X display {display}: nobody serves it")
        })
    };
    let screen = |display: &str, os: &str, allowed: bool| ScreenAccess {
        display: display.to_owned(),
        os: os.to_owned(),
        allowed,
    };
    let cases = [
        ("not allowed", screen(":88", "linux", false), SCREEN_OPT_IN),
        (
            "a Mac",
            screen("/private/tmp/com.apple.launchd.x/org.xquartz:0", "darwin", true),
            "not through X",
        ),
    ];
    for (label, access, reason) in cases {
        let (source, answered) = ladder.climb(&access, None);
        assert!(source.is_none() && answered.contains(reason), "{label}: {answered}");
    }
    assert_eq!(opened.load(Ordering::SeqCst), 0, "a display the ladder may not read was opened");

    let missing = screen(":7357", "linux", true);
    let (_, first) = ladder.climb(&missing, None);
    let (_, second) = ladder.climb(&missing, None);
    assert!(first.contains(":7357"), "{first}");
    assert_eq!(second, first);
    assert_eq!(
        opened.load(Ordering::SeqCst),
        1,
        "a display that failed once was tried again at once"
    );
    assert_eq!(ladder.failed.as_ref().map(|failed| failed.at_ms), Some(t0));

    // A minute on, the display is tried again, which dates the failure anew.
    let later = t0 + FAILED_DISPLAY_RETRY_MS;
    clock.set_ms(later);
    let (_, third) = ladder.climb(&missing, None);
    assert!(third.contains(":7357"), "{third}");
    assert_eq!(opened.load(Ordering::SeqCst), 2);
    assert_eq!(ladder.failed.as_ref().map(|failed| failed.at_ms), Some(later));

    // So is one whose failure the clock stepped back past.
    clock.set_ms(t0);
    ladder.climb(&missing, None);
    assert_eq!(opened.load(Ordering::SeqCst), 3);
    assert_eq!(ladder.failed.as_ref().map(|failed| failed.at_ms), Some(t0));
}

#[test]
fn without_x11_the_ladder_asks_the_ide_to_paint_and_says_why() {
    let clock = ScriptedClock::at_ms(1);
    let mut ladder = SystemLadder::new(Diagnostics::new(SharedBuffer::default()), clock.clock(), "darwin".to_owned());
    let client = Client::new("http://127.0.0.1:1", "token").unwrap();
    let (source, reason) = ladder.climb(&ScreenAccess::default(), Some(&client));
    assert_eq!(source.map(|source| source.kind()), Some(CaptureSource::IdePaint));
    assert!(reason.starts_with("no X11: "), "{reason}");
    let (source, reason) = ladder.climb(&ScreenAccess::default(), None);
    assert!(source.is_none());
    assert!(reason.ends_with("no IDE paint: the lane named no bridge"), "{reason}");
}

/// A source that is only its kind: a video's plan reads nothing else.
struct Kind(CaptureSource);

impl Source for Kind {
    fn kind(&self) -> CaptureSource {
        self.0
    }

    fn grab(&self, _deadline: Instant, _frame: &mut Frame) -> anyhow::Result<()> {
        anyhow::bail!("a plan grabs no frame")
    }
}

/// X11 frames go to the recorder's own encoder, which needs no ffmpeg and no screen permission. Only a Mac's screen
/// goes through ffmpeg, and only when the lane allowed it.
#[test]
fn the_videographer_encodes_x11_itself_and_records_only_a_macs_screen_with_ffmpeg() {
    let videographer = |os: &str, ffmpeg: Result<PathBuf, String>| SystemVideographer {
        ffmpeg,
        os: os.to_owned(),
        clock: ScriptedClock::at_ms(1).clock(),
    };
    let missing = || Err("ffmpeg is not on PATH".to_owned());
    let (x11, paint) = (Kind(CaptureSource::X11), Kind(CaptureSource::IdePaint));
    let linux = videographer("linux", missing());
    let darwin = videographer("darwin", missing());

    assert_eq!(linux.plan(Some(&x11), false), VideoPlan::h264(true));
    assert_eq!(darwin.plan(Some(&paint), true), VideoPlan::none("ffmpeg is not on PATH"));
    let refused = darwin.plan(Some(&paint), false);
    assert!(
        refused.codec == VideoCodec::None && refused.reason.contains(SCREEN_OPT_IN),
        "{refused:?}"
    );
    let found = videographer("darwin", Ok(PathBuf::from("/opt/homebrew/bin/ffmpeg")));
    assert_eq!(found.plan(Some(&paint), true), VideoPlan::h264(false));
    let painted = linux.plan(Some(&paint), true);
    assert!(
        painted.codec == VideoCodec::None && painted.reason.contains("linux has no screen recording"),
        "{painted:?}"
    );
    assert_eq!(linux.plan(None, true), VideoPlan::none("nothing is captured"));
}
