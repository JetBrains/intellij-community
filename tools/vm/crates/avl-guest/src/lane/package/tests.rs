use std::io::{Cursor, Read};
use std::path::Path;

use expect_test::expect;
use pretty_assertions::assert_eq;

use super::*;
use crate::lane::services::testing::{FIXTURE_JAVA, fixture_jar, fixture_packages};

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
fn a_posix_archive_holds_the_launcher_and_the_jar_and_a_windows_archive_the_jar_only() {
    let packages = fixture_packages();
    let jar = fs::read(fixture_jar()).unwrap();
    for platform in PLATFORMS {
        let archive = packages.archive(platform).unwrap();
        let mut want = Vec::new();
        if !platform.starts_with("windows-") {
            want.push(("bin/acp-scenario-agent".to_owned(), true, launcher(FIXTURE_JAVA).into_bytes()));
        }
        want.push(("lib/acp-scenario-agent.jar".to_owned(), true, jar.clone()));
        assert_eq!(entries(archive), want, "{platform}");
        assert_eq!(packages.sha256(platform), Some(sha256_hex(archive).as_str()), "{platform}");
    }
    assert!(packages.archive("solaris-sparc").is_none());
}

#[test]
fn every_entry_has_the_time_zero_and_no_file_attributes() {
    let packages = fixture_packages();
    let mut zip = zip::ZipArchive::new(Cursor::new(packages.archive("linux-x86_64").unwrap())).unwrap();
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

#[test]
fn the_launcher_runs_the_java_of_the_distribution_by_its_absolute_path() {
    expect![[r#"
        #!/bin/sh
        SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
        exec "/home/admin/air-lane/jbr/bin/java" -jar "$SCRIPT_DIR/../lib/acp-scenario-agent.jar" "$@"
    "#]]
    .assert_eq(&launcher(FIXTURE_JAVA));
}

#[test]
fn the_manifest_command_is_the_launcher_or_the_java_exe_beside_that_java() {
    let packages = fixture_packages();
    assert_eq!(packages.command("linux-aarch64"), "./bin/acp-scenario-agent");
    assert_eq!(Packages::args("linux-aarch64"), Vec::<String>::new());
    assert_eq!(packages.command("windows-x86_64"), "/home/admin/air-lane/jbr/bin/java.exe");
    assert_eq!(Packages::args("windows-x86_64"), ["-jar", "./lib/acp-scenario-agent.jar"]);
}

#[test]
fn the_packages_are_the_same_bytes_on_each_assembly() {
    let (first, second) = (fixture_packages(), fixture_packages());
    for platform in PLATFORMS {
        assert_eq!(first.archive(platform), second.archive(platform), "{platform}");
    }
}

#[test]
fn a_relative_java_and_a_missing_jar_are_refused_with_the_input() {
    let relative = Packages::assemble(&fixture_jar(), Path::new("jbr/bin/java")).err().unwrap();
    assert_eq!(relative.code, INPUT_CODE);
    assert_eq!(relative.message, "the Java command jbr/bin/java is not an absolute UTF-8 path");

    let missing = Path::new("/nonexistent/agent.jar");
    let refusal = Packages::assemble(missing, Path::new(FIXTURE_JAVA)).err().unwrap();
    assert_eq!(refusal.code, INPUT_CODE);
    assert!(
        refusal.message.starts_with("cannot read the agent jar /nonexistent/agent.jar: "),
        "{}",
        refusal.message
    );
}

/// The checksums that `java.util.zip.ZipOutputStream` gives the same entries, with the code of the Java packer of the
/// fake agent, in the time zones UTC, America/Los_Angeles and Asia/Tokyo.
#[test]
fn the_archives_are_the_bytes_of_the_java_packer() {
    let packages = fixture_packages();
    assert_eq!(
        packages.sha256("linux-x86_64"),
        Some("7f8dd9a99a3a55d8ff9b2e8b5fd67fac555c7df9529f7925566e6144334081c3")
    );
    assert_eq!(
        packages.sha256("windows-x86_64"),
        Some("85ae41b1615c5ed0285949ab81917081a6f5fa9e563a270a2e738281ddbd24b5")
    );
}

#[test]
fn the_crc_is_the_zip_crc() {
    assert_eq!(crc32(b""), 0);
    assert_eq!(crc32(b"123456789"), 0xCBF4_3926);
}
