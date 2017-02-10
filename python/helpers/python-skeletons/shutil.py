"""Skeleton for 'shutil' stdlib module."""


import sys


def copyfile(src, dst):
    """Copy the contents (no metadata) of the file named src to a file named
    dst.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: bytes | unicode
    """
    pass


def copymode(src, dst):
    """Copy the permission bits from src to dst.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: None
    """
    pass


def copystat(src, dst):
    """Copy the permission bits, last access time, last modification time, and
    flags from src to dst.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: None
    """
    pass


def copy(src, dst):
    """Copy the file src to the file or directory dst.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: bytes | unicode
    """
    pass


def copy2(src, dst):
    """Similar to shutil.copy(), but metadata is copied as well.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: bytes | unicode
    """
    pass


def ignore_patterns(*patterns):
    """This factory function creates a function that can be used as a callable
    for copytree()'s ignore argument, ignoring files and directories that match
    one of the glob-style patterns provided.

    :type patterns: collections.Iterable[bytes | unicode]
    :rtype: (bytes | unicode, list[bytes | unicode]) -> collections.Iterable[bytes | unicode]
    """
    return lambda path, files: []


def copytree(src, dst, symlinks=False, ignore=None):
    """Recursively copy an entire directory tree rooted at src.

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :type symlinks: bool
    :type ignore: ((bytes | unicode, list[bytes | unicode]) -> collections.Iterable[bytes | unicode]) | None
    :rtype: bytes | unicode
    """
    pass


def rmtree(path, ignore_errors=False, onerror=None):
    """Delete an entire directory tree.

    :type path: bytes | unicode
    :type ignore_errors: bool
    :type onerror: (unknown, bytes | unicode, unknown) -> None
    :rtype: None
    """


def move(src, dst):
    """Recursively move a file or directory (src) to another location (dst).

    :type src: bytes | unicode
    :type dst: bytes | unicode
    :rtype: bytes | unicode
    """
    pass
