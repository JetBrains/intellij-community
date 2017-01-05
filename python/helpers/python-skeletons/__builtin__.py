"""Skeletons for Python 2 built-in symbols."""


from __future__ import unicode_literals
import sys


def abs(number):
    """Return the absolute value of the argument.

    :type number: T
    :rtype: T | unknown
    """
    return number


def all(iterable):
    """Return True if bool(x) is True for all values x in the iterable.

    :type iterable: collections.Iterable
    :rtype: bool
    """
    return False


def any(iterable):
    """Return True if bool(x) is True for any x in the iterable.

    :type iterable: collections.Iterable
    :rtype: bool
    """
    return False


def bin(number):
    """Return the binary representation of an integer or long integer.

    :type number: numbers.Number
    :rtype: bytes
    """
    return b''


def callable(object):
    """Return whether the object is callable (i.e., some kind of function).
    Note that classes are callable, as are instances with a __call__() method.

    :rtype: bool
    """
    return False


def chr(i):
    """Return a string of one character with ordinal i; 0 <= i < 256.

    :type i: numbers.Integral
    :rtype: bytes
    """
    return b''


def cmp(x, y):
    """Return negative if x<y, zero if x==y, positive if x>y.

    :rtype: int
    """
    return 0


def dir(object=None):
    """If called without an argument, return the names in the current scope.
    Else, return an alphabetized list of names comprising (some of) the
    attributes of the given object, and of attributes reachable from it.

    :rtype: list[string]
    """
    return []


def divmod(x, y):
    """Return the tuple ((x-x%y)/y, x%y).

    :type x: numbers.Number
    :type y: numbers.Number
    :rtype: (int | long | float | unknown, int | long | float | unknown)
    """
    return 0, 0


def filter(function_or_none, sequence):
    """Return those items of sequence for which function(item) is true. If
    function is None, return the items that are true. If sequence is a tuple
    or string, return the same type, else return a list.

    :type function_or_none: collections.Callable | None
    :type sequence: T <= list[V] | collections.Iterable[V] | bytes | unicode
    :rtype: T
    """
    return sequence


def getattr(object, name, default=None):
    """Get a named attribute from an object; getattr(x, 'y') is equivalent to
    x.y. When a default argument is given, it is returned when the attribute
    doesn't exist; without it, an exception is raised in that case.

    :type name: string
    """
    pass


def globals():
    """Return the dictionary containing the current scope's global variables.

    :rtype: dict[string, unknown]
    """
    return {}


def hasattr(object, name):
    """Return whether the object has an attribute with the given name.

    :type name: string
    :rtype: bool
    """
    return False


def hash(object):
    """Return a hash value for the object.

    :rtype: int
    """
    return 0


def hex(number):
    """Return the hexadecimal representation of an integer or long integer.

    :type number: numbers.Integral
    :rtype: bytes
    """
    return b''


def id(object):
    """Return the identity of an object.

    :rtype: int
    """
    return 0


def isinstance(object, class_or_type_or_tuple):
    """Return whether an object is an instance of a class or of a subclass
    thereof.

    :rtype: bool
    """
    return False


def issubclass(C, B):
    """Return whether class C is a subclass (i.e., a derived class) of class B.

    :rtype: bool
    """
    return False


def iter(o, sentinel=None):
    """Get an iterator from an object. In the first form, the argument must
    supply its own iterator, or be a sequence. In the second form, the callable
    is called until it returns the sentinel.

    :type o: collections.Iterable[T] | (() -> object)
    :type sentinel: object | None
    :rtype: collections.Iterator[T]
    """
    return []


def len(object):
    """Return the number of items of a sequence or mapping.

    :type object: collections.Sized
    :rtype: int
    """
    return 0


def locals():
    """Update and return a dictionary containing the current scope's local
    variables.

    :rtype: dict[string, unknown]
    """
    return {}


def map(function, sequence, *sequence_1):
    """Return a list of the results of applying the function to the items of
    the argument sequence(s).

    :type function: None | (T) -> V
    :type sequence: collections.Iterable[T]
    :rtype: list[V]
    """
    pass


def min(*args, key=None, default=None):
    """Return the smallest item in an iterable or the smallest of two or more
    arguments.

    :type args: T
    :rtype: T
    """
    pass


def max(*args, key=None, default=None):
    """Return the largest item in an iterable or the largest of two or more
    arguments.

    :type args: T
    :rtype: T
    """
    pass


def sum(iterable, start=0):
    """Sums start and the items of an iterable from left to right and returns
    the total.

    :type iterable: collections.Iterable[T]
    :rtype: T
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
    :rtype: bytes
    """
    return b''


def open(name, mode='r', buffering=-1, encoding=None, errors=None, newline=None,
         closefd=None, opener=None):
    """Open a file, returns a file object.

    :type name: string
    :type mode: string
    :type buffering: numbers.Integral
    :type encoding: string | None
    :type errors: string | None
    :rtype: file
    """
    return file()


def ord(c):
    """Return the integer ordinal of a one-character string.

    :type c: string
    :rtype: int
    """
    return 0


def pow(x, y, z=None):
    """With two arguments, equivalent to x**y. With three arguments,
    equivalent to (x**y) % z, but may be more efficient (e.g. for longs).

    :type x: numbers.Number
    :type y: numbers.Number
    :type z: numbers.Number | None
    :rtype: int | long | float | complex
    """
    return 0


