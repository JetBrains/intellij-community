//! The fragmented MP4 writer of a raw video: one H.264 track, in the layout ffmpeg's
//! `+frag_keyframe+empty_moov+default_base_moof` writes.
//!
//! The file starts with `ftyp` and a `moov` that lists no sample. Then each keyframe starts a fragment, a `moof`
//! that lists the samples and the `mdat` that holds them. A fragment is written whole when the next keyframe or the
//! stop arrives, so a recorder killed mid-scenario leaves a file that plays to its last complete fragment. At the
//! encoder's keyframe interval that is its last complete second.
//!
//! A clean stop ends the file with `mfra`, the index of its fragments that ffmpeg writes too. A player can seek by
//! it. A killed recorder leaves no `mfra`, and a player then finds the fragments by their `moof`s.
//!
//! A write that fails part way, as on a full disk, breaks the writer: it takes no later frame and writes no `mfra`,
//! so the file ends at its last complete fragment and some bytes of a cut one.
//!
//! The track's parameter sets live in the sample entry (`avc1` with its `avcC`), so a sample holds only the slices
//! of one frame, each prefixed with its length as four bytes. Samples have no composition offsets: the encoder makes
//! no B-frames, so a frame's decode time is its presentation time, and frame i plays at i/[FRAME_RATE], which is
//! what the frame index says.

use std::fs::File;
use std::io::{self, Write};
use std::path::Path;

use super::FRAME_RATE;

#[cfg(test)]
pub(super) mod tests;

/// The track's time units per second. Milliseconds, the unit of the frame index.
const TIMESCALE: u32 = 1000;

/// How long one frame plays, in [TIMESCALE] units.
const SAMPLE_DURATION: u32 = TIMESCALE / FRAME_RATE;

const _: () = assert!(
    TIMESCALE.is_multiple_of(FRAME_RATE),
    "a frame's duration must be a whole number of time units"
);

/// The one track's id.
const TRACK_ID: u32 = 1;

/// The flags of a sample that depends on others and is not a sync sample: every frame but a fragment's first.
const NON_SYNC_SAMPLE_FLAGS: u32 = 0x0101_0000;

/// The flags of a sample that depends on no other one and is a sync sample: a fragment's first frame, a keyframe.
const SYNC_SAMPLE_FLAGS: u32 = 0x0200_0000;

/// `tfhd` flags: the fragment's data offsets count from its `moof`, and it has a default sample duration and
/// default sample flags.
const TFHD_FLAGS: u32 = 0x02_0028;

/// `trun` flags: a data offset, the first sample's own flags, and a size per sample.
const TRUN_FLAGS: u32 = 0x00_0205;

/// The H.264 profiles whose `avcC` has no fields past its parameter sets.
const PROFILE_BASELINE: u8 = 66;
const PROFILE_MAIN: u8 = 77;
const PROFILE_EXTENDED: u8 = 88;

/// The identity transform of `mvhd` and `tkhd`.
const MATRIX: [u32; 9] = [0x0001_0000, 0, 0, 0, 0x0001_0000, 0, 0, 0, 0x4000_0000];

/// A fragmented MP4 that is being written, to a file unless a test gives another writer.
pub(crate) struct FragmentedMp4<W: Write = File> {
    file: W,
    /// The `mdat` payload of the fragment not yet written.
    pending: Vec<u8>,
    /// The sizes of the samples in [FragmentedMp4::pending].
    sizes: Vec<u32>,
    /// The index of the pending fragment's first sample among all the samples of the file.
    first_sample: u64,
    /// The sequence number of the last fragment written, 0 before the first.
    sequence: u32,
    /// The bytes written to the file so far.
    written: u64,
    /// The decode time and the `moof` offset of each fragment written, which the `mfra` lists.
    fragments: Vec<(u64, u64)>,
    /// A write failed. The file's end is then unknown, and a later write would put a box after a cut one.
    broken: bool,
}

impl FragmentedMp4 {
    /// Creates `path` for a `width` x `height` track and writes its header. `sps` and `pps` are the encoder's
    /// parameter sets, each one NAL unit without its start code.
    pub(crate) fn start(path: &Path, width: usize, height: usize, sps: &[u8], pps: &[u8]) -> io::Result<Self> {
        // The header is built first, so that a track this writer cannot describe leaves no file.
        let header = header(width, height, sps, pps)?;
        Self::open(File::create(path)?, &header)
    }
}

impl<W: Write> FragmentedMp4<W> {
    /// Writes a `width` x `height` track's header to `out`, as [FragmentedMp4::start] does to its file.
    #[cfg(test)]
    pub(super) fn to_writer(out: W, width: usize, height: usize, sps: &[u8], pps: &[u8]) -> io::Result<Self> {
        Self::open(out, &header(width, height, sps, pps)?)
    }

