use std::io::{Cursor, ErrorKind, Read, Write};
use std::net::TcpListener;
use std::thread;

use pretty_assertions::assert_eq;

use super::*;

fn frame_of(bytes: &[u8], role: Role) -> io::Result<Frame> {
    read_frame(&mut Cursor::new(bytes), role)
}

fn refusal(bytes: &[u8], role: Role) -> String {
    let error = frame_of(bytes, role).expect_err("a refused frame");
    assert_eq!(error.kind(), ErrorKind::InvalidData, "{error}");
    error.to_string()
}

#[test]
fn the_accept_key_is_the_example_of_rfc_6455() {
    assert_eq!(accept_key("dGhlIHNhbXBsZSBub25jZQ=="), "s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
}

// The examples of RFC 6455, section 5.7.
#[test]
fn the_frames_are_the_examples_of_rfc_6455() {
    let hello = || b"Hello".to_vec();
    let unmasked = [0x81, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f];
    let masked = [0x81, 0x85, 0x37, 0xfa, 0x21, 0x3d, 0x7f, 0x9f, 0x4d, 0x51, 0x58];
    assert_eq!(encode_frame(opcode::TEXT, b"Hello", None), unmasked);
    assert_eq!(encode_frame(opcode::TEXT, b"Hello", Some([0x37, 0xfa, 0x21, 0x3d])), masked);
    let text = Frame {
        fin: true,
        opcode: opcode::TEXT,
        payload: hello(),
    };
    assert_eq!(frame_of(&unmasked, Role::Client).unwrap(), text);
    assert_eq!(frame_of(&masked, Role::Server).unwrap(), text);
    let first = frame_of(&[0x01, 0x03, 0x48, 0x65, 0x6c], Role::Client).unwrap();
    assert_eq!((first.fin, first.opcode, first.payload), (false, opcode::TEXT, b"Hel".to_vec()));
    assert_eq!(
        encode_frame(opcode::PING, b"Hello", None),
        [0x89, 0x05, 0x48, 0x65, 0x6c, 0x6c, 0x6f]
    );
    assert_eq!(&encode_frame(opcode::BINARY, &[0; 256], None)[..4], [0x82, 0x7e, 0x01, 0x00]);
    assert_eq!(
        &encode_frame(opcode::BINARY, &vec![0; 65_536], None)[..10],
        [0x82, 0x7f, 0, 0, 0, 0, 0, 0x01, 0x00, 0x00]
    );
}

#[test]
fn a_length_has_its_shortest_form() {
    for (length, head) in [(125, 2), (126, 4), (65_535, 4), (65_536, 10)] {
        let frame = encode_frame(opcode::TEXT, &vec![b'x'; length], None);
        assert_eq!(frame.len(), head + length, "{length}");
        assert_eq!(frame_of(&frame, Role::Client).unwrap().payload.len(), length);
    }
}

#[test]
fn a_frame_that_breaks_the_protocol_is_refused() {
    assert_eq!(refusal(&[0x81, 0x00], Role::Server), "a frame of the client is not masked");
    assert_eq!(refusal(&[0x81, 0x80, 0, 0, 0, 0], Role::Client), "a frame of the server is masked");
    assert_eq!(refusal(&[0xc1, 0x00], Role::Client), "a frame sets a reserved bit");
    assert_eq!(
        refusal(&[0x09, 0x00], Role::Client),
        "a control frame is fragmented or longer than 125 bytes"
    );
    assert_eq!(
        refusal(&[0x89, 0x7e, 0x00, 0x7e], Role::Client),
        "a control frame is fragmented or longer than 125 bytes"
    );
    assert_eq!(
        refusal(&[0x81, 0x7f, 0, 0, 0, 0, 0x01, 0, 0, 0x01], Role::Client),
        "a frame of 16777217 bytes is longer than 16777216 bytes"
    );
    assert_eq!(
        frame_of(&[0x81, 0x05, 0x48], Role::Client).unwrap_err().kind(),
        ErrorKind::UnexpectedEof
    );
}

/// A connected pair: the client side as this module writes it, and the raw stream that the server side accepted.
fn raw_pair(role: Role) -> (Sender, Receiver, TcpStream) {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let ours = TcpStream::connect(listener.local_addr().unwrap()).unwrap();
    let (theirs, _) = listener.accept().unwrap();
    let reader = BufReader::new(ours.try_clone().unwrap());
    (Sender::new(ours, role), Receiver::new(reader, role), theirs)
}

#[test]
fn a_receiver_joins_the_fragments_answers_a_ping_and_drops_a_binary_message() {
    let (sender, mut receiver, mut theirs) = raw_pair(Role::Client);
    theirs.write_all(&[0x01, 0x03, b'H', b'e', b'l']).unwrap();
    theirs.write_all(&[0x89, 0x02, 7, 8]).unwrap();
    theirs.write_all(&[0x80, 0x02, b'l', b'o']).unwrap();
    theirs.write_all(&encode_frame(opcode::BINARY, b"dropped", None)).unwrap();
    theirs.write_all(&encode_frame(opcode::TEXT, &[b'a', 0xff], None)).unwrap();
    theirs
        .write_all(&encode_frame(opcode::CLOSE, &GOING_AWAY.to_be_bytes(), None))
        .unwrap();
    assert_eq!(receiver.next(&sender).unwrap(), Message::Text("Hello".to_owned()));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Text("a\u{fffd}".to_owned()));
    assert_eq!(receiver.next(&sender).unwrap(), Message::Close(Some(GOING_AWAY)));
    let pong = read_frame(&mut theirs, Role::Server).unwrap();
    assert_eq!((pong.opcode, pong.payload), (opcode::PONG, vec![7, 8]));
}

