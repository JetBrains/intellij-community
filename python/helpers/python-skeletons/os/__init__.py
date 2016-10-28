"""Skeleton for 'os' stdlib module."""


from __future__ import unicode_literals
import io
import os
import subprocess
import sys


error = OSError


def ctermid():
    """Return the filename corresponding to the controlling terminal of the
    process.

    :rtype: string
    """
    return ''


def getegid():
    """Return the effective group id of the current process.

    :rtype: int
    """
    return 0


def geteuid():
    """Return the current process's effective user id.

    :rtype: int
    """
    return 0


def getgid():
    """Return the real group id of the current process.

    :rtype: int
    """
    return 0


def getgroups():
    """Return list of supplemental group ids associated with the current
    process.

    :rtype: list[int]
    """
    return []


if sys.version_info >= (2, 7):
    def initgroups(username, gid):
        """Call the system initgroups() to initialize the group access list
        with all of the groups of which the specified username is a member,
        plus the specified group id.

        :type username: string
        :type gid: int
        :rtype: None
        """
        pass


def getlogin():
    """Return the name of the user logged in on the controlling terminal of the
    process.

    :rtype: string
    """
    return ''


def getpgid(pid):
    """Return the process group id of the process with process id pid.

    :type pid: int
    :rtype: int
    """
    return 0


def getpgrp():
    """Return the id of the current process group.

    :rtype: int
    """
    return 0


def getpid():
    """Return the current process id.

    :rtype: int
    """
    return 0


def getppid():
    """Return the parent's process id.

    :rtype: int
    """
    return 0


if sys.version_info >= (2, 7):
    def getresuid():
        """Return a tuple (ruid, euid, suid) denoting the current process's
        real, effective, and saved user ids.

        :rtype: (int, int, int)
        """
        return 0, 0, 0

    def getresgid():
        """Return a tuple (rgid, egid, sgid) denoting the current process's
        real, effective, and saved group ids.

        :rtype: (int, int, int)
        """
        return 0, 0, 0


def getuid():
    """Return the current process's user id.

    :rtype: int
    """
    return 0


def getenv(varname, value=None):
    """Return the value of the environment variable varname if it exists, or
    value if it doesn't.

    :type varname: string
    :type value: T
    :rtype: string | T
    """
    pass


def putenv(varname, value):
    """Set the environment variable named varname to the string value.

    :type varname: string
    :rtype: None
    """
    pass


def setegid(egid):
    """Set the current process's effective group id.

    :type egid: int
    :rtype: None
    """
    pass


def seteuid(euid):
    """Set the current process's effective user id.

    :type euid: int
    :rtype: None
    """
    pass


def setgid(gid):
    """Set the current process' group id.

    :type gid: int
    :rtype: None
    """
    pass


def setgroups(groups):
    """Set the list of supplemental group ids associated with the current
    process to groups.

    :type groups: collections.Iterable[int]
    :rtype: None
    """
    pass


def setpgid(pid, pgrp):
    """Call the system call setpgid() to set the process group id of the
    process with id pid to the process group with id pgrp.

    :type pid: int
    :type pgrp: int
    :rtype: None
    """
    pass


def setregid(rgid, egid):
    """Set the current process's real and effective group ids.

    :type rgid: int
    :type egid: int
    :rtype: None
    """
    pass


if sys.version_info >= (2, 7):
    def setresgid(rgid, egid, sgid):
        """Set the current process's real, effective, and saved group ids.

        :type rgid: int
        :type egid: int
        :type sgid: int
        :rtype: None
        """
        pass

    def setresuid(ruid, euid, suid):
        """Set the current process's real, effective, and saved user ids.

        :type ruid: int
        :type euid: int
        :type suid: int
        :rtype None
        """
        pass


def setreuid(ruid, euid):
    """Set the current process's real and effective user ids.

    :type ruid: int
    :type euid: int
    :rtype None
    """
    pass


def setsid():
    """Call the system call getsid().

    :rtype: None
    """
    pass


def setuid(uid):
    """Set the current process's user id.

    :type uid: int
    :rtype: None
    """
    pass


def strerror(code):
    """Return the error message corresponding to the error code in code.

    :type code: int
    :rtype: string
    """
    return ''


def umask(mask):
    """Set the current numeric umask and return the previous umask.

    :type mask: int
    :rtype: int
    """
    return 0


