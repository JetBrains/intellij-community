import abc
import posixpath as path


def _fscodec():
    pass


fsencode, fsdecode = _fscodec()


def _fspath(path):
    pass


fspath = _fspath


class PathLike(abc.ABC):

    """Abstract base class for implementing the file system path protocol."""

    @abc.abstractmethod
    def __fspath__(self):
        """Return the file system path representation of the object."""
        raise NotImplementedError

    @classmethod
    def __subclasshook__(cls, subclass):
        return hasattr(subclass, '__fspath__')