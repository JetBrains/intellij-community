use std::sync::Arc;
use std::time::{Duration, Instant, UNIX_EPOCH};

use avl_trace::bridge::{PAINT_CONTENT_TYPE, PAINT_ROUTE, TOKEN_HEADER};

use super::*;
use crate::testing::{FakeHttp, Response};

#[test]
fn a_paint_becomes_a_bgrx_frame_stamped_when_it_was_asked_for() {
    let mut picture = Vec::new();
    for y in 0..4_u8 {
        for x in 0..5_u8 {
            picture.extend_from_slice(&[0xa0, x, y, 0xff]);
        }
    }
    let mut encoded = Vec::new();
    let mut encoder = png::Encoder::new(&mut encoded, 5, 4);
    encoder.set_color(png::ColorType::Rgba);
    let mut writer = encoder.write_header().unwrap();
    writer.write_image_data(&picture).unwrap();
    writer.finish().unwrap();
    let server = FakeHttp::start(move |request| {
        if request.path != PAINT_ROUTE || request.header(TOKEN_HEADER) != Some("token") {
            return Response::error(404, "not found");
        }
        Response::ok(PAINT_CONTENT_TYPE, encoded.clone())
    });

    let at = UNIX_EPOCH + Duration::from_millis(1_790_158_500_120);
    let source = IdePaint::new(Client::new(server.url(), "token").unwrap(), Arc::new(move || at));
    assert_eq!(source.kind(), CaptureSource::IdePaint);
    let mut frame = Frame::default();
    source.grab(Instant::now() + Duration::from_secs(5), &mut frame).unwrap();
    assert_eq!((frame.width, frame.height, frame.at), (5, 4, at));
    assert_eq!(&frame.row(3)[2 * 4..3 * 4], [3, 2, 0xa0, 0], "the pixel (2,3)");
}
