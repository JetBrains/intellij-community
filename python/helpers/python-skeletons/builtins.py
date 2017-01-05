"""Skeletons for Python 3 built-in symbols."""


import os


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
    :rtype: str
    """
    return ''


def callable(object):
    """Return whether the object is callable (i.e., some kind of function).
    Note that classes are callable, as are instances with a __call__() method.

    :rtype: bool
    """
    return False


def chr(i):
    """Return a string of one character with ordinal i; 0 <= i < 256.

    :type i: numbers.Integral
    :rtype: str
    """
    return ''


def dir(object=None):
    """If called without an argument, return the names in the current scope.
    Else, return an alphabetized list of names comprising (some of) the
    attributes of the given object, and of attributes reachable from it.

    :rtype: list[str]
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
    :type sequence: T <= list | collections.Iterable | bytes | str
    :rtype: T
    """
    return sequence


def getattr(object, name, default=None):
    """Get a named attribute from an object; getattr(x, 'y') is equivalent to
    x.y. When a default argument is given, it is returned when the attribute
    doesn't exist; without it, an exception is raised in that case.

    :type name: str
    """
    pass


def globals():
    """Return the dictionary containing the current scope's global variables.

    :rtype: dict[str, unknown]
    """
    return {}


def hasattr(object, name):
    """Return whether the object has an attribute with the given name.

    :type name: str
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
    :rtype: str
    """
    return ''


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


def iter(object, sentinel=None):
    """Get an iterator from an object. In the first form, the argument must
    supply its own iterator, or be a sequence. In the second form, the callable
    is called until it returns the sentinel.

    :type object: collections.Iterable[T] | (() -> object)
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

    :rtype: dict[str, unknown]
    """
    return {}


class map(object):
    def __init__(self, function, sequence, *sequence_1):
        """Return an iterable of the results of applying the function to the items of
        the argument sequence(s).

        :type function: None | (T) -> V
        :type sequence: collections.Iterable[T]
        :rtype: map[T, V]
        """
        pass

    def __iter__(self):
        """
        :rtype: collections.Iterator[V]
        """
        return self

    def __next__(self):
        """
        :rtype: V
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
    :rtype: str
    """
    return ''


def open(name, mode='r', buffering=-1, encoding=None, errors=None, newline=None,
         closefd=None, opener=None):
    """Open a file, returns a file object.

    :type name: str | os.PathLike
    :type mode: str
    :type buffering: numbers.Integral
    :type encoding: str | None
    :type errors: str | None
    :rtype: file
    """
    return file()


def ord(c):
    """Return the integer ordinal of a one-character string.

    :type c: bytes | str
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


def print(*objects, sep=' ', end='\n', file=None, flush=False):
    """Print objects to the stream file, separated by sep and followed by end.

    :type sep: str
    :type end: str
    :type flush: bool
    :rtype: None
    """
    pass


class range(object):
    """range object."""

    def __init__(self, start, stop=None, step=None):
        """Create a range object.

        :type start: numbers.Integral
        :type stop: numbers.Integral | None
        :type step: numbers.Integral | None
        :rtype: range[int]
        """
        pass


def repr(object):
    """
    Return the canonical string representation of the object.

    :rtype: str
    """
    return ''


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
        return


def vars(object=None):
    """Without arguments, equivalent to locals(). With an argument, equivalent
    to object.__dict__.

    :rtype: dict[str, unknown]
    """
    return {}


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

    @staticmethod
    def __prepare__(metacls, name, bases):
        """Used to create the namespace for the class statement.

        :rtype: dict[str, unknown]
        """
        return {}


