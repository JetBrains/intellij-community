use pretty_assertions::assert_eq;

use super::*;

fn host() -> HostFile {
    HostFile {
        alias: "studio".to_owned(),
        fingerprint: "ab".repeat(32).parse().expect("a fingerprint"),
        port: 7741,
        hostname: "develar-mac-studio-1965.local".to_owned(),
        addresses: vec!["192.168.1.24:7741".parse().expect("an address")],
        repo: "~/projects/idea-1".to_owned(),
    }
}

#[test]
fn a_host_file_reads_back_as_it_was_written() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let config = Config::new(dir.path().to_path_buf());
    config.write_host(&host()).expect("written");
    assert_eq!(config.read_host("studio").expect("read"), host());
    let text = fs::read_to_string(config.host_path("studio")).expect("the text");
    assert!(
        text.contains(
            r#""addresses": [
    "192.168.1.24:7741"
  ]"#
        ),
        "{text}"
    );
}

#[test]
fn a_missing_host_file_names_the_prepare_commands() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let refusal = Config::new(dir.path().to_path_buf()).read_host("studio").expect_err("no host file");
    assert_eq!(refusal.code, code::NO_HOST);
    assert!(refusal.message.contains("`bt --host studio --prepare`"), "{}", refusal.message);
    assert!(
        refusal.message.contains("`yonder prepare studio --agent <yonderd>`"),
        "{}",
        refusal.message
    );
}

#[test]
fn a_host_file_of_another_alias_or_shape_is_refused() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let config = Config::new(dir.path().to_path_buf());
    config.write_host(&host()).expect("written");
    fs::copy(config.host_path("studio"), config.host_path("other")).expect("a copy");
    assert_eq!(config.read_host("other").expect_err("another alias").code, code::BAD_HOST_FILE);
    fs::write(config.host_path("studio"), "{}").expect("a bad file");
    assert_eq!(config.read_host("studio").expect_err("another shape").code, code::BAD_HOST_FILE);
}

#[test]
fn an_alias_that_cannot_name_a_file_is_refused() {
    for alias in ["", "../x", ".hidden", "a b"] {
        assert_eq!(check_alias(alias).expect_err(alias).code, code::USAGE, "{alias:?}");
    }
    check_alias("studio-2.local").expect("a plain alias");
}

/// Two writers at the same time never tear the file: each reader sees one whole host file.
#[test]
fn concurrent_writers_leave_one_whole_host_file() {
    let dir = tempfile::tempdir().expect("a temporary directory");
    let config = Config::new(dir.path().to_path_buf());
    config.write_host(&host()).expect("written");
    std::thread::scope(|scope| {
        for writer in 0..8u16 {
            let config = &config;
            scope.spawn(move || {
                for round in 0..50u16 {
                    let mut file = host();
                    file.port = 7000 + writer * 100 + round;
                    config.write_host(&file).expect("written");
                    config.read_host("studio").expect("a whole host file");
                }
            });
        }
    });
    let names: Vec<String> = fs::read_dir(dir.path().join("hosts"))
        .expect("the hosts directory")
        .map(|entry| entry.expect("an entry").file_name().to_string_lossy().into_owned())
        .collect();
    assert_eq!(names, ["studio.json"], "no temporary file stays");
}
