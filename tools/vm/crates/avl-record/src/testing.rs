//! What the tests of several modules share: a buffer more than one thread writes, a clock the test sets, and a frame
//! with the texture of a UI. The loopback HTTP server that stands in for the IDE's bridge is `avl_testkit::http`.

use std::io::{self, Write};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::SystemTime;

use crate::capture::{BYTES_PER_PIXEL, Frame};
use crate::{Clock, from_unix_ms, lock};

/// A buffer more than one thread may write, for the diagnostics and the acks.
#[derive(Clone, Default)]
pub(crate) struct SharedBuffer(Arc<Mutex<Vec<u8>>>);

impl SharedBuffer {
    pub(crate) fn text(&self) -> String {
        String::from_utf8_lossy(&lock(&self.0)).into_owned()
    }
}

impl Write for SharedBuffer {
    fn write(&mut self, chunk: &[u8]) -> io::Result<usize> {
        lock(&self.0).extend_from_slice(chunk);
        Ok(chunk.len())
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

/// A clock the test sets. The recorder, the fake bridge and the fake screen all read it, from their own threads.
#[derive(Clone, Default)]
pub(crate) struct ScriptedClock(Arc<AtomicI64>);

impl ScriptedClock {
    pub(crate) fn at_ms(ms: i64) -> Self {
        let clock = Self::default();
        clock.set_ms(ms);
        clock
    }

    pub(crate) fn set_ms(&self, ms: i64) {
        self.0.store(ms, Ordering::SeqCst);
    }

    pub(crate) fn ms(&self) -> i64 {
        self.0.load(Ordering::SeqCst)
    }

    pub(crate) fn now(&self) -> SystemTime {
        from_unix_ms(self.ms())
    }

    pub(crate) fn clock(&self) -> Clock {
        let clock = self.clone();
        Arc::new(move || clock.now())
    }

    pub(crate) fn advance_ms(&self, ms: i64) -> SystemTime {
        self.set_ms(self.ms() + ms);
        self.now()
    }
}

/// Something with the texture of a UI: flat panels, hard edges and a few odd pixels, which is what the encoder's
/// palette and predictor paths see on a real screen. The accent shows only below row 12.
pub(crate) fn ui_frame(width: usize, height: usize, accent: u8) -> Frame {
    let mut frame = Frame::default();
    frame.resize(width, height);
    for (index, pixel) in frame.pix.as_chunks_mut::<BYTES_PER_PIXEL>().0.iter_mut().enumerate() {
        let (x, y) = (index % width, index / width);
        let bgr = match (y, x) {
            (0..12, _) => [0x30, 0x2d, 0x2b],
            (_, x) if x < width / 3 => [0x22, 0x1f, 0x1e],
            _ => [u8::try_from(x * 7 % 256).unwrap(), u8::try_from(y * 3 % 256).unwrap(), accent],
        };
        pixel[..3].copy_from_slice(&bgr);
    }
    frame
}
