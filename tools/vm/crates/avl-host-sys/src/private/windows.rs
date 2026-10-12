//! The Windows form of the private seam: a protected access list for the caller, SYSTEM and Administrators, a
//! reparse point opened itself and refused, and the owner SID and the access list of the open handle.
//!
//! The access list is written as SDDL, `O:<user>D:P(A;;FA;;;<user>)(A;;FA;;;SY)(A;;FA;;;BA)`. `P` protects it, so
//! nothing the parent grants is inherited. A directory's entries carry `OICI`, so files and directories created in
//! it inherit the same list. The owner is the user SID, also for an elevated process, whose default owner is the
//! Administrators group.

use std::ffi::c_void;
use std::fs::{File, Metadata};
use std::io;
use std::os::windows::ffi::OsStrExt;
use std::os::windows::fs::MetadataExt;
use std::os::windows::io::{AsRawHandle, FromRawHandle, OwnedHandle};
use std::path::Path;
use std::ptr;

use windows_sys::Win32::Foundation::{GENERIC_READ, GENERIC_WRITE, HANDLE, INVALID_HANDLE_VALUE, LocalFree, WIN32_ERROR};
use windows_sys::Win32::Security::Authorization::{
    ConvertSidToStringSidW, ConvertStringSecurityDescriptorToSecurityDescriptorW, GetSecurityInfo, SDDL_REVISION_1, SE_FILE_OBJECT,
    SetNamedSecurityInfoW,
};
use windows_sys::Win32::Security::{
    ACCESS_ALLOWED_ACE, ACE_HEADER, ACL, ACL_SIZE_INFORMATION, AclSizeInformation, DACL_SECURITY_INFORMATION, EqualSid, GetAce,
    GetAclInformation, GetSecurityDescriptorDacl, GetTokenInformation, INHERIT_ONLY_ACE, IsWellKnownSid, OWNER_SECURITY_INFORMATION,
    PROTECTED_DACL_SECURITY_INFORMATION, PSECURITY_DESCRIPTOR, PSID, SECURITY_ATTRIBUTES, TOKEN_INFORMATION_CLASS, TOKEN_OWNER,
    TOKEN_QUERY, TOKEN_USER, TokenOwner, TokenUser, WinBuiltinAdministratorsSid, WinLocalSystemSid,
};
use windows_sys::Win32::Storage::FileSystem::{
    CREATE_NEW, CreateDirectoryW, CreateFileW, FILE_ATTRIBUTE_NORMAL, FILE_ATTRIBUTE_REPARSE_POINT, FILE_CREATION_DISPOSITION,
    FILE_FLAG_BACKUP_SEMANTICS, FILE_FLAG_OPEN_REPARSE_POINT, FILE_FLAGS_AND_ATTRIBUTES, FILE_GENERIC_WRITE, FILE_READ_ATTRIBUTES,
    FILE_SHARE_DELETE, FILE_SHARE_READ, FILE_SHARE_WRITE, FILE_WRITE_DATA, OPEN_ALWAYS, OPEN_EXISTING, READ_CONTROL,
};
use windows_sys::Win32::System::Threading::{GetCurrentProcess, OpenProcessToken};

use super::{NoFollowError, PrivateOpen, Protection};

/// The ACE types of a DACL, from `winnt.h`. They live in a `windows-sys` feature this crate does not enable.
const ACCESS_ALLOWED_ACE_TYPE: u8 = 0;
const ACCESS_DENIED_ACE_TYPE: u8 = 1;
const ACCESS_DENIED_OBJECT_ACE_TYPE: u8 = 6;
const ACCESS_DENIED_CALLBACK_ACE_TYPE: u8 = 10;
const ACCESS_DENIED_CALLBACK_OBJECT_ACE_TYPE: u8 = 12;

/// The share mode std gives every file it opens, so a private file does not lock out a reader that may open it.
const SHARE_ALL: u32 = FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE;

#[expect(clippy::cast_possible_truncation, reason = "the two structures are a few bytes long")]
const SECURITY_ATTRIBUTES_SIZE: u32 = size_of::<SECURITY_ATTRIBUTES>() as u32;
#[expect(clippy::cast_possible_truncation, reason = "the two structures are a few bytes long")]
const ACL_SIZE_INFORMATION_SIZE: u32 = size_of::<ACL_SIZE_INFORMATION>() as u32;

