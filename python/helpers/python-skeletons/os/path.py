"""Skeleton for 'os.path' stdlib module."""


import sys
import os


def abspath(path):
    """Return a normalized absolutized version of the pathname path.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def basename(path):
    """Return the base name of pathname path.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def commonprefix(list):
    """Return the longest path prefix (taken character-by-character) that is a
    prefix of all paths in list.

    :type list: collections.Iterable[bytes | unicode | os.PathLike]
    :rtype: bytes | unicode
    """
    pass


def dirname(path):
    """Return the directory name of pathname path.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def exists(path):
    """Return True if path refers to an existing path. Returns False for broken
    symbolic links.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def lexists(path):
    """Return True if path refers to an existing path. Returns True for broken
    symbolic links.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def expanduser(path):
    """On Unix and Windows, return the argument with an initial component of ~
    or ~user replaced by that user's home directory.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def expandvars(path):
    """Return the argument with environment variables expanded.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def getatime(path):
    """Return the time of last access of path.

    :type path: bytes | unicode | os.PathLike
    :rtype: float
    """
    return 0.0


def getmtime(path):
    """Return the time of last modification of path.

    :type path: bytes | unicode | os.PathLike
    :rtype: float
    """
    return 0.0


def getctime(path):
    """Return the system's ctime.

    :type path: bytes | unicode | os.PathLike
    :rtype: float
    """
    return 0.0


def getsize(path):
    """Return the size, in bytes, of path.

    :type path: bytes | unicode | os.PathLike
    :rtype: int
    """
    return 0


def isabs(path):
    """Return True if path is an absolute pathname.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def isfile(path):
    """Return True if path is an existing regular file.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def isdir(path):
    """Return True if path is an existing directory.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def islink(path):
    """Return True if path refers to a directory entry that is a symbolic link.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def ismount(path):
    """Return True if pathname path is a mount point: a point in a file system
    where a different file system has been mounted.

    :type path: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def join(path, *paths):
    """Join one or more path components intelligently.

    :type path: bytes | unicode | os.PathLike
    :type paths: collections.Iterable[bytes | unicode | os.PathLike]
    :rtype: bytes | unicode
    """
    return path


def normcase(path):
    """Normalize the case of a pathname.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def normpath(path):
    """Normalize a pathname by collapsing redundant separators and up-level
    references.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def realpath(path):
    """Return the canonical path of the specified filename, eliminating any
    symbolic links encountered in the path.

    :type path: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def relpath(path, start=os.curdir):
    """Return a relative filepath to path either from the current directory or
    from an optional start directory.

    :type path: bytes | unicode | os.PathLike
    :type start: bytes | unicode | os.PathLike
    :rtype: bytes | unicode
    """
    return path


def samefile(path1, path2):
    """Return True if both pathname arguments refer to the same file or
    directory.

    :type path1: bytes | unicode | os.PathLike
    :type path2: bytes | unicode | os.PathLike
    :rtype: bool
    """
    return False


def sameopenfile(fp1, fp2):
    """Return True if the file descriptors fp1 and fp2 refer to the same file.

    :type fp1: int
    :type fp2: int
    :rtype: bool
    """
    return False


def samestat(stat1, stat2):
    """Return True if the stat tuples stat1 and stat2 refer to the same file.

    :type stat1: os.stat_result | tuple
    :type stat2: os.stat_result | tuple
    :rtype: bool
    """
    return False


def split(path):
    """Split the pathname path into a pair, (head, tail).

    :type path: bytes | unicode | os.PathLike
    :rtype: (bytes | unicode, bytes | unicode)
    """
    return path, path


def splitdrive(path):
    """Split the pathname path into a pair (drive, tail).

    :type path: bytes | unicode | os.PathLike
    :rtype: (bytes | unicode, bytes | unicode)
    """
    return path, path


def splitext(path):
    """Split the pathname path into a pair (root, ext).

    :type path: bytes | unicode | os.PathLike
    :rtype: (bytes | unicode, bytes | unicode)
    """
    return path, path


def splitunc(path):
    """Split the pathname path into a pair (unc, rest).

    :type path: bytes | unicode | os.PathLike
    :rtype: (bytes | unicode, bytes | unicode)
    """
    return path, path


if sys.version_info < (3, 0):
    def walk(path, visit, arg):
        """Calls the function visit with arguments (arg, dirname, names) for
        each directory in the directory tree rooted at path.

        :type path: T <= bytes | unicode
        :type visit: (V, T, list[T]) -> None
        :type arg: V
        :rtype: None
        """
        pass