def range(start, stop=None, step=None):
    """Return a list containing an arithmetic progression of integers.

    :type start: numbers.Integral
    :type stop: numbers.Integral | None
    :type step: numbers.Integral | None
    :rtype: list[int]
    """
    return []


def reduce(function, sequence, initial=None):
    """Apply a function of two arguments cumulatively to the items of a
    sequence, from left to right, so as to reduce the sequence to a single
    value.

    :type function: collections.Callable
    :type sequence: collections.Iterable
    :type initial: T
    :rtype: T | unknown
    """
    return initial


def repr(object):
    """
    Return the canonical string representation of the object.

    :rtype: bytes
    """
    return b''


def round(number, ndigits=None):
    """Round a number to a given precision in decimal digits (default 0 digits).

    :type number: object
    :type ndigits: numbers.Integral | None
    :rtype: float
    """
    return 0.0


class slice(object):
    def __init__(self, start, stop=None, step=None):
        """Create a slice object. This is used for extended slicing (e.g.
        a[0:10:2]).

        :type start: numbers.Integral
        :type stop: numbers.Integral | None
        :type step: numbers.Integral | None
        """
        pass


def unichr(i):
    """Return the Unicode string of one character whose Unicode code is the
    integer i.

    :type i: numbers.Integral
    :rtype: unicode
    """
    return ''


def vars(object=None):
    """Without arguments, equivalent to locals(). With an argument, equivalent
    to object.__dict__.

    :rtype: dict[string, unknown]
    """
    return {}


def zip(*iterables):
    """This function returns a list of tuples, where the i-th tuple contains
    the i-th element from each of the argument sequences or iterables.

    :rtype: list[tuple]
    """
    return []


class object:
    """ The most base type."""

    @staticmethod
    def __new__(cls, *more):
        """Create a new object.

        :type cls: T
        :rtype: T
        """
        pass


class type(object):
    """Type of object."""

    def __instancecheck__(cls, instance):
        """Return true if instance should be considered a (direct or indirect)
        instance of class.
        """
        return False

    def __subclasscheck__(cls, subclass):
        """Return true if subclass should be considered a (direct or indirect)
        subclass of class.
        """
        return False


class enumerate(object):
    """enumerate object."""

    def __init__(self, iterable, start=0):
        """Create an enumerate object.

        :type iterable: collections.Iterable[T]
        :type start: numbers.Integral
        :rtype: enumerate[T]
        """
        pass

    def next(self):
        """Return the next value, or raise StopIteration.

        :rtype: (int, T)
        """
        pass

    def __iter__(self):
        """x.__iter__() <==> iter(x).

        :rtype: collections.Iterator[(int, T)]
        """
        return self


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