/// Whether an access list grants nobody beyond the caller, SYSTEM and Administrators.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(super) struct Access {
    owner_only: bool,
}

impl Access {
    pub(super) const fn is_private(self) -> bool {
        self.owner_only
    }

    #[expect(
        clippy::verbose_bit_mask,
        reason = "`mode & 0o077 == 0` reads as no group or other bits; trailing_zeros() >= 6 does not"
    )]
    pub(super) const fn is_exactly(self, mode: u32) -> bool {
        self.owner_only && mode & 0o077 == 0
    }
}

pub(super) fn open_private_file(path: &Path, how: PrivateOpen) -> io::Result<File> {
    let (access, disposition): (u32, FILE_CREATION_DISPOSITION) = match how {
        PrivateOpen::CreateNew => (GENERIC_WRITE, CREATE_NEW),
        PrivateOpen::ReadWrite => (GENERIC_READ | GENERIC_WRITE, OPEN_ALWAYS),
        PrivateOpen::Truncate => (GENERIC_WRITE, OPEN_ALWAYS),
        // What std asks for an append: every write right except the one that writes at an offset.
        PrivateOpen::Append => (FILE_GENERIC_WRITE & !FILE_WRITE_DATA, OPEN_ALWAYS),
    };
    let descriptor = private_descriptor(Inheritance::None)?;
    let file = create_file(path, access, Some(&descriptor), disposition, FILE_ATTRIBUTE_NORMAL)?;
    // Emptied after the open rather than with `CREATE_ALWAYS`, which refuses a hidden or a system file.
    if how == PrivateOpen::Truncate {
        file.set_len(0)?;
    }
    Ok(file)
}

pub(super) fn create_private_directory(path: &Path) -> io::Result<()> {
    let descriptor = private_descriptor(Inheritance::Entries)?;
    let attributes = descriptor.attributes();
    let path = wide(path)?;
    // SAFETY: `path` is a NUL-terminated wide string, and `attributes` points at a descriptor that outlives the call.
    let created = unsafe { CreateDirectoryW(path.as_ptr(), &raw const attributes) };
    if created == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}

pub(super) fn restrict_file(path: &Path) -> io::Result<()> {
    restrict(path, Inheritance::None)
}

pub(super) fn restrict_directory(path: &Path) -> io::Result<()> {
    restrict(path, Inheritance::Entries)
}

/// Replaces the access list of `path` with the private one, and protects it from what the parent grants.
fn restrict(path: &Path, inheritance: Inheritance) -> io::Result<()> {
    let descriptor = private_descriptor(inheritance)?;
    let dacl = descriptor.dacl()?;
    let path = wide(path)?;
    // SAFETY: `path` is a NUL-terminated wide string, and `dacl` points into `descriptor`, which outlives the call.
    let status = unsafe {
        SetNamedSecurityInfoW(
            path.as_ptr(),
            SE_FILE_OBJECT,
            DACL_SECURITY_INFORMATION | PROTECTED_DACL_SECURITY_INFORMATION,
            ptr::null_mut(),
            ptr::null_mut(),
            dacl,
            ptr::null(),
        )
    };
    win32_result(status)
}

pub(super) fn open_no_follow(path: &Path) -> Result<File, NoFollowError> {
    let file = create_file(
        path,
        GENERIC_READ,
        None,
        OPEN_EXISTING,
        FILE_FLAG_OPEN_REPARSE_POINT | FILE_FLAG_BACKUP_SEMANTICS,
    )
    .map_err(|error| match error.kind() {
        io::ErrorKind::NotFound => NoFollowError::Missing,
        _ => NoFollowError::Failed(error),
    })?;
    let metadata = file.metadata().map_err(NoFollowError::Failed)?;
    if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
        return Err(NoFollowError::Link);
    }
    Ok(file)
}

pub(super) fn protection(file: &File, _metadata: &Metadata) -> io::Result<Protection> {
    handle_protection(file.as_raw_handle())
}

pub(super) fn directory_protection(path: &Path) -> io::Result<Option<Protection>> {
    let directory = create_file(
        path,
        READ_CONTROL | FILE_READ_ATTRIBUTES,
        None,
        OPEN_EXISTING,
        FILE_FLAG_OPEN_REPARSE_POINT | FILE_FLAG_BACKUP_SEMANTICS,
    )?;
    let metadata = directory.metadata()?;
    if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 || !metadata.is_dir() {
        return Ok(None);
    }
    handle_protection(directory.as_raw_handle()).map(Some)
}

