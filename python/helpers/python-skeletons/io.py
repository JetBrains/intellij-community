"""Skeleton for 'io' stdlib module."""


from __future__ import unicode_literals
import sys
import io


def open(file, mode='r', buffering=-1, encoding=None, errors=None, newline=None,
         closefd=True, opener=None):
    """This is an alias for the builtin open() function.

    :type file: string
    :type mode: string
    :type buffering: numbers.Integral
    :type encoding: string | None
    :type errors: string | None
    :type newline: string | None
    :type closefd: bool
    :type opener: ((string, int) -> int) | None
    :rtype: io.FileIO[bytes] | io.TextIOWrapper[unicode]
    """
    pass


class IOBase(object):
    """The abstract base class for all I/O classes, acting on streams of
    bytes.

    :type closed: bool
    """

    def __init__(self, *args, **kwargs):
        """Private constructor of IOBase.

        :rtype: io.IOBase[T <= bytes | unicode]
        """
        self.closed = False

    def __iter__(self):
        """Iterate over lines.

        :rtype: collections.Iterator[T]
        """
        return []

    def close(self):
        """Flush and close this stream.

        :rtype: None
        """
        pass

    def fileno(self):
        """Return the underlying file descriptor (an integer) of the stream if
        it exists.

        :rtype: int
        """
        return 0

    def flush(self):
        """Flush the write buffers of the stream if applicable.

        :rtype: None
        """
        pass

    def isatty(self):
        """Return True if the stream is interactive (i.e., connected to a
        terminal/tty device).

        :rtype: bool
        """
        return False

    def readable(self):
        """Return True if the stream can be read from.

        :rtype: bool
        """
        return False

    def readline(self, limit=-1):
        """Read and return one line from the stream.

        :type limit: numbers.Integral
        :rtype: T
        """
        pass

    def readlines(self, hint=-1):
        """Read and return a list of lines from the stream.

        :type hint: numbers.Integral
        :rtype: list[T]
        """
        return []

    def seek(self, offset, whence=io.SEEK_SET):
        """Change the stream position to the given byte offset.

        :type offset: numbers.Integral
        :type whence: numbers.Integral
        :rtype: None
        """
        pass

    def seekable(self):
        """Return True if the stream supports random access.

        :rtype: bool
        """
        return False

    def tell(self):
        """Return the current stream position.

        :rtype: int
        """
        return 0

    def truncate(self, size=None):
        """Resize the stream to the given size in bytes (or the current
        position if size is not specified).

        :type size: numbers.Integral | None
        :rtype: None
        """
        pass

    def writable(self):
        """Return True if the stream supports writing.

        :rtype: bool
        """
        return False

    def writelines(self, lines):
        """Write a list of lines to the stream.

        :type lines: collections.Iterable[T]
        :rtype: None
        """
        pass


class RawIOBase(io.IOBase):
    """Base class for raw binary I/O."""

    def __init__(self, *args, **kwargs):
        """Private constructor of RawIOBase.

        :rtype: io.RawIOBase[bytes]
        """
        pass

    def read(self, n=1):
        """Read up to n bytes from the object and return them.

        :type n: numbers.Integral
        :rtype: bytes
        """
        return b''

    def readall(self):
        """Read and return all the bytes from the stream until EOF, using
        multiple calls to the stream if necessary.

        :rtype: bytes
        """
        return b''

    def readinto(self, b):
        """Read up to len(b) bytes into bytearray b and return the number of
        bytes read.

        :type b: bytearray
        :rtype: int
        """
        return 0

    def write(self, b):
        """Write the given bytes or bytearray object, b, to the underlying raw
        stream and return the number of bytes written.

        :type b: bytes | bytearray
        :rtype: int
        """
        return 0


