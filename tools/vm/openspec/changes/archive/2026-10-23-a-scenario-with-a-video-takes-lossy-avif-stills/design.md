# A scenario with a video takes lossy AVIF pictures from libaom

## Context

A TeamCity build records the screen, as
[the TeamCity lane run decision](../2026-10-20-a-teamcity-lane-run-records-video-and-keeps-its-evidence/design.md)
states. The lossless stills and the PNG attachments are then most of the bytes of a bundle. A lossless encoder cannot
make a screen picture small.

The video of the scenario already shows each moment with loss. The Swing tree still has the exact text.

libaom 3.15 has the `iq` tuning for stills, and its speed 6 keeps pace with a lane. On the step pictures of a CI build,
lossy AVIF at quality 50 takes about 41% of the size of lossy WebP. It also scores higher on SSIMULACRA2.

The requirements are in [The bundle](../../../../spec/scenario-trace.spec.md#the-bundle).

## Decisions

1. **A scenario that starts a video takes lossy pictures.** The scenario picks the encoding once, after it starts the
   video. A video that fails later leaves the pictures lossy. A scenario without a video keeps lossless pictures.
2. **The manifest names lossy pictures.** The field is `"stills": {"encoding": "lossy"}`. A manifest without the field
   has lossless pictures.
3. **A lossy picture is AVIF from libaom 3.15.2.** This covers a still and a PNG attachment of a scenario with a video.
   The picture is 8-bit YCbCr 4:4:4 with the BT.601 matrix in full range. The encoder runs at speed 6, with one thread
   per encode and the `iq` tuning. The quality is 50 on the scale of `avifenc -q`. The files are `snap/NNNN.avif` and
   `attach/NNNN.avif`. The code is [avif.rs](../../../../crates/avl-record/src/avif.rs) and
   [stills.rs](../../../../crates/avl-record/src/stills.rs).
4. **The fallback of a failed lossy encode is a lossless AVIF picture.** The snapshot record names its file before the
   encode, so the fallback keeps that format. An `air.trace.error` with the source `still` names the file.
5. **One libaom release builds under Bazel and under cargo.** Bazel uses
   [aom.BUILD.bazel](../../../../aom/aom.BUILD.bazel) and the `nasm` module of the Bazel Central Registry. The crate
   `avl-aom-sys` holds the bindings, and its `build.rs` builds the same release with cmake.
   [update.mjs](../../../../aom/update.mjs) writes the headers, the source lists and the bindings for a new release.
   The container comes from `avif-serialize`.
6. **A lossless picture stays WebP from `image-webp`, and a PNG attachment without a video stays a PNG.** A scenario
   without a video runs on a developer machine or on Windows, never on CI. Windows builds no C codec of the recorder.
7. **Every reader names the type of a picture by its extension.** An older bundle holds only WebP, so its reader needs
   nothing new.

## Alternatives rejected

- **libaom 3.11.0 from `libaom-sys`.** Its speed 6 is too slow, and a lane run lost pictures with it.
- **Speed 8 of libaom 3.15.** It writes 39% more bytes. It stays the fallback when a slower agent overflows the queue.
- **The quality 55.** It is 10% larger, and 50 already beats the WebP that it replaces.
- **The BCR module `aom`.** It downloads from hosts that the downloader config refuses, and it needs rules_perl.
- **`libavif-sys` with its libaom.** Its bindings are old, and its build is cmake.
- **`shiguredo_aom`.** Its build script clones libaom or downloads a prebuilt library.
- **`gamut-av1`.** It writes lossless AV1 only.
- **rav1e through `ravif`.** It writes 1.4 times the size at 3.5 times the CPU.
- **Lossy WebP from libwebp.** It writes 40.5% of the PNG at a lower quality.
- **JPEG XL.** It writes 2.5 times the size at 3 times the CPU.
- **JPEG through `jpeg-encoder`.** It writes about twice the size at a lower quality, and it adds a format.

## Consequences

- The pictures of a lane run take about a quarter of the bytes of the PNG and lossless WebP files.
- A lossy picture is not the exact screen. Antialiased text gets faint color fringes.
- A lossy picture costs about 0.36 s of CPU on one core. The writer runs one encoder per two cores, at least 1 and at
  most 4, with four frames in its queue.
- The x86_64 and the arm64 builds of libaom write the same bytes for the same picture.
- A cargo build downloads the release once into `OUT_DIR`. On an x86_64 host it needs nasm.
- A reader built before the `stills` field refuses the manifest of a bundle with a video.
- A browser without AVIF shows no lossy picture. Every Chrome and Safari that the viewer supports decodes AVIF.
