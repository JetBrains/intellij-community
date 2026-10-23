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
files as a share of the sum of the PNG files. The first table measures the encoder code of the recorder itself. The
libaom rows are AVIF 8-bit 4:4:4 with SSIM tuning. The CPU time is the median per picture on one thread of the studio
Mac, an M1 Ultra.

| encoder | size | mean | worst tenth | worst | CPU ms |
| --- | --- | --- | --- | --- | --- |
| `gamut-webp`, quality 90, before this record | 59.4% | 83.3 | 80.0 | 75.2 | 67 |
| libaom 3.11, speed 8, quality 55 | 27.8% | 84.1 | 79.5 | 76.7 | 78 |
| libaom 3.11, speed 8, quality 60, this record | 30.2% | 86.6 | 82.9 | 79.4 | 81 |
| libaom 3.11, speed 6, quality 50 | 19.8% | 82.0 | 80.6 | 79.4 | |
| libaom 3.11, speed 6, quality 55 | 22.8% | 85.1 | 84.2 | 82.8 | 1270 |
| libaom 3.11, speed 6, quality 60 | 25.0% | 86.9 | 86.0 | 85.6 | |

A second table of the same corpus came from the command line tools of the studio Mac, one thread each:

| encoder | size | mean | worst tenth | worst | CPU ms |
| --- | --- | --- | --- | --- | --- |
| `cwebp` (libwebp), quality 90 | 40.5% | 81.9 | 77.5 | 72.5 | 111 |
| `avifenc` (libaom 3.15), speed 6, quality 50, the `iq` tuning | 21.7% | 83.0 | 80.7 | 79.3 | 452 |
| `avifenc` (libaom 3.15), speed 6, quality 50, the SSIM tuning | 21.2% | 82.3 | 81.3 | 80.7 | 409 |
| `avifenc` (libaom 3.15), speed 8, quality 50 | 22.6% | 75.9 | 72.2 | 71.4 | 130 |
| `cavif` (rav1e, pure Rust), quality 80 | 35.6% | 83.2 | 80.7 | 79.2 | 1259 |
| `cjxl`, distance 1, effort 7 | 61.5% | 90.9 | 89.6 | 88.6 | 1098 |

Three facts follow. The `iq` tuning of libaom 3.12 and later is not better than the SSIM tuning on these pictures. The
speed presets of libaom 3.11 differ from those of 3.15. Speed 6 of 3.11 takes three times the CPU of 3.15, and speed 8
of 3.11 keeps its quality. And speed 6 of 3.11 is too slow for a lane. A lane run of `AirGenUiHostUiTest` on a Docker worker, with
speed 6, kept 30 attachments as PNG files. Its encoders were 4 pictures behind at the end of the 1.8 s budget.

A lossless picture is a different case. `image-webp`, the lossless encoder of the recorder, writes 116% of the PNG of
the IDE. libwebp at `-z 9` writes 51.2% of the PNG in 2939 ms, and at `-z 6` 88.0% in 708 ms. A lossless AVIF picture
of libaom 3.11 at speed 6 is 131.9% of the PNG in 1867 ms.

## Decision

1. **A lossy picture is AVIF from libaom.** This is a still of a scenario with a video and a PNG attachment of such a
   scenario. The picture is 8-bit YCbCr 4:4:4 with the BT.601 matrix in full range, at speed 8, with one thread per
   encode and SSIM tuning. The quality is 60 on the scale of `avifenc -q`, the quantizer 25. At 60 each of the three
   quality numbers beats the WebP that it replaces, at half its size and about its CPU. At 55 the worst tenth is
   lower. The files are `snap/NNNN.avif` and `attach/NNNN.avif`, of the media type `image/avif`.
2. **The lossless fallback of a lossy picture is AVIF too.** The snapshot record names its file before the encode,
   so the fallback keeps the format of that name. libaom then writes the picture lossless with the identity matrix.
   The `air.trace.error` of the fallback, and of every other problem of a picture, has the source `still`, which
   replaces `webp`.
3. **libaom comes from the `libaom-sys` crate.** Cargo builds it with the cmake of the crate. Bazel builds the same
   `vendor` directory with `aom/aom.BUILD.bazel` of this workspace, from the crates.io mirror. The x86_64 assembly
   takes the `nasm` module of the Bazel Central Registry. `aom/config/` holds the config headers that cmake writes for
   arm64 and for x86_64, and `aom/update.mjs` writes them and the source lists again. The recorder builds the encoder
   only, with no decoder and no high bit depth. The container comes from `avif-serialize`.
4. **A lossless picture stays WebP from `image-webp`, and a PNG attachment without a video stays a PNG.** libwebp
   saves a third of a lossless still and half of a PNG attachment only at `-z 9`, which takes 3 s per picture. A
   scenario without a video runs on a developer machine or on Windows, and never on CI. Windows builds no C codec of
   the recorder. So libwebp would be a new C build for the platform that has no lossy picture.
5. **Every reader names the type of a picture by its extension.** This is the Allure result, the thumbnail of
   discovery, the failure picture of `teamcity-publish`, `air-trace serve`, the packer, and the trace viewer. An older
   bundle holds only WebP, so its reader needs nothing new.

## Consequences

- The lossy pictures of the corpus take 13.7 MB instead of 26.9 MB. A lane run of the 12 scenarios of
  `AirGenUiHostUiTest` on a Docker worker passed with every attachment as AVIF. Its pictures took 12.1 MB, and the
  PNG attachments and WebP stills of the run before 2026-10-21 took 38.8 MB. Its zips took 30.0 MB instead of 55.2 MB.
- A lossy picture costs about 81 ms of CPU on one M1 Ultra core, against 67 ms for the WebP before. An x86_64 CI agent
  is slower. The writer keeps its encoders: one per two cores, at least 1 and at most 4, and four frames in its queue.
  One encode holds about 50 MB at 1920x1200.
- The x86_64 and the arm64 builds of libaom write the same bytes for the same picture. A check under Rosetta ran the
  x86_64 build through its SSE2 to AVX2 paths. The Linux x86_64 recorder is a static PIE, so the config of libaom sets
  `CONFIG_PIC`.
- The recorder closure loses the 7 gamut crates and `thiserror`. It gains `libaom-sys`, `avif-serialize` and
  `arrayvec`. The cmake crate is a build dependency of `libaom-sys` under cargo only.
- A version of libaom later than 3.11 needs a release of `libaom-sys`. A later libaom is faster at speed 6, so its
  update is a reason to measure the speed again.
- A browser without AVIF shows no lossy picture. Every Chrome and Safari that the viewer supports decodes AVIF.

## Alternatives rejected

- **Speed 6.** It saves 3.4 MB of the 13.7 MB of the corpus for 16 times the CPU, and it lost pictures in a lane.
- **The BCR module `aom`.** It downloads its source from `storage.googleapis.com` and `rules_nasm` from GitHub. The
  downloader config refuses both, and the cache redirector serves neither. It also brings rules_perl to the
  monorepo for the generated headers.
- **`libavif-sys` with its libaom.** Its bindings are of libavif 1.0.4 from 2024, and its build is cmake as well.
- **`shiguredo_aom`.** Its build script clones libaom from Git or downloads a prebuilt library.
- **`gamut-av1`.** It writes lossless AV1 only.
- **rav1e through `ravif`.** It is pure Rust, but it takes 15 times the CPU of libaom at speed 8 for a larger file.
- **libwebp, lossy.** `cwebp` at quality 90 is 40.5% of the PNG at a lower quality.
- **JPEG XL.** It is twice the size of the AVIF at 13 times its CPU on these pictures.