class BufferedIOBase(io.IOBase):
    """Base class for binary streams that support some kind of buffering."""

    def __init__(self, *args, **kwargs):
        """Private constructor of BufferedIOBase.

        :rtype: io.BufferedIOBase[bytes]
        """
        pass

    if sys.version_info >= (2, 7):
        def detach(self):
            """Separate the underlying raw stream from the buffer and return
            it.

            :rtype: None
            """
            pass

    def read1(self, n=-1):
        """Read and return up to n bytes, with at most one call to the
        underlying raw stream's read() method.

        :type n: numbers.Integral
        :rtype: bytes
        """
        return b''


class FileIO(io.RawIOBase):
    """FileIO represents an OS-level file containing bytes data.

    :type name: string
    :type mode: string
    :type closefd: bool
    :type closed: bool
    """

    def __init__(self, name, mode='r', closefd=True):
        """Create a FileIO object.

        :type name: string
        :type mode: string
        :type closefd: bool
        :rtype: io.FileIO[bytes]
        """
        self.name = name
        self.mode = mode
        self.closefd = closefd
        self.closed = False
        pass

    def __iter__(self):
        """Iterate over lines.

        :rtype: collections.Iterator[bytes]
        """
        return []

    def close(self):
        """Flush and close this stream.

        :rtype: None
        """
        pass

    def fileno(self):
        """Return the underlying file descriptor (an integer) of the stream if
        it exists.

        :rtype: int
        """
        return 0

    def flush(self):
        """Flush the write buffers of the stream if applicable.

        :rtype: None
        """
        pass

    def isatty(self):
        """Return True if the stream is interactive (i.e., connected to a
        terminal/tty device).

        :rtype: bool
        """
        return False

    def readable(self):
        """Return True if the stream can be read from.

        :rtype: bool
        """
        return False

    def readline(self, limit=-1):
        """Read and return one line from the stream.

        :type limit: numbers.Integral
        :rtype: bytes
        """
        return b''

    def readlines(self, hint=-1):
        """Read and return a list of lines from the stream.

        :type hint: numbers.Integral
        :rtype: list[bytes]
        """
        return []

    def seek(self, offset, whence=io.SEEK_SET):
        """Change the stream position to the given byte offset.

        :type offset: numbers.Integral
        :type whence: numbers.Integral
        :rtype: None
        """
        pass

    def seekable(self):
        """Return True if the stream supports random access.

        :rtype: bool
        """
        return False

    def tell(self):
        """Return the current stream position.

        :rtype: int
        """
        return 0

    def truncate(self, size=None):
        """Resize the stream to the given size in bytes (or the current
        position if size is not specified).

        :type size: numbers.Integral | None
        :rtype: None
        """
        pass

    def writable(self):
        """Return True if the stream supports writing.

        :rtype: bool
        """
        return False

    def writelines(self, lines):
        """Write a list of lines to the stream.

        :type lines: collections.Iterable[bytes]
        :rtype: None
        """
        pass

    def read(self, n=1):
        """Read up to n bytes from the object and return them.

        :type n: numbers.Integral
        :rtype: bytes
        """
        return b''

    def readall(self):
        """Read and return all the bytes from the stream until EOF, using
        multiple calls to the stream if necessary.

        :rtype: bytes
        """
        return b''

    def readinto(self, b):
        """Read up to len(b) bytes into bytearray b and return the number of
        bytes read.

        :type b: bytearray
        :rtype: int
        """
        return 0

    def write(self, b):
        """Write the given bytes or bytearray object, b, to the underlying raw
        stream and return the number of bytes written.

        :type b: bytes | bytearray
        :rtype: int
        """
        return 0


class BytesIO(io.BufferedIOBase):
    """A stream implementation using an in-memory bytes buffer."""

    def __init__(self, initial_bytes=None):
        """Create a BytesIO object.

        :rtype: io.BytesIO[bytes]
        """
        pass

    if sys.version_info >= (3, 2):
        def getbuffer(self):
            """Return a readable and writable view over the contents of the
            buffer without copying them.

            :rtype: bytearray
            """
            return bytearray()

    def getvalue(self):
        """Return bytes containing the entire contents of the buffer.

        :rtype: bytes
        """
        return b''


