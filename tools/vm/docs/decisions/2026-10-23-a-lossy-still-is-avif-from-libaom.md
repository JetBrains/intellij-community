---
topic: testing
---

# A lossy still is AVIF from libaom

Date: 2026-10-23

## Status

Accepted. Issue [IJAI-4382](https://youtrack.jetbrains.com/issue/IJAI-4382). It supersedes the encoder of decision 1
and decision 3 of [A scenario with a video takes lossy stills](2026-10-21-a-scenario-with-a-video-takes-lossy-stills.md).
Its decision 2, the manifest field, stays. The behavior is stated in [the scenario trace spec](../../spec/scenario-trace.spec.md)
under **The bundle** and **Attachments**.

## Context

Since the record of 2026-10-21, a scenario with a video takes lossy WebP stills from `gamut-webp` at quality 90. Since
the same day, a PNG attachment of such a scenario becomes a lossy WebP picture too. The attachments are most of the
bytes of a bundle. A lane run of `AirGenUiHostUiTest` before that day had 12 bundles. They held 36.8 MB of PNG
attachments, 15.2 MB of video and 2.0 MB of stills.

The corpus is the 347 different PNG step pictures of the TeamCity build 1101705922, 45.4 MB in all. The quality is
SSIMULACRA2 against the PNG: the mean, the mean of the worst tenth, and the worst picture. The size is the sum of the
files as a share of the sum of the PNG files. The table measures the encoder code of the recorder itself. The libaom
rows are AVIF 8-bit 4:4:4. The CPU time is the median per picture on one thread of the studio Mac, an M1 Ultra.

| encoder | size | mean | worst tenth | worst | CPU ms |
| --- | --- | --- | --- | --- | --- |
| `gamut-webp`, quality 90, before this record | 59.4% | 83.3 | 80.0 | 75.2 | 67 |
| libaom 3.15.2, speed 6, `iq`, quality 50, this record | 24.6% | 86.4 | 84.4 | 83.4 | 356 |
| libaom 3.15.2, speed 6, `iq`, quality 55 | 27.0% | 88.3 | 86.6 | 85.9 | 371 |
| libaom 3.15.2, speed 6, `ssimulacra2`, quality 55 | 26.8% | 88.2 | 86.5 | 86.0 | 355 |
| libaom 3.15.2, speed 7, `iq`, quality 55 | 28.1% | 86.7 | 84.7 | 82.9 | 217 |
| libaom 3.15.2, speed 8, `iq`, quality 60 | 34.2% | 86.8 | 85.2 | 84.2 | 83 |
| libaom 3.11.0, speed 8, SSIM, quality 60 | 30.2% | 86.6 | 82.9 | 79.4 | 81 |
| libaom 3.11.0, speed 6, SSIM, quality 55 | 22.8% | 85.1 | 84.2 | 82.8 | 1270 |

The command line tools of the studio Mac gave these rows of the same corpus, one thread each:

| encoder | size | mean | worst tenth | worst | CPU ms |
| --- | --- | --- | --- | --- | --- |
| `cwebp` (libwebp), quality 90 | 40.5% | 81.9 | 77.5 | 72.5 | 111 |
| `avifenc` (libaom 3.15), speed 6, quality 50, the `iq` tuning | 21.7% | 83.0 | 80.7 | 79.3 | 452 |
| `cavif` (rav1e, pure Rust), quality 80 | 35.6% | 83.2 | 80.7 | 79.2 | 1259 |
| `cjxl`, distance 1, effort 7 | 61.5% | 90.9 | 89.6 | 88.6 | 1098 |

libaom 3.11.0 is the release that the crate `libaom-sys` vendors, and its speed 6 takes 1.27 s per picture. A lane run
of `AirGenUiHostUiTest` on a Docker worker with it kept 30 attachments as PNG files. Its encoders were 4 pictures
behind at the end of the 1.8 s budget. libaom 3.15 does speed 6 in a quarter of that time, and it has the `iq` tuning
for stills. The BCR module `aom` downloads its source from `storage.googleapis.com` and `rules_nasm` from GitHub. The
downloader config refuses both. So the release tarball is on the Space file mirror now, and it matches the git tag
`v3.15.2` file by file.

With libaom 3.15.2 at speed 6, two lane runs of `AirGenUiHostUiTest` on a Docker worker kept every attachment as AVIF.
No snapshot of them waited for the queue. One of the two runs had a red scenario. There the data model of an app view
had no status after its OnShow, 14 s into the step. The run after it passed all 12 scenarios.

A lossless picture is a different case. `image-webp`, the lossless encoder of the recorder, writes 116% of the PNG of
the IDE. libwebp at `-z 9` writes 51.2% of the PNG in 2939 ms, and at `-z 6` 88.0% in 708 ms.

## Decision

1. **A lossy picture is AVIF from libaom 3.15.2.** This is a still of a scenario with a video and a PNG attachment of
   such a scenario. The picture is 8-bit YCbCr 4:4:4 with the BT.601 matrix in full range, at speed 6, with one thread
   per encode and the `iq` tuning. The quality is 50 on the scale of `avifenc -q`, the quantizer 32. At 50 each of the
   three quality numbers beats the WebP that it replaces by 3 points or more, at 41% of its size. The files are
   `snap/NNNN.avif` and `attach/NNNN.avif`, of the media type `image/avif`.
2. **The lossless fallback of a lossy picture is AVIF too.** The snapshot record names its file before the encode,
   so the fallback keeps the format of that name. libaom then writes the picture lossless with the identity matrix.
   The `air.trace.error` of the fallback, and of every other problem of a picture, has the source `still`, which
   replaces `webp`.
3. **One release builds under Bazel and under cargo.** Bazel builds it with `aom/aom.BUILD.bazel` of this workspace.
   The x86_64 assembly takes the `nasm` module of the Bazel Central Registry. `aom/config/` holds the config headers
   that cmake writes for arm64 and for x86_64. The crate `avl-aom-sys` holds the bindings of the encoder, and its
   `build.rs` builds the same release with cmake under cargo. `aom/update.mjs` writes the headers, the source lists and
   the bindings again for a new release. The recorder builds the encoder only, with no decoder, no high bit depth and
   no Highway. The container comes from `avif-serialize`.
4. **A lossless picture stays WebP from `image-webp`, and a PNG attachment without a video stays a PNG.** libwebp
   saves a third of a lossless still and half of a PNG attachment only at `-z 9`, which takes 3 s per picture. A
   scenario without a video runs on a developer machine or on Windows, and never on CI. Windows builds no C codec of
   the recorder. So libwebp would be a new C build for the platform that has no lossy picture.
5. **Every reader names the type of a picture by its extension.** This is the Allure result, the thumbnail of
   discovery, the failure picture of `teamcity-publish`, `air-trace serve`, the packer, and the trace viewer. An older
   bundle holds only WebP, so its reader needs nothing new.

## Consequences

- The lossy pictures of the corpus take 11.2 MB instead of 26.9 MB. The pictures of a passed lane run of the 12
  scenarios of `AirGenUiHostUiTest` took 9.1 MB. The PNG attachments and WebP stills of the run before 2026-10-21
  took 38.8 MB. The zips took 23.6 MB instead of 55.2 MB.
- A lossy picture costs about 0.36 s of CPU on one M1 Ultra core, against 67 ms for the WebP before. A Linux container
  of the x86_64 build under Rosetta took 1.2 times the time of the arm64 build. An x86_64 CI agent is slower than the
  M1 Ultra. The writer keeps its encoders: one per two cores, at least 1 and at most 4, and four frames in its queue.
  Speed 8 is the fallback setting when a slower agent overflows the queue: it takes a quarter of the CPU for 39% more
  bytes.
- The x86_64 and the arm64 builds of libaom write the same bytes for the same picture. The Linux x86_64 recorder is a
  static PIE, so the config of libaom sets `CONFIG_PIC`.
- The recorder closure loses the 7 gamut crates and `thiserror`. It gains `avl-aom-sys`, `avif-serialize` and
  `arrayvec`. `cmake` and `sha2` are build dependencies of `avl-aom-sys` under cargo only.
- A cargo build downloads the release once into `OUT_DIR`. On an x86_64 host it needs nasm, as the cmake of libaom
  does.
- A browser without AVIF shows no lossy picture. Every Chrome and Safari that the viewer supports decodes AVIF.

## Alternatives rejected

- **libaom 3.11.0 from `libaom-sys`.** It needs no mirror, but its speed 6 lost pictures in a lane. Its speed 8 is 23%
  larger than 3.15 at speed 6, at a lower quality in the worst tenth.
- **Speed 8 of 3.15.** It takes a quarter of the CPU, for 39% more bytes at quality 60.
- **The quality 55.** It is 10% larger than 50, and 50 already beats the WebP before it.
- **The BCR module `aom`.** It downloads from hosts that the downloader config refuses, and it brings rules_perl to
  the monorepo for the generated headers.
- **`libavif-sys` with its libaom.** Its bindings are of libavif 1.0.4 from 2024, and its build is cmake as well.
- **`shiguredo_aom`.** Its build script clones libaom from Git or downloads a prebuilt library.
- **`gamut-av1`.** It writes lossless AV1 only.
- **rav1e through `ravif`.** It is pure Rust, but its file is 1.4 times the size of libaom, at 3.5 times the CPU.
- **libwebp, lossy.** `cwebp` at quality 90 is 40.5% of the PNG at a lower quality.
- **JPEG XL.** It is 2.5 times the size of the AVIF at 3 times its CPU on these pictures.