class int(object):
    """Integer numeric type."""

    def __init__(self, x=None, base=10):
        """Convert a number or string x to an integer, or return 0 if no
        arguments are given.

        :type x: object
        :type base: numbers.Integral
        """
        pass

    def __eq__(self, y):
        return False

    def __ne__(self, y):
        return False

    def __lt__(self, y):
        return False

    def __gt__(self, y):
        return False

    def __le__(self, y):
        return False

    def __ge__(self, y):
        return False

    def __add__(self, y):
        """Sum of x and y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __sub__(self, y):
        """Difference of x and y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __mul__(self, y):
        """Product of x and y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __floordiv__(self, y):
        """Floored quotient of x and y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __mod__(self, y):
        """Remainder of x / y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __pow__(self, y, modulo=None):
        """x to the power y.

        :type y: numbers.Number
        :type modulo: numbers.Integral | None
        :rtype: int
        """
        return 0

    def __lshift__(self, n):
        """x shifted left by n bits.

        :type n: numbers.Integral
        :rtype: int
        """
        return 0

    def __rshift__(self, n):
        """x shifted right by n bits.

        :type n: numbers.Integral
        :rtype: int
        """
        return 0

    def __and__(self, y):
        """Bitwise and of x and y.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __or__(self, y):
        """Bitwise or of x and y.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __xor__(self, y):
        """Bitwise exclusive or of x and y.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __div__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __truediv__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __radd__(self, y):
        """Sum of y and x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rsub__(self, y):
        """Difference of y and x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rmul__(self, y):
        """Product of y and x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rfloordiv__(self, y):
        """Floored quotient of y and x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rmod__(self, y):
        """Remainder of y / x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rpow__(self, y):
        """x to the power y.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rlshift__(self, y):
        """y shifted left by x bits.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __rrshift__(self, y):
        """y shifted right by n bits.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __rand__(self, y):
        """Bitwise and of y and x.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __ror__(self, y):
        """Bitwise or of y and x.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __rxor__(self, y):
        """Bitwise exclusive or of y and x.

        :type y: numbers.Integral
        :rtype: int
        """
        return 0

    def __rdiv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __rtruediv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: int
        """
        return 0

    def __pos__(self):
        """x unchanged.

        :rtype: int
        """
        return 0

    def __neg__(self):
        """x negated.

        :rtype: int
        """
        return 0

    def __invert__(self):
        """The bits of x inverted.

        :rtype: int
        """
        return 0


class long(object):
    """Long integer numeric type."""

    def __init__(self, x=None, base=10):
        """Convert a number or string x to a long integer, or return 0 if
        no arguments are given.

        :type x: object
        :type base: numbers.Integral
        """
        pass

    def __add__(self, y):
        """Sum of x and y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __sub__(self, y):
        """Difference of x and y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __mul__(self, y):
        """Product of x and y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __floordiv__(self, y):
        """Floored quotient of x and y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __mod__(self, y):
        """Remainder of x / y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __pow__(self, y, modulo=None):
        """x to the power y.

        :type y: numbers.Number
        :type modulo: numbers.Integral | None
        :rtype: long
        """
        return 0

    def __lshift__(self, n):
        """x shifted left by n bits.

         :type n: numbers.Integral
         :rtype: long
         """
        return 0

    def __rshift__(self, n):
        """x shifted right by n bits.

         :type n: numbers.Integral
         :rtype: long
         """
        return 0

    def __and__(self, y):
        """Bitwise and of x and y.

        :type y: numbers.Integral
        :rtype: long
        """
        return 0

    def __or__(self, y):
        """Bitwise or of x and y.

        :type y: numbers.Integral
        :rtype: long
        """
        return 0

    def __xor__(self, y):
        """Bitwise exclusive or of x and y.

        :type y: numbers.Integral
        :rtype: long
        """
        return 0

    def __div__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __truediv__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __radd__(self, y):
        """Sum of y and x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rsub__(self, y):
        """Difference of y and x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rmul__(self, y):
        """Product of y and x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rfloordiv__(self, y):
        """Floored quotient of y and x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rmod__(self, y):
        """Remainder of y / x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rpow__(self, y):
        """x to the power y.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rlshift__(self, y):
        """y shifted left by x bits.

         :type y: numbers.Integral
         :rtype: long
         """
        return 0

    def __rrshift__(self, y):
        """y shifted right by n bits.

         :type y: numbers.Integral
         :rtype: long
         """
        return 0

    def __rand__(self, y):
        """Bitwise and of y and x.

        :type y: numbers.Integral
        :rtype: long
        """
        return 0

    def __ror__(self, y):
        """Bitwise or of y and x.

        :type y: numbers.Integral
        :rtype: long
        """
        return 0

    def __rxor__(self, y):
        """Bitwise exclusive or of y and x.

        :type y: numbers.Integral
        :rtype: long
        """
        return 0

    def __rdiv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __rtruediv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: long
        """
        return 0

    def __pos__(self):
        """x unchanged.

        :rtype: long
        """
        return 0

    def __neg__(self):
        """x negated.

        :rtype: long
        """
        return 0

    def __invert__(self):
        """The bits of x inverted.

        :rtype: long
        """
        return 0


class float(object):
    """Floating point numeric type."""

    def __init__(self, x=None):
        """Convert a string or a number to floating point.

        :type x: object
        """
        pass

    def __add__(self, y):
        """Sum of x and y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __sub__(self, y):
        """Difference of x and y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __mul__(self, y):
        """Product of x and y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __floordiv__(self, y):
        """Floored quotient of x and y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __mod__(self, y):
        """Remainder of x / y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __pow__(self, y):
        """x to the power y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __div__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __truediv__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __radd__(self, y):
        """Sum of y and x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rsub__(self, y):
        """Difference of y and x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rmul__(self, y):
        """Product of y and x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rfloordiv__(self, y):
        """Floored quotient of y and x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rmod__(self, y):
        """Remainder of y / x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rpow__(self, y):
        """x to the power y.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rdiv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __rtruediv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: float
        """
        return 0.0

    def __pos__(self):
        """x unchanged.

        :rtype: float
        """
        return 0.0

    def __neg__(self):
        """x negated.

        :rtype: float
        """
        return 0.0

    @staticmethod
    def fromhex(string):
        """Create a floating-point number from a hexadecimal string.

        :type string: str
        :rtype: float
        """
        pass