    fn open(mut file: W, header: &[u8]) -> io::Result<Self> {
        file.write_all(header)?;
        Ok(Self {
            file,
            pending: Vec::new(),
            sizes: Vec::new(),
            first_sample: 0,
            sequence: 0,
            written: header.len() as u64,
            fragments: Vec::new(),
            broken: false,
        })
    }

    /// Adds one frame. `sample` holds the frame's NAL units, each prefixed with its length as four big-endian bytes
    /// and without its start code. A keyframe first writes the pending fragment, then starts the next one.
    pub(crate) fn push(&mut self, keyframe: bool, sample: &[u8]) -> io::Result<()> {
        self.check()?;
        if keyframe {
            self.flush()?;
        } else if self.sizes.is_empty() {
            return Err(invalid("a fragment must start with a keyframe"));
        }
        let size = fit(sample.len(), "a sample")?;
        self.pending.extend_from_slice(sample);
        self.sizes.push(size);
        Ok(())
    }

    /// Writes the pending fragment, then the `mfra`. The file is not synced: no other file of the bundle is, and a
    /// sync on a shared folder of the VM can hold the stop for an unbounded time.
    pub(crate) fn finish(mut self) -> io::Result<()> {
        self.check()?;
        self.flush()?;
        self.file.write_all(&self.mfra())
    }

    /// Refuses to go on after a failed write.
    fn check(&self) -> io::Result<()> {
        if self.broken {
            return Err(invalid(
                "the video file is broken after a failed write, and ends at its last complete fragment",
            ));
        }
        Ok(())
    }

    /// Passes a write's `result` on, and breaks the writer if it failed.
    const fn wrote(&mut self, result: io::Result<()>) -> io::Result<()> {
        if result.is_err() {
            self.broken = true;
        }
        result
    }

    /// Writes the pending fragment, if there is one: its `moof`, then its `mdat`.
    fn flush(&mut self) -> io::Result<()> {
        if self.sizes.is_empty() {
            return Ok(());
        }
        let mdat_size = fit(self.pending.len() + 8, "a fragment")?;
        self.sequence += 1;
        let mut head = self.moof();
        head.put_u32(mdat_size);
        head.extend_from_slice(b"mdat");
        let result = self.file.write_all(&head).and_then(|()| self.file.write_all(&self.pending));
        self.wrote(result)?;
        self.fragments.push((self.decode_time(), self.written));
        self.written += (head.len() + self.pending.len()) as u64;
        self.first_sample += self.sizes.len() as u64;
        self.pending.clear();
        self.sizes.clear();
        Ok(())
    }

    /// The pending fragment's `moof`.
    fn moof(&self) -> Vec<u8> {
        let mut moof = Vec::new();
        boxed(&mut moof, *b"moof", |moof| {
            full(moof, *b"mfhd", 0, 0, |mfhd| mfhd.put_u32(self.sequence));
            boxed(moof, *b"traf", |traf| {
                full(traf, *b"tfhd", 0, TFHD_FLAGS, |tfhd| {
                    tfhd.put_u32(TRACK_ID);
                    tfhd.put_u32(SAMPLE_DURATION);
                    tfhd.put_u32(NON_SYNC_SAMPLE_FLAGS);
                });
                full(traf, *b"tfdt", 1, 0, |tfdt| {
                    tfdt.put_u64(self.decode_time());
                });
                full(traf, *b"trun", 0, TRUN_FLAGS, |trun| {
                    trun.put_u32(header_u32(self.sizes.len()));
                    // The data offset, patched below once the size of the `moof` is known.
                    trun.put_u32(0);
                    trun.put_u32(SYNC_SAMPLE_FLAGS);
                    for &size in &self.sizes {
                        trun.put_u32(size);
                    }
                });
            });
        });
        // `trun` is the last box of the `moof`, and its sizes end it, so the data offset sits right before the first
        // sample's flags and the sizes. The samples start after the `moof` and the 8 bytes of the `mdat` header.
        let at = moof.len() - 4 * self.sizes.len() - 8;
        let data_offset = header_u32(moof.len() + 8);
        moof[at..at + 4].copy_from_slice(&data_offset.to_be_bytes());
        moof
    }

    /// The decode time of the pending fragment's first sample.
    fn decode_time(&self) -> u64 {
        self.first_sample * u64::from(SAMPLE_DURATION)
    }

