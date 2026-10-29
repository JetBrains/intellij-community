---
topic: testing
---

# A scenario with a video takes lossy stills

Date: 2026-10-21

## Status

Accepted. Issue [IJAI-4382](https://youtrack.jetbrains.com/issue/IJAI-4382). It amends decision 6 of
[ADR 0155](../../../../plugins/air/docs/decisions/0155-a-ui-scenario-leaves-a-trace.md), which makes every still
lossless WebP. The behavior is stated in [the scenario trace spec](../../spec/scenario-trace.spec.md) under **The
bundle**.

Amended 2026-10-23: [A lossy still is AVIF from libaom](2026-10-23-a-lossy-still-is-avif-from-libaom.md) replaces
the encoder of decision 1 and decision 3. A lossy still is AVIF now, and decision 2 stays.

## Context

Since [A TeamCity lane run records video and keeps its evidence](2026-10-20-a-teamcity-lane-run-records-video-and-keeps-its-evidence.md),
a TeamCity build records the screen. The personal build 1101267129 shows the cost. A median bundle is 6.5 MB. Its 4
stills of the 1920x1200 screen take 3.7 MB, and its video takes 2 MB. One shard publishes 413 MiB of traces. The
desktop wallpaper of the CI screen is a gradient with grain, and a lossless encoder cannot make it small.

The measurement took the 4 stills of that median bundle. The CPU time is per frame on an Apple M-series Mac. The load
average was 60 to 160, so the wall time was not usable.

| encoder | bytes | CPU ms | PSNR dB | SSIM of the text crop |
| --- | --- | --- | --- | --- |
| `image-webp` lossless, before this record | 922 KB | 8 | lossless | 1 |
| `gamut-webp` lossy, quality 90 | 74 KB | 62 | 39.6 | 0.998 |
| libwebp lossy, quality 90 (C) | 58 KB | 81 | 39.7 | 0.997 |
| `webp-rust` lossy, quality 90, method 0 | 92 KB | 29 | 39.2 | 0.992 |
| `webp-rust` lossy, quality 90, method 1 or 2 | 61 KB | 182 | 39.1 | 0.992 |
| `webp-rust` lossy, quality 90, method 3 or 4 | 58 KB | 505 | 39.1 | 0.992 |
| `jpeg-encoder`, quality 90, 4:2:0 | 151 KB | 9 | 38.3 | 0.992 |

A stress frame of UI text over the full screen takes 678 KB lossless and 359 KB with `gamut-webp` at quality 90.
libwebp and ImageIO, the decoder of Safari, decode each lossy file to the same pixels as `image-webp`. That also holds
at the sizes 1x1, 17x9, 1366x768, 1921x1199 and 3840x2160. The Diff of the viewer compares two stills with
`pixelmatch` at the threshold 0.1. Its color test marks almost the same pixels between two lossy stills as between
the two lossless stills. The counts are 470 against 427 of 2.3 million, and 66015 against 66439. The counts are at
the full size, and they leave out the antialias pass of `pixelmatch`. The viewer compares at a quarter of each side,
so it marks fewer pixels than these counts.

The two pure Rust WebP encoders are young. A check of their crates and repositories on 2026-10-21 found no problem
that rules out `gamut-webp`:

| | `gamut-webp` | `webp-rust` |
| --- | --- | --- |
| first release | 2026-06-01 | 2026-03-13 |
| releases | 4, the last 0.3.1 on 2026-08-05 | 6, the last 0.3.2 on 2026-09-26 |
| downloads | 1,600, and 187,000 of `gamut-core` | 7,400 |
| maintainers | 1, with 2,215 commits, 366 of them in the last 30 days | 1, with 47 commits |
| `unsafe` | forbidden or denied in `gamut-webp` and the 5 crates of its encoder, and none of them uses it | none, and 1 block in `bin-rs` |
| tests | mutation surveys, libwebp as the oracle in both directions, a robustness corpus | its own tests |
| open soundness issues, RustSec advisories | none, none | none, none |
| license | MIT OR Apache-2.0 | MIT |

The only `unsafe` of the `gamut-webp` closure is the C bridge of `gamut-codec-abi`. Only a C backend that a caller
pushes runs it, and the recorder pushes none. The open issue 311 of `gamut` asks for an audit of the decoders. The
recorder only encodes its own frames. The open issue 826 says that the release workflow stalls. A fix can take longer
to reach crates.io than to reach the repository. The Bazel builds of `air-trace-record` for aarch64 and x86_64 musl
pass with `gamut-webp`.

## Decision

1. **A scenario that starts a video takes lossy stills.** The encoder is `gamut-webp` at quality 90. A scenario
   without a video keeps lossless stills from `image-webp`. The scenario picks the encoding once, after it starts
   the video. A video that fails later leaves the stills lossy. When the lossy encoder fails or panics on a still, the
  recorder writes that still lossless, and an `air.trace.error` names its file.
2. **The manifest names lossy stills.** The field is `"stills": {"encoding": "lossy"}`. A manifest without the field
   has lossless stills, as every bundle had before this record. So the manifest of a bundle without a video does not
   change.
3. **The stills stay WebP.** The file names, the viewer and the archive stay the same. The repetition rule stays
   the same too, because the recorder compares the grabbed frames before it encodes them.

## Consequences

- A median CI still goes from about 920 KB to about 75 KB. The median CI bundle goes from 6.5 MB to about 3 MB, and
  its video is then its largest file.
- A still of a scenario with a video is not the exact screen. Antialiased text gets faint color fringes. The video
  has the same loss, and the Swing tree still has the exact text.
- A lossy still costs about 60 ms of CPU, against 8 ms for a lossless one. A median scenario takes 4 stills. One
  encoder thread is too slow for a burst, and a snapshot of the burst then waits for a free place in the queue. So
  the recorder runs one encoder per two cores, at least 1 and at most 4.
- The table has 6 runs of a burst of 8 changed lossy stills. The Mac has 18 cores, and its load average was 27 to 35.

  | encoders | wall ms, median (range) | CPU ms | longest snapshot wait ms, median | sum of the waits ms, median |
  | --- | --- | --- | --- | --- |
  | 1 | 1182 (622 to 3020) | 492 to 552 | 171 | 426 |
  | 4 | 249 (130 to 794) | 507 to 548 | 1 | 5 |
- The recorder links 8 more crates: the 7 crates `gamut-webp`, `gamut-core` (in versions 2 and 3), `gamut-color`,
  `gamut-dsp`, `gamut-riff`, `gamut-bitstream` and `gamut-codec-abi`, and `thiserror`. All are pure Rust. None of the
  gamut crates has a build script. `thiserror` has one, and the workspace already builds it.
- The manifest type of the viewer names the field, so the viewer can label a lossy still. It shows no label yet.
- A reader built before the field refuses the manifest of each bundle with a video, with "unknown field `stills`".
  So an older `trace.cmd serve` lists such a bundle as invalid. Restart an older `trace.cmd serve`.

## Alternatives rejected

- **libwebp through `libwebp-sys`.** It is the smallest at the same quality. The guest builds already compile C++ for
  OpenH264, but libwebp needs a Bazel build file of its own, as `openh264-sys2.BUILD.bazel` is for OpenH264.
  `gamut-webp` is 16 KB larger per still, and it takes 20 ms less CPU.
- **`webp-rust`.** It adds 2 crates instead of 8. Its method 0 is the only one below 80 ms. A still is then 92 KB,
  1.24 times the size of `gamut-webp`, at a lower quality. The stress frame is 476 KB. Its methods 1 and 2 give 61 KB
  with three times the CPU of `gamut-webp`. The user accepts the 8 crates for the smaller and better still.
- **JPEG through `jpeg-encoder`.** It is twice the size at a lower PSNR, and it changes the file format of the viewer
  and the archive.
- **`zenwebp`.** Its license is AGPL-3.0.
- **`oxideav-webp`.** It takes 263 ms per still.
- **A better lossless encoder.** libwebp lossless is 708 KB per still and takes 700 ms. The grain of the wallpaper
  stays.
