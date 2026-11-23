//! The responsible process of macOS: the process that the system charges for a process, also for an XPC service that
//! serves it, such as a WebKit process of a web view.
//!
//! A child inherits the responsible process of its parent, so a child of a terminal names the terminal. A process
//! without a responsible process, and each child that inherits from it, answers its own pid. A child that disclaims the
//! responsibility at its start is its own responsible process, as an app that Launch Services starts, and its children
//! name it. Other hosts have no responsible process, so there [`Responsibility::Own`] changes nothing and
//! [`responsible_pid`] answers none.

use tokio::process::Command;

#[cfg(test)]
#[cfg(target_os = "macos")]
mod tests;

/// The responsible process of a child, see the module text.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub enum Responsibility {
    /// The child names the responsible process of the controller.
    #[default]
    Inherited,
    /// The child is its own responsible process on macOS. The XPC services that serve it, and its children, then name
    /// its pid.
    Own,
}

/// The responsible process of `pid` on macOS, from `responsibility_get_pid_responsible_for_pid` of libSystem. A pid
/// without a process, and every pid on another host, has none.
#[cfg_attr(
    not(target_os = "macos"),
    expect(clippy::missing_const_for_fn, reason = "the call of libSystem on macOS is not const")
)]
pub fn responsible_pid(pid: i32) -> Option<i32> {
    imp::responsible_pid(pid)
}

/// Makes `command` start its program as its own responsible process, when `responsibility` is
/// [`Responsibility::Own`]. `argv` and `environment` are the argv and the whole environment of the child.
pub(super) fn prepare(command: &mut Command, responsibility: Responsibility, argv: &[String], environment: &[(String, String)]) {
    if responsibility == Responsibility::Own {
        imp::disclaim(command, argv, environment);
    }
}

#[cfg(target_os = "macos")]
mod imp {
    use std::ffi::{CString, c_char, c_int};
    use std::io;
    use std::path::Path;

    use tokio::process::Command;

    unsafe extern "C" {
        /// Exported by libSystem since macOS 10.14 and not in a public header.
        fn responsibility_get_pid_responsible_for_pid(pid: libc::pid_t) -> libc::pid_t;
        /// Exported by libSystem since macOS 10.14 and not in a public header. With `disclaim` 1, the process that
        /// the attributes start is its own responsible process.
        fn responsibility_spawnattrs_setdisclaim(attributes: *mut libc::posix_spawnattr_t, disclaim: c_int) -> c_int;
    }

    pub(super) fn responsible_pid(pid: i32) -> Option<i32> {
        if pid <= 0 {
            return None;
        }
        // SAFETY: the call takes a plain pid and reads no memory of this process.
        let responsible = unsafe { responsibility_get_pid_responsible_for_pid(pid) };
        (responsible > 0).then_some(responsible)
    }

    /// The exec of the child, which the parent prepares before the fork: the spawn attributes with `SETEXEC` and the
    /// disclaim, and the C strings of the program, the argv and the environment with their pointer lists.
    struct Exec {
        attributes: libc::posix_spawnattr_t,
        program: CString,
        _arguments: Vec<CString>,
        argument_pointers: Vec<*mut c_char>,
        _variables: Vec<CString>,
        variable_pointers: Vec<*mut c_char>,
    }

    // SAFETY: the child reads the pointers only after the fork, in its own copy of the memory, and the parent never
    // writes through them. The strings and the attributes live as long as the closure of the command.
    unsafe impl Send for Exec {}
    // SAFETY: as for `Send`; nothing writes through a shared `Exec`.
    unsafe impl Sync for Exec {}

    impl Drop for Exec {
        fn drop(&mut self) {
            // SAFETY: the attributes were initialised in `Exec::new`, and nothing uses them after this.
            let _ = unsafe { libc::posix_spawnattr_destroy(&raw mut self.attributes) };
        }
    }

