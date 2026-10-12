use std::sync::{Arc, Mutex};

use super::*;
use crate::lock;

/// One complete box of a file.
pub(crate) struct Mp4Box<'a> {
    pub(crate) kind: [u8; 4],
    pub(crate) body: &'a [u8],
    /// Where the box starts in the bytes it was read from.
    offset: usize,
}

impl Mp4Box<'_> {
    const fn size(&self) -> usize {
        self.body.len() + 8
    }
}

/// The complete boxes of `bytes`, in order. A box cut short, the last one of a truncated file, ends the walk.
pub(crate) fn walk(bytes: &[u8]) -> Vec<Mp4Box<'_>> {
    let mut boxes = Vec::new();
    let mut at = 0;
    while bytes.len() - at >= 8 {
        let size = u32::from_be_bytes(bytes[at..at + 4].try_into().unwrap()) as usize;
        if size < 8 || at + size > bytes.len() {
            break;
        }
        boxes.push(Mp4Box {
            kind: bytes[at + 4..at + 8].try_into().unwrap(),
            body: &bytes[at + 8..at + size],
            offset: at,
        });
        at += size;
    }
    boxes
}

/// The body of the one child of `kind` in `body`.
fn child<'a>(body: &'a [u8], kind: &[u8; 4]) -> &'a [u8] {
    let found: Vec<&[u8]> = walk(body)
        .into_iter()
        .filter(|found| &found.kind == kind)
        .map(|found| found.body)
        .collect();
    assert_eq!(found.len(), 1, "{} boxes of {}", found.len(), String::from_utf8_lossy(kind));
    found[0]
}

/// The body of the one child at `path` below `body`, one box type per level.
fn descend<'a>(body: &'a [u8], path: &[&[u8; 4]]) -> &'a [u8] {
    path.iter().fold(body, |body, kind| child(body, kind))
}

/// Reads big-endian fields off the front of a box body.
struct Fields<'a>(&'a [u8]);

impl<'a> Fields<'a> {
    fn take(&mut self, count: usize) -> &'a [u8] {
        let (taken, rest) = self.0.split_at(count);
        self.0 = rest;
        taken
    }

    fn u8(&mut self) -> u8 {
        self.take(1)[0]
    }

    fn u16(&mut self) -> u16 {
        u16::from_be_bytes(self.take(2).try_into().unwrap())
    }

    fn u32(&mut self) -> u32 {
        u32::from_be_bytes(self.take(4).try_into().unwrap())
    }

    fn u64(&mut self) -> u64 {
        u64::from_be_bytes(self.take(8).try_into().unwrap())
    }

    /// A full box's version and flags.
    fn version_and_flags(&mut self) -> (u8, u32) {
        let word = self.u32();
        (u8::try_from(word >> 24).unwrap(), word & 0x00FF_FFFF)
    }
}

/// What the tests read back from a fragmented MP4.
#[derive(Debug)]
pub(crate) struct Mp4 {
    pub(crate) major_brand: [u8; 4],
    pub(crate) minor_version: u32,
    pub(crate) compatible_brands: Vec<[u8; 4]>,
    /// The track's size, as `tkhd` and the sample entry both give it.
    pub(crate) width: u16,
    pub(crate) height: u16,
    pub(crate) avcc: Vec<u8>,
    pub(crate) sps: Vec<u8>,
    pub(crate) pps: Vec<u8>,
    /// `trex`'s default sample duration.
    pub(crate) sample_duration: u32,
    pub(crate) fragments: Vec<Fragment>,
    /// The decode time and the `moof` offset of each fragment, as the `mfra` lists them. `None` when the file has no
    /// complete `mfra`, as one that was not finished.
    pub(crate) index: Option<Vec<(u64, u64)>>,
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) struct Fragment {
    /// Where the fragment's `moof` starts in the file.
    pub(crate) moof_offset: u64,
    pub(crate) sequence: u32,
    pub(crate) decode_time: u64,
    pub(crate) first_sample_flags: u32,
    pub(crate) samples: Vec<Vec<u8>>,
}

impl Mp4 {
    /// The index of each fragment's first sample among all samples: the keyframes.
    pub(crate) fn keyframes(&self) -> Vec<usize> {
        let mut first = 0;
        self.fragments
            .iter()
            .map(|fragment| {
                let this = first;
                first += fragment.samples.len();
                this
            })
            .collect()
    }