/// The owner and the access list of an open handle, which must carry `READ_CONTROL`.
fn handle_protection(handle: HANDLE) -> io::Result<Protection> {
    let caller = Caller::current()?;
    let mut owner: PSID = ptr::null_mut();
    let mut dacl: *mut ACL = ptr::null_mut();
    let mut descriptor: PSECURITY_DESCRIPTOR = ptr::null_mut();
    // SAFETY: `handle` is open for the duration of the call, and every out pointer is a valid local.
    let status = unsafe {
        GetSecurityInfo(
            handle,
            SE_FILE_OBJECT,
            OWNER_SECURITY_INFORMATION | DACL_SECURITY_INFORMATION,
            &raw mut owner,
            ptr::null_mut(),
            &raw mut dacl,
            ptr::null_mut(),
            &raw mut descriptor,
        )
    };
    win32_result(status)?;
    // `owner` and `dacl` point into the descriptor, so it is freed only after they are read.
    let _descriptor = LocalMemory(descriptor);
    Ok(Protection {
        owned_by_caller: caller.is_owner(owner),
        access: Access {
            owner_only: grants_only_trusted(dacl, &caller),
        },
    })
}

/// Whether every entry that applies to the object itself grants access only to a trusted SID.
///
/// A null access list grants everyone everything. A deny entry only takes access away, so it is accepted. An
/// inherit-only entry applies to the children, not to the object. Any other allow entry, an object or a callback
/// entry included, is refused, because this check cannot say whom it grants.
fn grants_only_trusted(dacl: *const ACL, caller: &Caller) -> bool {
    if dacl.is_null() {
        return false;
    }
    let mut size = ACL_SIZE_INFORMATION::default();
    // SAFETY: `dacl` is a valid access list, and `size` is a buffer of the size the call is told.
    let read = unsafe { GetAclInformation(dacl, (&raw mut size).cast(), ACL_SIZE_INFORMATION_SIZE, AclSizeInformation) };
    if read == 0 {
        return false;
    }
    (0..size.AceCount).all(|index| {
        let mut ace: *mut c_void = ptr::null_mut();
        // SAFETY: `index` is below the entry count the list reported, and `ace` is a valid out pointer.
        if unsafe { GetAce(dacl, index, &raw mut ace) } == 0 {
            return false;
        }
        // SAFETY: every entry of an access list starts with an `ACE_HEADER`.
        let header = unsafe { ace.cast::<ACE_HEADER>().read_unaligned() };
        if u32::from(header.AceFlags) & INHERIT_ONLY_ACE != 0 {
            return true;
        }
        match header.AceType {
            ACCESS_ALLOWED_ACE_TYPE => {
                let sid = ace.cast::<u8>().wrapping_add(std::mem::offset_of!(ACCESS_ALLOWED_ACE, SidStart));
                caller.is_trusted(sid.cast())
            }
            ACCESS_DENIED_ACE_TYPE
            | ACCESS_DENIED_OBJECT_ACE_TYPE
            | ACCESS_DENIED_CALLBACK_ACE_TYPE
            | ACCESS_DENIED_CALLBACK_OBJECT_ACE_TYPE => true,
            _ => false,
        }
    })
}

/// Opens or creates a file with `CreateFileW`, the one call that takes an access list for the creation.
fn create_file(
    path: &Path,
    access: u32,
    descriptor: Option<&LocalMemory>,
    disposition: FILE_CREATION_DISPOSITION,
    flags: FILE_FLAGS_AND_ATTRIBUTES,
) -> io::Result<File> {
    let attributes = descriptor.map(LocalMemory::attributes);
    let attributes_pointer = attributes.as_ref().map_or(ptr::null(), ptr::from_ref);
    let path = wide(path)?;
    // SAFETY: `path` is a NUL-terminated wide string, and `attributes_pointer` is null or points at attributes whose
    // descriptor outlives the call.
    let handle = unsafe {
        CreateFileW(
            path.as_ptr(),
            access,
            SHARE_ALL,
            attributes_pointer,
            disposition,
            flags,
            ptr::null_mut(),
        )
    };
    if handle == INVALID_HANDLE_VALUE {
        return Err(io::Error::last_os_error());
    }
    // SAFETY: `CreateFileW` answered an open handle, and nothing else owns it.
    Ok(unsafe { File::from_raw_handle(handle) })
}

