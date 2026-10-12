//! The capture ladder's first rung: the X server's root window, the Linux guest's `Xvfb :88` or a Linux host's
//! display, at 1:1.
//!
//! It is the best rung because the X server holds exactly what a person would see, native popups included, and
//! reading it costs the IDE nothing: no event-thread paint, no Swing call. The frame is the whole root window rather
//! than the IDE's frame, since a dialog, a popup or a notification can sit anywhere on it.
//!
//! Pixels come through MIT-SHM when the X server offers it, which on a local server is one request and no copy
//! through the socket: the server writes the picture into a System V segment both sides have attached. Where MIT-SHM
//! is missing, or the segment cannot be shared (another IPC namespace, a remote display), the plain core `GetImage`
//! request carries the same bytes through the socket instead. Both answer the frame in the layout [Frame] keeps, so
//! neither needs a pass over the pixels.
//!
//! The module dials the display's socket itself and keeps a second handle on it, for one reason: a server that stops
//! answering must not be able to hold the recorder. x11rb blocks in `poll` for a reply with no timeout, so a watchdog
//! shuts the socket down when a grab outlives its deadline, which fails the pending reply and ends the grab.

use std::net::{Shutdown, TcpStream, ToSocketAddrs};
use std::os::unix::net::UnixStream;
use std::sync::{Mutex, OnceLock};
use std::thread;
use std::time::{Duration, Instant};

use anyhow::{Context, anyhow, bail};
use avl_trace::protocol::CaptureSource;
use crossbeam_channel::{RecvTimeoutError, Sender, bounded, unbounded};
use x11rb::connection::Connection;
use x11rb::protocol::xproto::{ConnectionExt as _, ImageFormat, ImageOrder, Screen, Setup, VisualClass};
use x11rb::reexports::x11rb_protocol::parse_display::{ConnectAddress, parse_display};
use x11rb::reexports::x11rb_protocol::xauth;
use x11rb::rust_connection::{DefaultStream, RustConnection};

use super::{BYTES_PER_PIXEL, Frame, Source};

mod shm;

#[cfg(test)]
mod tests;

/// Bounds the connection, the handshake and the checks after it. A display that does not answer within it is
/// treated as absent, and the recorder climbs down the ladder instead of stalling the lane's ack.
const OPEN_TIMEOUT: Duration = Duration::from_secs(3);

/// Bounds one plain `GetImage` reply. A whole 4K root in one reply is 33 MB, which a server may refuse or take its
/// time over; strips of rows keep each reply modest and the total the same.
const MAX_STRIP_BYTES: usize = 4 << 20;

/// A second handle on the display's socket, which can shut it down while x11rb waits on it.
enum Shutter {
    Unix(UnixStream),
    Tcp(TcpStream),
}

impl Shutter {
    fn shut(&self) {
        let _ = match self {
            Self::Unix(stream) => stream.shutdown(Shutdown::Both),
            Self::Tcp(stream) => stream.shutdown(Shutdown::Both),
        };
    }
}

/// The connection and what a grab reads through it.
struct Connected {
    conn: RustConnection,
    shm: Option<shm::Segment>,
}

/// Reads one X server's root window.
pub(crate) struct X11Source {
    /// Serializes grabs: the frame loop and a snapshot both grab, and they share the segment and the connection.
    connected: Mutex<Connected>,
    root: u32,
    width: u16,
    height: u16,
    /// Why grabs go through the plain `GetImage`, `None` when they go through MIT-SHM.
    plain: Option<String>,
    broken: OnceLock<String>,
    watchdog: Sender<Watch>,
    fired: std::sync::Arc<std::sync::atomic::AtomicBool>,
}

enum Watch {
    Arm(Instant),
    Disarm,
}

impl X11Source {
    /// Connects to `display`, such as `:88`, and checks that its root window is in a layout this module reads.
    pub(crate) fn open(display: &str) -> anyhow::Result<Self> {
        let parsed = parse_display(Some(display)).map_err(|error| anyhow!("bad X display {display:?}: {error}"))?;
        let mut failure = None;
        for address in parsed.connect_instruction() {
            let dialed = match &address {
                ConnectAddress::Hostname(host, port) => dial_tcp(host, *port).and_then(|stream| {
                    let shutter = Shutter::Tcp(stream.try_clone()?);
                    let (stream, peer) = DefaultStream::from_tcp_stream(stream)?;
                    Ok((stream, peer, shutter))
                }),
                ConnectAddress::Socket(path) => UnixStream::connect(path).and_then(|stream| {
                    let shutter = Shutter::Unix(stream.try_clone()?);
                    let (stream, peer) = DefaultStream::from_unix_stream(stream)?;
                    Ok((stream, peer, shutter))
                }),
                _ => Err(std::io::Error::other("an address family x11rb cannot dial")),
            };
            match dialed {
                Ok((stream, (family, address), shutter)) => {
                    // A server with no access control, the guest's `Xvfb`, needs no cookie, and an empty one connects
                    // without.
                    let (name, data) = xauth::get_auth(family, &address, parsed.display).ok().flatten().unwrap_or_default();
                    return open_stream(stream, shutter, name, data, usize::from(parsed.screen), display);
                }
                Err(error) => failure = Some(error),
            }
        }
        match failure {
            Some(error) => Err(anyhow!("cannot connect to the X display {display}: {error}")),
            None => Err(anyhow!("the X display {display} names no address to connect to")),
        }
    }