#[test]
fn a_continuation_without_a_message_and_a_message_inside_a_message_are_refused() {
    let (sender, mut receiver, mut theirs) = raw_pair(Role::Client);
    theirs.write_all(&[0x80, 0x01, b'x']).unwrap();
    let error = receiver.next(&sender).unwrap_err();
    assert_eq!(error.to_string(), "a continuation frame continues no message");
    let (sender, mut receiver, mut theirs) = raw_pair(Role::Client);
    theirs.write_all(&[0x01, 0x01, b'x', 0x81, 0x01, b'y']).unwrap();
    let error = receiver.next(&sender).unwrap_err();
    assert_eq!(error.to_string(), "a message starts before the last one ended");
}

#[test]
fn a_close_is_sent_once_ends_the_writing_direction_and_refuses_later_text() {
    let (sender, _receiver, mut theirs) = raw_pair(Role::Server);
    assert!(sender.close(INTERNAL_ERROR));
    assert!(!sender.close(NORMAL_CLOSURE));
    assert_eq!(sender.text("late").unwrap_err().kind(), ErrorKind::BrokenPipe);
    let mut rest = Vec::new();
    theirs.read_to_end(&mut rest).unwrap();
    assert_eq!(rest, [0x88, 0x02, 0x03, 0xf3]);
}

#[test]
fn a_client_masks_its_frames_with_a_new_key_each_time() {
    let (sender, _receiver, mut theirs) = raw_pair(Role::Client);
    sender.text("same").unwrap();
    sender.text("same").unwrap();
    let mut bytes = [0; 20];
    theirs.read_exact(&mut bytes).unwrap();
    assert_eq!(read_frame(&mut &bytes[..10], Role::Server).unwrap().payload, b"same");
    assert_eq!(read_frame(&mut &bytes[10..], Role::Server).unwrap().payload, b"same");
    assert_ne!(bytes[2..6], bytes[12..16], "each frame has its own mask");
}

#[test]
fn a_client_connects_with_the_upgrade_headers_and_checks_the_accept_key() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let server = thread::spawn(move || {
        let (stream, _) = listener.accept().unwrap();
        let mut reader = BufReader::new(stream.try_clone().unwrap());
        let mut head = Vec::new();
        while !head.ends_with(b"\r\n\r\n") {
            let mut byte = [0];
            reader.read_exact(&mut byte).unwrap();
            head.push(byte[0]);
        }
        let head = String::from_utf8(head).unwrap();
        let key = head
            .lines()
            .find_map(|line| line.strip_prefix("Sec-WebSocket-Key: "))
            .unwrap()
            .to_owned();
        (&stream)
            .write_all(
                format!(
                    "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: {}\r\n\r\n",
                    accept_key(&key)
                )
                .as_bytes(),
            )
            .unwrap();
        (&stream).write_all(&encode_frame(opcode::TEXT, b"welcome", None)).unwrap();
        (head, key)
    });
    let (sender, mut receiver) = connect(
        &format!("ws://{address}/control?token=secret"),
        &[("Authorization", "Bearer x")],
        Duration::from_secs(5),
    )
    .unwrap();
    assert_eq!(receiver.next(&sender).unwrap(), Message::Text("welcome".to_owned()));
    let (head, key) = server.join().unwrap();
    assert_eq!(
        head,
        format!(
            "GET /control?token=secret HTTP/1.1\r\nHost: {address}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\
             Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\nAuthorization: Bearer x\r\n\r\n"
        )
    );
    assert_eq!(Base64::Standard.decode(&key).map(|bytes| bytes.len()), Some(16));
}

#[test]
fn a_refused_upgrade_names_the_status_and_hides_the_query() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let server = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        let mut head = [0; 1024];
        let _ = stream.read(&mut head).unwrap();
        stream
            .write_all(b"HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")
            .unwrap();
    });
    let Err(error) = connect(&format!("ws://{address}/control?token=secret"), &[], Duration::from_secs(5)) else {
        panic!("a refused upgrade connects");
    };
    server.join().unwrap();
    assert_eq!(error.kind(), ErrorKind::ConnectionRefused);
    assert_eq!(
        error.to_string(),
        format!("the upgrade to ws://{address}/control answered \"HTTP/1.1 401 Unauthorized\"")
    );
}
