use std::fs;
use std::io::{Read, Write};

use pretty_assertions::assert_eq;

use super::*;

/// Gives everybody access to `path`, the way a loose umask or an inherited access list does.
#[cfg(unix)]
fn loosen(path: &Path) {
    use std::os::unix::fs::PermissionsExt;
    fs::set_permissions(path, fs::Permissions::from_mode(0o644)).unwrap();
}

/// Gives everybody access to `path`, the way a loose umask or an inherited access list does.
#[cfg(windows)]
fn loosen(path: &Path) {
    use std::os::windows::ffi::OsStrExt;
    use windows_sys::Win32::Security::Authorization::{
        ConvertStringSecurityDescriptorToSecurityDescriptorW, SDDL_REVISION_1, SE_FILE_OBJECT, SetNamedSecurityInfoW,
    };
    use windows_sys::Win32::Security::{
        DACL_SECURITY_INFORMATION, GetSecurityDescriptorDacl, PSECURITY_DESCRIPTOR, UNPROTECTED_DACL_SECURITY_INFORMATION,
    };

    let sddl: Vec<u16> = "D:(A;;FA;;;WD)".encode_utf16().chain([0]).collect();
    let mut descriptor: PSECURITY_DESCRIPTOR = std::ptr::null_mut();
    // SAFETY: `sddl` is NUL-terminated, and `descriptor` is a valid out pointer. The test leaks the descriptor.
    let converted = unsafe {
        ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.as_ptr(), SDDL_REVISION_1, &raw mut descriptor, std::ptr::null_mut())
    };
    assert_ne!(converted, 0, "{}", io::Error::last_os_error());
    let (mut present, mut defaulted) = (0, 0);
    let mut dacl = std::ptr::null_mut();
    // SAFETY: `descriptor` is valid, and every out pointer is a valid local.
    let read = unsafe { GetSecurityDescriptorDacl(descriptor, &raw mut present, &raw mut dacl, &raw mut defaulted) };
    assert_ne!(read, 0, "{}", io::Error::last_os_error());
    let path: Vec<u16> = path.as_os_str().encode_wide().chain([0]).collect();
    // SAFETY: `path` is NUL-terminated, and `dacl` points into the leaked descriptor.
    let status = unsafe {
        SetNamedSecurityInfoW(
            path.as_ptr(),
            SE_FILE_OBJECT,
            DACL_SECURITY_INFORMATION | UNPROTECTED_DACL_SECURITY_INFORMATION,
            std::ptr::null_mut(),
            std::ptr::null_mut(),
            dacl,
            std::ptr::null(),
        )
    };
    assert_eq!(status, 0);
}

fn file_protection(path: &Path) -> Protection {
    let file = open_no_follow(path).unwrap_or_else(|error| panic!("{error:?}"));
    let metadata = file.metadata().unwrap();
    protection(&file, &metadata).unwrap()
}

#[test]
fn a_created_file_is_private_and_a_second_creation_is_refused() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("output.log");
    let mut file = open_private_file(&path, PrivateOpen::CreateNew).unwrap();
    file.write_all(b"first").unwrap();
    drop(file);

    let protection = file_protection(&path);
    assert!(protection.owned_by_caller(), "{protection:?}");
    assert!(protection.is_private(), "{protection:?}");
    assert!(protection.is_exactly(0o600), "{protection:?}");

    let error = open_private_file(&path, PrivateOpen::CreateNew).unwrap_err();
    assert_eq!(error.kind(), io::ErrorKind::AlreadyExists);
}

#[test]
fn every_open_keeps_what_it_promises() {
    let directory = tempfile::tempdir().unwrap();
    let path = directory.path().join("record");

    // Read and write, never truncated: what a lock file needs.
    let mut record = open_private_file(&path, PrivateOpen::ReadWrite).unwrap();
    record.write_all(b"held").unwrap();
    drop(record);
    let mut record = open_private_file(&path, PrivateOpen::ReadWrite).unwrap();
    let mut content = String::new();
    #[expect(
        clippy::verbose_file_reads,
        reason = "the read goes through the handle the open answered, which is what is tested"
    )]
    record.read_to_string(&mut content).unwrap();
    assert_eq!(content, "held");
    drop(record);

    // Appended, then emptied.
    open_private_file(&path, PrivateOpen::Append).unwrap().write_all(b" twice").unwrap();
    assert_eq!(fs::read_to_string(&path).unwrap(), "held twice");
    open_private_file(&path, PrivateOpen::Truncate).unwrap().write_all(b"new").unwrap();
    assert_eq!(fs::read_to_string(&path).unwrap(), "new");
    assert!(file_protection(&path).is_private());
}

