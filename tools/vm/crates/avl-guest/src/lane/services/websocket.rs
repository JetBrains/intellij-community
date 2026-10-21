//! The WebSocket of RFC 6455 that the Agent Spawner of the Cloud emulator speaks: the server side, which a dial of the
//! IDE reaches, and the client side, which reaches the control server in the IDE.
//!
//! The subset is what the two peers send: text messages, whole or fragmented, pings, pongs and a close. A binary
//! message is read and dropped. No extension and no subprotocol is negotiated. A message is at most 16 MiB, the bound of
//! the control server in the IDE. A server reads only masked frames, and a client reads only unmasked frames.
//!
//! A connection has two halves: one [Receiver] that one thread reads, and one [Sender] that any thread writes. The sender
//! writes each frame whole under its lock, so two writers never interleave.

use std::collections::hash_map::RandomState;
use std::hash::{BuildHasher, Hasher};
use std::io::{self, BufRead, BufReader, Read, Write};
use std::net::{Shutdown, TcpStream, ToSocketAddrs};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, PoisonError};
use std::time::Duration;

use super::encoding::{Base64, sha1};

#[cfg(test)]
mod tests;

/// The close code of a normal end.
pub(crate) const NORMAL_CLOSURE: u16 = 1000;
/// The close code of a peer that goes away, as an agent process that exits does.
pub(crate) const GOING_AWAY: u16 = 1001;
/// The close code of a frame that breaks the protocol.
pub(crate) const PROTOCOL_ERROR: u16 = 1002;
/// The close code of a failure on this side.
pub(crate) const INTERNAL_ERROR: u16 = 1011;

/// The largest message that a peer reads.
pub(crate) const MAX_MESSAGE_BYTES: usize = 16 << 20;

/// How long a peer that sent a close waits for the answer of the other peer, as the minimal server of the test process
/// does.
pub(crate) const CLOSE_GRACE: Duration = Duration::from_secs(2);

const GUID: &str = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
const FIN: u8 = 0x80;
const RESERVED: u8 = 0x70;
const MASK: u8 = 0x80;
const MAX_CONTROL_PAYLOAD: u64 = 125;
const MAX_HEAD_LINE: u64 = 16 << 10;

/// The opcodes of RFC 6455, section 5.2.
pub(crate) mod opcode {
    pub(crate) const CONTINUATION: u8 = 0x0;
    pub(crate) const TEXT: u8 = 0x1;
    pub(crate) const BINARY: u8 = 0x2;
    pub(crate) const CLOSE: u8 = 0x8;
    pub(crate) const PING: u8 = 0x9;
    pub(crate) const PONG: u8 = 0xa;
}

/// The `Sec-WebSocket-Accept` value for the `Sec-WebSocket-Key` value `key`.
pub(crate) fn accept_key(key: &str) -> String {
    Base64::Standard.encode(&sha1(format!("{key}{GUID}").as_bytes()))
}

/// The end of a connection that this side is.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Role {
    /// It reads masked frames and writes unmasked ones.
    Server,
    /// It reads unmasked frames and writes masked ones.
    Client,
}

/// One message of the other peer.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) enum Message {
    /// A text message. A byte that is not UTF-8 becomes the replacement character.
    Text(String),
    /// A close frame, with its code when it has one.
    Close(Option<u16>),
}

/// One frame, without its mask.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct Frame {
    pub fin: bool,
    pub opcode: u8,
    pub payload: Vec<u8>,
}