def uname():
    """Return a 5-tuple containing information identifying the current
    operating system.

    :rtype: (string, string, string, string, string)
    """
    return '', '', '', '', ''


def unsetenv(varname):
    """Unset (delete) the environment variable named varname.

    :type varname: string
    :rtype: None
    """
    pass


def fdopen(fd, mode='r', bufsize=-1):
    """Return an open file object connected to the file descriptor fd.

    :type fd: int
    :type mode: string
    :type bufsize: int
    :rtype: file
    """
    return file()


def popen(command, mode='r', bufsize=-1):
    """Open a pipe to or from command.

    :type command: string
    :type mode: string
    :type bufsize: int
    :rtype: os._wrap_close
    """
    pass


class _wrap_close(io.TextIOWrapper[unicode]):
    def __init__(self, stream, proc):
        """
        :type stream: io.TextIOWrapper[unicode]
        :type proc: subprocess.Popen
        """
        pass

    def close(self):
        """
        :rtype: int | None
        """
        pass

    def __enter__(self):
        """
        :rtype: os._wrap_close
        """
        pass

    def __exit__(self, *args):
        pass

    def __iter__(self):
        """
        :rtype: collections.Iterator[unicode]
        """
        pass


def tmpfile():
    """Return a new file object opened in update mode (w+b).

    :rtype: io.FileIO[bytes]
    """
    pass


def popen2(cmd, mode='r', bufsize=-1):
    """Execute cmd as a sub-process and return the file objects (child_stdin,
    child_stdout).

    :type cmd: string
    :type mode: string
    :type bufsize: int
    :rtype: (io.FileIO[bytes], io.FileIO[bytes])
    """
    pass


def popen3(cmd, mode='r', bufsize=-1):
    """Execute cmd as a sub-process and return the file objects (child_stdin,
    child_stdout, child_stderr).

    :type cmd: string
    :type mode: string
    :type bufsize: int
    :rtype: (io.FileIO[bytes], io.FileIO[bytes], io.FileIO[bytes])
    """
    pass


def popen4(cmd, mode='r', bufsize=-1):
    """Execute cmd as a sub-process and return the file objects (child_stdin,
    child_stdout_and_stderr).

    :type cmd: string
    :type mode: string
    :type bufsize: int
    :rtype: (io.FileIO[bytes], io.FileIO[bytes])
    """
    pass


def close(fd):
    """Close file descriptor fd.

    :type fd: int
    :rtype: None
    """
    pass


if sys.version_info >= (2, 6):
    def closerange(fd_low, fd_high):
        """Close all file descriptors from fd_low (inclusive) to fd_high
        (exclusive), ignoring errors.

        :type fd_low: int
        :type fd_high: int
        :rtype: None
        """
        pass


def dup(fd):
    """Return a duplicate of file descriptor fd.

    :type fd: int
    :rtype: int
    """
    return 0


def dup2(fd, fd2):
    """Duplicate file descriptor fd to fd2, closing the latter first if
    necessary.

    :type fd: int
    :type fd2: int
    :rtype: None
    """
    pass


if sys.version_info >= (2, 6):
    def fchmod(fd, mode):
        """Change the mode of the file given by fd to the numeric mode.

        :type fd: int
        :type mode: int
        :rtype: None
        """
        pass

    def fchown(fd, uid, gid):
        """Change the owner and group id of the file given by fd to the numeric
        uid and gid.

        :type fd: int
        :type uid: int
        :type gid: int
        :rtype: None
        """
        pass


def fdatasync(fd):
    """Force write of file with filedescriptor fd to disk.

    :type fd: int
    :rtype: None
    """
    pass


def fpathconf(fd, name):
    """Return system configuration information relevant to an open file.

    :type fd: int
    :type name: string | int
    """
    pass


def fstat(fd):
    """Return status for file descriptor fd, like stat().

    :type fd: int
    :rtype: os.stat_result
    """
    pass


def fstatvfs(fd):
    """Return information about the filesystem containing the file associated
    with file descriptor fd, like statvfs().

    :type fd: int
    :rtype: os.statvfs_result
    """
    pass


def fsync(fd):
    """Force write of file with filedescriptor fd to disk.

    :type fd: int
    :rtype: None
    """
    pass


def ftruncate(fd, length):
    """Truncate the file corresponding to file descriptor fd, so that it is at
    most length bytes in size.

    :type fd: int
    :type length: numbers.Integral
    :rtype: None
    """
    pass


