"""Skeleton for 'pathlib' stdlib module."""

import pathlib
import os


class PurePath(object):
    def __new__(cls, *pathsegments):
        """
        :type pathsegments: str | bytes | os.PathLike
        :rtype: pathlib.PurePath
        """
        return cls.__new__(*pathsegments)

    def __truediv__(self, key):
        """
        :type key: string | pathlib.PurePath
        :rtype: pathlib.PurePath
        """
        return pathlib.PurePath()

    def __rtruediv__(self, key):
        """
        :type key: string | pathlib.PurePath
        :rtype: pathlib.PurePath
        """
        return pathlib.PurePath()

    @property
    def parts(self):
        """
        :rtype: tuple[str]
        """
        return ()

    @property
    def drive(self):
        """
        :rtype: str
        """
        return ''

    @property
    def root(self):
        """
        :rtype: str
        """
        return ''

    @property
    def anchor(self):
        """
        :rtype: str
        """
        return ''

    @property
    def parent(self):
        """
        :rtype: pathlib.PurePath | unknown
        """
        return pathlib.PurePath()

    @property
    def name(self):
        """
        :rtype: str
        """
        return ''

    @property
    def suffix(self):
        """
        :rtype: str
        """
        return ''

    @property
    def suffixes(self):
        """
        :rtype: list[str]
        """
        return []

    @property
    def stem(self):
        """
        :rtype: str
        """
        return ''

    def as_posix(self):
        """
        :rtype: str
        """
        return ''

    def as_uri(self):
        """
        :rtype: str
        """
        return ''

    def is_absolute(self):
        """
        :rtype: bool
        """
        return False

    def is_reserved(self):
        """
        :rtype: bool
        """
        return False

    def joinpath(self, *other):
        """
        :rtype: pathlib.PurePath
        """
        return pathlib.PurePath()

    def match(self, pattern):
        """
        :type pattern: string
        :rtype: bool
        """
        return False

    def relative_to(self, *other):
        """
        :rtype: pathlib.PurePath
        """
        return pathlib.PurePath()

    def __fspath__(self):
        """
        :rtype: str
        """
        pass

class PurePosixPath(pathlib.PurePath):
    pass


class PureWindowsPath(pathlib.PurePath):
    pass


class Path(pathlib.PurePath):
    def __new__(cls, *pathsegments):
        """
        :type pathsegments: str | bytes | os.PathLike
        :rtype: pathlib.Path
        """
        return cls.__new__(*pathsegments)

    def __truediv__(self, key):
        """
        :type key: string | pathlib.Path
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    def __rtruediv__(self, key):
        """
        :type key: string | pathlib.Path
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    @property
    def parents(self):
        """
        :rtype: collections.Sequence[pathlib.Path]
        """
        return []

    @property
    def parent(self):
        """
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    def joinpath(self, *other):
        """
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    def relative_to(self, *other):
        """
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    @classmethod
    def cwd(cls):
        """
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    def stat(self):
        """
        :rtype: os.stat_result
        """
        pass

    def chmod(self, mode):
        """
        :rtype mode: int
        :rtype: None
        """
        pass

    def exists(self):
        """
        :rtype: bool
        """
        return False

    def glob(self, pattern):
        """
        :type pattern: string
        :rtype: collections.Iterable[pathlib.Path]
        """
        return []

    def group(self):
        """
        :rtype: str
        """
        return ''

    def is_dir(self):
        """
        :rtype: bool
        """
        return False

    def is_file(self):
        """
        :rtype: bool
        """
        return False

    def is_file(self):
        """
        :rtype: bool
        """
        return False

    def is_symlink(self):
        """
        :rtype: bool
        """
        return False

    def is_socket(self):
        """
        :rtype: bool
        """
        return False

    def is_fifo(self):
        """
        :rtype: bool
        """
        return False

    def is_block_device(self):
        """
        :rtype: bool
        """
        return False

    def is_char_device(self):
        """
        :rtype: bool
        """
        return False

    def iterdir(self):
        """
        :rtype: collections.Iterable[pathlib.Path]
        """
        return []

    def lchmod(self, mode):
        """
        :rtype mode: int
        :rtype: None
        """
        pass

    def lstat(self):
        """
        :rtype: os.stat_result
        """
        pass

    def mkdir(self, mode=0o777, parents=False, exist_ok=False):
        """
        :type mode: int
        :type parents: bool
        :type exist_ok: bool
        :rtype: None
        """
        pass

    def open(self, mode='r', buffering=-1, encoding=None, errors=None,
             newline=None):
        """
        :type mode: string
        :type buffering: numbers.Integral
        :type encoding: string | None
        :type errors: string | None
        :type newline: string | None
        :rtype: io.FileIO[bytes] | io.TextIOWrapper[unicode]
        """
        pass

    def owner(self):
        """
        :rtype: str
        """
        return ''

    def rename(self, target):
        """
        :type target: string | pathlib.Path
        :rtype: None
        """
        pass

    def replace(self, target):
        """
        :type target: string | pathlib.Path
        :rtype: None
        """
        pass

    def resolve(self):
        """
        :rtype: pathlib.Path
        """
        return pathlib.Path()

    def rglob(self, pattern):
        """
        :type pattern: string
        :rtype: collections.Iterable[pathlib.Path]
        """
        return []

    def rmdir(self):
        """
        :rtype: None
        """
        pass

    def symlink_to(self, target, target_is_directory=False):
        """
        :type target: string | pathlib.Path
        :type target_is_directory: bool
        :rtype: None
        """
        pass

    def touch(self, mode=0o777, exist_ok=True):
        """
        :type mode: int
        :type exist_ok: bool
        :rtype: None
        """
        pass

    def unlink(self):
        """
        :rtype: None
        """
        pass
