---
topic: testing
---

# 185. The recorder encodes its own video

Date: 2026-09-29

## Status

Accepted. It supersedes the `ffmpeg` half of the "Two levers stay open" consequence of
[ADR 0184](0184-the-worker-image-is-pulled-by-its-content-tag.md). The GTK half of that consequence stands. The
recorder is `plugins/air/tests/integration/vm-lane/crates/avl-record`. The operator view of the traces is
[the VM guide](../vm-ui-tests.md), and the behavior is [`community/tools/vm/spec/scenario-trace.spec.md`](../../spec/scenario-trace.spec.md).

## Context

The recorder `air-trace-record` writes the `video.mp4` of each scenario. In raw mode it reads the X11 root itself,
over `x11rb` and MIT-SHM, as BGRX frames at 10 frames per second. It then wrote each frame to the standard input of
an `ffmpeg` process as `rawvideo bgr0`. ffmpeg only encoded and muxed. The frame index `video.index.json` was
always the recorder's own.

The encoding that ffmpeg did:

- libx264 with the preset `veryfast` and CRF 26.
- A keyframe every 10 frames, which is one second (`-g 10`).
- No B-frames (`-bf 0`). The decode time and the presentation time are then the same, and the first frame plays
  at 0.
- `yuv420p`, with an odd frame size cropped to even.
- Fragmented MP4 with `+frag_keyframe+empty_moov+default_base_moof`. Every fragment plays on its own, so a killed
  encoder still leaves a video up to its last complete second.

H.264 has hardware decode in Chrome and Safari on every Mac. `VideoCodec` in `avl-trace` names `h264` and `none`,
`avl-trace-serve` serves `video.mp4` as `video/mp4`, and the viewer seeks by the index.

The package audit of ADR 0184 measured the cost. The exclusive closure of `ffmpeg` is 138 MB of the 691 MB that
the Linux worker image installs. Ubuntu's `libavfilter` pulls `flite`, ghostscript, `poppler-data`, `codec2` and
`placebo`. It is the largest part of the image that a Rust program can replace.

These facts decided the options:

- **The C++ cross toolchain already existed.** `community/MODULE.bazel` registers the hermetic clang of `@llvm`,
  which `rules_rs` links with. `build/native/platforms/defs.bzl` names the `musl` libc constraint. Fleet's native
  crates already build C code for `aarch64-unknown-linux-musl` through it. The `openh264-sys2` crate vendors
  Cisco's sources, and its build script only runs the `cc` crate over them. So a `crate.annotation` in
  `avl.MODULE.bazel` turns the build script off. It gives the crate a `cc_library` over the same files, from
  `openh264-sys2.BUILD.bazel`. The hermetic clang links that library against libc++. On aarch64 the library
  also compiles the NEON assembly. On x86_64 it compiles no assembly, because the x86 assembly needs `nasm`.
- **The guest has no hardware encoder.** Virtualization.framework passes no video encode engine to a Linux guest.
  There is no VideoToolbox, no VA-API device and no mainline `virtio-video`. libx264 in the guest was software too.
- **The host cannot encode for the guest.** Raw 1080p frames at 10 frames per second are about 83 MB/s. The guest
  would stream them out, and the host would then hold a guest artifact.
- **Screen mode is macOS only.** On a macOS host with the screen opt-in, ffmpeg captures the screen through
  `avfoundation`. The recorder never sees those frames, and no ScreenCaptureKit binding exists in the recorder.

## Decision

**The recorder encodes the X11 frames to H.264 itself and writes the fragmented MP4 itself. The Linux guest
installs no video encoder.**

1. **The encoder is `openh264`, built from source in the recorder.** The crate is BSD-2 and bundles Cisco's C++
   sources. A `cc_library` compiles them with the hermetic clang for each target platform. The recorder uses it
   only on Unix.
2. **The recorder has its own fragmented MP4 muxer.** The box shape is the one ffmpeg wrote:
   - `ftyp` with the major brand `iso5` and the compatible brands `iso5`, `iso6` and `mp41`.
   - One `moov` at the first keyframe. It holds `mvhd`, a `trak` with an `avc1` sample entry and an `avcC`, and an
     `mvex` with a `trex`. The `avcC` carries the SPS and the PPS out of band, so the samples do not repeat them.
   - For each keyframe, an `moof` with `mfhd` and a `traf` of `tfhd`, `tfdt` and `trun`, then its `mdat`. The
     `trun` offsets count from the `moof`, as `default_base_moof` does.
   - The muxer writes a fragment at each new keyframe and at the stop. A killed recorder leaves every complete
     fragment, and a fragment is at most one second long.
   - At a clean stop the muxer writes the `mfra` trailer, with one `tfra` entry for each fragment, as ffmpeg did.
     A player seeks by it. A killed recorder leaves no `mfra`, and a player then scans the `moof` boxes.
