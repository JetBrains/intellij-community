use std::io::{Cursor, Read};
use std::path::Path;

use expect_test::expect;
use pretty_assertions::assert_eq;

use super::*;
use crate::lane::services::testing::{FIXTURE_PLATFORM, fixture_agent, fixture_packages};

/// The entries of `archive`: the name, whether it is stored, and the bytes, which the reader checks against the CRC.
fn entries(archive: &[u8]) -> Vec<(String, bool, Vec<u8>)> {
    let mut zip = zip::ZipArchive::new(Cursor::new(archive)).expect("a zip archive");
    (0..zip.len())
        .map(|index| {
            let mut entry = zip.by_index(index).unwrap();
            let mut bytes = Vec::new();
            entry.read_to_end(&mut bytes).unwrap();
            (
                entry.name().to_owned(),
                entry.compression() == zip::CompressionMethod::Stored,
                bytes,
            )
        })
        .collect()
}

#[test]
fn the_archive_holds_the_fake_agent_binary_for_the_one_platform() {
    let packages = fixture_packages();
    let archive = packages.archive(FIXTURE_PLATFORM).unwrap();
    assert_eq!(
        entries(archive),
        [("bin/acp-fake-agent".to_owned(), true, fs::read(fixture_agent()).unwrap())]
    );
    assert_eq!(packages.sha256(), sha256_hex(archive));
    assert_eq!(packages.platform(), FIXTURE_PLATFORM);
    assert!(packages.archive("linux-aarch64").is_none());
    assert!(packages.archive("solaris-sparc").is_none());
}

#[test]
fn every_entry_has_the_time_zero_and_no_file_attributes() {
    let packages = fixture_packages();
    let mut zip = zip::ZipArchive::new(Cursor::new(packages.archive(FIXTURE_PLATFORM).unwrap())).unwrap();
    for index in 0..zip.len() {
        let entry = zip.by_index(index).unwrap();
        let time = entry.last_modified().unwrap();
        assert_eq!(
            (time.year(), time.month(), time.day(), time.hour(), time.minute()),
            (1980, 1, 1, 0, 0),
            "{}",
            entry.name()
        );
        assert_eq!(entry.unix_mode(), None, "{}", entry.name());
    }
}

/// The installer of the IDE makes the command executable after the extraction, so the archive carries no mode.
#[test]
fn the_manifest_command_is_the_packaged_binary() {
    assert_eq!(Packages::command(), "./bin/acp-fake-agent");
}

#[test]
fn the_guest_platform_is_a_registry_key_of_this_os_and_cpu() {
    let expected = match (std::env::consts::OS, std::env::consts::ARCH) {
        ("macos", "aarch64") => "darwin-aarch64",
        ("linux", "aarch64") => "linux-aarch64",
        ("linux", "x86_64") => "linux-x86_64",
        (os, arch) => panic!("no guest runs on {os} {arch}"),
    };
    assert_eq!(guest_platform(), expected);
}

#[test]
fn the_package_is_the_same_bytes_on_each_assembly() {
    let (first, second) = (fixture_packages(), fixture_packages());
    assert_eq!(first.archive(FIXTURE_PLATFORM), second.archive(FIXTURE_PLATFORM));
    expect!["ed202efe69e1aba91aab4823f8d19b6c805e4ce73e07ffa8ee96a555830f17d5"].assert_eq(first.sha256());
}

#[test]
fn a_missing_binary_is_refused_with_the_input() {
    let refusal = Packages::assemble(Path::new("/nonexistent/acp-fake-agent"), FIXTURE_PLATFORM)
        .err()
        .unwrap();
    assert_eq!(refusal.code, INPUT_CODE);
    assert!(
        refusal
            .message
            .starts_with("cannot read the fake agent /nonexistent/acp-fake-agent: "),
        "{}",
        refusal.message
    );
}

#[test]
fn the_crc_is_the_zip_crc() {
    assert_eq!(crc32(b""), 0);
    assert_eq!(crc32(b"123456789"), 0xCBF4_3926);
}