/// Reads one frame that the other peer of `role` sent. A frame that breaks the protocol is an `InvalidData` error.
pub(crate) fn read_frame(reader: &mut impl Read, role: Role) -> io::Result<Frame> {
    let mut head = [0; 2];
    reader.read_exact(&mut head)?;
    let [first, second] = head;
    if first & RESERVED != 0 {
        return Err(invalid("a frame sets a reserved bit"));
    }
    let masked = second & MASK != 0;
    match (role, masked) {
        (Role::Server, false) => return Err(invalid("a frame of the client is not masked")),
        (Role::Client, true) => return Err(invalid("a frame of the server is masked")),
        _ => {}
    }
    let length = match second & 0x7f {
        126 => {
            let mut length = [0; 2];
            reader.read_exact(&mut length)?;
            u64::from(u16::from_be_bytes(length))
        }
        127 => {
            let mut length = [0; 8];
            reader.read_exact(&mut length)?;
            u64::from_be_bytes(length)
        }
        short => u64::from(short),
    };
    let (fin, opcode) = (first & FIN != 0, first & 0x0f);
    if opcode & 0x8 != 0 && (!fin || length > MAX_CONTROL_PAYLOAD) {
        return Err(invalid("a control frame is fragmented or longer than 125 bytes"));
    }
    let length = usize::try_from(length)
        .ok()
        .filter(|length| *length <= MAX_MESSAGE_BYTES)
        .ok_or_else(|| invalid(&format!("a frame of {length} bytes is longer than {MAX_MESSAGE_BYTES} bytes")))?;
    let mut mask = [0; 4];
    if masked {
        reader.read_exact(&mut mask)?;
    }
    let mut payload = vec![0; length];
    reader.read_exact(&mut payload)?;
    if masked {
        for (index, byte) in payload.iter_mut().enumerate() {
            *byte ^= mask[index % 4];
        }
    }
    Ok(Frame { fin, opcode, payload })
}

/// The bytes of one final frame with `opcode` and `payload`, masked with `mask` when it is given. The length has its
/// shortest form.
pub(crate) fn encode_frame(opcode: u8, payload: &[u8], mask: Option<[u8; 4]>) -> Vec<u8> {
    let mut frame = Vec::with_capacity(payload.len() + 14);
    frame.push(FIN | opcode);
    let mask_bit = if mask.is_some() { MASK } else { 0 };
    match u16::try_from(payload.len()) {
        Ok(length @ 0..=125) => frame.push(mask_bit | u8::try_from(length).unwrap_or_default()),
        Ok(length) => {
            frame.push(mask_bit | 126);
            frame.extend_from_slice(&length.to_be_bytes());
        }
        Err(_) => {
            frame.push(mask_bit | 127);
            frame.extend_from_slice(&u64::try_from(payload.len()).unwrap_or_default().to_be_bytes());
        }
    }
    match mask {
        Some(mask) => {
            frame.extend_from_slice(&mask);
            frame.extend(payload.iter().enumerate().map(|(index, byte)| byte ^ mask[index % 4]));
        }
        None => frame.extend_from_slice(payload),
    }
    frame
}

/// The writing half of one connection.
pub(crate) struct Sender {
    stream: Mutex<TcpStream>,
    role: Role,
    close_sent: AtomicBool,
}

impl Sender {
    /// The writing half of `stream`, for this side of `role`.
    pub(crate) const fn new(stream: TcpStream, role: Role) -> Self {
        Self {
            stream: Mutex::new(stream),
            role,
            close_sent: AtomicBool::new(false),
        }
    }

    /// Sends `text` as one text message. A sender that sent its close sends nothing more.
    pub(crate) fn text(&self, text: &str) -> io::Result<()> {
        self.frame(opcode::TEXT, text.as_bytes())
    }

    /// Answers a ping with a pong of the same payload.
    pub(crate) fn pong(&self, payload: &[u8]) -> io::Result<()> {
        self.frame(opcode::PONG, payload)
    }

    /// Sends a close frame with `code`, once, and ends the writing direction. A later read of the other half waits at
    /// most [CLOSE_GRACE] for the answer of the other peer. A read that already waits keeps its bound, so its owner
    /// ends it with [Sender::shutdown]. It answers whether this call sent the close.
    pub(crate) fn close(&self, code: u16) -> bool {
        let mut stream = self.stream.lock().unwrap_or_else(PoisonError::into_inner);
        if self.close_sent.swap(true, Ordering::SeqCst) {
            return false;
        }
        let _ = stream.write_all(&encode_frame(opcode::CLOSE, &code.to_be_bytes(), self.mask()));
        let _ = stream.flush();
        let _ = stream.shutdown(Shutdown::Write);
        let _ = stream.set_read_timeout(Some(CLOSE_GRACE));
        true
    }

