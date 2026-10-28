use super::*;

#[test]
fn a_pack_file_goes_on_every_way_out() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let path = dir.path().join("1.pack");
    fs::write(&path, b"pack").expect("a pack");
    drop(PackFile::from(crate::bd::Pack {
        path: path.clone(),
        bytes: 4,
        commits: 1,
    }));
    assert!(!path.exists(), "{}", path.display());
}

/// Ctrl+C ends the upload at the next chunk.
#[test]
fn an_upload_stops_at_ctrl_c() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let path = dir.path().join("pack");
    fs::write(&path, b"abc").expect("a pack");
    let mut calm = Upload {
        file: File::open(&path).expect("the pack"),
        stop: &|| false,
    };
    let mut read = Vec::new();
    calm.read_to_end(&mut read).expect("the bytes");
    assert_eq!(read, b"abc");
    let mut stopped = Upload {
        file: File::open(&path).expect("the pack"),
        stop: &|| true,
    };
    stopped.read_to_end(&mut read).expect_err("stopped");
}
