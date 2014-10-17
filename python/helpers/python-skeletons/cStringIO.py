"""Skeleton for 'cStringIO' stdlib module."""


import cStringIO


def StringIO(s=None):
    """Return a StringIO-like stream for reading or writing.

    :type s: T <= bytes | unicode
    :rtype: cStringIO.OutputType[T]
    """
    return cStringIO.OutputType(s)


class OutputType(object):
    def __init__(self, s):
        """Create an OutputType object.

        :rtype: cStringIO.OutputType[T <= bytes | unicode]
        """
        pass

    def getvalue(self):
        """Retrieve the entire contents of the "file" at any time before the
        StringIO object's close() method is called.

        :rtype: T
        """
        pass

    def close(self):
        """Free the memory buffer.

        :rtype: None
        """
        pass

    def flush(self):
        """Flush the internal buffer.

        :rtype: None
        """
        pass

    def isatty(self):
        """Return True if the file is connected to a tty(-like) device,
        else False.

        :rtype: bool
        """
        return False

    def __iter__(self):
        """Return an iterator over lines.

        :rtype: cStringIO.OutputType[T]
        """
        return self

    def next(self):
        """Returns the next input line.

        :rtype: T
        """
        pass

    def read(self, size=-1):
        """Read at most size bytes or characters from the buffer.

        :type size: numbers.Integral
        :rtype: T
        """
        pass

    def readline(self, size=-1):
        """Read one entire line from the buffer.

        :type size: numbers.Integral
        :rtype: T
        """
        pass

    def readlines(self, sizehint=-1):
        """Read until EOF using readline() and return a list containing the
        lines thus read.

        :type sizehint: numbers.Integral
        :rtype: list[T]
        """
        return []

    def seek(self, offset, whence=0):
        """Set the buffer's current position, like stdio's fseek().

        :type offset: numbers.Integral
        :type whence: numbers.Integral
        :rtype: None
        """
        pass

    def tell(self):
        """Return the buffer's current position, like stdio's ftell().

        :rtype: int
        """
        return 0

    def truncate(self, size=-1):
        """Truncate the buffer's size.

        :type size: numbers.Integral
        :rtype: None
        """
        pass

    def write(self, str):
        """"Write bytes or a string to the buffer.

        :type str: T
        :rtype: None
        """
        pass

    def writelines(self, sequence):
        """Write a sequence of bytes or strings to the buffer.

        :type sequence: collections.Iterable[T]
        :rtype: None
        """
        pass
