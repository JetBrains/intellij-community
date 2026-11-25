# The recorder encodes the H.264 video and writes the fragmented MP4 itself

## Context

The recorder `air-trace-record` writes the `video.mp4` of each scenario. It reads the X11 root itself, over `x11rb`
and MIT-SHM, as BGRX frames at 10 frames per second. The frame index `video.index.json` is its own.

H.264 has hardware decode in Chrome and Safari on every Mac. The viewer seeks by the index.

These facts decide the options:

- The hermetic clang of the workspace already builds C and C++ for the `musl` targets. The crate `openh264-sys2`
  vendors the sources of Cisco, so a `cc_library` can compile them.
- The guest has no hardware encoder. Virtualization.framework passes no video encode engine to a Linux guest.
- The host cannot encode for the guest. Raw 1080p frames at 10 frames per second are about 83 MB/s.
- An external encoder program is the largest part of the worker image.

The requirements are in [Capture and video](../../../../spec/scenario-trace.spec.md#capture-and-video).

## Decisions

1. **The encoder is `openh264`, built from source.** A `cc_library` from
   [openh264-sys2.BUILD.bazel](../../../../openh264-sys2.BUILD.bazel) compiles the sources with the hermetic clang for
   each target platform. On aarch64 it also compiles the NEON assembly. The recorder uses the encoder only on Unix.
2. **The recorder has its own fragmented MP4 muxer.** The file has `ftyp`, one `moov` at the first keyframe, and an
   `moof` with its `mdat` for each keyframe. The `avcC` carries the SPS and the PPS out of band. A killed recorder
   leaves every complete fragment, and a fragment is at most one second long. At a clean stop the muxer writes the
   `mfra` trailer, and a player seeks by it.
3. **The encoder settings keep the earlier video.** The encoder runs in the low complexity mode on one thread, for
   screen content. The QP is fixed at 26, adaptive quantization is off, and the intra period is 10 frames. Scene
   change detection is on, and there are no B-frames. The encoder skips no input frame, so frame i of
   `video.index.json` is sample i of the video.
4. **The worker image installs no video encoder.** No encoder package is in `GUEST_PACKAGES`.
5. **The trace error source of an encoder failure is `video`.**

The code is [the H.264 module of the recorder](../../../../crates/avl-record/src/video/h264.rs).

## Alternatives rejected

- **`rav1e`, a pure-Rust AV1 encoder.** Safari decodes AV1 only in hardware, on M3 and later.
- **`x264` through `x264-sys`.** It is GPL.
- **`less-avc`, a pure-Rust H.264 encoder.** It is lossless only, and a lossless 1080p stream is too large.
- **`openh264` with `libloading` and the binary of Cisco.** The recorder is a static `musl` binary, and a static
  binary cannot `dlopen`.
- **A hardware encoder.** The guest has none, and an encoder on the host needs about 83 MB/s of raw frames from the
  guest.

## Consequences

- The worker image is smaller by the whole encoder chain.
- The recorder binary grows by about 0.9 MB.
- The first build compiles the C++ sources of openh264. The disk cache and the remote cache keep each action.
- openh264 takes a frame of at most 3840 by 2160, in either orientation. The recorder refuses a larger X screen at the
  start, and the bundle then has stills without a video.
- The muxer refuses a High profile SPS. openh264 encodes Baseline only.
- The muxer is code of the recorder. A round-trip test decodes what the recorder encoded. A muxer change must also
  open in Chrome and Safari before it lands.
- The recorder is an internal test tool and is not shipped. The patent grant of Cisco covers only its binaries, and
  the build from source is accepted for this tool.
