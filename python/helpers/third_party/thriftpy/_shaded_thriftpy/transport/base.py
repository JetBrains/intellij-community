# -*- coding: utf-8 -*-

from __future__ import absolute_import

from ..thrift import TType, TException


def readall(read_fn, sz):
    buff = b''
    have = 0
    while have < sz:
        chunk = read_fn(sz - have)
        have += len(chunk)
        buff += chunk

        if len(chunk) == 0:
            raise TTransportException(TTransportException.END_OF_FILE,
                                      "End of file reading from transport")

    return buff


class TTransportBase(object):
    """Base class for Thrift transport layer."""

    def is_open(self):
        """Check if this transport is open."""
        raise NotImplementedError

    def open(self):
        """
        Prepare this transport for usage and allocate any necessary resources
        like sockets or sessions.
        """
        raise NotImplementedError

    def close(self):
        """Clean up and deallocate any resources allocated in open()."""
        raise NotImplementedError

    def _read(self, sz):
        """
        Internal read method which can read up to `sz` bytes but doesn't
        need to return them all.
        """
        raise NotImplementedError

    def read(self, sz):
        """
        Get exactly `sz` bytes from the underlying connection.

        When implementing a custom transport, this method must return exactly
        `sz` bytes if it is expected to be called from the protocol layer. If
        it intends to wrapped by another transport, like TBufferedTransport,
        it should return whatever the underlying connection/transport can get.
        The wrapping transport will take care of ensuring `sz` bytes are
        returned. For a more in depth discussion, see:
        https://github.com/Thriftpy/_shaded_thriftpy/pull/108#discussion_r355131677
        """
        return readall(self._read, sz)

    def write(self, buf):
        """
        Submit some data to tbe written to the connection. May be
        buffered until flush is called.
        """
        raise NotImplementedError

    def flush(self):
        """Ensure that all internal buffers are emptied into the connection."""
        raise NotImplementedError


class TTransportException(TException):
    """Custom Transport Exception class"""

    thrift_spec = {
        1: (TType.STRING, 'message'),
        2: (TType.I32, 'type'),
    }

    UNKNOWN = 0
    NOT_OPEN = 1
    ALREADY_OPEN = 2
    TIMED_OUT = 3
    END_OF_FILE = 4

    def __init__(self, type=UNKNOWN, message=None):
        super(TTransportException, self).__init__()
        self.type = type
        self.message = message