class TextIOBase(io.IOBase):
    """Base class for text streams.

    :type encoding: string
    :type errors: string
    :type newlines: string | tuple | None
    :type buffer: BufferedIOBase
    """

    def __init__(self, *args, **kwargs):
        """Private constructor of TextIOBase.

        :rtype: TextIOBase[unicode]
        """
        self.encoding = str()
        self.errors = str()
        self.newlines = None
        self.buffer = BufferedIOBase()

    if sys.version_info >= (2, 7):
        def detach(self):
            """Separate the underlying raw stream from the buffer and return
            it.

            :rtype: None
            """
            pass

    def read(self, n=None):
        """Read and return at most n characters from the stream as a single
        unicode.

        :type n: numbers.Integral | None
        :rtype: unicode
        """
        return ''

    def write(self, s):
        """Write the unicode string s to the stream and return the number of
        characters written.

        :type b: unicode
        :rtype: int
        """
        return 0


class TextIOWrapper(io.TextIOBase):
    """A buffered text stream over a BufferedIOBase binary stream.

    :type buffer: io.BufferedIOBase
    :type encoding: string
    :type errors: string
    :type newlines: string
    :type line_buffering: bool
    :type name: string
    """

    def __init__(self, buffer, encoding=None, errors=None, newline=None,
                 line_buffering=False):
        """Creat a TextIOWrapper object.

        :type buffer: io.BufferedIOBase
        :type encoding: string | None
        :type errors: string | None
        :type newline: string | None
        :type line_buffering: bool
        :rtype: io.TextIOWrapper[unicode]
        """
        self.name = ''
        self.buffer = buffer
        self.encoding = encoding
        self.errors = errors
        self.newlines = newline
        self.line_buffering = line_buffering

    def __iter__(self):
        """Iterate over lines.

        :rtype: collections.Iterator[unicode]
        """
        return []

    def close(self):
        """Flush and close this stream.

        :rtype: None
        """
        pass

    def fileno(self):
        """Return the underlying file descriptor (an integer) of the stream if
        it exists.

        :rtype: int
        """
        return 0

    def flush(self):
        """Flush the write buffers of the stream if applicable.

        :rtype: None
        """
        pass

    def isatty(self):
        """Return True if the stream is interactive (i.e., connected to a
        terminal/tty device).

        :rtype: bool
        """
        return False

    def readable(self):
        """Return True if the stream can be read from.

        :rtype: bool
        """
        return False

    def readline(self, limit=-1):
        """Read and return one line from the stream.

        :type limit: numbers.Integral
        :rtype: unicode
        """
        pass

    def readlines(self, hint=-1):
        """Read and return a list of lines from the stream.

        :type hint: numbers.Integral
        :rtype: list[unicode]
        """
        return []

    def seek(self, offset, whence=io.SEEK_SET):
        """Change the stream position to the given byte offset.

        :type offset: numbers.Integral
        :type whence: numbers.Integral
        :rtype: None
        """
        pass

    def seekable(self):
        """Return True if the stream supports random access.

        :rtype: bool
        """
        return False

    def tell(self):
        """Return the current stream position.

        :rtype: int
        """
        return 0

    def truncate(self, size=None):
        """Resize the stream to the given size in bytes (or the current
        position if size is not specified).

        :type size: numbers.Integral | None
        :rtype: None
        """
        pass

    def writable(self):
        """Return True if the stream supports writing.

        :rtype: bool
        """
        return False

    def writelines(self, lines):
        """Write a list of lines to the stream.

        :type lines: collections.Iterable[unicode]
        :rtype: None
        """
        pass

    if sys.version_info >= (2, 7):
        def detach(self):
            """Separate the underlying raw stream from the buffer and return
            it.

            :rtype: None
            """
            pass

    def read(self, n=None):
        """Read and return at most n characters from the stream as a single
        unicode.

        :type n: numbers.Integral | None
        :rtype: unicode
        """
        return ''

    def write(self, s):
        """Write the unicode string s to the stream and return the number of
        characters written.

        :type b: unicode
        :rtype: int
        """
        return 0