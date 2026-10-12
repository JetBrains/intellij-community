use std::time::Duration;

use avl_base::format::words;
use pretty_assertions::assert_eq;

use super::*;
use crate::answer::{answer_guest, said};
use avl_base::RefusalExt;

async fn exec(channel: &dyn Channel, argv: &[&str]) -> Captured {
    channel
        .exec(&Ctx::background(), &words(argv), &SpawnOptions::within(Duration::from_mins(1)))
        .await
        .expect("a fake channel answers")
}

// A worker's own answer wins over the pool's, the pool's over the silent default, and one log keeps the order across
// workers while each channel keeps its own.
#[tokio::test]
async fn a_channel_answers_its_own_then_the_pool_answer_and_both_logs_record() {
    let guests = FakeGuests::new();
    let factory = guests.factory();
    let (one, two) = (factory("air-linux-1"), factory("air-linux-2"));
    assert_eq!(exec(one.as_ref(), &["/bin/true"]).await, Captured::default());
    guests.answer(answer_guest(vec![("uname", said("Linux\n"))]));
    guests.channel("air-linux-2").answer(|_, _| Ok(said("own\n")));
    assert_eq!(exec(one.as_ref(), &["/bin/uname"]).await.stdout, "Linux\n");
    assert_eq!(exec(two.as_ref(), &["/bin/uname"]).await.stdout, "own\n");
    assert_eq!(guests.lines(), ["/bin/true", "/bin/uname", "/bin/uname"], "the pool log");
    let workers: Vec<String> = guests.calls().into_iter().map(|call| call.worker).collect();
    assert_eq!(workers, ["air-linux-1", "air-linux-1", "air-linux-2"]);
    assert_eq!(guests.channel("air-linux-2").lines(), ["/bin/uname"]);
    assert!(guests.channel("air-linux-1").saw_call_containing("bin/true"));

    // A test that shares the guests between its cases forgets the calls of one case before the next, and keeps the
    // answers.
    guests.forget_calls();
    assert!(guests.lines().is_empty() && guests.channel("air-linux-2").lines().is_empty());
    assert_eq!(exec(two.as_ref(), &["/bin/uname"]).await.stdout, "own\n");
    assert_eq!(guests.lines(), ["/bin/uname"]);
}

#[tokio::test]
async fn failing_everything_fails_every_worker() {
    let guests = FakeGuests::new();
    guests.fail_everything();
    assert_eq!(exec(guests.channel("any").as_ref(), &["/bin/true"]).await.exit_code, 1);
}

// A pool made for named workers treats any other name as a defect of the test, the way a manager would never
// ask for one.
#[test]
#[should_panic(expected = "air-macos-1 is not a worker of this pool")]
fn a_pool_of_named_workers_has_no_other_channel() {
    let guests = FakeGuests::of(&words(["air-linux-1", "air-linux-2"]));
    assert_eq!(guests.channels().len(), 2);
    guests.channel("air-macos-1");
}

// With no handler nothing listens, and the refusal comes at the first read with the shape the daemon suite matches:
// a connect that no test prepared is a port with no server, not a panic.
#[tokio::test]
async fn a_connect_with_no_handler_is_refused_at_the_first_read() {
    use tokio::io::{AsyncReadExt as _, AsyncWriteExt as _};
    let guests = FakeGuests::new();
    let channel = guests.channel("air-linux-1");
    let mut stream = channel
        .connect(&Ctx::background(), 1)
        .await
        .expect("a fake connect answers a stream");
    stream.write_all(b"GET / HTTP/1.1\r\n\r\n").await.expect("the request is accepted");
    let error = stream.read(&mut [0; 8]).await.unwrap_err();
    assert_eq!(
        (error.kind(), error.to_string()),
        (
            std::io::ErrorKind::ConnectionRefused,
            "nothing listens on 127.0.0.1:1 inside air-linux-1".to_owned()
        )
    );
    assert!(channel.saw_call_containing("relay"));
    assert_eq!(guests.lines(), ["relay 1"]);
}

// A worker's own handler wins over the pool's, and a served connect carries bytes both ways.
#[tokio::test]
async fn a_connect_answers_its_own_then_the_pool_handler() {
    use tokio::io::{AsyncReadExt as _, AsyncWriteExt as _};
    let guests = FakeGuests::new();
    guests.on_connect(serve_on_connect(|mut server| {
        tokio::spawn(async move {
            let mut request = [0; 4];
            server.read_exact(&mut request).await.unwrap();
            server.write_all(&request).await.unwrap();
        });
    }));
    guests
        .channel("air-linux-2")
        .on_connect(|port| Err(Refusal::internal(format!("port {port} refused"))));

    let mut stream = guests.channel("air-linux-1").connect(&Ctx::background(), 7654).await.unwrap();
    stream.write_all(b"ping").await.unwrap();
    let mut echoed = Vec::new();
    stream.read_to_end(&mut echoed).await.unwrap();
    assert_eq!(echoed, b"ping");

    let refusal = guests.channel("air-linux-2").connect(&Ctx::background(), 7654).await.unwrap_err();
    assert_eq!(refusal.message, "port 7654 refused");
    assert_eq!(guests.lines(), ["relay 7654", "relay 7654"]);
}
