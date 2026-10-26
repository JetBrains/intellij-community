//! A child process that is responsible for itself, on macOS.
//!
//! macOS asks the responsible process for the Local Network permission. A child inherits the responsible process of
//! its parent, such as the terminal app or the IDE that started the client. The private
//! `responsibility_spawnattrs_setdisclaim` of libSystem starts a child that is responsible for itself, as LLDB and
//! Chromium do. The standard library has no way to pass it, so a `pre_exec` closure replaces the exec of the child with
//! `posix_spawn` and `POSIX_SPAWN_SETEXEC`: the same process, its pipes and its wait stay, with a new program.

use std::ffi::{CString, c_char, c_int, c_short, c_void};
use std::io;
use std::os::unix::ffi::OsStrExt as _;
use std::os::unix::process::CommandExt as _;
use std::process::Command;
use std::ptr;
use std::sync::OnceLock;

/// `int responsibility_spawnattrs_setdisclaim(posix_spawnattr_t *attrs, int disclaim)` of `<responsibility.h>`.
type SetDisclaim = unsafe extern "C" fn(*mut libc::posix_spawnattr_t, c_int) -> c_int;

/// Makes the child of `command` responsible for itself. Answers why it cannot, and then the child keeps the responsible
/// process of this process. A `command` that changes the environment is refused, because the standard library applies
/// the change after the closure that replaces the exec. A program without a path is refused, because the search of
/// `PATH` reads the environment, which the child of a fork must not do.
pub(crate) fn disclaim(command: &mut Command) -> Result<(), String> {
    if command.get_envs().next().is_some() {
        return Err("the command changes the environment".to_owned());
    }
    if !command.get_program().as_bytes().contains(&b'/') {
        return Err(format!("{} has no path", command.get_program().display()));
    }
    let exec = Exec::new(command, set_disclaim()?)?;
    // SAFETY: the closure runs in the child between the fork and the exec. It calls only `posix_spawn`, which
    // allocates nothing, on values that the parent made.
    unsafe {
        command.pre_exec(move || Err(exec.run()));
    }
    Ok(())
}

/// Why this process cannot start a child that is responsible for itself, or `None` when it can.
pub(crate) fn unavailable() -> Option<String> {
    set_disclaim().err()
}

/// The disclaim function of libSystem, looked up once.
fn set_disclaim() -> Result<SetDisclaim, String> {
    static FOUND: OnceLock<Option<SetDisclaim>> = OnceLock::new();
    let found = *FOUND.get_or_init(|| {
        // SAFETY: the name is a C string, and `RTLD_DEFAULT` searches every image of the process.
        let symbol = unsafe { libc::dlsym(libc::RTLD_DEFAULT, c"responsibility_spawnattrs_setdisclaim".as_ptr()) };
        (!symbol.is_null()).then(|| {
            // SAFETY: libSystem exports the symbol as the function of `SetDisclaim`.
            unsafe { std::mem::transmute::<*mut c_void, SetDisclaim>(symbol) }
        })
    });
    found.ok_or_else(|| "libSystem has no responsibility_spawnattrs_setdisclaim".to_owned())
}

/// What the child needs for its exec. The parent makes it, because the child of a fork must not allocate.
struct Exec {
    program: CString,
    argv: Argv,
    attributes: Attributes,
}

impl Exec {
    fn new(command: &Command, set_disclaim: SetDisclaim) -> Result<Self, String> {
        let text = |value: &std::ffi::OsStr| {
            CString::new(value.as_bytes()).map_err(|error| format!("cannot pass {} to the child: {error}", value.display()))
        };
        let program = text(command.get_program())?;
        let mut strings = vec![program.clone()];
        for arg in command.get_args() {
            strings.push(text(arg)?);
        }
        let pointers = strings
            .iter()
            .map(|arg| arg.as_ptr().cast_mut())
            .chain(std::iter::once(ptr::null_mut()))
            .collect();
        Ok(Self {
            program,
            argv: Argv {
                _strings: strings,
                pointers,
            },
            attributes: Attributes::disclaiming(set_disclaim)?,
        })
    }

    /// Becomes the program, responsible for itself. Answers only the error of a failed spawn.
    fn run(&self) -> io::Error {
        let mut pid: libc::pid_t = 0;
        // SAFETY: the call only answers the address of the environment pointer of the process.
        let environ = unsafe { libc::_NSGetEnviron() };
        // SAFETY: the address stays valid for the whole life of the process.
        let environment = unsafe { *environ };
        // SAFETY: each pointer is valid, and `argv` ends with a null. With `POSIX_SPAWN_SETEXEC` the call returns only
        // when it fails.
        let failed = unsafe {
            libc::posix_spawn(
                &raw mut pid,
                self.program.as_ptr(),
                ptr::null(),
                &raw const self.attributes.0,
                self.argv.pointers.as_ptr(),
                environment,
            )
        };
        io::Error::from_raw_os_error(failed)
    }
}

/// The arguments of the child: the program first and a null last, and the strings that they point to.
struct Argv {
    _strings: Vec<CString>,
    pointers: Vec<*mut c_char>,
}

// SAFETY: the pointers point into `_strings`, which `Argv` owns and never changes, and no code writes through them.
unsafe impl Send for Argv {}
// SAFETY: as for `Send`.
unsafe impl Sync for Argv {}

/// The spawn attributes of a child that is responsible for itself.
struct Attributes(libc::posix_spawnattr_t);

// SAFETY: only `disclaiming` writes the attributes, before any other thread sees them, and `posix_spawn` only reads
// them.
unsafe impl Send for Attributes {}
// SAFETY: as for `Send`.
unsafe impl Sync for Attributes {}

impl Attributes {
    fn disclaiming(set_disclaim: SetDisclaim) -> Result<Self, String> {
        #[expect(
            clippy::cast_possible_truncation,
            reason = "POSIX_SPAWN_SETEXEC is 0x40, and posix_spawnattr_setflags takes a short"
        )]
        const SETEXEC: c_short = libc::POSIX_SPAWN_SETEXEC as c_short;
        let checked = |call: &str, failed: c_int| {
            if failed == 0 {
                Ok(())
            } else {
                Err(format!("{call}: {}", io::Error::from_raw_os_error(failed)))
            }
        };
        let mut attributes: libc::posix_spawnattr_t = ptr::null_mut();
        // SAFETY: `posix_spawnattr_init` writes the attributes that it allocates.
        checked("posix_spawnattr_init", unsafe { libc::posix_spawnattr_init(&raw mut attributes) })?;
        let mut attributes = Self(attributes);
        // SAFETY: the attributes are initialized.
        checked("posix_spawnattr_setflags", unsafe {
            libc::posix_spawnattr_setflags(&raw mut attributes.0, SETEXEC)
        })?;
        // SAFETY: the attributes are initialized, and `set_disclaim` has the signature of `SetDisclaim`.
        checked("responsibility_spawnattrs_setdisclaim", unsafe {
            set_disclaim(&raw mut attributes.0, 1)
        })?;
        Ok(attributes)
    }
}

impl Drop for Attributes {
    fn drop(&mut self) {
        // SAFETY: the attributes are initialized, and nothing uses them after the drop.
        unsafe {
            libc::posix_spawnattr_destroy(&raw mut self.0);
        }
    }
}

#[cfg(test)]
mod tests;