/// A path as `CreateFileW` takes it: absolute, verbatim so a long path is not cut at `MAX_PATH`, and NUL-terminated.
fn wide(path: &Path) -> io::Result<Vec<u16>> {
    let absolute = std::path::absolute(path)?;
    let text: Vec<u16> = absolute.as_os_str().encode_wide().collect();
    if text.contains(&0) {
        return Err(io::Error::new(io::ErrorKind::InvalidInput, "a path contains a NUL"));
    }
    let starts = |prefix: &str| text.starts_with(&prefix.encode_utf16().collect::<Vec<_>>());
    let mut verbatim: Vec<u16> = if starts(r"\\?\") || starts(r"\\.\") {
        text
    } else if starts(r"\\") {
        r"\\?\UNC\".encode_utf16().chain(text[2..].iter().copied()).collect()
    } else {
        r"\\?\".encode_utf16().chain(text).collect()
    };
    verbatim.push(0);
    Ok(verbatim)
}

fn win32_result(status: WIN32_ERROR) -> io::Result<()> {
    if status == 0 {
        Ok(())
    } else {
        Err(io::Error::from_raw_os_error(status.cast_signed()))
    }
}

/// Whether the private access list is inherited by the entries created in a directory.
#[derive(Clone, Copy)]
enum Inheritance {
    None,
    Entries,
}

/// The private security descriptor for the caller, as SDDL describes it in the module comment.
fn private_descriptor(inheritance: Inheritance) -> io::Result<LocalMemory> {
    let user = Caller::current()?.user_sid_string()?;
    let flags = match inheritance {
        Inheritance::None => "",
        Inheritance::Entries => "OICI",
    };
    let sddl = format!("O:{user}D:P(A;{flags};FA;;;{user})(A;{flags};FA;;;SY)(A;{flags};FA;;;BA)");
    let sddl: Vec<u16> = sddl.encode_utf16().chain(std::iter::once(0)).collect();
    let mut descriptor: PSECURITY_DESCRIPTOR = ptr::null_mut();
    // SAFETY: `sddl` is a NUL-terminated wide string, and `descriptor` is a valid out pointer.
    let converted = unsafe {
        ConvertStringSecurityDescriptorToSecurityDescriptorW(sddl.as_ptr(), SDDL_REVISION_1, &raw mut descriptor, ptr::null_mut())
    };
    if converted == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(LocalMemory(descriptor))
}

/// Memory the system allocated with `LocalAlloc` for this process: a security descriptor or a SID string.
struct LocalMemory(*mut c_void);

impl LocalMemory {
    /// Attributes that create an object with this security descriptor. They borrow it, so they must not outlive it.
    const fn attributes(&self) -> SECURITY_ATTRIBUTES {
        SECURITY_ATTRIBUTES {
            nLength: SECURITY_ATTRIBUTES_SIZE,
            lpSecurityDescriptor: self.0,
            bInheritHandle: 0,
        }
    }

    /// The access list of this security descriptor, which points into it.
    fn dacl(&self) -> io::Result<*const ACL> {
        let mut present = 0;
        let mut defaulted = 0;
        let mut dacl: *mut ACL = ptr::null_mut();
        // SAFETY: `self.0` is a valid security descriptor, and every out pointer is a valid local.
        let read = unsafe { GetSecurityDescriptorDacl(self.0, &raw mut present, &raw mut dacl, &raw mut defaulted) };
        if read == 0 {
            return Err(io::Error::last_os_error());
        }
        if present == 0 || dacl.is_null() {
            return Err(io::Error::other("the private security descriptor has no access list"));
        }
        Ok(dacl.cast_const())
    }
}

impl Drop for LocalMemory {
    fn drop(&mut self) {
        if !self.0.is_null() {
            // SAFETY: the system allocated this memory with `LocalAlloc`, and nothing uses it after the drop.
            unsafe { LocalFree(self.0) };
        }
    }
}

/// The invoking process's user SID and default owner SID, each in the buffer `GetTokenInformation` filled.
struct Caller {
    user: Vec<u64>,
    owner: Vec<u64>,
}