    /// The trailer: a `tfra` that lists each fragment's decode time and `moof` offset, then the `mfro` that gives the
    /// size of the whole `mfra`, so that a reader can find it from the end of the file.
    fn mfra(&self) -> Vec<u8> {
        let mut mfra = Vec::new();
        boxed(&mut mfra, *b"mfra", |mfra| {
            full(mfra, *b"tfra", 1, 0, |tfra| {
                tfra.put_u32(TRACK_ID);
                // 26 reserved bits, then the traf, trun and sample numbers of an entry take one byte each.
                tfra.put_u32(0);
                tfra.put_u32(header_u32(self.fragments.len()));
                for &(time, moof_offset) in &self.fragments {
                    tfra.put_u64(time);
                    tfra.put_u64(moof_offset);
                    // The fragment's one traf, its one trun and its first sample, a keyframe. Each counts from 1.
                    tfra.extend_from_slice(&[1, 1, 1]);
                }
            });
            // The size, patched below once the `mfra` is complete.
            full(mfra, *b"mfro", 0, 0, |mfro| mfro.put_u32(0));
        });
        let size = header_u32(mfra.len());
        let at = mfra.len() - 4;
        mfra[at..].copy_from_slice(&size.to_be_bytes());
        mfra
    }
}

/// The file's header: `ftyp`, then the `moov` of one video track that lists no sample.
fn header(width: usize, height: usize, sps: &[u8], pps: &[u8]) -> io::Result<Vec<u8>> {
    let width: u16 = fit(width, "a video width")?;
    let height: u16 = fit(height, "a video height")?;
    if sps.len() < 4 {
        return Err(invalid("an SPS shorter than its profile and level"));
    }
    // The `avcC` of the other profiles, High and up, holds more fields: the chroma format, the bit depths and the SPS
    // extensions. This one does not write them, so it is correct only for Baseline, Main and Extended.
    let profile = sps[1];
    if !matches!(profile, PROFILE_BASELINE | PROFILE_MAIN | PROFILE_EXTENDED) {
        return Err(invalid(format!(
            "an SPS of profile {profile}, whose avcC needs fields this writer does not write"
        )));
    }
    let sps_size: u16 = fit(sps.len(), "an SPS")?;
    let pps_size: u16 = fit(pps.len(), "a PPS")?;

    let mut out = Vec::new();
    boxed(&mut out, *b"ftyp", |ftyp| {
        ftyp.extend_from_slice(b"iso5");
        ftyp.put_u32(512);
        ftyp.extend_from_slice(b"iso5iso6mp41");
    });
    boxed(&mut out, *b"moov", |moov| {
        full(moov, *b"mvhd", 0, 0, |mvhd| {
            mvhd.put_u32(0); // creation time
            mvhd.put_u32(0); // modification time
            mvhd.put_u32(TIMESCALE);
            mvhd.put_u32(0); // duration: the fragments carry it
            mvhd.put_u32(0x0001_0000); // rate 1.0
            mvhd.put_u16(0x0100); // volume 1.0
            mvhd.extend_from_slice(&[0; 10]);
            put_matrix(mvhd);
            mvhd.extend_from_slice(&[0; 24]);
            mvhd.put_u32(TRACK_ID + 1); // next track id
        });
        boxed(moov, *b"trak", |trak| {
            // Flags: the track is enabled and in the movie.
            full(trak, *b"tkhd", 0, 3, |tkhd| {
                tkhd.put_u32(0); // creation time
                tkhd.put_u32(0); // modification time
                tkhd.put_u32(TRACK_ID);
                tkhd.put_u32(0);
                tkhd.put_u32(0); // duration
                tkhd.extend_from_slice(&[0; 8]);
                tkhd.put_u16(0); // layer
                tkhd.put_u16(0); // alternate group
                tkhd.put_u16(0); // volume: a video has none
                tkhd.put_u16(0);
                put_matrix(tkhd);
                tkhd.put_u32(u32::from(width) << 16);
                tkhd.put_u32(u32::from(height) << 16);
            });
            boxed(trak, *b"mdia", |mdia| {
                full(mdia, *b"mdhd", 0, 0, |mdhd| {
                    mdhd.put_u32(0); // creation time
                    mdhd.put_u32(0); // modification time
                    mdhd.put_u32(TIMESCALE);
                    mdhd.put_u32(0); // duration
                    mdhd.put_u16(0x55C4); // language: `und`
                    mdhd.put_u16(0);
                });
                full(mdia, *b"hdlr", 0, 0, |hdlr| {
                    hdlr.put_u32(0);
                    hdlr.extend_from_slice(b"vide");
                    hdlr.extend_from_slice(&[0; 12]);
                    hdlr.extend_from_slice(b"VideoHandler\0");
                });
                boxed(mdia, *b"minf", |minf| {
                    full(minf, *b"vmhd", 0, 1, |vmhd| vmhd.extend_from_slice(&[0; 8]));
                    boxed(minf, *b"dinf", |dinf| {
                        full(dinf, *b"dref", 0, 0, |dref| {
                            dref.put_u32(1);
                            // Flags: the media is in this file.
                            full(dref, *b"url ", 0, 1, |_| {});
                        });
                    });
                    boxed(minf, *b"stbl", |stbl| {
                        full(stbl, *b"stsd", 0, 0, |stsd| {
                            stsd.put_u32(1);
                            boxed(stsd, *b"avc1", |avc1| {
                                avc1.extend_from_slice(&[0; 6]);
                                avc1.put_u16(1); // data reference index
                                avc1.extend_from_slice(&[0; 16]);
                                avc1.put_u16(width);
                                avc1.put_u16(height);
                                avc1.put_u32(0x0048_0000); // 72 dpi across
                                avc1.put_u32(0x0048_0000); // 72 dpi down
                                avc1.put_u32(0);
                                avc1.put_u16(1); // frame count
                                avc1.extend_from_slice(&[0; 32]); // compressor name
                                avc1.put_u16(24); // depth
                                avc1.put_u16(0xFFFF);
                                boxed(avc1, *b"avcC", |avcc| {
                                    avcc.push(1);
                                    // Profile, profile compatibility and level, as the SPS names them.
                                    avcc.extend_from_slice(&sps[1..4]);
                                    // Six reserved bits, then a sample's NAL lengths take four bytes.
                                    avcc.push(0xFF);
                                    // Three reserved bits, then one SPS.
                                    avcc.push(0xE1);
                                    avcc.put_u16(sps_size);
                                    avcc.extend_from_slice(sps);
                                    avcc.push(1);
                                    avcc.put_u16(pps_size);
                                    avcc.extend_from_slice(pps);
                                });
                            });
                        });
                        full(stbl, *b"stts", 0, 0, |stts| stts.put_u32(0));
                        full(stbl, *b"stsc", 0, 0, |stsc| stsc.put_u32(0));
                        full(stbl, *b"stsz", 0, 0, |stsz| {
                            stsz.put_u32(0); // sample size: each has its own
                            stsz.put_u32(0); // sample count
                        });
                        full(stbl, *b"stco", 0, 0, |stco| stco.put_u32(0));
                    });
                });
            });
        });
        boxed(moov, *b"mvex", |mvex| {
            full(mvex, *b"trex", 0, 0, |trex| {
                trex.put_u32(TRACK_ID);
                trex.put_u32(1); // sample description index
                trex.put_u32(SAMPLE_DURATION);
                trex.put_u32(0); // sample size
                trex.put_u32(NON_SYNC_SAMPLE_FLAGS);
            });
        });
    });
    Ok(out)
}