    pub(crate) fn samples(&self) -> impl Iterator<Item = &[u8]> {
        self.fragments
            .iter()
            .flat_map(|fragment| fragment.samples.iter().map(Vec::as_slice))
    }
}

/// Reads `bytes` as the muxer writes them, and asserts what every file must hold. A fragment whose `mdat` is cut
/// short is not read, and neither is an `mfra` cut short.
pub(crate) fn parse(bytes: &[u8]) -> Mp4 {
    let top = walk(bytes);
    assert!(top.len() >= 2, "no ftyp and moov");
    assert_eq!(&top[0].kind, b"ftyp");
    let mut ftyp = Fields(top[0].body);
    let major_brand = ftyp.take(4).try_into().unwrap();
    let minor_version = ftyp.u32();
    let compatible_brands = ftyp.0.as_chunks::<4>().0.to_vec();

    assert_eq!(&top[1].kind, b"moov");
    let moov = top[1].body;
    let mut tkhd = Fields(descend(moov, &[b"trak", b"tkhd"]));
    assert_eq!(tkhd.version_and_flags(), (0, 3));
    tkhd.take(8);
    assert_eq!(tkhd.u32(), TRACK_ID);
    tkhd.take(60);
    let (width, height) = (tkhd.u32(), tkhd.u32());
    let mut mdhd = Fields(descend(moov, &[b"trak", b"mdia", b"mdhd"]));
    mdhd.take(12);
    assert_eq!(mdhd.u32(), TIMESCALE);
    let mut hdlr = Fields(descend(moov, &[b"trak", b"mdia", b"hdlr"]));
    hdlr.take(8);
    assert_eq!(hdlr.take(4), b"vide");

    let mut stsd = Fields(descend(moov, &[b"trak", b"mdia", b"minf", b"stbl", b"stsd"]));
    stsd.take(4);
    assert_eq!(stsd.u32(), 1, "one sample entry");
    let avc1 = child(stsd.0, b"avc1");
    let mut entry = Fields(avc1);
    entry.take(24);
    let (entry_width, entry_height) = (entry.u16(), entry.u16());
    entry.take(50);
    assert_eq!(
        (u32::from(entry_width) << 16, u32::from(entry_height) << 16),
        (width, height),
        "the sample entry and tkhd disagree on the size"
    );
    let avcc = child(entry.0, b"avcC");
    let mut config = Fields(avcc);
    assert_eq!(config.u8(), 1, "the avcC version");
    let profile = config.take(3).to_vec();
    assert_eq!(config.u8(), 0xFF, "four-byte NAL lengths");
    assert_eq!(config.u8(), 0xE1, "one SPS");
    let sps_size = usize::from(config.u16());
    let sps = config.take(sps_size).to_vec();
    assert_eq!(profile, sps[1..4], "the avcC profile is the SPS's");
    assert_eq!(config.u8(), 1, "one PPS");
    let pps_size = usize::from(config.u16());
    let pps = config.take(pps_size).to_vec();
    assert!(config.0.is_empty(), "the avcC has bytes past its PPS");

    let mut trex = Fields(descend(moov, &[b"mvex", b"trex"]));
    trex.take(4);
    assert_eq!(trex.u32(), TRACK_ID);
    trex.take(4);
    let sample_duration = trex.u32();
    trex.take(4);
    assert_eq!(trex.u32(), NON_SYNC_SAMPLE_FLAGS);

    let mut fragments = Vec::new();
    let mut index = None;
    let mut rest = &top[2..];
    loop {
        match rest {
            [] => break,
            [mfra] if &mfra.kind == b"mfra" => {
                index = Some(fragment_index(mfra));
                break;
            }
            // A `moof` whose `mdat` is cut short.
            [moof] => {
                assert_eq!(&moof.kind, b"moof");
                break;
            }
            [moof, mdat, tail @ ..] => {
                assert_eq!(&moof.kind, b"moof");
                assert_eq!(&mdat.kind, b"mdat");
                fragments.push(fragment(bytes, moof, mdat));
                rest = tail;
            }
        }
    }
    if let Some(index) = &index {
        let listed: Vec<(u64, u64)> = fragments
            .iter()
            .map(|fragment| (fragment.decode_time, fragment.moof_offset))
            .collect();
        assert_eq!(*index, listed, "the mfra does not list the fragments");
    }
    Mp4 {
        major_brand,
        minor_version,
        compatible_brands,
        width: u16::try_from(width >> 16).unwrap(),
        height: u16::try_from(height >> 16).unwrap(),
        avcc: avcc.to_vec(),
        sps,
        pps,
        sample_duration,
        fragments,
        index,
    }
}