#[test]
fn a_restricted_file_and_directory_are_private_again() {
    let root = tempfile::tempdir().unwrap();
    let file = root.path().join("artifact.png");
    fs::write(&file, b"bytes").unwrap();
    loosen(&file);
    assert!(!file_protection(&file).is_private());
    restrict_file(&file).unwrap();
    assert!(file_protection(&file).is_exactly(0o600));

    let directory = root.path().join("state");
    fs::create_dir(&directory).unwrap();
    loosen(&directory);
    let loose = directory_protection(&directory).unwrap().unwrap();
    assert!(!loose.is_private(), "{loose:?}");
    restrict_directory(&directory).unwrap();
    let restricted = directory_protection(&directory).unwrap().unwrap();
    assert!(restricted.is_private() && restricted.owned_by_caller(), "{restricted:?}");
}

#[test]
fn a_created_directory_is_private_and_so_is_a_file_created_in_it() {
    let root = tempfile::tempdir().unwrap();
    let directory = root.path().join("receipts");
    create_private_directory(&directory).unwrap();
    let protection = directory_protection(&directory).unwrap().unwrap();
    assert!(protection.owned_by_caller() && protection.is_private(), "{protection:?}");
    assert_eq!(
        create_private_directory(&directory).unwrap_err().kind(),
        io::ErrorKind::AlreadyExists
    );

    // A file that asks for nothing: on Windows it inherits the directory's list.
    let receipt = directory.join("lease.json");
    fs::write(&receipt, b"{}").unwrap();
    if cfg!(windows) {
        assert!(file_protection(&receipt).is_private());
    }

    // A file is not a directory.
    assert_eq!(directory_protection(&receipt).unwrap(), None);
}

#[test]
fn a_missing_file_is_missing() {
    let root = tempfile::tempdir().unwrap();
    assert!(matches!(open_no_follow(&root.path().join("absent")), Err(NoFollowError::Missing)));
}

#[cfg(unix)]
#[test]
fn a_link_is_refused_and_not_followed() {
    let root = tempfile::tempdir().unwrap();
    let target = root.path().join("target");
    open_private_file(&target, PrivateOpen::CreateNew).unwrap();
    let link = root.path().join("link");
    std::os::unix::fs::symlink(&target, &link).unwrap();
    assert!(matches!(open_no_follow(&link), Err(NoFollowError::Link)));

    let directory = root.path().join("directory");
    create_private_directory(&directory).unwrap();
    let directory_link = root.path().join("directory-link");
    std::os::unix::fs::symlink(&directory, &directory_link).unwrap();
    assert_eq!(directory_protection(&directory_link).unwrap(), None);
}

// A symlink needs developer mode or a privilege on Windows, so the test passes without one.
#[cfg(windows)]
#[test]
fn a_link_is_refused_and_not_followed() {
    let root = tempfile::tempdir().unwrap();
    let target = root.path().join("target");
    open_private_file(&target, PrivateOpen::CreateNew).unwrap();
    let link = root.path().join("link");
    if std::os::windows::fs::symlink_file(&target, &link).is_err() {
        return;
    }
    assert!(matches!(open_no_follow(&link), Err(NoFollowError::Link)));

    let directory = root.path().join("directory");
    create_private_directory(&directory).unwrap();
    let directory_link = root.path().join("directory-link");
    std::os::windows::fs::symlink_dir(&directory, &directory_link).unwrap();
    assert_eq!(directory_protection(&directory_link).unwrap(), None);
}

// The whole permission word is compared, not only the group and other bits: a mode-0400 or a mode-0700 file is
// private and is still not a mode-0600 one. A setuid bit is compared the same way, but a sandbox can drop it at
// `chmod`, so the test does not set one.
#[cfg(unix)]
#[test]
fn the_whole_permission_word_is_compared() {
    use std::os::unix::fs::PermissionsExt;
    let root = tempfile::tempdir().unwrap();
    let path = root.path().join("receipt");
    open_private_file(&path, PrivateOpen::CreateNew).unwrap();
    for mode in [0o400, 0o700] {
        fs::set_permissions(&path, fs::Permissions::from_mode(mode)).unwrap();
        let protection = file_protection(&path);
        assert!(!protection.is_exactly(0o600), "{mode:o}");
        assert!(protection.is_private(), "{mode:o}");
    }
}