    impl Exec {
        /// The exec of `argv` with `environment`, or the errno of the part that cannot be prepared: `EINVAL` for a
        /// relative program or a NUL in a string, and the errno of the attribute calls.
        fn new(argv: &[String], environment: &[(String, String)]) -> Result<Self, c_int> {
            let program = argv
                .first()
                .filter(|program| Path::new(program).is_absolute())
                .ok_or(libc::EINVAL)?;
            let program = CString::new(program.as_str()).map_err(|_nul| libc::EINVAL)?;
            let arguments = argv
                .iter()
                .map(|argument| CString::new(argument.as_str()))
                .collect::<Result<Vec<_>, _>>()
                .map_err(|_nul| libc::EINVAL)?;
            let variables = environment
                .iter()
                .map(|(name, value)| CString::new(format!("{name}={value}")))
                .collect::<Result<Vec<_>, _>>()
                .map_err(|_nul| libc::EINVAL)?;
            let pointers = |strings: &[CString]| -> Vec<*mut c_char> {
                strings
                    .iter()
                    .map(|string| string.as_ptr().cast_mut())
                    .chain(std::iter::once(std::ptr::null_mut()))
                    .collect()
            };
            let mut attributes: libc::posix_spawnattr_t = std::ptr::null_mut();
            // SAFETY: `attributes` is a valid out pointer for one attribute object.
            let status = unsafe { libc::posix_spawnattr_init(&raw mut attributes) };
            if status != 0 {
                return Err(status);
            }
            let mut exec = Self {
                attributes,
                program,
                argument_pointers: pointers(&arguments),
                _arguments: arguments,
                variable_pointers: pointers(&variables),
                _variables: variables,
            };
            let flags = libc::c_short::try_from(libc::POSIX_SPAWN_SETEXEC).map_err(|_overflow| libc::EINVAL)?;
            // SAFETY: the attribute object is initialised, and the calls only set its fields.
            let status = unsafe { libc::posix_spawnattr_setflags(&raw mut exec.attributes, flags) };
            if status != 0 {
                return Err(status);
            }
            // SAFETY: as above.
            let status = unsafe { responsibility_spawnattrs_setdisclaim(&raw mut exec.attributes, 1) };
            if status != 0 {
                return Err(status);
            }
            Ok(exec)
        }

        /// Replaces the image of this process with the program, as `execve` does, with the disclaim. It returns only
        /// on a failure.
        fn run(&self) -> io::Error {
            // SAFETY: every pointer points into the strings of `self`, each list ends with a null pointer, and the
            // attributes are initialised. With `SETEXEC` the call is the exec of this process, so no pid comes back.
            let status = unsafe {
                libc::posix_spawn(
                    std::ptr::null_mut(),
                    self.program.as_ptr(),
                    std::ptr::null(),
                    &raw const self.attributes,
                    self.argument_pointers.as_ptr(),
                    self.variable_pointers.as_ptr(),
                )
            };
            io::Error::from_raw_os_error(status)
        }
    }

    /// Replaces the exec of the child with a `posix_spawn` that has `POSIX_SPAWN_SETEXEC` and the disclaim. The child
    /// keeps its pid, its process group, its directory and its descriptors, which the command set before the hook.
    /// The hook runs before the command sets the environment, so the exec passes `environment` itself.
    pub(super) fn disclaim(command: &mut Command, argv: &[String], environment: &[(String, String)]) {
        let exec = Exec::new(argv, environment);
        let hook = move || match &exec {
            Ok(exec) => Err(exec.run()),
            Err(errno) => Err(io::Error::from_raw_os_error(*errno)),
        };
        // SAFETY: the hook runs between the fork and the exec. It allocates nothing: the parent prepared every string
        // and the attributes, and an error is a raw OS error. `posix_spawn` with `SETEXEC` is the exec of the child.
        unsafe {
            command.pre_exec(hook);
        }
    }
}

#[cfg(not(target_os = "macos"))]
mod imp {
    use tokio::process::Command;

    pub(super) const fn responsible_pid(_pid: i32) -> Option<i32> {
        None
    }

    pub(super) const fn disclaim(_command: &mut Command, _argv: &[String], _environment: &[(String, String)]) {}
}
