# encoding: utf-8
# module _io
# from /Users/vlan/.virtualenvs/obraz-py2.7/lib/python2.7/lib-dynload/_io.so
# by generator 1.137
"""
The io module provides the Python interfaces to stream handling. The
builtin open function is defined in this module.

At the top of the I/O hierarchy is the abstract base class IOBase. It
defines the basic interface to a stream. Note, however, that there is no
separation between reading and writing to streams; implementations are
allowed to raise an IOError if they do not support a given operation.

Extending IOBase is RawIOBase which deals simply with the reading and
writing of raw bytes to a stream. FileIO subclasses RawIOBase to provide
an interface to OS files.

BufferedIOBase deals with buffering on a raw byte stream (RawIOBase). Its
subclasses, BufferedWriter, BufferedReader, and BufferedRWPair buffer
streams that are readable, writable, and both respectively.
BufferedRandom provides a buffered interface to random access
streams. BytesIO is a simple stream of in-memory bytes.

Another IOBase subclass, TextIOBase, deals with the encoding and decoding
of streams into text. TextIOWrapper, which extends it, is a buffered text
interface to a buffered raw stream (`BufferedIOBase`). Finally, StringIO
is a in-memory stream for text.

Argument names are not part of the specification, and only the arguments
of open() are intended to be used as keyword arguments.

data:

DEFAULT_BUFFER_SIZE

   An int containing the default buffer size used by the module's buffered
   I/O classes. open() uses the file's blksize (as obtained by os.stat) if
   possible.
"""
# no imports

# Variables with simple values

DEFAULT_BUFFER_SIZE = 8192

# functions

def open(name, mode=None, buffering=None): # known case of _io.open
    """
    Open file and return a stream.  Raise IOError upon failure.
    
    file is either a text or byte string giving the name (and the path
    if the file isn't in the current working directory) of the file to
    be opened or an integer file descriptor of the file to be
    wrapped. (If a file descriptor is given, it is closed when the
    returned I/O object is closed, unless closefd is set to False.)
    
    mode is an optional string that specifies the mode in which the file
    is opened. It defaults to 'r' which means open for reading in text
    mode.  Other common values are 'w' for writing (truncating the file if
    it already exists), and 'a' for appending (which on some Unix systems,
    means that all writes append to the end of the file regardless of the
    current seek position). In text mode, if encoding is not specified the
    encoding used is platform dependent. (For reading and writing raw
    bytes use binary mode and leave encoding unspecified.) The available
    modes are:
    
    ========= ===============================================================
    Character Meaning
    --------- ---------------------------------------------------------------
    'r'       open for reading (default)
    'w'       open for writing, truncating the file first
    'a'       open for writing, appending to the end of the file if it exists
    'b'       binary mode
    't'       text mode (default)
    '+'       open a disk file for updating (reading and writing)
    'U'       universal newline mode (for backwards compatibility; unneeded
              for new code)
    ========= ===============================================================
    
    The default mode is 'rt' (open for reading text). For binary random
    access, the mode 'w+b' opens and truncates the file to 0 bytes, while
    'r+b' opens the file without truncation.
    
    Python distinguishes between files opened in binary and text modes,
    even when the underlying operating system doesn't. Files opened in
    binary mode (appending 'b' to the mode argument) return contents as
    bytes objects without any decoding. In text mode (the default, or when
    't' is appended to the mode argument), the contents of the file are
    returned as strings, the bytes having been first decoded using a
    platform-dependent encoding or using the specified encoding if given.
    
    buffering is an optional integer used to set the buffering policy.
    Pass 0 to switch buffering off (only allowed in binary mode), 1 to select
    line buffering (only usable in text mode), and an integer > 1 to indicate
    the size of a fixed-size chunk buffer.  When no buffering argument is
    given, the default buffering policy works as follows:
    
    * Binary files are buffered in fixed-size chunks; the size of the buffer
      is chosen using a heuristic trying to determine the underlying device's
      "block size" and falling back on `io.DEFAULT_BUFFER_SIZE`.
      On many systems, the buffer will typically be 4096 or 8192 bytes long.
    
    * "Interactive" text files (files for which isatty() returns True)
      use line buffering.  Other text files use the policy described above
      for binary files.
    
    encoding is the name of the encoding used to decode or encode the
    file. This should only be used in text mode. The default encoding is
    platform dependent, but any encoding supported by Python can be
    passed.  See the codecs module for the list of supported encodings.
    
    errors is an optional string that specifies how encoding errors are to
    be handled---this argument should not be used in binary mode. Pass
    'strict' to raise a ValueError exception if there is an encoding error
    (the default of None has the same effect), or pass 'ignore' to ignore
    errors. (Note that ignoring encoding errors can lead to data loss.)
    See the documentation for codecs.register for a list of the permitted
    encoding error strings.
    
    newline controls how universal newlines works (it only applies to text
    mode). It can be None, '', '\n', '\r', and '\r\n'.  It works as
    follows:
    
    * On input, if newline is None, universal newlines mode is
      enabled. Lines in the input can end in '\n', '\r', or '\r\n', and
      these are translated into '\n' before being returned to the
      caller. If it is '', universal newline mode is enabled, but line
      endings are returned to the caller untranslated. If it has any of
      the other legal values, input lines are only terminated by the given
      string, and the line ending is returned to the caller untranslated.
    
    * On output, if newline is None, any '\n' characters written are
      translated to the system default line separator, os.linesep. If
      newline is '', no translation takes place. If newline is any of the
      other legal values, any '\n' characters written are translated to
      the given string.
    
    If closefd is False, the underlying file descriptor will be kept open
    when the file is closed. This does not work when a file name is given
    and must be True in that case.
    
    open() returns a file object whose type depends on the mode, and
    through which the standard file operations such as reading and writing
    are performed. When open() is used to open a file in a text mode ('w',
    'r', 'wt', 'rt', etc.), it returns a TextIOWrapper. When used to open
    a file in a binary mode, the returned class varies: in read binary
    mode, it returns a BufferedReader; in write binary and append binary
    modes, it returns a BufferedWriter, and in read/write mode, it returns
    a BufferedRandom.
    
    It is also possible to use a string or bytearray as a file for both
    reading and writing. For strings StringIO can be used like a file
    opened in a text mode, and for bytes a BytesIO can be used like a file
    opened in a binary mode.
    """
    return file('/dev/null')

