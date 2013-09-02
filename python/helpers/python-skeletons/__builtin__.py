"""Skeletons for built-in symbols."""

import sys as __sys


def abs(number):
    """Return the absolute value of the argument.

    :type number: T
    :rtype: T | unknown
    """
    pass


def all(iterable):
    """Return True if bool(x) is True for all values x in the iterable.

    :type iterable: collections.Iterable
    :rtype: bool
    """
    pass


def any(iterable):
    """Return True if bool(x) is True for any x in the iterable.

    :type iterable: collections.Iterable
    :rtype: bool
    """
    pass


def bin(number):
    """Return the binary representation of an integer or long integer.

    :type number: numbers.Number
    :rtype: bytes
    """
    pass


def callable(object):
    """Return whether the object is callable (i.e., some kind of function).
    Note that classes are callable, as are instances with a __call__() method.

    :rtype: bool
    """
    pass


def chr(i):
    """Return a string of one character with ordinal i; 0 <= i < 256.

    :type i: int
    :rtype: string
    """
    pass


def cmp(x, y):
    """Return negative if x<y, zero if x==y, positive if x>y.

    :rtype: int
    """
    pass


def dir(object=None):
    """If called without an argument, return the names in the current scope.
    Else, return an alphabetized list of names comprising (some of) the attributes
    of the given object, and of attributes reachable from it.

    :rtype: list[string]
    """
    pass


def divmod(x, y):
    """Return the tuple ((x-x%y)/y, x%y).

    :type x: numbers.Number
    :type y: numbers.Number
    :rtype: (int | long | float | unknown, int | long | float | unknown)
    """
    pass


def filter(function_or_none, sequence):
    """Return those items of sequence for which function(item) is true. If
    function is None, return the items that are true. If sequence is a tuple
    or string, return the same type, else return a list.

    :type function_or_none: collections.Callable | None
    :type sequence: T <= list | collections.Iterable | bytes | unicode
    :rtype: T
    """
    pass


def getattr(object, name, default=None):
    """Get a named attribute from an object; getattr(x, 'y') is equivalent to
    x.y. When a default argument is given, it is returned when the attribute
    doesn't exist; without it, an exception is raised in that case.

    :type name: string
    :rtype: object | unknown
    """
    pass


def globals():
    """Return the dictionary containing the current scope's global variables.

    :rtype: dict[string, unknown]
    """
    pass


def hasattr(object, name):
    """Return whether the object has an attribute with the given name.

    :type name: string
    :rtype: bool
    """
    pass


def hash(object):
    """Return a hash value for the object.

    :rtype: int
    """
    pass


def hex(number):
    """Return the hexadecimal representation of an integer or long integer.

    :type number: numbers.Integral
    :rtype: string
    """
    pass


def id(object):
    """Return the identity of an object.

    :rtype: int
    """
    pass


def isinstance(object, class_or_type_or_tuple):
    """Return whether an object is an instance of a class or of a subclass
    thereof.

    :rtype: bool
    """
    pass


def issubclass(C, B):
    """Return whether class C is a subclass (i.e., a derived class) of class B.

    :rtype: bool
    """
    pass


def iter(source, sentinel=None):
    """Get an iterator from an object. In the first form, the argument must
    supply its own iterator, or be a sequence. In the second form, the callable
    is called until it returns the sentinel.

    :type source: collections.Iterable[T]
    :rtype: collections.Iterator[T]
    """
    pass


def len(object):
    """Return the number of items of a sequence or mapping.

    :type object: collections.Sized
    :rtype: int
    """
    pass


def locals():
    """Update and return a dictionary containing the current scope's local
    variables.

    :rtype: dict[string, unknown]
    """
    pass


def map(function, sequence, *sequence_1):
    """Return a list of the results of applying the function to the items of
    the argument sequence(s).

    :type function: ((T) -> V) | None
    :type sequence: collections.Iterable[T]
    :rtype: list[V] | bytes | unicode
    """
    pass


def next(iterator, default=None):
    """Return the next item from the iterator.

    :type iterator: collections.Iterator[T]
    :rtype: T
    """
    pass


def oct(number):
    """Return the octal representation of an integer or long integer.

    :type number: numbers.Integral
    :rtype: string
    """
    pass


def open(name, mode='r', buffering=-1, encoding=None, errors=None, newline=None, closefd=None, opener=None):
    """Open a file, returns a file object.

    :type name: string
    :type mode: string
    :type buffering: int
    :type encoding: string | None
    :type errors: string | None
    :rtype: file
    """
    pass


def ord(c):
    """Return the integer ordinal of a one-character string.

    :type c: string
    :rtype: int
    """
    pass


def pow(x, y, z=None):
    """With two arguments, equivalent to x**y. With three arguments,
    equivalent to (x**y) % z, but may be more efficient (e.g. for longs).

    :type x: numbers.Number
    :type y: numbers.Number
    :type z: numbers.Number | None
    :rtype: int | long | float | complex
    """
    pass


if __sys.version_info < (3,):
    def range(start, stop=None, step=None):
        """Return a list containing an arithmetic progression of integers.

        :type start: numbers.Integral
        :type stop: numbers.Integral | None
        :type step: numbers.Integral | None
        :rtype: list[int]
        """
        pass


def reduce(function, sequence, initial=None):
    """Apply a function of two arguments cumulatively to the items of a sequence,
    from left to right, so as to reduce the sequence to a single value.

    :type function: collections.Callable
    :type sequence: collections.Iterable
    :type initial: T
    :rtype: T | unknown
    """
    pass


def repr(object):
    """
    Return the canonical string representation of the object.

    :rtype: string
    """
    pass


def round(number, ndigits=None):
    """Round a number to a given precision in decimal digits (default 0 digits).

    :type number: numbers.Real
    :type ndigits: numbers.Real | None
    :rtype: float
    """
    pass


class slice(object):
    def __init__(self, start, stop=None, step=None):
        """Create a slice object. This is used for extended slicing (e.g. a[0:10:2]).

        :type start: numbers.Integral
        :type stop: numbers.Integral | None
        :type step: numbers.Integral | None
        """
        return


def vars(object=None):
    """Without arguments, equivalent to locals(). With an argument, equivalent
    to object.__dict__.

    :rtype: dict[string, unknown]
    """
    pass


class object:
    """ The most base type."""

    @staticmethod
    def __new__(cls, *more):
        """Create a new object.

        :type cls: T
        :rtype: T
        """
        pass


class enumerate(object):
    """enumerate object."""

    def __init__(self, iterable, start=0):
        """Create an enumerate object.

        :type iterable: collections.Iterable[T]
        :type start: int | long
        :rtype: enumerate[int, T]
        """
        pass

    def next(self):
        """Return the next value, or raise StopIteration.

        :rtype: (int, T)
        """
        pass

    def __iter__(self):
        """x.__iter__() <==> iter(x).

        :rtype: enumerate[int, T]
        """
        pass


if __sys.version_info < (3,):
    class xrange(object):
        """xrange object."""

        def __init__(self, start, stop=None, step=None):
            """Create an xrange object.

            :type start: numbers.Integral
            :type stop: numbers.Integral | None
            :type step: numbers.Integral | None
            :rtype: xrange[int]
            """
            pass