    /// Why frames come through the plain `GetImage` rather than MIT-SHM, `None` when they do not.
    pub(crate) fn plain_reason(&self) -> Option<&str> {
        self.plain.as_deref()
    }

    #[cfg(test)]
    const fn size(&self) -> (u16, u16) {
        (self.width, self.height)
    }

    fn mark_broken(&self, why: String) -> String {
        self.broken.get_or_init(|| why).clone()
    }

    fn read(&self, connected: &Connected, frame: &mut Frame) -> anyhow::Result<()> {
        let (width, height) = (usize::from(self.width), usize::from(self.height));
        frame.resize(width, height);
        let size = frame.pix.len();
        if let Some(segment) = &connected.shm {
            let written = segment
                .get_image(&connected.conn, self.root, self.width, self.height)
                .context("MIT-SHM GetImage")?;
            if (written as usize) < size {
                bail!("MIT-SHM GetImage answered {written} bytes for a {width}x{height} root");
            }
            segment.copy_to(&mut frame.pix);
            return Ok(());
        }
        let row_bytes = width * BYTES_PER_PIXEL;
        let strip_rows = (MAX_STRIP_BYTES / row_bytes.max(1)).clamp(1, height.max(1));
        let mut top = 0;
        while top < height {
            let rows = strip_rows.min(height - top);
            let reply = connected
                .conn
                .get_image(
                    ImageFormat::Z_PIXMAP,
                    self.root,
                    0,
                    i16::try_from(top)?,
                    self.width,
                    u16::try_from(rows)?,
                    !0,
                )?
                .reply()
                .context("GetImage")?;
            let wanted = rows * row_bytes;
            if reply.data.len() < wanted {
                bail!("GetImage answered {} bytes for {rows} rows of {width}", reply.data.len());
            }
            frame.pix[top * row_bytes..top * row_bytes + wanted].copy_from_slice(&reply.data[..wanted]);
            top += rows;
        }
        Ok(())
    }
}

impl Source for X11Source {
    fn kind(&self) -> CaptureSource {
        CaptureSource::X11
    }

    /// Reads the whole root window into `frame`.
    ///
    /// A grab waiting behind another gives up at its own deadline. A grab whose reply does not come by its deadline
    /// has its socket shut down by the watchdog, which fails the pending reply and marks this source broken: an X
    /// server that stopped answering is not one to try again at ten frames a second.
    fn grab(&self, deadline: Instant, frame: &mut Frame) -> anyhow::Result<()> {
        let connected = loop {
            match self.connected.try_lock() {
                Ok(connected) => break connected,
                Err(std::sync::TryLockError::Poisoned(poisoned)) => break poisoned.into_inner(),
                Err(std::sync::TryLockError::WouldBlock) => {
                    if Instant::now() >= deadline {
                        bail!("another grab of the X server did not finish in time");
                    }
                    thread::sleep(Duration::from_millis(2));
                }
            }
        };
        if let Some(broken) = self.broken.get() {
            bail!("{broken}");
        }
        // A grab that got the connection with no time left would only have the watchdog break a working source.
        if Instant::now() >= deadline {
            bail!("another grab of the X server did not finish in time");
        }
        let at = std::time::SystemTime::now();
        let _ = self.watchdog.send(Watch::Arm(deadline));
        let read = self.read(&connected, frame);
        let _ = self.watchdog.send(Watch::Disarm);
        if self.fired.load(std::sync::atomic::Ordering::SeqCst) {
            let broken = self.mark_broken("the X server stopped answering within the grab's bound".to_owned());
            // The deadline can pass between the reply and the disarm: the frame is whole, but the socket is shut, so
            // the source is broken all the same and the next climb reopens it.
            if read.is_err() {
                bail!("{broken}");
            }
        }
        if let Err(error) = read {
            // A failed request on a working connection is this frame's problem; a connection x11rb gave up on is
            // the source's.
            if connection_failed(&error) {
                self.mark_broken(format!("the X connection failed: {error:#}"));
            }
            return Err(error);
        }
        frame.at = at;
        Ok(())
    }

    fn broken(&self) -> Option<String> {
        self.broken.get().cloned()
    }
}

/// Whether an error is x11rb giving up on the connection, rather than the server refusing one request.
fn connection_failed(error: &anyhow::Error) -> bool {
    use x11rb::errors::{ConnectionError, ReplyError};
    error
        .chain()
        .any(|cause| cause.is::<ConnectionError>() || matches!(cause.downcast_ref::<ReplyError>(), Some(ReplyError::ConnectionError(_))))
}

/// Dials a TCP display within the open bound.
fn dial_tcp(host: &str, port: u16) -> std::io::Result<TcpStream> {
    let mut failure = std::io::Error::other(format!("{host} resolves to no address"));
    for address in (host, port).to_socket_addrs()? {
        match TcpStream::connect_timeout(&address, OPEN_TIMEOUT) {
            Ok(stream) => return Ok(stream),
            Err(error) => failure = error,
        }
    }
    Err(failure)
}