def isatty(fd):
    """Return True if the file descriptor fd is open and connected to a
    tty(-like) device, else False.

    :type fd: int
    :rtype: bool
    """
    return False


def lseek(fd, pos, how):
    """Set the current position of file descriptor fd to position pos, modified
    by how.

    :type fd: int
    :type pos: numbers.Integral
    :type how: int
    :rtype: None
    """
    pass


def open(file, flags, mode=0o777):
    """Open the file file and set various flags according to flags and possibly
    its mode according to mode.

    :type file: string
    :type flags: int
    :type mode: int
    :rtype: int
    """
    return 0


def openpty():
    """Open a new pseudo-terminal pair.

    :rtype: (int, int)
    """
    return 0, 0


def pipe():
    """Create a pipe.

    :rtype: (int, int)
    """
    return 0, 0


def read(fd, n):
    """Read at most n bytes from file descriptor fd.

    :type fd: int
    :type n: numbers.Integral
    :rtype: bytes
    """
    pass


def tcgetpgrp(fd):
    """Return the process group associated with the terminal given by fd.

    :type fd: int
    :rtype: int
    """
    return 0


def tcsetpgrp(fd, pg):
    """Set the process group associated with the terminal given by fd to pg.

    :type fd: int
    :type pg: int
    :rtype: None
    """
    pass


def ttyname(fd):
    """Return a string which specifies the terminal device associated with file
    descriptor fd.

    :type fd: int
    :rtype: string
    """
    return ''


def write(fd, str):
    """Write the string str to file descriptor fd. Return the number of bytes
    actually written.

    :type fd: int
    :type str: bytes
    :rtype: int
    """
    return 0


def access(path, mode):
    """Use the real uid/gid to test for access to path.

    :type path: bytes | unicode
    :type mode: int
    :rtype: bool
    """
    return False


def chdir(path):
    """Change the current working directory to path.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def fchdir(fd):
    """Change the current working directory to the directory represented by the
    file descriptor fd.

    :type fd: int
    :rtype: None
    """
    pass


def getcwd():
    """Return a string representing the current working directory.

    :rtype: string
    """
    return ''


if sys.version_info < (3, 0):
    def getcwdu():
        """Return a Unicode object representing the current working directory.

        :rtype: unicode
        """
        return ''


def chflags(path, flags):
    """Set the flags of path to the numeric flags.

    :type path: bytes | unicode
    :type flags: int
    :rtype: None
    """
    pass


def chroot(path):
    """Change the root directory of the current process to path.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def chmod(path, mode):
    """Change the mode of path to the numeric mode.

    :type path: bytes | unicode
    :type mode: int
    :rtype: None
    """
    pass


def chown(path, uid, gid):
    """Change the owner and group id of path to the numeric uid and gid.

    :type path: bytes | unicode
    :type uid: int
    :type gid: int
    :rtype: None
    """
    pass


def lchflags(path, flags):
    """Set the flags of path to the numeric flags, like chflags(), but do not
    follow symbolic links.

    :type path: bytes | unicode
    :type flags: int
    :rtype: None
    """
    pass


def lchmod(path, mode):
    """Change the mode of path to the numeric mode. If path is a symlink, this
    affects the symlink rather than the target.

    :type path: bytes | unicode
    :type mode: int
    :rtype: None
    """
    pass


def lchown(path, uid, gid):
    """Change the owner and group id of path to the numeric uid and gid. This
    function will not follow symbolic links.

    :type path: bytes | unicode
    :type uid: int
    :type gid: int
    :rtype: None
    """
    pass


def link(source, link_name):
    """Create a hard link pointing to source named link_name.

    :type source: bytes | unicode
    :type link_name: bytes | unicode
    :rtype: None
    """
    pass


def listdir(path):
    """Return a list containing the names of the entries in the directory given
    by path.

    :type path: T <= bytes | unicode
    :rtype: list[T]
    """
    return []


def lstat(path):
    """Perform the equivalent of an lstat() system call on the given path.
    Similar to stat(), but does not follow symbolic links.

    :type path: bytes | unicode
    :rtype: os.stat_result
    """
    pass


def mkfifo(path, mode=0o666):
    """Create a FIFO (a named pipe) named path with numeric mode mode.

    :type path: bytes | unicode
    :type mode: int
    :rtype: None
    """
    pass