impl Caller {
    fn current() -> io::Result<Self> {
        // SAFETY: `GetCurrentProcess` has no preconditions, and answers a pseudo-handle that needs no close.
        let process = unsafe { GetCurrentProcess() };
        let mut token: HANDLE = ptr::null_mut();
        // SAFETY: `process` is the current process's pseudo-handle, and `token` is a valid out pointer.
        if unsafe { OpenProcessToken(process, TOKEN_QUERY, &raw mut token) } == 0 {
            return Err(io::Error::last_os_error());
        }
        // SAFETY: `OpenProcessToken` answered an open handle, and nothing else owns it.
        let token = unsafe { OwnedHandle::from_raw_handle(token) };
        Ok(Self {
            user: token_information(&token, TokenUser)?,
            owner: token_information(&token, TokenOwner)?,
        })
    }

    const fn user_sid(&self) -> PSID {
        // SAFETY: the buffer holds the `TOKEN_USER` that `GetTokenInformation` wrote, aligned for it.
        unsafe { self.user.as_ptr().cast::<TOKEN_USER>().read().User.Sid }
    }

    const fn owner_sid(&self) -> PSID {
        // SAFETY: the buffer holds the `TOKEN_OWNER` that `GetTokenInformation` wrote, aligned for it.
        unsafe { self.owner.as_ptr().cast::<TOKEN_OWNER>().read().Owner }
    }

    /// Whether `sid` is the caller's user or its default owner.
    fn is_owner(&self, sid: PSID) -> bool {
        !sid.is_null() && (equal_sid(sid, self.user_sid()) || equal_sid(sid, self.owner_sid()))
    }

    /// Whether `sid` may have access to a private object: the caller, SYSTEM, or Administrators.
    fn is_trusted(&self, sid: PSID) -> bool {
        // SAFETY: `sid` points at the SID of an access list entry that the caller keeps alive.
        let system = unsafe { IsWellKnownSid(sid, WinLocalSystemSid) } != 0;
        // SAFETY: as above.
        let administrators = unsafe { IsWellKnownSid(sid, WinBuiltinAdministratorsSid) } != 0;
        self.is_owner(sid) || system || administrators
    }

    /// The user SID as SDDL writes it, `S-1-5-21-…`.
    fn user_sid_string(&self) -> io::Result<String> {
        let mut text: *mut u16 = ptr::null_mut();
        // SAFETY: the user SID is valid while `self` is, and `text` is a valid out pointer.
        if unsafe { ConvertSidToStringSidW(self.user_sid(), &raw mut text) } == 0 {
            return Err(io::Error::last_os_error());
        }
        let memory = LocalMemory(text.cast());
        let mut units = Vec::new();
        loop {
            // SAFETY: the string is NUL-terminated, and the loop stops at the NUL.
            let unit = unsafe { text.wrapping_add(units.len()).read() };
            if unit == 0 {
                break;
            }
            units.push(unit);
        }
        drop(memory);
        String::from_utf16(&units).map_err(io::Error::other)
    }
}

fn equal_sid(left: PSID, right: PSID) -> bool {
    // SAFETY: both SIDs are valid for the duration of the call.
    unsafe { EqualSid(left, right) != 0 }
}

/// One `GetTokenInformation` answer, in a buffer aligned for every structure it can hold.
fn token_information(token: &OwnedHandle, class: TOKEN_INFORMATION_CLASS) -> io::Result<Vec<u64>> {
    let mut needed = 0u32;
    // SAFETY: a null buffer of length 0 asks for the size only, and `needed` is a valid out pointer. The call fails
    // with `ERROR_INSUFFICIENT_BUFFER`, which the size below stands for.
    unsafe { GetTokenInformation(token.as_raw_handle(), class, ptr::null_mut(), 0, &raw mut needed) };
    if needed == 0 {
        return Err(io::Error::last_os_error());
    }
    let mut buffer = vec![0u64; (needed as usize).div_ceil(size_of::<u64>())];
    // SAFETY: `buffer` holds at least `needed` bytes, and `needed` is a valid out pointer.
    let read = unsafe { GetTokenInformation(token.as_raw_handle(), class, buffer.as_mut_ptr().cast(), needed, &raw mut needed) };
    if read == 0 {
        return Err(io::Error::last_os_error());
    }
    Ok(buffer)
}