    /// A handle of the connection, which ends it with a shutdown from another thread.
    pub(crate) fn try_clone_stream(&self) -> io::Result<TcpStream> {
        self.stream.lock().unwrap_or_else(PoisonError::into_inner).try_clone()
    }

    /// Whether this side sent its close.
    pub(crate) fn close_sent(&self) -> bool {
        self.close_sent.load(Ordering::SeqCst)
    }

    /// Ends both directions of the connection at once, without a close frame. A read of the other half ends.
    pub(crate) fn shutdown(&self) {
        self.close_sent.store(true, Ordering::SeqCst);
        let _ = self.stream.lock().unwrap_or_else(PoisonError::into_inner).shutdown(Shutdown::Both);
    }

    fn frame(&self, opcode: u8, payload: &[u8]) -> io::Result<()> {
        let mut stream = self.stream.lock().unwrap_or_else(PoisonError::into_inner);
        if self.close_sent() {
            return Err(io::Error::new(io::ErrorKind::BrokenPipe, "the connection sent its close"));
        }
        stream.write_all(&encode_frame(opcode, payload, self.mask()))?;
        stream.flush()
    }

    fn mask(&self) -> Option<[u8; 4]> {
        (self.role == Role::Client).then(random_bytes::<4>)
    }
}

/// The reading half of one connection.
pub(crate) struct Receiver {
    reader: BufReader<TcpStream>,
    role: Role,
}

impl Receiver {
    /// The reading half of `reader`, for this side of `role`.
    pub(crate) const fn new(reader: BufReader<TcpStream>, role: Role) -> Self {
        Self { reader, role }
    }

    /// Bounds each read of the connection by `timeout`, or lets it wait without a bound for `None`.
    pub(crate) fn set_read_timeout(&self, timeout: Option<Duration>) -> io::Result<()> {
        self.reader.get_ref().set_read_timeout(timeout)
    }

    /// The next text message or close of the other peer. It answers each ping through `sender`, and it skips a pong and
    /// a binary message. A frame that breaks the protocol is an `InvalidData` error.
    pub(crate) fn next(&mut self, sender: &Sender) -> io::Result<Message> {
        let mut message: Option<(bool, Vec<u8>)> = None;
        loop {
            let frame = read_frame(&mut self.reader, self.role)?;
            match frame.opcode {
                opcode::TEXT | opcode::BINARY if message.is_none() => message = Some((frame.opcode == opcode::TEXT, frame.payload)),
                opcode::TEXT | opcode::BINARY => return Err(invalid("a message starts before the last one ended")),
                opcode::CONTINUATION => match message.as_mut() {
                    Some((_, bytes)) if bytes.len() + frame.payload.len() <= MAX_MESSAGE_BYTES => bytes.extend(frame.payload),
                    Some(_) => return Err(invalid(&format!("a message is longer than {MAX_MESSAGE_BYTES} bytes"))),
                    None => return Err(invalid("a continuation frame continues no message")),
                },
                opcode::PING => {
                    sender.pong(&frame.payload)?;
                    continue;
                }
                opcode::PONG => continue,
                opcode::CLOSE => return Ok(Message::Close(close_code(&frame.payload))),
                other => return Err(invalid(&format!("the opcode {other:#x} is unknown"))),
            }
            if frame.fin
                && let Some((text, bytes)) = message.take()
                && text
            {
                return Ok(Message::Text(String::from_utf8_lossy(&bytes).into_owned()));
            }
        }
    }
}

/// The code of a close frame with `payload`, when it has one.
fn close_code(payload: &[u8]) -> Option<u16> {
    match payload {
        [high, low, ..] => Some(u16::from_be_bytes([*high, *low])),
        _ => None,
    }
}