/// The entries of an `mfra`'s one `tfra`: each fragment's decode time and `moof` offset.
fn fragment_index(mfra: &Mp4Box<'_>) -> Vec<(u64, u64)> {
    let boxes = walk(mfra.body);
    let kinds: Vec<[u8; 4]> = boxes.iter().map(|found| found.kind).collect();
    assert_eq!(kinds, [*b"tfra", *b"mfro"], "the boxes of the mfra");
    let mut tfra = Fields(boxes[0].body);
    assert_eq!(tfra.version_and_flags(), (1, 0));
    assert_eq!(tfra.u32(), TRACK_ID);
    assert_eq!(tfra.u32(), 0, "one byte each for the traf, trun and sample numbers");
    let count = tfra.u32();
    let entries = (0..count)
        .map(|_| {
            let entry = (tfra.u64(), tfra.u64());
            assert_eq!(tfra.take(3), [1, 1, 1], "the first sample of the one trun");
            entry
        })
        .collect();
    assert!(tfra.0.is_empty(), "the tfra has bytes past its entries");
    let mut mfro = Fields(boxes[1].body);
    assert_eq!(mfro.version_and_flags(), (0, 0));
    assert_eq!(mfro.u32() as usize, mfra.size(), "the mfro gives the size of the mfra");
    entries
}

fn fragment(bytes: &[u8], moof: &Mp4Box<'_>, mdat: &Mp4Box<'_>) -> Fragment {
    let mut mfhd = Fields(child(moof.body, b"mfhd"));
    mfhd.take(4);
    let sequence = mfhd.u32();
    let traf = child(moof.body, b"traf");
    let mut tfhd = Fields(child(traf, b"tfhd"));
    assert_eq!(tfhd.version_and_flags(), (0, TFHD_FLAGS));
    assert_eq!(tfhd.u32(), TRACK_ID);
    assert_eq!(tfhd.u32(), SAMPLE_DURATION);
    assert_eq!(tfhd.u32(), NON_SYNC_SAMPLE_FLAGS);
    let mut tfdt = Fields(child(traf, b"tfdt"));
    assert_eq!(tfdt.version_and_flags(), (1, 0));
    let decode_time = tfdt.u64();
    let mut trun = Fields(child(traf, b"trun"));
    assert_eq!(trun.version_and_flags(), (0, TRUN_FLAGS));
    let count = trun.u32() as usize;
    let data_offset = trun.u32() as usize;
    let first_sample_flags = trun.u32();
    assert_eq!(data_offset, moof.size() + 8, "the samples start right after the mdat header");
    let mut at = moof.offset + data_offset;
    let samples = (0..count)
        .map(|_| {
            let size = trun.u32() as usize;
            let sample = bytes[at..at + size].to_vec();
            at += size;
            sample
        })
        .collect();
    assert_eq!(at, mdat.offset + mdat.size(), "the samples fill the mdat exactly");
    Fragment {
        moof_offset: moof.offset as u64,
        sequence,
        decode_time,
        first_sample_flags,
        samples,
    }
}

const SPS: &[u8] = &[0x67, 0x42, 0xC0, 0x1F, 0x8C, 0x8D];
const PPS: &[u8] = &[0x68, 0xCE, 0x3C, 0x80];

/// A sample of one fake NAL unit whose size grows with `frame`, so each sample has a size of its own.
fn sample(frame: u8) -> Vec<u8> {
    let nal: Vec<u8> = std::iter::once(0x41)
        .chain(std::iter::repeat_n(frame, usize::from(frame) + 1))
        .collect();
    let mut sample = u32::try_from(nal.len()).unwrap().to_be_bytes().to_vec();
    sample.extend(nal);
    sample
}