class enumerate(object):
    """enumerate object."""

    def __init__(self, iterable, start=0):
        """Create an enumerate object.

        :type iterable: collections.Iterable[T]
        :type start: int | long
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


class int(object):
    """Integer numeric type."""

    def __init__(self, x=None, base=10):
        """Convert a number or string x to an integer, or return 0 if no
        arguments are given.

        :type x: object
        :type base: numbers.Integral
        """
        pass

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


class bytes(object):
    """Bytes object."""

    def __init__(self, source='', encoding='utf8', errors='strict'):
        """Construct an immutable array of bytes.

        :type source: object
        :type encoding: str
        :type errors: str
        """
        pass

    def __add__(self, y):
        """The concatenation of x and y.

        :type y: bytes
        :rtype: bytes
        """
        return b''

    def __mul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: bytes
        """
        return b''

    def __rmul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: bytes
        """
        return b''

    def __getitem__(self, y):
        """y-th item of x or substring, origin 0.

        :type y: numbers.Integral | slice
        :rtype: int | bytes
        """
        return 0

    def __iter__(self):
        """Iterator over bytes.

        :rtype: collections.Iterator[int]
        """
        return []

    def capitalize(self):
        """Return a copy of the string with its first character capitalized
        and the rest lowercased.

        :rtype: bytes
        """
        return b''

    def center(self, width, fillchar=' '):
        """Return centered in a string of length width.

        :type width: numbers.Integral
        :type fillchar: bytes
        :rtype: bytes
        """
        return b''

    def count(self, sub, start=None, end=None):
        """Return the number of non-overlapping occurrences of substring
        sub in the range [start, end].

        :type sub: bytes
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: int
        """
        return 0

    def decode(self, encoding='utf-8', errors='strict'):
        """Return a string decoded from the given bytes.

        :type encoding: str
        :type errors: str
        :rtype: str
        """
        return ''

    def endswith(self, suffix, start=None, end=None):
        """Return True if the string ends with the specified suffix,
        otherwise return False.

        :type suffix: bytes | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def find(self, sub, start=None, end=None):
        """Return the lowest index in the string where substring sub is
        found, such that sub is contained in the slice s[start:end].

        :type sub: bytes
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def index(self, sub, start=None, end=None):
        """Like find(), but raise ValueError when the substring is not
        found.

        :type sub: bytes
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

        :type iterable: collections.Iterable[bytes]
        :rtype: bytes
        """
        return b''

    def ljust(self, width, fillchar=' '):
        """Return the string left justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: bytes
        :rtype: bytes
        """
        return b''

    def lower(self):
        """Return a copy of the string with all the cased characters
        converted to lowercase.

        :rtype: bytes
        """
        return b''

    def lstrip(self, chars=None):
        """Return a copy of the string with leading characters removed.

        :type chars: bytes | None
        :rtype: bytes
        """
        return b''

    def partition(self, sep):
        """Split the string at the first occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: bytes
        :rtype: (bytes, bytes, bytes)
        """
        return b'', b'', b''

    def replace(self, old, new, count=-1):
        """Return a copy of the string with all occurrences of substring
        old replaced by new.

        :type old: bytes
        :type new: bytes
        :type count: numbers.Integral
        :rtype: bytes
        """
        return b''

    def rfind(self, sub, start=None, end=None):
        """Return the highest index in the string where substring sub is
        found, such that sub is contained within s[start:end].

        :type sub: bytes
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rindex(self, sub, start=None, end=None):
        """Like rfind(), but raise ValueError when the substring is not
        found.

        :type sub: bytes
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rjust(self, width, fillchar=' '):
        """Return the string right justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: bytes
        :rtype: bytes
        """
        return b''

    def rpartition(self, sep):
        """Split the string at the last occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: bytes
        :rtype: (bytes, bytes, bytes)
        """
        return b'', b'', b''

    def rsplit(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: bytes | None
        :type maxsplit: numbers.Integral
        :rtype: list[bytes]
        """
        return []

    def rstrip(self, chars=None):
        """Return a copy of the string with trailing characters removed.

        :type chars: bytes | None
        :rtype: bytes
        """
        return b''

    def split(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: bytes | None
        :type maxsplit: numbers.Integral
        :rtype: list[bytes]
        """
        return []

    def splitlines(self, keepends=False):
        """Return a list of the lines in the string, breaking at line
        boundaries.

        :type keepends: bool
        :rtype: list[bytes]
        """
        return []

    def startswith(self, prefix, start=None, end=None):
        """Return True if string starts with the prefix, otherwise return
        False.

        :type prefix: bytes | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def strip(self, chars=None):
        """Return a copy of the string with the leading and trailing
        characters removed.

        :type chars: bytes | None
        :rtype: bytes
        """
        return b''

    def swapcase(self):
        """Return a copy of the string with uppercase characters converted
        to lowercase and vice versa.

        :rtype: bytes
        """
        return b''

    def title(self):
        """Return a titlecased version of the string where words start with
        an uppercase character and the remaining characters are lowercase.

        :rtype: bytes
        """
        return b''

    def upper(self):
        """Return a copy of the string with all the cased characters
        converted to uppercase.

        :rtype: bytes
        """
        return b''

    def zfill(self, width):
        """Return the numeric string left filled with zeros in a string of
        length width.

        :type width: numbers.Integral
        :rtype: bytes
        """
        return b''


class str(object):
    """String object."""

    def __init__(self, object='', encoding='utf-8', errors='strict'):
        """Construct an immutable string.

        :type object: object
        :type encoding: str
        :type errors: str
        """
        pass

    def __add__(self, y):
        """The concatenation of x and y.

        :type y: str
        :rtype: str
        """
        return ''

    def __mul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: str
        """
        return ''

    def __mod__(self, y):
        """x % y.

        :rtype: str
        """
        return ''

    def __rmul__(self, n):
        """n shallow copies of x concatenated.

        :type n: numbers.Integral
        :rtype: str
        """
        return ''

    def __getitem__(self, y):
        """y-th item of x or substring, origin 0.

        :type y: numbers.Integral | slice
        :rtype: str
        """
        return ''

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
        return ''

    def center(self, width, fillchar=' '):
        """Return centered in a string of length width.

        :type width: numbers.Integral
        :type fillchar: str
        :rtype: str
        """
        return ''

    def count(self, sub, start=None, end=None):
        """Return the number of non-overlapping occurrences of substring
        sub in the range [start, end].

        :type sub: str
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: int
        """
        return 0

    def encode(self, encoding='utf-8', errors='strict'):
        """Return an encoded version of the string as a bytes object.

        :type encoding: str
        :type errors: str
        :rtype: bytes
        """
        return b''

    def endswith(self, suffix, start=None, end=None):
        """Return True if the string ends with the specified suffix,
        otherwise return False.

        :type suffix: str | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def find(self, sub, start=None, end=None):
        """Return the lowest index in the string where substring sub is
        found, such that sub is contained in the slice s[start:end].

        :type sub: str
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def format(self, *args, **kwargs):
        """Perform a string formatting operation.

        :rtype: str
        """
        return ''

    def index(self, sub, start=None, end=None):
        """Like find(), but raise ValueError when the substring is not
        found.

        :type sub: str
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

        :type iterable: collections.Iterable[str]
        :rtype: str
        """
        return ''

    def ljust(self, width, fillchar=' '):
        """Return the string left justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: str
        :rtype: str
        """
        return ''

    def lower(self):
        """Return a copy of the string with all the cased characters
        converted to lowercase.

        :rtype: str
        """
        return ''

    def lstrip(self, chars=None):
        """Return a copy of the string with leading characters removed.

        :type chars: str | None
        :rtype: str
        """
        return ''

    def partition(self, sep):
        """Split the string at the first occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: str
        :rtype: (str, str, str)
        """
        return '', '', ''

    def replace(self, old, new, count=-1):
        """Return a copy of the string with all occurrences of substring
        old replaced by new.

        :type old: str
        :type new: str
        :type count: numbers.Integral
        :rtype: str
        """
        return ''

    def rfind(self, sub, start=None, end=None):
        """Return the highest index in the string where substring sub is
        found, such that sub is contained within s[start:end].

        :type sub: str
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rindex(self, sub, start=None, end=None):
        """Like rfind(), but raise ValueError when the substring is not
        found.

        :type sub: str
        :type start: numbers.Integral | None
        :type end: numbers.Integral | none
        :rtype: int
        """
        return 0

    def rjust(self, width, fillchar=' '):
        """Return the string right justified in a string of length width.
        Padding is done using the specified fillchar (default is a space).

        :type width: numbers.Integral
        :type fillchar: str
        :rtype: str
        """
        return ''

    def rpartition(self, sep):
        """Split the string at the last occurrence of sep, and return a
        3-tuple containing the part before the separator, the separator
        itself, and the part after the separator.

        :type sep: str
        :rtype: (str, str, str)
        """
        return '', '', ''

    def rsplit(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: str | None
        :type maxsplit: numbers.Integral
        :rtype: list[str]
        """
        return []

    def rstrip(self, chars=None):
        """Return a copy of the string with trailing characters removed.

        :type chars: str | None
        :rtype: str
        """
        return ''

    def split(self, sep=None, maxsplit=-1):
        """Return a list of the words in the string, using sep as the
        delimiter string.

        :type sep: str | None
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

        :type prefix: str | tuple
        :type start: numbers.Integral | None
        :type end: numbers.Integral | None
        :rtype: bool
        """
        return False

    def strip(self, chars=None):
        """Return a copy of the string with the leading and trailing
        characters removed.

        :type chars: str | None
        :rtype: str
        """
        return ''

    def swapcase(self):
        """Return a copy of the string with uppercase characters converted
        to lowercase and vice versa.

        :rtype: str
        """
        return ''

    def title(self):
        """Return a titlecased version of the string where words start with
        an uppercase character and the remaining characters are lowercase.

        :rtype: str
        """
        return ''

    def upper(self):
        """Return a copy of the string with all the cased characters
        converted to uppercase.

        :rtype: str
        """
        return ''

    def zfill(self, width):
        """Return the numeric string left filled with zeros in a string of
        length width.

        :type width: numbers.Integral
        :rtype: str
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

    def items(self):
        """Return a copy of the dictionary's list of (key, value) pairs.

        :rtype: collections.Iterable[(T, V)]
        """
        return []

    def keys(self):
        """Return a copy of the dictionary's list of keys.

        :rtype: collections.Iterable[T]
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

    def values():
        """Return a copy of the dictionary's list of values.

        :rtype: collections.Iterable[V]
        """
        return []


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

    def __next__(self):
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


class __coroutine(object):
    """A mock class representing the generator function type."""

    def __init__(self):
        """
        :rtype: __coroutine[V]
        """
        self.__name__ = ''
        self.__qualname__ = ''
        self.cr_await = None
        self.cr_frame = None
        self.cr_running = False
        self.cr_code = None

    def __await__(self):
        """
        :rtype: __generator[unknown, unknown, V]
        """
        return []

    def close(self):
        """
        :rtype: None
        """
        pass

    def send(self, value):
        """
        :rtype: None
        """
        pass

    def throw(self, type, value=None, traceback=None):
        """
        :rtype: None
        """
        pass


class __asyncgenerator(object):
    """A mock class representing the async generator function type."""
    def __init__(self):
        """Create an async generator object.

        :rtype: __asyncgenerator[T, U]
        """
        self.__name__ = ''
        self.__qualname__ = ''
        self.ag_await = None
        self.ag_frame = None
        self.ag_running = False
        self.ag_code = None

    def __aiter__(self):
        """Defined to support iteration over container.

        :rtype: collections.AsyncIterator[T]
        """
        pass

    def __anext__(self):
        """Returns an awaitable, that performs one asynchronous generator
        iteration when awaited.

        :rtype: collections.Awaitable[T]
        """
        pass

    def aclose(self):
        """Returns an awaitable, that throws a GeneratorExit exception
        into generator.

        :rtype: collections.Awaitable[T]
        """
        pass

    def asend(self, value):
        """Returns an awaitable, that pushes the value object in generator.

        :type value: U
        :rtype: collections.Awaitable[T]
        """
        pass

    def athrow(self, type, value=None, traceback=None):
        """Returns an awaitable, that throws an exception into generator.

        :rtype: collections.Awaitable[T]
        """
        pass


class __function(object):
    """A mock class representing function type."""

    def __init__(self):
        self.__name__ = ''
        self.__doc__ = ''
        self.__dict__ = ''
        self.__module__ = ''

        self.__annotations__ = {}
        self.__defaults__ = {}
        self.__globals__ = {}
        self.__kwdefaults__ = {}
        self.__closure__ = None
        self.__code__ = None

        if sys.version_info >= (3, 3):
            self.__qualname__ = ''

class __method(object):
    """A mock class representing bound method type."""

    def __init__(self):
        self.__func__ = None
        self.__self__ = None


def input(prompt=None):
    """
    :type prompt: Any
    :rtype: str
    """
    pass
