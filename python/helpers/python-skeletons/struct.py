"""Skeleton for 'struct' stdlib module."""


from __future__ import unicode_literals
import sys


def pack(fmt, *values):
    """Return a string containing the values packed according to the given
    format.

    :type fmt: bytes | unicode
    :rtype: bytes
    """
    return b''


def unpack(fmt, string):
    """Unpack the string according to the given format.

    :type fmt: bytes | unicode
    :type string: bytestring
    :rtype: tuple
    """
    pass


def pack_into(fmt, buffer, offset, *values):
    """"Pack the values according to the given format, write the packed
    bytes into the writable buffer starting at offset.

    :type fmt: bytes | unicode
    :type offset: int | long
    :rtype: bytes
    """
    return b''

def unpack_from(fmt, buffer, offset=0):
    """Unpack the buffer according to the given format.

    :type fmt: bytes | unicode
    :type offset: int | long
    :rtype: tuple
    """
    pass


def calcsize(fmt):
    """Return the size of the struct (and hence of the string) corresponding to
    the given format.

    :type fmt: bytes | unicode
    :rtype: int
    """
    return 0


class Struct(object):
    """Struct object which writes and reads binary data according to the format
    string.

    :param format: The format string used to construct this Struct object.
    :type format: bytes | unicode

    :param size: The calculated size of the struct corresponding to format.
    :type size: int
    """

    def __init__(self, format):
        """Create a new Struct object.

        :type format: bytes | unicode
        """
        self.format = format
        self.size = 0

    def pack(self, *values):
        """Identical to the pack() function, using the compiled format.

        :rtype: bytes
        """
        return b''

    def pack_into(self, buffer, offset, *values):
        """Identical to the pack_into() function, using the compiled format.

        :type offset: int | long
        :rtype: bytes
        """
        return b''

    def unpack(self, string):
        """Identical to the unpack() function, using the compiled format.

        :type string: bytestring
        :rtype: tuple
        """
        pass

    def unpack_from(self, buffer, offset=0):
        """Identical to the unpack_from() function, using the compiled format.

        :type offset: int | long
        :rtype: tuple
        """
        pass