/// Writes a 1920x1080 file of one sample per entry of `keyframes`, and answers the samples.
fn write(path: &Path, keyframes: &[bool], finish: bool) -> Vec<Vec<u8>> {
    let mut mp4 = FragmentedMp4::start(path, 1920, 1080, SPS, PPS).unwrap();
    let samples: Vec<Vec<u8>> = (0..keyframes.len()).map(|frame| sample(u8::try_from(frame).unwrap())).collect();
    for (&keyframe, sample) in keyframes.iter().zip(&samples) {
        mp4.push(keyframe, sample).unwrap();
    }
    if finish {
        mp4.finish().unwrap();
    }
    samples
}

const KEYFRAMES: [bool; 8] = [true, false, false, false, true, false, false, true];

#[test]
fn every_keyframe_starts_a_fragment_that_lists_its_samples() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("video.mp4");
    let samples = write(&path, &KEYFRAMES, true);
    let mp4 = parse(&std::fs::read(&path).unwrap());

    assert_eq!(&mp4.major_brand, b"iso5");
    assert_eq!(mp4.minor_version, 512);
    assert_eq!(mp4.compatible_brands, [*b"iso5", *b"iso6", *b"mp41"]);
    assert_eq!((mp4.width, mp4.height), (1920, 1080));
    let mut avcc = vec![1, 0x42, 0xC0, 0x1F, 0xFF, 0xE1, 0, 6];
    avcc.extend_from_slice(SPS);
    avcc.extend_from_slice(&[1, 0, 4]);
    avcc.extend_from_slice(PPS);
    assert_eq!(mp4.avcc, avcc);
    assert_eq!((mp4.sps.as_slice(), mp4.pps.as_slice()), (SPS, PPS));
    assert_eq!(mp4.sample_duration, 1000 / FRAME_RATE);

    let fragments: Vec<(u32, u64, u32)> = mp4
        .fragments
        .iter()
        .map(|fragment| (fragment.sequence, fragment.decode_time, fragment.first_sample_flags))
        .collect();
    let at = |frame: u64| frame * u64::from(1000 / FRAME_RATE);
    assert_eq!(
        fragments,
        [
            (1, at(0), SYNC_SAMPLE_FLAGS),
            (2, at(4), SYNC_SAMPLE_FLAGS),
            (3, at(7), SYNC_SAMPLE_FLAGS),
        ]
    );
    assert_eq!(mp4.keyframes(), [0, 4, 7]);
    assert_eq!(mp4.samples().collect::<Vec<_>>(), samples);

    // The file ends with the mfra, which lists every fragment, and whose last four bytes give its size.
    let bytes = std::fs::read(&path).unwrap();
    let top = walk(&bytes);
    let moofs: Vec<u64> = top
        .iter()
        .filter(|found| &found.kind == b"moof")
        .map(|found| found.offset as u64)
        .collect();
    assert_eq!(mp4.index, Some(vec![(at(0), moofs[0]), (at(4), moofs[1]), (at(7), moofs[2])]));
    let mfra = top.last().unwrap();
    assert_eq!(&mfra.kind, b"mfra");
    assert_eq!(
        u32::from_be_bytes(bytes[bytes.len() - 4..].try_into().unwrap()) as usize,
        mfra.size()
    );
}

/// The property the container was chosen for: a recorder killed at any byte leaves every fragment before that byte
/// playable.
#[test]
fn a_file_cut_inside_its_last_fragment_keeps_every_earlier_one() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("video.mp4");
    write(&path, &KEYFRAMES, true);
    let bytes = std::fs::read(&path).unwrap();
    let whole = parse(&bytes);
    let top = walk(&bytes);
    let offset = |kind: &[u8; 4]| top.iter().rfind(|found| &found.kind == kind).unwrap().offset;
    let (last_moof, mfra) = (offset(b"moof"), offset(b"mfra"));
    // A cut inside the last fragment loses it. A cut inside the mfra loses only the index, which a player does
    // without.
    for (cut, kept) in [(last_moof + 10, 2), (mfra - 1, 2), (mfra + 10, 3), (bytes.len() - 1, 3)] {
        let cut_short = parse(&bytes[..cut]);
        assert_eq!(cut_short.fragments, whole.fragments[..kept], "a cut at {cut} of {}", bytes.len());
        assert_eq!(cut_short.index, None, "a cut at {cut} kept the mfra");
    }
}