# classes

class BlockingIOError(IOError):
    """ Exception raised when I/O would block on a non-blocking I/O stream """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    characters_written = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class _IOBase(object):
    """
    The abstract base class for all I/O classes, acting on streams of
    bytes. There is no public constructor.
    
    This class provides dummy implementations for many methods that
    derived classes can override selectively; the default implementations
    represent a file that cannot be read, written or seeked.
    
    Even though IOBase does not declare read, readinto, or write because
    their signatures will vary, implementations and clients should
    consider those methods part of the interface. Also, implementations
    may raise a IOError when operations they do not support are called.
    
    The basic type used for binary data read from or written to a file is
    bytes. bytearrays are accepted too, and in some cases (such as
    readinto) needed. Text I/O classes work with str data.
    
    Note that calling any method (except additional calls to close(),
    which are ignored) on a closed stream should raise a ValueError.
    
    IOBase (and its subclasses) support the iterator protocol, meaning
    that an IOBase object can be iterated over yielding the lines in a
    stream.
    
    IOBase also supports the :keyword:`with` statement. In this example,
    fp is closed after the suite of the with statement is complete:
    
    with open('spam.txt', 'r') as fp:
        fp.write('Spam and eggs!')
    """
    def close(self, *args, **kwargs): # real signature unknown
        """
        Flush and close the IO object.
        
        This method has no effect if the file is already closed.
        """
        pass

    def fileno(self, *args, **kwargs): # real signature unknown
        """
        Returns underlying file descriptor if one exists.
        
        An IOError is raised if the IO object does not use a file descriptor.
        """
        pass

    def flush(self, *args, **kwargs): # real signature unknown
        """
        Flush write buffers, if applicable.
        
        This is not implemented for read-only and non-blocking streams.
        """
        pass

    def isatty(self, *args, **kwargs): # real signature unknown
        """
        Return whether this is an 'interactive' stream.
        
        Return False if it can't be determined.
        """
        pass

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def readable(self, *args, **kwargs): # real signature unknown
        """
        Return whether object was opened for reading.
        
        If False, read() will raise IOError.
        """
        pass

    def readline(self, *args, **kwargs): # real signature unknown
        """
        Read and return a line from the stream.
        
        If limit is specified, at most limit bytes will be read.
        
        The line terminator is always b'\n' for binary files; for text
        files, the newlines argument to open can be used to select the line
        terminator(s) recognized.
        """
        pass

    def readlines(self, *args, **kwargs): # real signature unknown
        """
        Return a list of lines from the stream.
        
        hint can be specified to control the number of lines read: no more
        lines will be read if the total size (in bytes/characters) of all
        lines so far exceeds hint.
        """
        pass

    def seek(self, *args, **kwargs): # real signature unknown
        """
        Change stream position.
        
        Change the stream position to the given byte offset. The offset is
        interpreted relative to the position indicated by whence.  Values
        for whence are:
        
        * 0 -- start of stream (the default); offset should be zero or positive
        * 1 -- current stream position; offset may be negative
        * 2 -- end of stream; offset is usually negative
        
        Return the new absolute position.
        """
        pass

    def seekable(self, *args, **kwargs): # real signature unknown
        """
        Return whether object supports random access.
        
        If False, seek(), tell() and truncate() will raise IOError.
        This method may need to do a test seek().
        """
        pass

    def tell(self, *args, **kwargs): # real signature unknown
        """ Return current stream position. """
        pass

    def truncate(self, *args, **kwargs): # real signature unknown
        """
        Truncate file to size bytes.
        
        File pointer is left unchanged.  Size defaults to the current IO
        position as reported by tell().  Returns the new size.
        """
        pass

    def writable(self, *args, **kwargs): # real signature unknown
        """
        Return whether object was opened for writing.
        
        If False, read() will raise IOError.
        """
        pass

    def writelines(self, *args, **kwargs): # real signature unknown
        pass

    def _checkClosed(self, *args, **kwargs): # real signature unknown
        pass

    def _checkReadable(self, *args, **kwargs): # real signature unknown
        pass

    def _checkSeekable(self, *args, **kwargs): # real signature unknown
        pass

    def _checkWritable(self, *args, **kwargs): # real signature unknown
        pass

    def __enter__(self, *args, **kwargs): # real signature unknown
        pass

    def __exit__(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class _BufferedIOBase(_IOBase):
    """
    Base class for buffered IO objects.
    
    The main difference with RawIOBase is that the read() method
    supports omitting the size argument, and does not have a default
    implementation that defers to readinto().
    
    In addition, read(), readinto() and write() may raise
    BlockingIOError if the underlying raw stream is in non-blocking
    mode and not ready; unlike their raw counterparts, they will never
    return None.
    
    A typical implementation should not inherit from a RawIOBase
    implementation, but wrap one.
    """
    def detach(self, *args, **kwargs): # real signature unknown
        """
        Disconnect this buffer from its underlying raw stream and return it.
        
        After the raw stream has been detached, the buffer is in an unusable
        state.
        """
        pass

    def read(self, *args, **kwargs): # real signature unknown
        """
        Read and return up to n bytes.
        
        If the argument is omitted, None, or negative, reads and
        returns all data until EOF.
        
        If the argument is positive, and the underlying raw stream is
        not 'interactive', multiple raw reads may be issued to satisfy
        the byte count (unless EOF is reached first).  But for
        interactive raw streams (as well as sockets and pipes), at most
        one raw read will be issued, and a short result does not imply
        that EOF is imminent.
        
        Returns an empty bytes object on EOF.
        
        Returns None if the underlying raw stream was open in non-blocking
        mode and no data is available at the moment.
        """
        pass

    def read1(self, *args, **kwargs): # real signature unknown
        """
        Read and return up to n bytes, with at most one read() call
        to the underlying raw stream. A short result does not imply
        that EOF is imminent.
        
        Returns an empty bytes object on EOF.
        """
        pass

    def readinto(self, *args, **kwargs): # real signature unknown
        pass

    def write(self, *args, **kwargs): # real signature unknown
        """
        Write the given buffer to the IO stream.
        
        Returns the number of bytes written, which is never less than
        len(b).
        
        Raises BlockingIOError if the buffer is full and the
        underlying raw stream cannot accept more data at the moment.
        """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass


class BufferedRandom(_BufferedIOBase):
    """
    A buffered interface to random access streams.
    
    The constructor creates a reader and writer for a seekable stream,
    raw, given in the first argument. If the buffer_size is omitted it
    defaults to DEFAULT_BUFFER_SIZE. max_buffer_size isn't used anymore.
    """
    def close(self, *args, **kwargs): # real signature unknown
        pass

    def detach(self, *args, **kwargs): # real signature unknown
        pass

    def fileno(self, *args, **kwargs): # real signature unknown
        pass

    def flush(self, *args, **kwargs): # real signature unknown
        pass

    def isatty(self, *args, **kwargs): # real signature unknown
        pass

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def peek(self, *args, **kwargs): # real signature unknown
        pass

    def read(self, *args, **kwargs): # real signature unknown
        pass

    def read1(self, *args, **kwargs): # real signature unknown
        pass

    def readable(self, *args, **kwargs): # real signature unknown
        pass

    def readinto(self, *args, **kwargs): # real signature unknown
        pass

    def readline(self, *args, **kwargs): # real signature unknown
        pass

    def seek(self, *args, **kwargs): # real signature unknown
        pass

    def seekable(self, *args, **kwargs): # real signature unknown
        pass

    def tell(self, *args, **kwargs): # real signature unknown
        pass

    def truncate(self, *args, **kwargs): # real signature unknown
        pass

    def writable(self, *args, **kwargs): # real signature unknown
        pass

    def write(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __sizeof__(self, *args, **kwargs): # real signature unknown
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    mode = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    name = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    raw = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class BufferedReader(_BufferedIOBase):
    """ Create a new buffered reader using the given readable raw IO object. """
    def close(self, *args, **kwargs): # real signature unknown
        pass

    def detach(self, *args, **kwargs): # real signature unknown
        pass

    def fileno(self, *args, **kwargs): # real signature unknown
        pass

    def flush(self, *args, **kwargs): # real signature unknown
        pass

    def isatty(self, *args, **kwargs): # real signature unknown
        pass

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def peek(self, *args, **kwargs): # real signature unknown
        pass

    def read(self, *args, **kwargs): # real signature unknown
        pass

    def read1(self, *args, **kwargs): # real signature unknown
        pass

    def readable(self, *args, **kwargs): # real signature unknown
        pass

    def readline(self, *args, **kwargs): # real signature unknown
        pass

    def seek(self, *args, **kwargs): # real signature unknown
        pass

    def seekable(self, *args, **kwargs): # real signature unknown
        pass

    def tell(self, *args, **kwargs): # real signature unknown
        pass

    def truncate(self, *args, **kwargs): # real signature unknown
        pass

    def writable(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __sizeof__(self, *args, **kwargs): # real signature unknown
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    mode = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    name = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    raw = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class BufferedRWPair(_BufferedIOBase):
    """
    A buffered reader and writer object together.
    
    A buffered reader object and buffered writer object put together to
    form a sequential IO object that can read and write. This is typically
    used with a socket or two-way pipe.
    
    reader and writer are RawIOBase objects that are readable and
    writeable respectively. If the buffer_size is omitted it defaults to
    DEFAULT_BUFFER_SIZE.
    """
    def close(self, *args, **kwargs): # real signature unknown
        pass

    def flush(self, *args, **kwargs): # real signature unknown
        pass

    def isatty(self, *args, **kwargs): # real signature unknown
        pass

    def peek(self, *args, **kwargs): # real signature unknown
        pass

    def read(self, *args, **kwargs): # real signature unknown
        pass

    def read1(self, *args, **kwargs): # real signature unknown
        pass

    def readable(self, *args, **kwargs): # real signature unknown
        pass

    def readinto(self, *args, **kwargs): # real signature unknown
        pass

    def writable(self, *args, **kwargs): # real signature unknown
        pass

    def write(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class BufferedWriter(_BufferedIOBase):
    """
    A buffer for a writeable sequential RawIO object.
    
    The constructor creates a BufferedWriter for the given writeable raw
    stream. If the buffer_size is not given, it defaults to
    DEFAULT_BUFFER_SIZE. max_buffer_size isn't used anymore.
    """
    def close(self, *args, **kwargs): # real signature unknown
        pass

    def detach(self, *args, **kwargs): # real signature unknown
        pass

    def fileno(self, *args, **kwargs): # real signature unknown
        pass

    def flush(self, *args, **kwargs): # real signature unknown
        pass

    def isatty(self, *args, **kwargs): # real signature unknown
        pass

    def readable(self, *args, **kwargs): # real signature unknown
        pass

    def seek(self, *args, **kwargs): # real signature unknown
        pass

    def seekable(self, *args, **kwargs): # real signature unknown
        pass

    def tell(self, *args, **kwargs): # real signature unknown
        pass

    def truncate(self, *args, **kwargs): # real signature unknown
        pass

    def writable(self, *args, **kwargs): # real signature unknown
        pass

    def write(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __sizeof__(self, *args, **kwargs): # real signature unknown
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    mode = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    name = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    raw = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class BytesIO(_BufferedIOBase):
    """
    BytesIO([buffer]) -> object
    
    Create a buffered I/O implementation using an in-memory bytes
    buffer, ready for reading and writing.
    """
    def close(self): # real signature unknown; restored from __doc__
        """ close() -> None.  Disable all I/O operations. """
        pass

    def flush(self): # real signature unknown; restored from __doc__
        """ flush() -> None.  Does nothing. """
        pass

    def getvalue(self): # real signature unknown; restored from __doc__
        """
        getvalue() -> bytes.
        
        Retrieve the entire contents of the BytesIO object.
        """
        pass

    def isatty(self): # real signature unknown; restored from __doc__
        """
        isatty() -> False.
        
        Always returns False since BytesIO objects are not connected
        to a tty-like device.
        """
        pass

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def read(self, size=None): # real signature unknown; restored from __doc__
        """
        read([size]) -> read at most size bytes, returned as a string.
        
        If the size argument is negative, read until EOF is reached.
        Return an empty string at EOF.
        """
        pass

    def read1(self, size): # real signature unknown; restored from __doc__
        """
        read1(size) -> read at most size bytes, returned as a string.
        
        If the size argument is negative or omitted, read until EOF is reached.
        Return an empty string at EOF.
        """
        pass

    def readable(self): # real signature unknown; restored from __doc__
        """ readable() -> bool. Returns True if the IO object can be read. """
        pass

    def readinto(self, bytearray): # real signature unknown; restored from __doc__
        """
        readinto(bytearray) -> int.  Read up to len(b) bytes into b.
        
        Returns number of bytes read (0 for EOF), or None if the object
        is set not to block as has no data to read.
        """
        pass

    def readline(self, size=None): # real signature unknown; restored from __doc__
        """
        readline([size]) -> next line from the file, as a string.
        
        Retain newline.  A non-negative size argument limits the maximum
        number of bytes to return (an incomplete line may be returned then).
        Return an empty string at EOF.
        """
        pass

    def readlines(self, size=None): # real signature unknown; restored from __doc__
        """
        readlines([size]) -> list of strings, each a line from the file.
        
        Call readline() repeatedly and return a list of the lines so read.
        The optional size argument, if given, is an approximate bound on the
        total number of bytes in the lines returned.
        """
        return []

    def seek(self, pos, whence=0): # real signature unknown; restored from __doc__
        """
        seek(pos, whence=0) -> int.  Change stream position.
        
        Seek to byte offset pos relative to position indicated by whence:
             0  Start of stream (the default).  pos should be >= 0;
             1  Current position - pos may be negative;
             2  End of stream - pos usually negative.
        Returns the new absolute position.
        """
        pass

    def seekable(self): # real signature unknown; restored from __doc__
        """ seekable() -> bool. Returns True if the IO object can be seeked. """
        pass

    def tell(self): # real signature unknown; restored from __doc__
        """ tell() -> current file position, an integer """
        pass

    def truncate(self, size=None): # real signature unknown; restored from __doc__
        """
        truncate([size]) -> int.  Truncate the file to at most size bytes.
        
        Size defaults to the current file position, as returned by tell().
        The current file position is unchanged.  Returns the new size.
        """
        pass

    def writable(self): # real signature unknown; restored from __doc__
        """ writable() -> bool. Returns True if the IO object can be written. """
        pass

    def write(self, bytes): # real signature unknown; restored from __doc__
        """
        write(bytes) -> int.  Write bytes to file.
        
        Return the number of bytes written.
        """
        pass

    def writelines(self, sequence_of_strings): # real signature unknown; restored from __doc__
        """
        writelines(sequence_of_strings) -> None.  Write strings to the file.
        
        Note that newlines are not added.  The sequence can be any iterable
        object producing strings. This is equivalent to calling write() for
        each string.
        """
        pass

    def __getstate__(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, buffer=None): # real signature unknown; restored from __doc__
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __setstate__(self, *args, **kwargs): # real signature unknown
        pass

    def __sizeof__(self, *args, **kwargs): # real signature unknown
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """True if the file is closed."""



class _RawIOBase(_IOBase):
    """ Base class for raw binary I/O. """
    def read(self, *args, **kwargs): # real signature unknown
        pass

    def readall(self, *args, **kwargs): # real signature unknown
        """ Read until EOF, using multiple read() call. """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass


class FileIO(_RawIOBase):
    """
    file(name: str[, mode: str]) -> file IO object
    
    Open a file.  The mode can be 'r' (default), 'w' or 'a' for reading,
    writing or appending.  The file will be created if it doesn't exist
    when opened for writing or appending; it will be truncated when
    opened for writing.  Add a '+' to the mode to allow simultaneous
    reading and writing.
    """
    def close(self): # real signature unknown; restored from __doc__
        """
        close() -> None.  Close the file.
        
        A closed file cannot be used for further I/O operations.  close() may be
        called more than once without error.
        """
        pass

    def fileno(self): # real signature unknown; restored from __doc__
        """ fileno() -> int.  Return the underlying file descriptor (an integer). """
        pass

    def isatty(self): # real signature unknown; restored from __doc__
        """ isatty() -> bool.  True if the file is connected to a TTY device. """
        pass

    def read(self, size=-1): # known case of _io.FileIO.read
        """
        read(size: int) -> bytes.  read at most size bytes, returned as bytes.
        
        Only makes one system call, so less data may be returned than requested
        In non-blocking mode, returns None if no data is available.
        On end-of-file, returns ''.
        """
        return ""

    def readable(self): # real signature unknown; restored from __doc__
        """ readable() -> bool.  True if file was opened in a read mode. """
        pass

    def readall(self): # real signature unknown; restored from __doc__
        """
        readall() -> bytes.  read all data from the file, returned as bytes.
        
        In non-blocking mode, returns as much as is immediately available,
        or None if no data is available.  On end-of-file, returns ''.
        """
        pass

    def readinto(self): # real signature unknown; restored from __doc__
        """ readinto() -> Same as RawIOBase.readinto(). """
        pass

    def seek(self, offset, whence=None): # real signature unknown; restored from __doc__
        """
        seek(offset: int[, whence: int]) -> int.  Move to new file position
        and return the file position.
        
        Argument offset is a byte count.  Optional argument whence defaults to
        SEEK_SET or 0 (offset from start of file, offset should be >= 0); other values
        are SEEK_CUR or 1 (move relative to current position, positive or negative),
        and SEEK_END or 2 (move relative to end of file, usually negative, although
        many platforms allow seeking beyond the end of a file).
        
        Note that not all file objects are seekable.
        """
        pass

    def seekable(self): # real signature unknown; restored from __doc__
        """ seekable() -> bool.  True if file supports random-access. """
        pass

    def tell(self): # real signature unknown; restored from __doc__
        """
        tell() -> int.  Current file position.
        
        Can raise OSError for non seekable files.
        """
        pass

    def truncate(self, size=None): # real signature unknown; restored from __doc__
        """
        truncate([size: int]) -> int.  Truncate the file to at most size bytes and
        return the truncated size.
        
        Size defaults to the current file position, as returned by tell().
        The current file position is changed to the value of size.
        """
        pass

    def writable(self): # real signature unknown; restored from __doc__
        """ writable() -> bool.  True if file was opened in a write mode. """
        pass

    def write(self, b): # real signature unknown; restored from __doc__
        """
        write(b: bytes) -> int.  Write bytes b to file, return number written.
        
        Only makes one system call, so not all of the data may be written.
        The number of bytes actually written is returned.  In non-blocking mode,
        returns None if the write would block.
        """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """True if the file is closed"""

    closefd = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """True if the file descriptor will be closed by close()."""

    mode = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """String giving the file mode"""



class IncrementalNewlineDecoder(object):
    """
    Codec used when reading a file in universal newlines mode.  It wraps
    another incremental decoder, translating \r\n and \r into \n.  It also
    records the types of newlines encountered.  When used with
    translate=False, it ensures that the newline sequence is returned in
    one piece. When used with decoder=None, it expects unicode strings as
    decode input and translates newlines without first invoking an external
    decoder.
    """
    def decode(self, *args, **kwargs): # real signature unknown
        pass

    def getstate(self, *args, **kwargs): # real signature unknown
        pass

    def reset(self, *args, **kwargs): # real signature unknown
        pass

    def setstate(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    newlines = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class _TextIOBase(_IOBase):
    """
    Base class for text I/O.
    
    This class provides a character and line based interface to stream
    I/O. There is no readinto method because Python's character strings
    are immutable. There is no public constructor.
    """
    def detach(self, *args, **kwargs): # real signature unknown
        """
        Separate the underlying buffer from the TextIOBase and return it.
        
        After the underlying buffer has been detached, the TextIO is in an
        unusable state.
        """
        pass

    def read(self, *args, **kwargs): # real signature unknown
        """
        Read at most n characters from stream.
        
        Read from underlying buffer until we have n characters or we hit EOF.
        If n is negative or omitted, read until EOF.
        """
        pass

    def readline(self, *args, **kwargs): # real signature unknown
        """
        Read until newline or EOF.
        
        Returns an empty string if EOF is hit immediately.
        """
        pass

    def write(self, *args, **kwargs): # real signature unknown
        """
        Write string to stream.
        Returns the number of characters written (which is always equal to
        the length of the string).
        """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    encoding = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """Encoding of the text stream.

Subclasses should override.
"""

    errors = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """The error setting of the decoder or encoder.

Subclasses should override.
"""

    newlines = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """Line endings translated so far.

Only line endings translated during reading are considered.

Subclasses should override.
"""



class StringIO(_TextIOBase):
    """
    Text I/O implementation using an in-memory buffer.
    
    The initial_value argument sets the value of object.  The newline
    argument is like the one of TextIOWrapper's constructor.
    """
    def close(self, *args, **kwargs): # real signature unknown
        """
        Close the IO object. Attempting any further operation after the
        object is closed will raise a ValueError.
        
        This method has no effect if the file is already closed.
        """
        pass

    def getvalue(self, *args, **kwargs): # real signature unknown
        """ Retrieve the entire contents of the object. """
        pass

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def read(self, *args, **kwargs): # real signature unknown
        """
        Read at most n characters, returned as a string.
        
        If the argument is negative or omitted, read until EOF
        is reached. Return an empty string at EOF.
        """
        pass

    def readable(self): # real signature unknown; restored from __doc__
        """ readable() -> bool. Returns True if the IO object can be read. """
        pass

    def readline(self, *args, **kwargs): # real signature unknown
        """
        Read until newline or EOF.
        
        Returns an empty string if EOF is hit immediately.
        """
        pass

    def seek(self, *args, **kwargs): # real signature unknown
        """
        Change stream position.
        
        Seek to character offset pos relative to position indicated by whence:
            0  Start of stream (the default).  pos should be >= 0;
            1  Current position - pos must be 0;
            2  End of stream - pos must be 0.
        Returns the new absolute position.
        """
        pass

    def seekable(self): # real signature unknown; restored from __doc__
        """ seekable() -> bool. Returns True if the IO object can be seeked. """
        pass

    def tell(self, *args, **kwargs): # real signature unknown
        """ Tell the current file position. """
        pass

    def truncate(self, *args, **kwargs): # real signature unknown
        """
        Truncate size to pos.
        
        The pos argument defaults to the current file position, as
        returned by tell().  The current file position is unchanged.
        Returns the new absolute position.
        """
        pass

    def writable(self): # real signature unknown; restored from __doc__
        """ writable() -> bool. Returns True if the IO object can be written. """
        pass

    def write(self, *args, **kwargs): # real signature unknown
        """
        Write string to file.
        
        Returns the number of characters written, which is always equal to
        the length of the string.
        """
        pass

    def __getstate__(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __setstate__(self, *args, **kwargs): # real signature unknown
        pass

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    line_buffering = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    newlines = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class TextIOWrapper(_TextIOBase):
    """
    Character and line based layer over a BufferedIOBase object, buffer.
    
    encoding gives the name of the encoding that the stream will be
    decoded or encoded with. It defaults to locale.getpreferredencoding.
    
    errors determines the strictness of encoding and decoding (see the
    codecs.register) and defaults to "strict".
    
    newline controls how line endings are handled. It can be None, '',
    '\n', '\r', and '\r\n'.  It works as follows:
    
    * On input, if newline is None, universal newlines mode is
      enabled. Lines in the input can end in '\n', '\r', or '\r\n', and
      these are translated into '\n' before being returned to the
      caller. If it is '', universal newline mode is enabled, but line
      endings are returned to the caller untranslated. If it has any of
      the other legal values, input lines are only terminated by the given
      string, and the line ending is returned to the caller untranslated.
    
    * On output, if newline is None, any '\n' characters written are
      translated to the system default line separator, os.linesep. If
      newline is '', no translation takes place. If newline is any of the
      other legal values, any '\n' characters written are translated to
      the given string.
    
    If line_buffering is True, a call to flush is implied when a call to
    write contains a newline character.
    """
    def close(self, *args, **kwargs): # real signature unknown
        pass

    def detach(self, *args, **kwargs): # real signature unknown
        pass

    def fileno(self, *args, **kwargs): # real signature unknown
        pass

    def flush(self, *args, **kwargs): # real signature unknown
        pass

    def isatty(self, *args, **kwargs): # real signature unknown
        pass

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def read(self, *args, **kwargs): # real signature unknown
        pass

    def readable(self, *args, **kwargs): # real signature unknown
        pass

    def readline(self, *args, **kwargs): # real signature unknown
        pass

    def seek(self, *args, **kwargs): # real signature unknown
        pass

    def seekable(self, *args, **kwargs): # real signature unknown
        pass

    def tell(self, *args, **kwargs): # real signature unknown
        pass

    def truncate(self, *args, **kwargs): # real signature unknown
        pass

    def writable(self, *args, **kwargs): # real signature unknown
        pass

    def write(self, *args, **kwargs): # real signature unknown
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    buffer = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    closed = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    encoding = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    errors = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    line_buffering = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    name = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    newlines = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    _CHUNK_SIZE = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class UnsupportedOperation(ValueError, IOError):
    # no doc
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    __weakref__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """list of weak references to the object (if defined)"""