3. **The encoder settings replace the ffmpeg settings one for one.**

   | ffmpeg | openh264 |
   |---|---|
   | `-preset veryfast` | the low complexity mode, on one thread, for the screen-content usage type |
   | CRF 26 | a fixed QP 26 in place of CRF 26: x264 varied the QP for each frame, and openh264 with rate control off does not |
   | adaptive quantization of x264 | adaptive quantization off, so the quantizer is flat over the frame |
   | `-g 10` | an intra period of 10 frames, which is one second |
   | scene cut detection of x264 | scene change detection on, which the screen-content mode keeps |
   | `-bf 0` | nothing: openh264 has no B-frames |

   The encoder skips no input frame. Frame i of `video.index.json` is then sample i of the video. A repeated
   picture costs only skipped macroblocks, as it did with x264.

   A frame that changes most of the screen is a keyframe too, and it starts a fragment of its own. The intra
   period still holds, so the gap between two keyframes is at most one second.
4. **ffmpeg stays for macOS screen mode.** A Mac with `ffmpeg` on the path records the screen, as before. A Mac
   without it records no screen video, and the manifest says why.
5. **`ffmpeg` leaves `GUEST_PACKAGES`.** The Docker image reads the list as a build argument, so the image tag
   moves with it.
6. **The trace error source `ffmpeg` becomes `video`.** An encoder failure is not an ffmpeg failure now.

## Consequences

- **The image is smaller.** The tag `fbe324ef6aa1` installs 528 MB. The previous tag `77bf961a7ac3` installs
  675 MB by the same `dpkg-query` sum over `Installed-Size` on the same day. ADR 0184 reported 691 MB for it with
  its own count. `docker image ls` says 741 MB against 970 MB. The tag was published for `linux/arm64` and
  `linux/amd64` on 2026-09-29.
- **The recorder binary grows.** `air-trace-record` for `aarch64-unknown-linux-musl` grows by about 0.9 MB, from
  6.4 MB to 7.3 MB (6,373,848 to 7,282,320 bytes).
- **The first build compiles C++.** For `aarch64-unknown-linux-musl`, Bazel compiles the openh264 sources in 103
  actions: 86 C++ files and 17 NEON assembly files. For x86_64 it compiles the 86 C++ files. The actions run in
  parallel. A cold aarch64 build of them took 15 s on an 18-core Mac on 2026-10-02, from the first compile to the
  last archive. The build script took 37 s as one action. The disk cache on a Mac and the remote cache on CI keep
  each action, so a build pays it once for each toolchain change.
- **A screen past the size limit of openh264 gets no video.** openh264 takes a frame whose longer side is at most
  3840 and whose shorter side is at most 2160, in either orientation. So a portrait 2160x3840 screen is taken. The
  recorder refuses a larger X screen at the start, and the bundle says why. libx264 had no such limit. A Linux host
  with a larger screen and `AIR_TRACE_SCREEN=on` now records stills without a video.
- **The muxer refuses a High profile SPS.** It writes no `avcC` extension fields, which a High profile needs.
  openh264 encodes Baseline, so the recorder never gives it one.
- **The muxer is the recorder's own code.** A malformed box is a video that plays in one browser and not in
  another. A round-trip test decodes what the recorder encoded. The video must also open in Chrome and Safari
  before a muxer change lands.
- **Legal.** The recorder is an internal test tool, and it is not shipped. Cisco's patent grant covers only
  Cisco's binaries. The user accepted the build from source for this tool.
- **A Tart worker provisioned earlier keeps its `ffmpeg` until it is recycled.** Nothing uses that package now, so
  it costs only disk.

## Alternatives rejected

- **`rav1e`, a pure-Rust AV1 encoder.** Safari decodes AV1 only in hardware, on M3 and later, so a trace on an
  older Mac has no video in Safari.
- **`x264` through `x264-sys`.** It is GPL.
- **`less-avc`, a pure-Rust H.264 encoder.** It is lossless only, and a lossless 1080p stream is too large.
- **`openh264` with `libloading` and Cisco's binary.** The recorder is a static `musl` binary, and a static binary
  cannot `dlopen`.
- **A hardware encoder.** The guest has none. An encoder on the macOS host needs about 83 MB/s of raw frames
  out of the guest, and the Context says why that is refused.