/// Appends a box of `kind` whose body `body` appends.
fn boxed(out: &mut Vec<u8>, kind: [u8; 4], body: impl FnOnce(&mut Vec<u8>)) {
    let start = out.len();
    out.put_u32(0);
    out.extend_from_slice(&kind);
    body(out);
    let size = header_u32(out.len() - start);
    out[start..start + 4].copy_from_slice(&size.to_be_bytes());
}

/// Appends a full box: a box whose body starts with a version and 24 bits of flags.
fn full(out: &mut Vec<u8>, kind: [u8; 4], version: u8, flags: u32, body: impl FnOnce(&mut Vec<u8>)) {
    boxed(out, kind, |out| {
        out.push(version);
        out.extend_from_slice(&flags.to_be_bytes()[1..]);
        body(out);
    });
}

fn put_matrix(out: &mut Vec<u8>) {
    for value in MATRIX {
        out.put_u32(value);
    }
}

/// A size or a count of the header boxes, which list the samples of one fragment at most and stay far below 4 GiB.
fn header_u32(value: usize) -> u32 {
    u32::try_from(value).expect("a header box is smaller than 4 GiB")
}

/// `value` as the field type `T`, or an error that names `what` did not fit.
fn fit<T: TryFrom<usize>>(value: usize, what: &str) -> io::Result<T> {
    T::try_from(value)
        .ok()
        .ok_or_else(|| invalid(format!("{what} of {value} is too large for MP4")))
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message.into())
}

/// Big-endian appends.
trait Put {
    fn put_u16(&mut self, value: u16);
    fn put_u32(&mut self, value: u32);
    fn put_u64(&mut self, value: u64);
}

impl Put for Vec<u8> {
    fn put_u16(&mut self, value: u16) {
        self.extend_from_slice(&value.to_be_bytes());
    }

    fn put_u32(&mut self, value: u32) {
        self.extend_from_slice(&value.to_be_bytes());
    }

    fn put_u64(&mut self, value: u64) {
        self.extend_from_slice(&value.to_be_bytes());
    }
}