def mknod(filename, mode=0o600, device=0):
    """Create a filesystem node (file, device special file or named pipe) named
    filename.

    :type filename: bytes | unicode
    :type mode: int
    :type device: int
    :rtype: None
    """
    pass


def major(device):
    """Extract the device major number from a raw device number (usually the
    st_dev or st_rdev field from stat).

    :type device: int
    :rtype: int
    """
    return 0


def minor(device):
    """Extract the device minor number from a raw device number (usually the
    st_dev or st_rdev field from stat).

    :type device: int
    :rtype: int
    """
    return 0


def makedev(major, minor):
    """Compose a raw device number from the major and minor device numbers.

    :type major: int
    :type minor: int
    :rtype: int
    """
    return 0


def mkdir(path, mode=0o777):
    """Create a directory named path with numeric mode mode.

    :type path: bytes | unicode
    :type mode: int
    :rtype: None
    """
    pass


def makedirs(path, mode=0o777, exist_ok=False):
    """Recursive directory creation function.

    :type path: bytes | unicode
    :type mode: int
    :type exist_ok: int
    :rtype: None
    """
    pass


def pathconf(path, name):
    """Return system configuration information relevant to a named file.

    :type path: bytes | unicode
    :type name: int | string
    """
    pass


def readlink(path):
    """Return a string representing the path to which the symbolic link points.

    :type path: T <= bytes | unicode
    :rtype: T
    """
    return path


def remove(path):
    """Remove (delete) the file path.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def removedirs(path):
    """Remove directories recursively.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def rename(src, dst):
    """Rename the file or directory src to dst.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: None
    """
    pass


def renames(old, new):
    """Recursive directory or file renaming function.

    :type old: bytes | unicode
    :type new: bytes | unicode
    :rtype: None
    """
    pass


def rmdir(path):
    """Remove (delete) the directory path.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def stat(path, dir_fd=None, follow_symlinks=True):
    """Perform the equivalent of a stat() system call on the given path.

    :type path: bytes | unicode | int
    :type dir_fd: int | None
    :type follow_symlinks: bool | None
    :rtype: os.stat_result
    """
    pass


def stat_float_times(newvalue=None):
    """Determine whether stat_result represents time stamps as float objects.

    :type newvalue: bool | None
    :rtype: bool
    """
    return False


def statvfs(path):
    """Perform a statvfs() system call on the given path.

    :type path: bytes | unicode
    :rtype: os.statvfs_result
    """
    pass


def symlink(source, link_name, target_is_directory=False, dir_fd=None):
    """Create a symbolic link pointing to source named link_name.

    :type source: bytes | unicode
    :type link_name: bytes| unicode
    :type target_is_directory: bool
    :type dir_fd: int | None
    :rtype: None
    """
    pass


def tempnam(dir=None, prefix=None):
    """Return a unique path name that is reasonable for creating a temporary
    file.

    :type dir: bytes | unicode
    :type prefix: bytes | unicode
    :rtype: string
    """
    return ''


def tmpnam():
    """Return a unique path name that is reasonable for creating a temporary
    file.

    :rtype: string
    """
    return ''


def unlink(path):
    """Remove (delete) the file path.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def utime(path, times):
    """Set the access and modified times of the file specified by path.

    :type path: bytes | unicode
    :type times: (numbers.Real, numbers.Real) | None
    :rtype: None
    """
    pass


def walk(top, topdown=True, onerror=None, followlinks=False):
    """Generate the file names in a directory tree by walking the tree either
    top-down or bottom-up.

    :type top: T <= bytes | unicode
    :type topdown: bool
    :type onerror: ((Exception) -> None) | None
    :rtype: collections.Iterator[(T, list[T], list[T])]
    """
    return []