/// Splits the stream of an accepted upgrade into its two halves, for the server side.
pub(crate) fn accept(stream: TcpStream) -> io::Result<(Sender, Receiver)> {
    let reader = BufReader::new(stream.try_clone()?);
    Ok((Sender::new(stream, Role::Server), Receiver::new(reader, Role::Server)))
}

/// Connects to `url`, `ws://<host>:<port><target>`, sends the upgrade with `headers`, and checks the answer. Each step
/// waits at most `timeout`, and so does each later read until the caller sets another bound. A refusal names the
/// address and the path of the URL, never its query, because the query of a control URL carries its token.
pub(crate) fn connect(url: &str, headers: &[(&str, &str)], timeout: Duration) -> io::Result<(Sender, Receiver)> {
    let rest = url
        .strip_prefix("ws://")
        .ok_or_else(|| invalid("a WebSocket URL of the lane starts with ws://"))?;
    let (authority, target) = rest.find('/').map_or((rest, "/"), |slash| rest.split_at(slash));
    let shown = format!("ws://{authority}{}", target.split('?').next().unwrap_or_default());
    let address = authority
        .to_socket_addrs()?
        .next()
        .ok_or_else(|| invalid(&format!("{shown} names no address")))?;
    let stream = TcpStream::connect_timeout(&address, timeout)?;
    stream.set_read_timeout(Some(timeout))?;
    stream.set_write_timeout(Some(timeout))?;
    let key = Base64::Standard.encode(&random_bytes::<16>());
    let mut request = format!(
        "GET {target} HTTP/1.1\r\nHost: {authority}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n\
         Sec-WebSocket-Version: 13\r\n"
    );
    for (name, value) in headers {
        request.push_str(&format!("{name}: {value}\r\n"));
    }
    request.push_str("\r\n");
    (&stream).write_all(request.as_bytes())?;
    let mut reader = BufReader::new(stream.try_clone()?);
    let status_line = read_head_line(&mut reader)?;
    let mut accept = None;
    loop {
        let line = read_head_line(&mut reader)?;
        if line.is_empty() {
            break;
        }
        if let Some((name, value)) = line.split_once(':')
            && name.trim().eq_ignore_ascii_case("Sec-WebSocket-Accept")
        {
            accept = Some(value.trim().to_owned());
        }
    }
    let status = status_line.split(' ').nth(1).unwrap_or_default();
    if status != "101" {
        return Err(io::Error::new(
            io::ErrorKind::ConnectionRefused,
            format!("the upgrade to {shown} answered {status_line:?}"),
        ));
    }
    if accept.as_deref() != Some(accept_key(&key).as_str()) {
        return Err(invalid(&format!("the upgrade to {shown} answered a wrong Sec-WebSocket-Accept")));
    }
    Ok((Sender::new(stream, Role::Client), Receiver::new(reader, Role::Client)))
}

/// One line of an answer head, without its line end.
fn read_head_line(reader: &mut impl BufRead) -> io::Result<String> {
    let mut line = Vec::new();
    reader.take(MAX_HEAD_LINE).read_until(b'\n', &mut line)?;
    if line.pop() != Some(b'\n') {
        return Err(io::Error::new(io::ErrorKind::UnexpectedEof, "the answer head ended early"));
    }
    if line.last() == Some(&b'\r') {
        line.pop();
    }
    String::from_utf8(line).map_err(|_invalid| invalid("a line of the answer head is not UTF-8"))
}

/// Bytes that differ on each call: a masking key or a handshake key. RFC 6455 asks for an unpredictable value against
/// a proxy cache. The peers are on loopback, so the random keys of the standard hasher are enough.
fn random_bytes<const N: usize>() -> [u8; N] {
    let mut bytes = [0; N];
    for chunk in bytes.chunks_mut(8) {
        let value = RandomState::new().build_hasher().finish().to_le_bytes();
        chunk.copy_from_slice(&value[..chunk.len()]);
    }
    bytes
}

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message.to_owned())
}