/// A fragment reaches the file only once the next keyframe or the stop ends it, so a recorder killed between two
/// keyframes leaves no half of one.
#[test]
fn an_unfinished_file_holds_every_complete_fragment() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("video.mp4");
    let samples = write(&path, &KEYFRAMES, false);
    let mp4 = parse(&std::fs::read(&path).unwrap());
    assert_eq!(mp4.keyframes(), [0, 4]);
    assert_eq!(mp4.samples().collect::<Vec<_>>(), samples[..7]);
    assert_eq!(mp4.index, None, "an unfinished file has an mfra");
}

#[test]
fn a_fragment_must_start_with_a_keyframe() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("video.mp4");
    let mut mp4 = FragmentedMp4::start(&path, 32, 16, SPS, PPS).unwrap();
    let error = mp4.push(false, &sample(0)).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::InvalidInput);
    mp4.finish().unwrap();
    let mp4 = parse(&std::fs::read(&path).unwrap());
    assert!(mp4.fragments.is_empty());
    assert_eq!(mp4.index, Some(Vec::new()));
}

/// The avcC this writes has none of the fields a High profile's needs, so an SPS of such a profile is refused rather
/// than written into a header that a strict demuxer rejects.
#[test]
fn an_sps_of_a_high_profile_is_refused() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("video.mp4");
    let high = [0x67, 100, 0x00, 0x1F, 0xAC];
    let Err(error) = FragmentedMp4::start(&path, 32, 16, &high, PPS) else {
        panic!("an SPS of profile 100 was taken");
    };
    assert_eq!(error.kind(), io::ErrorKind::InvalidInput);
    assert!(error.to_string().contains("profile 100"), "{error}");
    assert!(!path.exists(), "a refused header left a file");
}

/// A disk that fills up: it takes `room` bytes into its buffer and fails every write after.
struct FullDisk {
    out: Arc<Mutex<Vec<u8>>>,
    room: usize,
}

impl Write for FullDisk {
    fn write(&mut self, chunk: &[u8]) -> io::Result<usize> {
        if self.room == 0 {
            return Err(io::Error::new(io::ErrorKind::StorageFull, "the disk is full"));
        }
        let taken = chunk.len().min(self.room);
        self.room -= taken;
        lock(&self.out).extend_from_slice(&chunk[..taken]);
        Ok(taken)
    }

    fn flush(&mut self) -> io::Result<()> {
        Ok(())
    }
}

/// A write that fails part way leaves a cut box at the end of the file. The writer then refuses every later frame
/// and its finish, so no box follows the cut one, and the file keeps every fragment written before it.
#[test]
fn a_failed_write_breaks_the_writer_and_the_file_keeps_its_complete_fragments() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("video.mp4");
    let samples = write(&path, &KEYFRAMES, true);
    let whole = std::fs::read(&path).unwrap();
    let second_moof = walk(&whole).iter().filter(|found| &found.kind == b"moof").nth(1).unwrap().offset;

    // The disk fills inside the second fragment's moof, which the third keyframe writes.
    let out = Arc::new(Mutex::new(Vec::new()));
    let disk = FullDisk {
        out: out.clone(),
        room: second_moof + 10,
    };
    let mut mp4 = FragmentedMp4::to_writer(disk, 1920, 1080, SPS, PPS).unwrap();
    for (&keyframe, sample) in KEYFRAMES.iter().zip(&samples).take(7) {
        mp4.push(keyframe, sample).unwrap();
    }
    let full = mp4.push(true, &samples[7]).unwrap_err();
    assert_eq!(full.kind(), io::ErrorKind::StorageFull);
    for later in [
        mp4.push(false, &samples[7]).unwrap_err(),
        mp4.push(true, &samples[7]).unwrap_err(),
        mp4.finish().unwrap_err(),
    ] {
        assert_eq!(later.kind(), io::ErrorKind::InvalidInput);
        assert!(later.to_string().contains("broken"), "{later}");
    }

    let bytes = lock(&out).clone();
    assert_eq!(bytes, whole[..second_moof + 10], "the writer wrote past the failure");
    let mp4 = parse(&bytes);
    assert_eq!(mp4.keyframes(), [0]);
    assert_eq!(mp4.samples().collect::<Vec<_>>(), samples[..4]);
    assert_eq!(mp4.index, None, "a broken file has an mfra");
}