def execl(path, *args):
    """Execute a new program, replacing the current process.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def execle(path, *args):
    """Execute a new program, replacing the current process.

    :type path: bytes | unicode
    :rtype: None
    """
    pass


def execlp(file, *args):
    """Execute a new program, replacing the current process.

    :type file: bytes | unicode
    :rtype: None
    """
    pass


def execlpe(file, *args):
    """Execute a new program, replacing the current process.

    :type file: bytes | unicode
    :rtype: None
    """
    pass


def execv(path, args):
    """Execute a new program, replacing the current process.

    :type path: bytes | unicode
    :type args: collections.Iterable
    :rtype: None
    """
    pass


def execve(path, args, env):
    """Execute a new program, replacing the current process.

    :type path: bytes | unicode
    :type args: collections.Iterable
    :type env: collections.Mapping
    :rtype: None
    """
    pass


def execvp(file, args):
    """Execute a new program, replacing the current process.

    :type file: bytes | unicode
    :type args: collections.Iterable
    :rtype: None
    """
    pass


def execvpe(file, args, env):
    """Execute a new program, replacing the current process.

    :type file: bytes | unicode
    :type args: collections.Iterable
    :type env: collections.Mapping
    :rtype: None
    """
    pass


def _exit(n):
    """Exit the process with status n, without calling cleanup handlers,
    flushing stdio buffers, etc.

    :type n: int
    :rtype: None
    """
    pass


def fork():
    """Fork a child process.

    :rtype: int
    """
    return 0


def forkpty():
    """Fork a child process, using a new pseudo-terminal as the child's
    controlling terminal.

    :rtype: (int, int)
    """
    return 0, 0


def kill(pid, sig):
    """Send signal sig to the process pid.

    :type pid: int
    :type sig: int
    :rtype: None
    """
    pass


def killpg(pgid, sig):
    """Send the signal sig to the process group pgid.

    :type pgid: int
    :type sig: int
    :rtype: None
    """
    pass


def nice(increment):
    """Add increment to the process's "niceness".

    :type increment: int
    :rtype: int
    """
    return 0


def plock(op):
    """Lock program segments into memory.

    :rtype: None
    """
    pass


def spawnl(mode, path, *args):
    """Execute the program path in a new process.

    :type mode: int
    :type path: bytes | unicode
    :rtype: int
    """
    return 0


def spawnle(mode, path, *args):
    """Execute the program path in a new process.

    :type mode: int
    :type path: bytes | unicode
    :rtype: int
    """
    return 0


def spawnlp(mode, file, *args):
    """Execute the program path in a new process.

    :type mode: int
    :type file: bytes | unicode
    :rtype: int
    """
    return 0


def spawnlpe(mode, file, *args):
    """Execute the program path in a new process.

    :type mode: int
    :type file: bytes | unicode
    :rtype: int
    """
    return 0


def spawnv(mode, path, args):
    """Execute the program path in a new process.

    :type mode: int
    :type path: bytes | unicode
    :type args: collections.Iterable
    :rtype: int
    """
    return 0


def spawnve(mode, path, args, env):
    """Execute the program path in a new process.

    :type mode: int
    :type path: bytes | unicode
    :type args: collections.Iterable
    :type env: collections.Mapping
    :rtype: int
    """
    return 0


def spawnvp(mode, file, args):
    """Execute the program path in a new process.

    :type mode: int
    :type file: bytes | unicode
    :type args: collections.Iterable
    :rtype: int
    """
    return 0


def spawnvpe(mode, file, args, env):
    """Execute the program path in a new process.

    :type mode: int
    :type file: bytes | unicode
    :type args: collections.Iterable
    :type env: collections.Mapping
    :rtype: int
    """
    return 0


def system(command):
    """Execute the command (a string) in a subshell.

    :type command: bytes | unicode
    :rtype: int
    """
    return 0


def times():
    """Return a 5-tuple of floating point numbers indicating accumulated
    (processor or other) times, in seconds.

    :rtype: (float, float, float, float, float)
    """
    return 0.0, 0.0, 0.0, 0.0, 0.0


def wait():
    """Wait for completion of a child process, and return a tuple containing
    its pid and exit status indication

    :rtype: (int, int)
    """
    return 0, 0


def waitpid(pid, options):
    """Wait for completion of a child process given by process id pid, and
    return a tuple containing its process id and exit status indication.

    :type pid: int
    :type options: int
    :rtype: (int, int)
    """
    return 0, 0


def wait3(options):
    """Similar to waitpid(), except no process id argument is given and a
    3-element tuple containing the child's process id, exit status indication,
    and resource usage information is returned.

    :type options: int
    :rtype: (int, int, resource.struct_rusage)
    """
    pass


def wait4(pid, options):
    """Similar to waitpid(), except a 3-element tuple, containing the child's
    process id, exit status indication, and resource usage information is
    returned.

    :type pid: int
    :type options: int
    :rtype: (int, int, resource.struct_rusage)
    """
    pass


def urandom(n):
    """Return a string of n random bytes suitable for cryptographic use.

    :type n: int
    :rtype: bytes
    """
    return b''