class complex(object):
    """Complex numeric type."""

    def __init__(self, real=None, imag=None):
        """Create a complex number with the value real + imag*j or convert a
        string or number to a complex number.

        :type real: object
        :type imag: object
        """
        pass

    def __add__(self, y):
        """Sum of x and y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __sub__(self, y):
        """Difference of x and y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __mul__(self, y):
        """Product of x and y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __floordiv__(self, y):
        """Floored quotient of x and y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __mod__(self, y):
        """Remainder of x / y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __pow__(self, y):
        """x to the power y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __div__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __truediv__(self, y):
        """Quotient of x and y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __radd__(self, y):
        """Sum of y and x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rsub__(self, y):
        """Difference of y and x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rmul__(self, y):
        """Product of y and x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rfloordiv__(self, y):
        """Floored quotient of y and x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rmod__(self, y):
        """Remainder of y / x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rpow__(self, y):
        """x to the power y.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rdiv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __rtruediv__(self, y):
        """Quotient of y and x.

        :type y: numbers.Number
        :rtype: complex
        """
        return 0j

    def __pos__(self):
        """x unchanged.

        :rtype: complex
        """
        return 0j

    def __neg__(self):
        """x negated.

        :rtype: complex
        """
        return 0j


class str(basestring):
    """String object."""

    def __init__(self, object=''):
        """Construct an immutable string.

        :type object: object
        """
        pass

    def __add__(self, y):
        """The concatenation of x and y.

        :type y: string
        :rtype: string
        """
        return b''

    def __mul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: str
        """
        return b''

    def __mod__(self, y):
        """x % y.

        :rtype: string
        """
        return b''

    def __rmul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: str
        """
        return b''

    def __getitem__(self, y):
        """y-th item of x or substring, origin 0.

        :type y: numbers.Integral | slice
        :rtype: str
        """
        return b''

    def __iter__(self):
        """Iterator over bytes.

        :rtype: collections.Iterator[str]
        """
        return []

    def capitalize(self):
        """Return a copy of the string with its first character capitalized
        and the rest lowercased.

        :rtype: str
        """
        return b''

    def center(self, width, fillchar=' '):
        """Return centered in a string of length width.

        :type width: numbers.Integral
        :type fillchar: str
        :rtype: str
        """
        return b''

    def count(self, sub, start=None, end=None):
        """Return the number of non-overlapping occurrences of substring
        sub in the range [start, end].

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: int
        """
        return 0

    def decode(self, encoding='utf-8', errors='strict'):
        """Return a string decoded from the given bytes.

        :type encoding: string
        :type errors: string
        :rtype: unicode
        """
        return ''

    def encode(self, encoding='utf-8', errors='strict'):
        """Return an encoded version of the string as a bytes object.

        :type encoding: string
        :type errors: string
        :rtype: str
        """
        return b''

    def endswith(self, suffix, start=None, end=None):
        """Return True if the string ends with the specified suffix,
        otherwise return False.

        :type suffix: string | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def find(self, sub, start=None, end=None):
        """Return the lowest index in the string where substring sub is
        found, such that sub is contained in the slice s[start:end].

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def format(self, *args, **kwargs):
        """Perform a string formatting operation.

        :rtype: string
        """
        return ''

    def index(self, sub, start=None, end=None):
        """Like find(), but raise ValueError when the substring is not
        found.

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def isalnum(self):
        """Return true if all characters in the string are alphanumeric and
        there is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def isalpha(self):
        """Return true if all characters in the string are alphabetic and there
        is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def isdigit(self):
        """Return true if all characters in the string are digits and there
        is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def islower(self):
        """Return true if all cased characters in the string are lowercase
        and there is at least one cased character, false otherwise.

        :rtype: bool
        """
        return False

    def isspace(self):
        """Return true if there are only whitespace characters in the
        string and there is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def istitle(self):
        """Return true if the string is a titlecased string and there is at
        least one character, for example uppercase characters may only
        follow uncased characters and lowercase characters only cased ones.

        :rtype: bool
        """
        return False

    def isupper(self):
        """Return true if all cased characters in the string are uppercase
        and there is at least one cased character, false otherwise.

        :rtype: bool
        """
        return False

    def join(self, iterable):
        """Return a string which is the concatenation of the strings in the
        iterable.

        :type iterable: collections.Iterable[string]
        :rtype: string
        """
        return ''

    def ljust(self, width, fillchar=' '):
        """Return the string left justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: str
        :rtype: str
        """
        return b''

    def lower(self):
        """Return a copy of the string with all the cased characters
        converted to lowercase.

        :rtype: str
        """
        return b''

    def lstrip(self, chars=None):
        """Return a copy of the string with leading characters removed.

        :type chars: string | None
        :rtype: str
        """
        return b''

    def partition(self, sep):
        """Split the string at the first occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: string
        :rtype: (str, str, str)
        """
        return b'', b'', b''

    def replace(self, old, new, count=-1):
        """Return a copy of the string with all occurrences of substring
        old replaced by new.

        :type old: string
        :type new: string
        :type count: numbers.Integral
        :rtype: string
        """
        return ''

    def rfind(self, sub, start=None, end=None):
        """Return the highest index in the string where substring sub is
        found, such that sub is contained within s[start:end].

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rindex(self, sub, start=None, end=None):
        """Like rfind(), but raise ValueError when the substring is not
        found.

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rjust(self, width, fillchar=' '):
        """Return the string right justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: string
        :rtype: string
        """
        return ''

    def rpartition(self, sep):
        """Split the string at the last occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: string
        :rtype: (str, str, str)
        """
        return b'', b'', b''

    def rsplit(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: string | None
        :type maxsplit: numbers.Integral
        :rtype: list[str]
        """
        return []

    def rstrip(self, chars=None):
        """Return a copy of the string with trailing characters removed.

        :type chars: string | None
        :rtype: str
        """
        return b''

    def split(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: string | None
        :type maxsplit: numbers.Integral
        :rtype: list[str]
        """
        return []

    def splitlines(self, keepends=False):
        """Return a list of the lines in the string, breaking at line
        boundaries.

        :type keepends: bool
        :rtype: list[str]
        """
        return []

    def startswith(self, prefix, start=None, end=None):
        """Return True if string starts with the prefix, otherwise return
        False.

        :type prefix: string | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def strip(self, chars=None):
        """Return a copy of the string with the leading and trailing
        characters removed.

        :type chars: string | None
        :rtype: str
        """
        return b''

    def swapcase(self):
        """Return a copy of the string with uppercase characters converted
        to lowercase and vice versa.

        :rtype: str
        """
        return b''

    def title(self):
        """Return a titlecased version of the string where words start with
        an uppercase character and the remaining characters are lowercase.

        :rtype: str
        """
        return b''

    def upper(self):
        """Return a copy of the string with all the cased characters
        converted to uppercase.

        :rtype: str
        """
        return b''

    def zfill(self, width):
        """Return the numeric string left filled with zeros in a string of
        length width.

        :type width: numbers.Integral
        :rtype: str
        """
        return b''


class unicode(basestring):
    """Unicode string object."""

    def __init__(self, object='', encoding='utf-8', errors='strict'):
        """Construct an immutable Unicode string.

        :type object: object
        :type encoding: string
        :type errors: string
        """
        pass

    def __add__(self, y):
        """The concatenation of x and y.

        :type y: string
        :rtype: unicode
        """
        return ''

    def __mul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: unicode
        """
        return ''

    def __mod__(self, y):
        """x % y.

        :rtype: unicode
        """
        return ''

    def __rmul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: unicode
        """
        return ''

    def __getitem__(self, y):
        """y-th item of x or substring, origin 0.

        :type y: numbers.Integral | slice
        :rtype: unicode
        """
        return ''

    def __iter__(self):
        """Iterator over bytes.

        :rtype: collections.Iterator[unicode]
        """
        return []

    def capitalize(self):
        """Return a copy of the string with its first character capitalized
        and the rest lowercased.

        :rtype: unicode
        """
        return ''

    def center(self, width, fillchar=' '):
        """Return centered in a string of length width.

        :type width: numbers.Integral
        :type fillchar: string
        :rtype: unicode
        """
        return ''

    def count(self, sub, start=None, end=None):
        """Return the number of non-overlapping occurrences of substring
        sub in the range [start, end].

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: int
        """
        return 0

    def decode(self, encoding='utf-8', errors='strict'):
        """Return a string decoded from the given bytes.

        :type encoding: string
        :type errors: string
        :rtype: unicode
        """
        return ''

    def encode(self, encoding='utf-8', errors='strict'):
        """Return an encoded version of the string as a bytes object.

        :type encoding: string
        :type errors: string
        :rtype: bytes
        """
        return b''

    def endswith(self, suffix, start=None, end=None):
        """Return True if the string ends with the specified suffix,
        otherwise return False.

        :type suffix: string | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def find(self, sub, start=None, end=None):
        """Return the lowest index in the string where substring sub is
        found, such that sub is contained in the slice s[start:end].

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def format(self, *args, **kwargs):
        """Perform a string formatting operation.

        :rtype: unicode
        """
        return ''

    def index(self, sub, start=None, end=None):
        """Like find(), but raise ValueError when the substring is not
        found.

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def isalnum(self):
        """Return true if all characters in the string are alphanumeric and
        there is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def isalpha(self):
        """Return true if all characters in the string are alphabetic and there
        is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def isdigit(self):
        """Return true if all characters in the string are digits and there
        is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def islower(self):
        """Return true if all cased characters in the string are lowercase
        and there is at least one cased character, false otherwise.

        :rtype: bool
        """
        return False

    def isspace(self):
        """Return true if there are only whitespace characters in the
        string and there is at least one character, false otherwise.

        :rtype: bool
        """
        return False

    def istitle(self):
        """Return true if the string is a titlecased string and there is at
        least one character, for example uppercase characters may only
        follow uncased characters and lowercase characters only cased ones.

        :rtype: bool
        """
        return False

    def isupper(self):
        """Return true if all cased characters in the string are uppercase
        and there is at least one cased character, false otherwise.

        :rtype: bool
        """
        return False

    def join(self, iterable):
        """Return a string which is the concatenation of the strings in the
        iterable.

        :type iterable: collections.Iterable[string]
        :rtype: unicode
        """
        return ''

    def ljust(self, width, fillchar=' '):
        """Return the string left justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: string
        :rtype: unicode
        """
        return ''

    def lower(self):
        """Return a copy of the string with all the cased characters
        converted to lowercase.

        :rtype: unicode
        """
        return ''

    def lstrip(self, chars=None):
        """Return a copy of the string with leading characters removed.

        :type chars: string | None
        :rtype: unicode
        """
        return ''

    def partition(self, sep):
        """Split the string at the first occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: string
        :rtype: (unicode, unicode, unicode)
        """
        return '', '', ''

    def replace(self, old, new, count=-1):
        """Return a copy of the string with all occurrences of substring
        old replaced by new.

        :type old: string
        :type new: string
        :type count: numbers.Integral
        :rtype: unicode
        """
        return ''

    def rfind(self, sub, start=None, end=None):
        """Return the highest index in the string where substring sub is
        found, such that sub is contained within s[start:end].

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rindex(self, sub, start=None, end=None):
        """Like rfind(), but raise ValueError when the substring is not
        found.

        :type sub: string
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rjust(self, width, fillchar=' '):
        """Return the string right justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: string
        :rtype: unicode
        """
        return ''

    def rpartition(self, sep):
        """Split the string at the last occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: string
        :rtype: (unicode, unicode, unicode)
        """
        return '', '', ''

    def rsplit(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: string | None
        :type maxsplit: numbers.Integral
        :rtype: list[unicode]
        """
        return []

    def rstrip(self, chars=None):
        """Return a copy of the string with trailing characters removed.

        :type chars: string | None
        :rtype: unicode
        """
        return ''

    def split(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: string | None
        :type maxsplit: numbers.Integral
        :rtype: list[unicode]
        """
        return []

    def splitlines(self, keepends=False):
        """Return a list of the lines in the string, breaking at line
        boundaries.

        :type keepends: bool
        :rtype: list[unicode]
        """
        return []

    def startswith(self, prefix, start=None, end=None):
        """Return True if string starts with the prefix, otherwise return
        False.

        :type prefix: string | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def strip(self, chars=None):
        """Return a copy of the string with the leading and trailing
        characters removed.

        :type chars: string | None
        :rtype: unicode
        """
        return ''

    def swapcase(self):
        """Return a copy of the string with uppercase characters converted
        to lowercase and vice versa.

        :rtype: unicode
        """
        return ''

    def title(self):
        """Return a titlecased version of the string where words start with
        an uppercase character and the remaining characters are lowercase.

        :rtype: unicode
        """
        return ''

    def upper(self):
        """Return a copy of the string with all the cased characters
        converted to uppercase.

        :rtype: unicode
        """
        return ''

    def zfill(self, width):
        """Return the numeric string left filled with zeros in a string of
        length width.

        :type width: numbers.Integral
        :rtype: unicode
        """
        return ''


class list(object):
    """List object."""

    def __init__(self, iterable=None):
        """Create a list object.

        :type iterable: collections.Iterable[T]
        :rtype: list[T]
        """
        pass

    def __add__(self, y):
        """The concatenation of x and y.

        :type y: list[T]
        :rtype: list[T]
        """
        return []

    def __mul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: list[T]
        """
        return []

    def __rmul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: list[T]
        """
        return []

    def __iter__(self):
        """
        :rtype: collections.Iterator[T]
        """
        return []

    def __getitem__(self, y):
        """y-th item of x or sublist, origin 0.

        :type y: numbers.Integral | slice
        :rtype: T | list[T]
        """
        pass

    def __setitem__(self, i, y):
        """Item i is replaced by y.

        :type i: numbers.Integral
        :type y: T
        :rtype: None
        """
        pass

    def __delitem__(self, i):
        """Remove i-th item.

        :type i: numbers.Integral
        :rtype: None
        """
        pass

    def append(self, x):
        """Appends x to the end of the sequence.

        :type x: T
        :rtype: None
        """
        pass

    def extend(self, t):
        """Extends the sequence with the contents of t.

        :type t: collections.Iterable[T]
        :rtype: None
        """
        pass

    def count(self, x):
        """Total number of occurrences of x in the sequence.

        :type x: T
        :rtype: int
        """
        return 0

    def index(self, x, i=None, j=None):
        """Index of the first occurrence of x in the sequence.

        :type x: T
        :type i: numbers.Integral | None
        :type j: numbers.Integral | none
        :rtype: int
        """
        return 0

    def insert(self, i, x):
        """Inserts x into the sequence at the index given by i.

        :type i: numbers.Number
        :type x: T
        :rtype: None
        """
        pass

    def pop(self, i=-1):
        """Retrieves the item at i and also removes it from the sequence.

        :type i: numbers.Number
        :rtype: T
        """
        pass

    def remove(self, x):
        """Remove the first item x from the sequence.

        :type x: T
        :rtype: None
        """
        pass

    def sort(self, cmp=None, key=None, reverse=False):
        """Sort the items of the sequence in place.

        :type cmp: ((T, T) -> int) | None
        :type key: ((T) -> object) | None
        :type reverse: bool
        :rtype: None
        """
        pass


class set(object):
    """Set object."""

    def __init__(self, iterable=None):
        """Create a set object.

        :type iterable: collections.Iterable[T]
        :rtype: set[T]
        """
        pass

    def add(self, x):
        """Add an element x to a set.

        :type x: T
        :rtype: None
        """
        pass

    def discard(self, x):
        """Remove an element x from the set, do nothing if it's not present.

        :type x: T
        :rtype: None
        """
        pass

    def remove(self, x):
        """Remove an element x from the set, raise KeyError if it's not present.

        :type x: T
        :rtype: None
        """
        pass

    def pop(self):
        """Remove and return arbitrary element from the set.

        :rtype: T
        """
        pass

    def copy(self):
        """Return shallow copy of the set.

        :rtype: set[T]
        """
        pass

    def clear(self):
        """Delete all elements from the set.

        :rtype: None
        """
        pass

    def union(self, *other):
        """Return the union of this set and other collections as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return set()

    def __or__(self, other):
        """Return the union of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return set()

    def update(self, *other):
        """Update a set with the union of itself and other collections.

        :type other: collections.Iterable[T]
        :rtype: None
        """
        pass

    def difference(self, *other):
        """Return the difference of this set and other collections as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return set()

    def __sub__(self, other):
        """Return the difference of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return set()

    def difference_update(self, *other):
        """Remove all elements of other collections from this set.

        :type other: collections.Iterable[T]
        :rtype: None
        """
        pass

    def symmetric_difference(self, other):
        """Return the symmetric difference of this set and another collection as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return set()

    def __xor__(self, other):
        """Return the symmetric difference of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return set()

    def symmetric_difference_update(self, other):
        """Update the set with the symmetric difference of itself and another collection.

        :type other: collections.Iterable[T]
        :rtype: None
        """
        pass

    def intersection(self, *other):
        """Return the intersection of this set and other collections as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return set()

    def __and__(self, other):
        """Return the intersection of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return set()

    def intersection_update(self, *other):
        """Update a set with the intersection of itself and other collections.

        :type other: collections.Iterable[T]
        :rtype: None
        """
        pass

    def isdisjoint(self, other):
        """Return True if this set and another collection have a null intersection.

        :type other: collections.Iterable[T]
        :rtype: bool
        """
        return False

    def issubset(self, other):
        """Report whether another collection contains this set.

        :type other: collections.Iterable[T]
        :rtype: bool
        """
        return False

    def __le__(self, other):
        """Report whether another set contains this set.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def __lt__(self, other):
        """Report whether this set is a proper subset of other.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def issuperset(self, other):
        """Report whether this set contains another collection.

        :type other: collections.Iterable[T]
        :rtype: bool
        """
        return False

    def __ge__(self, other):
        """Report whether this set contains other set.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def __gt__(self, other):
        """Report whether this set is a proper superset of other.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def __iter__(self):
        """
        :rtype: collections.Iterator[T]
        """
        pass


class frozenset(object):
    """frozenset object."""

    def __init__(self, iterable=None):
        """Create a frozenset object.

        :type iterable: collections.Iterable[T]
        :rtype: frozenset[T]
        """
        pass

    def copy(self):
        """Return shallow copy of the set.

        :rtype: set[T]
        """
        pass

    def union(self, *other):
        """Return the union of this set and other collections as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return frozenset()

    def __or__(self, other):
        """Return the union of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return frozenset()

    def difference(self, *other):
        """Return the difference of this set and other collections as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return frozenset()

    def __sub__(self, other):
        """Return the difference of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return frozenset()

    def symmetric_difference(self, other):
        """Return the symmetric difference of this set and another collection as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return frozenset()

    def __xor__(self, other):
        """Return the symmetric difference of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return frozenset()

    def intersection(self, *other):
        """Return the intersection of this set and other collections as a new set.

        :type other: collections.Iterable[T]
        :rtype: set[T]
        """
        return frozenset()

    def __and__(self, other):
        """Return the intersection of two sets as a new set.

        :type other: collections.Set[T]
        :rtype: set[T]
        """
        return frozenset()

    def isdisjoint(self, other):
        """Return True if this set and another collection have a null intersection.

        :type other: collections.Iterable[T]
        :rtype: bool
        """
        return False

    def issubset(self, other):
        """Report whether another collection contains this set.

        :type other: collections.Iterable[T]
        :rtype: bool
        """
        return False

    def __le__(self, other):
        """Report whether another set contains this set.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def __lt__(self, other):
        """Report whether this set is a proper subset of other.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def issuperset(self, other):
        """Report whether this set contains another collection.

        :type other: collections.Iterable[T]
        :rtype: bool
        """
        return False

    def __ge__(self, other):
        """Report whether this set contains other set.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def __gt__(self, other):
        """Report whether this set is a proper superset of other.

        :type other: collections.Set[T]
        :rtype: bool
        """
        return False

    def __iter__(self):
        """
        :rtype: collections.Iterator[T]
        """
        pass


class tuple(object):
    """Tuple object."""

    def __add__(self, y):
        """The concatenation of x and y.

        :type y: tuple
        :rtype: tuple
        """
        pass

    def __mul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: tuple
        """
        pass

    def __rmul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: tuple
        """
        pass

    def __getitem__(self, y):
        """y-th item of x or subtuple, origin 0.

        :type y: numbers.Integral | slice
        :rtype: object | tuple | unknown
        """
        pass

    def count(self, x):
        """Total number of occurrences of x in the sequence.

        :type x: object
        :rtype: int
        """
        return 0

    def index(self, x, i=None, j=None):
        """Index of the first occurrence of x in the sequence.

        :type x: object
        :type i: numbers.Integral | None
        :type j: numbers.Integral | none
        :rtype: int
        """
        return 0

    def __iter__(self):
        """
        :rtype: collections.Iterator[object | unknown]
        """
        pass


class dict(object):
    """Dictionary object."""

    def __init__(self, iterable=None, **kwargs):
        """Create a dictionary object.

        :type iterable: collections.Iterable[(T, V)]
        :rtype: dict[T, V]
        """
        pass

    def __iter__(self):
        """
        :rtype: collections.Iterator[T]
        """
        pass

    def __len__(self):
        """Return the number of items in the dictionary d.

        :rtype: int
        """
        return 0

    def __getitem__(self, key):
        """Return the item of d with key key.

        :type key: T
        :rtype: V
        """
        pass

    def __setitem__(self, key, value):
        """Set d[key] to value.

        :type key: T
        :type value: V
        :rtype: None
        """
        pass

    def __delitem__(self, key):
        """Remove d[key] from d.

        :type key: T
        :rtype: None
        """
        pass

    def copy(self):
        """Return a shallow copy of the dictionary.

        :rtype: dict[T, V]
        """
        return self

    @staticmethod
    def fromkeys(seq, value=None):
        """Create a new dictionary with keys from seq and values set to value.

        :type seq: collections.Iterable[T]
        :type value: V
        :rtype: dict[T, V]
        """
        return {}

    def get(self, key, default=None):
        """Return the value for key if key is in the dictionary, else default.

        :type key: T
        :type default: V | None
        :rtype: V
        """
        pass

    def has_key(self, key):
        """Return True if d has a key key, else False.

        :type key: T
        :rtype: bool
        """
        return False

    def items(self):
        """Return a copy of the dictionary's list of (key, value) pairs.

        :rtype: list[(T, V)]
        """
        return []

    def iteritems(self):
        """Return an iterator over the dictionary's (key, value) pairs.

        :rtype: collections.Iterable[(T, V)]
        """
        return []

    def iterkeys(self):
        """Return an iterator over the dictionary's keys.

        :rtype: collections.Iterable[T]
        """
        return []

    def itervalues(self):
        """Return an iterator over the dictionary's values.

        :rtype: collections.Iterable[V]
        """
        return []

    def keys(self):
        """Return a copy of the dictionary's list of keys.

        :rtype: list[T]
        """
        return []

    def pop(self, key, default=None):
        """If key is in the dictionary, remove it and return its value, else
        return default.

        :type key: T
        :type default: V | None
        :rtype: V
        """
        pass

    def popitem(self):
        """Remove and return an arbitrary (key, value) pair from the
        dictionary.

        :rtype: (T, V)
        """
        pass

    def setdefault(self, key, default=None):
        """If key is in the dictionary, return its value.

        :type key: T
        :type default: V | None
        :rtype: V
        """
        pass

    def update(self, other=None, **kwargs):
        """Update the dictionary with the key/value pairs from other,
        overwriting existing keys.

        :type other: dict[T, V] | collections.Iterable[(T, V)]
        :rtype: None
        """
        pass

    def values(self):
        """Return a copy of the dictionary's list of values.

        :rtype: list[V]
        """
        return []


class file(object):
    """File object."""

    def __init__(self, name, mode='r', buffering=-1):
        """Create a file object.

        :type name: string
        :type mode: string
        :type buffering: numbers.Integral
        """
        self.name = name
        self.mode = mode

    def fileno(self):
        """Return the integer "file descriptor" that is used by the
        underlying implementation to request I/O operations from the
        operating system.

        :rtype: int
        """
        return 0

    def isatty(self):
        """Return True if the file is connected to a tty(-like) device,
        else False.

        :rtype: bool
        """
        return False

    def next(self):
        """Returns the next input line.

        :rtype: bytes
        """
        return ''

    def read(self, size=-1):
        """Read at most size bytes from the file (less if the read hits EOF
        before obtaining size bytes).

        :type size: numbers.Integral
        :rtype: bytes
        """
        return ''

    def readline(self, size=-1):
        """Read one entire line from the file.

        :type size: numbers.Integral
        :rtype: bytes
        """
        return ''

    def readlines(self, sizehint=-1):
        """Read until EOF using readline() and return a list containing the
        lines thus read.

        :type sizehint: numbers.Integral
        :rtype: list[bytes]
        """
        return []

    def xreadlines(self):
        """This method returns the same thing as iter(f).

        :rtype: collections.Iterable[bytes]
        """
        return []

    def seek(self, offset, whence=0):
        """Set the file's current position, like stdio's fseek().

        :type offset: numbers.Integral
        :type whence: numbers.Integral
        :rtype: None
        """
        pass

    def tell(self):
        """Return the file's current position, like stdio's ftell().

        :rtype: int
        """
        return 0

    def truncate(self, size=-1):
        """Truncate the file's size.

        :type size: numbers.Integral
        :rtype: None
        """
        pass

    def write(self, str):
        """"Write a string to the file.

        :type str: bytes
        :rtype: None
        """
        pass

    def writelines(self, sequence):
        """Write a sequence of strings to the file.

        :type sequence: collections.Iterable[bytes]
        :rtype: None
        """
        pass

    def __iter__(self):
        """
        :rtype: collections.Iterator[bytes]
        """
        pass


class __generator(object):
    """A mock class representing the generator function type."""
    def __init__(self):
        """Create a generator object.

        :rtype: __generator[T, U, V]
        """
        self.gi_code = None
        self.gi_frame = None
        self.gi_running = 0

    def __iter__(self):
        """Defined to support iteration over container.

        :rtype: collections.Iterator[T]
        """
        pass

    def next(self):
        """Return the next item from the container.

        :rtype: T
        """
        pass

    def close(self):
        """Raises new GeneratorExit exception inside the generator to
        terminate the iteration.

        :rtype: None
        """
        pass

    def send(self, value):
        """Resumes the generator and "sends" a value that becomes the
        result of the current yield-expression.

        :type value: U
        :rtype: None
        """
        pass

    def throw(self, type, value=None, traceback=None):
        """Used to raise an exception inside the generator.

        :rtype: None
        """
        pass


class __function(object):
    """A mock class representing function type."""

    def __init__(self):
        self.__name__ = ''
        self.__doc__ = ''
        self.__dict__ = ''
        self.__module__ = ''

        self.func_defaults = {}
        self.func_globals = {}
        self.func_closure = None
        self.func_code = None
        self.func_name = ''
        self.func_doc = ''
        self.func_dict = ''

        if sys.version_info >= (2, 6):
            self.__defaults__ = {}
            self.__globals__ = {}
            self.__closure__ = None
            self.__code__ = None


class __method(object):
    """A mock class representing method type (both bound and unbound)."""

    def __init__(self):
        self.im_class = None
        self.im_self = None
        self.im_func = None

        if sys.version_info >= (2, 6):
            self.__func__ = None
            self.__self__ = None


def input(prompt=None):
    """
    :type prompt: Any
    :rtype: Any
    """
    pass


def raw_input(prompt=None):
    """
    :type prompt: Any
    :rtype: str
    """
    pass