/// Runs the handshake over a connected stream and checks the screen, all of it inside [OPEN_TIMEOUT]. A server that
/// stops answering part way, in the handshake or in the MIT-SHM query after it, has its socket shut down, which ends
/// whatever was waiting on it.
fn open_stream(
    stream: DefaultStream,
    shutter: Shutter,
    auth_name: Vec<u8>,
    auth_data: Vec<u8>,
    screen: usize,
    display: &str,
) -> anyhow::Result<X11Source> {
    let (answer, answered) = bounded(1);
    let named = display.to_owned();
    thread::spawn(move || {
        let _ = answer.send(handshake(stream, auth_name, auth_data, screen, &named));
    });
    let Ok(opened) = answered.recv_timeout(OPEN_TIMEOUT) else {
        shutter.shut();
        bail!("the X display {display} did not answer within {OPEN_TIMEOUT:?}");
    };
    let (connected, root, width, height, plain) = match opened {
        Ok(opened) => opened,
        Err(error) => {
            shutter.shut();
            return Err(error);
        }
    };
    let fired = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
    let (watchdog, watched) = unbounded();
    {
        let fired = fired.clone();
        thread::spawn(move || {
            let mut armed: Option<Instant> = None;
            loop {
                let watch = match armed {
                    None => watched.recv().map_err(RecvTimeoutError::from),
                    Some(deadline) => watched.recv_deadline(deadline),
                };
                match watch {
                    Ok(Watch::Arm(deadline)) => armed = Some(deadline),
                    Ok(Watch::Disarm) => armed = None,
                    Err(RecvTimeoutError::Timeout) => {
                        fired.store(true, std::sync::atomic::Ordering::SeqCst);
                        shutter.shut();
                        armed = None;
                    }
                    // The source is gone: its connection closes with it, and so does this handle.
                    Err(RecvTimeoutError::Disconnected) => {
                        shutter.shut();
                        return;
                    }
                }
            }
        });
    }
    Ok(X11Source {
        connected: Mutex::new(connected),
        root,
        width,
        height,
        plain,
        broken: OnceLock::new(),
        watchdog,
        fired,
    })
}

type Opened = (Connected, u32, u16, u16, Option<String>);

fn handshake(stream: DefaultStream, auth_name: Vec<u8>, auth_data: Vec<u8>, screen: usize, display: &str) -> anyhow::Result<Opened> {
    let conn = RustConnection::connect_to_stream_with_auth_info(stream, screen, auth_name, auth_data)
        .map_err(|error| anyhow!("cannot connect to the X display {display}: {error}"))?;
    let setup = conn.setup();
    let Some(root) = setup.roots.get(screen) else {
        bail!("the X display {display} has no screen {screen}");
    };
    check_pixel_format(setup, root).with_context(|| format!("the X display {display}"))?;
    let (window, width, height) = (root.root, root.width_in_pixels, root.height_in_pixels);
    let size = usize::from(width) * usize::from(height) * BYTES_PER_PIXEL;
    let (shm, plain) = match shm::Segment::attach(&conn, size) {
        Ok(segment) => (Some(segment), None),
        Err(error) => (None, Some(format!("{error:#}"))),
    };
    Ok((Connected { conn, shm }, window, width, height, plain))
}

/// Refuses a root window whose pixels are not BGRX: 32 bits per pixel, least significant byte first, and a TrueColor
/// visual whose masks put blue, green and red in the first three bytes of each pixel. Every Xvfb and every modern
/// Linux desktop answers exactly that. A 16-bit or palette display would need a conversion this recorder does not
/// carry, so it is refused with the reason, and the ladder moves on.
fn check_pixel_format(setup: &Setup, screen: &Screen) -> anyhow::Result<()> {
    if setup.image_byte_order != ImageOrder::LSB_FIRST {
        bail!("the server sends images most significant byte first");
    }
    let bits_per_pixel = setup
        .pixmap_formats
        .iter()
        .rfind(|format| format.depth == screen.root_depth)
        .map_or(0, |format| format.bits_per_pixel);
    if bits_per_pixel != 32 {
        bail!(
            "the root window's depth {} has {bits_per_pixel} bits per pixel, not 32",
            screen.root_depth
        );
    }
    let visual = screen
        .allowed_depths
        .iter()
        .flat_map(|depth| &depth.visuals)
        .find(|visual| visual.visual_id == screen.root_visual)
        .with_context(|| format!("the root visual {} is not among the screen's visuals", screen.root_visual))?;
    if visual.class != VisualClass::TRUE_COLOR || visual.red_mask != 0x00ff_0000 || visual.green_mask != 0xff00 || visual.blue_mask != 0xff
    {
        bail!(
            "the root visual is of class {} with the masks {:06x}/{:06x}/{:06x}, not 8-bit TrueColor",
            u8::from(visual.class),
            visual.red_mask,
            visual.green_mask,
            visual.blue_mask
        );
    }
    Ok(())
}
