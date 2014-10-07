"""Skeleton for 'pickle' stdlib module."""


HIGHEST_PROTOCOL = 0
DEFAULT_PROTOCOL = 0


def dump(obj, file, protocol=None, fix_imports=True):
    """Write a pickled representation of obj to the open file object file.

    :type protocol: numbers.Integral | None
    :rtype: None
    """
    pass


def dumps(obj, protocol=None, fix_imports=True):
    """Return the pickled representation of the object as a bytes object,
    instead of writing it to a file.

    :type protocol: numbers.Integral | None
    :rtype: bytes
    """
    return b''


def load(file, fix_imports=True, encoding='ASCII', errors='strict'):
    """Read a pickled object representation from the open file object file and
    return the reconstituted object hierarchy specified therein.
    """
    pass


def loads(bytes_object, fix_imports=True, encoding='ASCII', errors='strict'):
    """Read a pickled object representation from the open file object file and
    return the reconstituted object hierarchy specified therein.
    """
    pass


class PickleError(Exception):
    pass


class PicklingError(PickleError):
    pass


class UnpicklingError(PickleError):
    pass


class Pickler(object):
    """This takes a binary file for writing a pickle data stream."""

    def __init__(self, file, protocol=None, fix_imports=True):
        self.dispatch_table = None
        self.fast = False

    def dump(self, obj):
        pass

    def persistent_id(self, obj):
        pass


class Unpickler(object):
    """This takes a binary file for reading a pickle data stream."""

    def __init__(self, file, fix_imports=True, encoding='ASCII',
                 errors='strict'):
        pass

    def load(self):
        pass

    def persistent_load(self, pid):
        pass

    def find_class(self, module, name):
        pass
