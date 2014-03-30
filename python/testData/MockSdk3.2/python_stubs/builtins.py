# encoding: utf-8
# module builtins
# from (built-in)
# by generator 1.118
"""
Built-in functions, exceptions, and other objects.

Noteworthy: None is the `nil' object; Ellipsis represents `...' in slices.
"""
# no imports

# Variables with simple values
# definition of False omitted
# definition of None omitted
# definition of True omitted
# definition of __debug__ omitted

# functions

def abs(number): # real signature unknown; restored from __doc__
    """
    abs(number) -> number
    
    Return the absolute value of the argument.
    """
    return 0


def all(iterable): # real signature unknown; restored from __doc__
    """
    all(iterable) -> bool
    
    Return True if bool(x) is True for all values x in the iterable.
    """
    return False


def any(iterable): # real signature unknown; restored from __doc__
    """
    any(iterable) -> bool
    
    Return True if bool(x) is True for any x in the iterable.
    """
    return False


def ascii(p_object): # real signature unknown; restored from __doc__
    """
    ascii(object) -> string
    
    As repr(), return a string containing a printable representation of an
    object, but escape the non-ASCII characters in the string returned by
    repr() using \x, \u or \U escapes.  This generates a string similar
    to that returned by repr() in Python 2.
    """
    return ""


def bin(number): # real signature unknown; restored from __doc__
    """
    bin(number) -> string
    
    Return the binary representation of an integer.
    """
    return ""


def callable(p_object): # real signature unknown; restored from __doc__
    """
    callable(object) -> bool
    
    Return whether the object is callable (i.e., some kind of function).
    Note that classes are callable, as are instances of classes with a
    __call__() method.
    """
    return False


def chr(i): # real signature unknown; restored from __doc__
    """
    chr(i) -> Unicode character
    
    Return a Unicode string of one character with ordinal i; 0 <= i <= 0x10ffff.
    """
    return ""


def compile(source, filename, mode, flags=None, dont_inherit=None): # real signature unknown; restored from __doc__
    """
    compile(source, filename, mode[, flags[, dont_inherit]]) -> code object
    
    Compile the source string (a Python module, statement or expression)
    into a code object that can be executed by exec() or eval().
    The filename will be used for run-time error messages.
    The mode must be 'exec' to compile a module, 'single' to compile a
    single (interactive) statement, or 'eval' to compile an expression.
    The flags argument, if present, controls which future statements influence
    the compilation of the code.
    The dont_inherit argument, if non-zero, stops the compilation inheriting
    the effects of any future statements in effect in the code calling
    compile; if absent or zero these statements do influence the compilation,
    in addition to any features explicitly specified.
    """
    pass


def copyright(*args, **kwargs): # real signature unknown
    """
    interactive prompt objects for printing the license text, a list of
        contributors and the copyright notice.
    """
    pass


def credits(*args, **kwargs): # real signature unknown
    """
    interactive prompt objects for printing the license text, a list of
        contributors and the copyright notice.
    """
    pass


def delattr(p_object, name): # real signature unknown; restored from __doc__
    """
    delattr(object, name)
    
    Delete a named attribute on an object; delattr(x, 'y') is equivalent to
    ``del x.y''.
    """
    pass


def dir(p_object=None): # real signature unknown; restored from __doc__
    """
    dir([object]) -> list of strings
    
    If called without an argument, return the names in the current scope.
    Else, return an alphabetized list of names comprising (some of) the attributes
    of the given object, and of attributes reachable from it.
    If the object supplies a method named __dir__, it will be used; otherwise
    the default dir() logic is used and returns:
      for a module object: the module's attributes.
      for a class object:  its attributes, and recursively the attributes
        of its bases.
      for any other object: its attributes, its class's attributes, and
        recursively the attributes of its class's base classes.
    """
    return []


def divmod(x, y): # known case of builtins.divmod
    """
    divmod(x, y) -> (div, mod)
    
    Return the tuple ((x-x%y)/y, x%y).  Invariant: div*y + mod == x.
    """
    return (0, 0)


def eval(source, globals=None, locals=None): # real signature unknown; restored from __doc__
    """
    eval(source[, globals[, locals]]) -> value
    
    Evaluate the source in the context of globals and locals.
    The source may be a string representing a Python expression
    or a code object as returned by compile().
    The globals must be a dictionary and locals can be any mapping,
    defaulting to the current globals and locals.
    If only globals is given, locals defaults to it.
    """
    pass


def exec(p_object, globals=None, locals=None): # real signature unknown; restored from __doc__
    """
    exec(object[, globals[, locals]])
    
    Read and execute code from an object, which can be a string or a code
    object.
    The globals and locals are dictionaries, defaulting to the current
    globals and locals.  If only globals is given, locals defaults to it.
    """
    pass


def exit(*args, **kwargs): # real signature unknown
    pass


def format(value, format_spec=None): # real signature unknown; restored from __doc__
    """
    format(value[, format_spec]) -> string
    
    Returns value.__format__(format_spec)
    format_spec defaults to ""
    """
    return ""


def getattr(object, name, default=None): # known special case of getattr
    """
    getattr(object, name[, default]) -> value
    
    Get a named attribute from an object; getattr(x, 'y') is equivalent to x.y.
    When a default argument is given, it is returned when the attribute doesn't
    exist; without it, an exception is raised in that case.
    """
    pass


def globals(): # real signature unknown; restored from __doc__
    """
    globals() -> dictionary
    
    Return the dictionary containing the current scope's global variables.
    """
    return {}


def hasattr(p_object, name): # real signature unknown; restored from __doc__
    """
    hasattr(object, name) -> bool
    
    Return whether the object has an attribute with the given name.
    (This is done by calling getattr(object, name) and catching AttributeError.)
    """
    return False


def hash(p_object): # real signature unknown; restored from __doc__
    """
    hash(object) -> integer
    
    Return a hash value for the object.  Two objects with the same value have
    the same hash value.  The reverse is not necessarily true, but likely.
    """
    return 0


def help(with_a_twist): # real signature unknown; restored from __doc__
    """
    Define the builtin 'help'.
        This is a wrapper around pydoc.help (with a twist).
    """
    pass


def hex(number): # real signature unknown; restored from __doc__
    """
    hex(number) -> string
    
    Return the hexadecimal representation of an integer.
    """
    return ""


def id(p_object): # real signature unknown; restored from __doc__
    """
    id(object) -> integer
    
    Return the identity of an object.  This is guaranteed to be unique among
    simultaneously existing objects.  (Hint: it's the object's memory address.)
    """
    return 0


def input(prompt=None): # real signature unknown; restored from __doc__
    """
    input([prompt]) -> string
    
    Read a string from standard input.  The trailing newline is stripped.
    If the user hits EOF (Unix: Ctl-D, Windows: Ctl-Z+Return), raise EOFError.
    On Unix, GNU readline is used if enabled.  The prompt string, if given,
    is printed without a trailing newline before reading.
    """
    return ""


def isinstance(p_object, class_or_type_or_tuple): # real signature unknown; restored from __doc__
    """
    isinstance(object, class-or-type-or-tuple) -> bool
    
    Return whether an object is an instance of a class or of a subclass thereof.
    With a type as second argument, return whether that is the object's type.
    The form using a tuple, isinstance(x, (A, B, ...)), is a shortcut for
    isinstance(x, A) or isinstance(x, B) or ... (etc.).
    """
    return False


def issubclass(C, B): # real signature unknown; restored from __doc__
    """
    issubclass(C, B) -> bool
    
    Return whether class C is a subclass (i.e., a derived class) of class B.
    When using a tuple as the second argument issubclass(X, (A, B, ...)),
    is a shortcut for issubclass(X, A) or issubclass(X, B) or ... (etc.).
    """
    return False


def iter(source, sentinel=None): # known special case of iter
    """
    iter(iterable) -> iterator
    iter(callable, sentinel) -> iterator
    
    Get an iterator from an object.  In the first form, the argument must
    supply its own iterator, or be a sequence.
    In the second form, the callable is called until it returns the sentinel.
    """
    pass


def len(p_object): # real signature unknown; restored from __doc__
    """
    len(object) -> integer
    
    Return the number of items of a sequence or mapping.
    """
    return 0


def license(*args, **kwargs): # real signature unknown
    """
    interactive prompt objects for printing the license text, a list of
        contributors and the copyright notice.
    """
    pass


def locals(): # real signature unknown; restored from __doc__
    """
    locals() -> dictionary
    
    Update and return a dictionary containing the current scope's local variables.
    """
    return {}


def max(*args, key=None): # known special case of max
    """
    max(iterable[, key=func]) -> value
    max(a, b, c, ...[, key=func]) -> value
    
    With a single iterable argument, return its largest item.
    With two or more arguments, return the largest argument.
    """
    pass


def min(*args, key=None): # known special case of min
    """
    min(iterable[, key=func]) -> value
    min(a, b, c, ...[, key=func]) -> value
    
    With a single iterable argument, return its smallest item.
    With two or more arguments, return the smallest argument.
    """
    pass


def next(iterator, default=None): # real signature unknown; restored from __doc__
    """
    next(iterator[, default])
    
    Return the next item from the iterator. If default is given and the iterator
    is exhausted, it is returned instead of raising StopIteration.
    """
    pass


def oct(number): # real signature unknown; restored from __doc__
    """
    oct(number) -> string
    
    Return the octal representation of an integer.
    """
    return ""


def open(file, mode='r', buffering=None, encoding=None, errors=None, newline=None, closefd=True): # known special case of open
    """
    open(file, mode='r', buffering=-1, encoding=None,
         errors=None, newline=None, closefd=True) -> file object
    
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
    pass


def ord(c): # real signature unknown; restored from __doc__
    """
    ord(c) -> integer
    
    Return the integer ordinal of a one-character string.
    """
    return 0


def pow(x, y, z=None): # real signature unknown; restored from __doc__
    """
    pow(x, y[, z]) -> number
    
    With two arguments, equivalent to x**y.  With three arguments,
    equivalent to (x**y) % z, but may be more efficient (e.g. for longs).
    """
    return 0


def print(*args, sep=' ', end='\n', file=None): # known special case of print
    """
    print(value, ..., sep=' ', end='\n', file=sys.stdout)
    
    Prints the values to a stream, or to sys.stdout by default.
    Optional keyword arguments:
    file: a file-like object (stream); defaults to the current sys.stdout.
    sep:  string inserted between values, default a space.
    end:  string appended after the last value, default a newline.
    """
    pass


def quit(*args, **kwargs): # real signature unknown
    pass


def repr(p_object): # real signature unknown; restored from __doc__
    """
    repr(object) -> string
    
    Return the canonical string representation of the object.
    For most object types, eval(repr(object)) == object.
    """
    return ""


def round(number, ndigits=None): # real signature unknown; restored from __doc__
    """
    round(number[, ndigits]) -> number
    
    Round a number to a given precision in decimal digits (default 0 digits).
    This returns an int when called with one argument, otherwise the
    same type as the number. ndigits may be negative.
    """
    return 0


def setattr(p_object, name, value): # real signature unknown; restored from __doc__
    """
    setattr(object, name, value)
    
    Set a named attribute on an object; setattr(x, 'y', v) is equivalent to
    ``x.y = v''.
    """
    pass


def sorted(iterable, key=None, reverse=False): # real signature unknown; restored from __doc__
    """ sorted(iterable, key=None, reverse=False) --> new sorted list """
    pass


def sum(iterable, start=None): # real signature unknown; restored from __doc__
    """
    sum(iterable[, start]) -> value
    
    Returns the sum of an iterable of numbers (NOT strings) plus the value
    of parameter 'start' (which defaults to 0).  When the iterable is
    empty, returns start.
    """
    pass


def vars(p_object=None): # real signature unknown; restored from __doc__
    """
    vars([object]) -> dictionary
    
    Without arguments, equivalent to locals().
    With an argument, equivalent to object.__dict__.
    """
    return {}


def __build_class__(func, name, *bases, metaclass=None, **kwds): # real signature unknown; restored from __doc__
    """
    __build_class__(func, name, *bases, metaclass=None, **kwds) -> class
    
    Internal helper function used by the class statement.
    """
    pass


def __import__(name, globals={}, locals={}, fromlist=[], level=-1): # real signature unknown; restored from __doc__
    """
    __import__(name, globals={}, locals={}, fromlist=[], level=-1) -> module
    
    Import a module. Because this function is meant for use by the Python
    interpreter and not for general use it is better to use
    importlib.import_module() to programmatically import a module.
    
    The globals argument is only used to determine the context;
    they are not modified.  The locals argument is unused.  The fromlist
    should be a list of names to emulate ``from name import ...'', or an
    empty list to emulate ``import name''.
    When importing a module from a package, note that __import__('A.B', ...)
    returns package A when fromlist is empty, but its submodule B when
    fromlist is not empty.  Level is used to determine whether to perform 
    absolute or relative imports.  -1 is the original strategy of attempting
    both absolute and relative imports, 0 is absolute, a positive number
    is the number of parent directories to search relative to the current module.
    """
    pass


# classes

class object:
    """ The most base type """
    def __delattr__(self, name): # real signature unknown; restored from __doc__
        """ x.__delattr__('name') <==> del x.name """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        """ default object formatter """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self): # known special case of object.__init__
        """ x.__init__(...) initializes x; see help(type(x)) for signature """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(cls, *more): # known special case of object.__new__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __reduce_ex__(self, *args, **kwargs): # real signature unknown
        """ helper for pickle """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ helper for pickle """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __setattr__(self, name, value): # real signature unknown; restored from __doc__
        """ x.__setattr__('name', value) <==> x.name = value """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """
        __sizeof__() -> int
        size of object in memory, in bytes
        """
        return 0

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    @classmethod # known case
    def __subclasshook__(cls, subclass): # known special case of object.__subclasshook__
        """
        Abstract classes can override this to customize issubclass().
        
        This is invoked early on by abc.ABCMeta.__subclasscheck__().
        It should return True, False or NotImplemented.  If it returns
        NotImplemented, the normal algorithm is used.  Otherwise, it
        overrides the normal algorithm (and the outcome is cached).
        """
        pass

    __class__ = None # (!) forward: type, real value is ''
    __dict__ = {}
    __doc__ = ''
    __module__ = ''


class BaseException(object):
    """ Common base class for all exceptions """
    def with_traceback(self, tb): # real signature unknown; restored from __doc__
        """
        Exception.with_traceback(tb) --
            set self.__traceback__ to tb and return self.
        """
        pass

    def __delattr__(self, name): # real signature unknown; restored from __doc__
        """ x.__delattr__('name') <==> del x.name """
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

    def __reduce__(self, *args, **kwargs): # real signature unknown
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __setattr__(self, name, value): # real signature unknown; restored from __doc__
        """ x.__setattr__('name', value) <==> x.name = value """
        pass

    def __setstate__(self, *args, **kwargs): # real signature unknown
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    args = property(lambda self: object()) # default
    __cause__ = property(lambda self: object()) # default
    __context__ = property(lambda self: object()) # default
    __traceback__ = property(lambda self: object()) # default

    __dict__ = None # (!) real value is ''


class Exception(BaseException):
    """ Common base class for all non-exit exceptions. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class ArithmeticError(Exception):
    """ Base class for arithmetic errors. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class AssertionError(Exception):
    """ Assertion failed. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class AttributeError(Exception):
    """ Attribute not found. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class int(object):
    """
    int(x[, base]) -> integer
    
    Convert a string or number to an integer, if possible.  A floating
    point argument will be truncated towards zero (this does not include a
    string representation of a floating point number!)  When converting a
    string, use the optional base.  It is an error to supply a base when
    converting a non-string.
    """
    def bit_length(self): # real signature unknown; restored from __doc__
        """
        int.bit_length() -> int
        
        Number of bits necessary to represent self in binary.
        >>> bin(37)
        '0b100101'
        >>> (37).bit_length()
        6
        """
        return 0

    def conjugate(self, *args, **kwargs): # real signature unknown
        """ Returns self, the complex conjugate of any int. """
        pass

    def from_bytes(self, bytes, byteorder, *args, **kwargs): # real signature unknown; NOTE: unreliably restored from __doc__ 
        """
        int.from_bytes(bytes, byteorder, *, signed=False) -> int
        
        Return the integer represented by the given array of bytes.
        
        The bytes argument must either support the buffer protocol or be an
        iterable object producing bytes.  Bytes and bytearray are examples of
        built-in objects that support the buffer protocol.
        
        The byteorder argument determines the byte order used to represent the
        integer.  If byteorder is 'big', the most significant byte is at the
        beginning of the byte array.  If byteorder is 'little', the most
        significant byte is at the end of the byte array.  To request the native
        byte order of the host system, use `sys.byteorder' as the byte order value.
        
        The signed keyword-only argument indicates whether two's complement is
        used to represent the integer.
        """
        pass

    def to_bytes(self, length, byteorder, *args, **kwargs): # real signature unknown; NOTE: unreliably restored from __doc__ 
        """
        int.to_bytes(length, byteorder, *, signed=False) -> bytes
        
        Return an array of bytes representing an integer.
        
        The integer is represented using length bytes.  An OverflowError is
        raised if the integer is not representable with the given number of
        bytes.
        
        The byteorder argument determines the byte order used to represent the
        integer.  If byteorder is 'big', the most significant byte is at the
        beginning of the byte array.  If byteorder is 'little', the most
        significant byte is at the end of the byte array.  To request the native
        byte order of the host system, use `sys.byteorder' as the byte order value.
        
        The signed keyword-only argument determines whether two's complement is
        used to represent the integer.  If signed is False and a negative integer
        is given, an OverflowError is raised.
        """
        pass

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __and__(self, y): # real signature unknown; restored from __doc__
        """ x.__and__(y) <==> x&y """
        pass

    def __bool__(self): # real signature unknown; restored from __doc__
        """ x.__bool__() <==> x != 0 """
        pass

    def __ceil__(self, *args, **kwargs): # real signature unknown
        """ Ceiling of an Integral returns itself. """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __float__(self): # real signature unknown; restored from __doc__
        """ x.__float__() <==> float(x) """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__floordiv__(y) <==> x//y """
        pass

    def __floor__(self, *args, **kwargs): # real signature unknown
        """ Flooring an Integral returns itself. """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __index__(self): # real signature unknown; restored from __doc__
        """ x[y:z] <==> x[y.__index__():z.__index__()] """
        pass

    def __init__(self, x, base=10): # known special case of int.__init__
        """
        int(x[, base]) -> integer
        
        Convert a string or number to an integer, if possible.  A floating
        point argument will be truncated towards zero (this does not include a
        string representation of a floating point number!)  When converting a
        string, use the optional base.  It is an error to supply a base when
        converting a non-string.
        # (copied from class doc)
        """
        pass

    def __int__(self): # real signature unknown; restored from __doc__
        """ x.__int__() <==> int(x) """
        pass

    def __invert__(self): # real signature unknown; restored from __doc__
        """ x.__invert__() <==> ~x """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lshift__(self, y): # real signature unknown; restored from __doc__
        """ x.__lshift__(y) <==> x<<y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mod__(self, y): # real signature unknown; restored from __doc__
        """ x.__mod__(y) <==> x%y """
        pass

    def __mul__(self, y): # real signature unknown; restored from __doc__
        """ x.__mul__(y) <==> x*y """
        pass

    def __neg__(self): # real signature unknown; restored from __doc__
        """ x.__neg__() <==> -x """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __or__(self, y): # real signature unknown; restored from __doc__
        """ x.__or__(y) <==> x|y """
        pass

    def __pos__(self): # real signature unknown; restored from __doc__
        """ x.__pos__() <==> +x """
        pass

    def __pow__(self, y, z=None): # real signature unknown; restored from __doc__
        """ x.__pow__(y[, z]) <==> pow(x, y[, z]) """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """ x.__radd__(y) <==> y+x """
        pass

    def __rand__(self, y): # real signature unknown; restored from __doc__
        """ x.__rand__(y) <==> y&x """
        pass

    def __rdivmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdivmod__(y) <==> divmod(y, x) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rfloordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rfloordiv__(y) <==> y//x """
        pass

    def __rlshift__(self, y): # real signature unknown; restored from __doc__
        """ x.__rlshift__(y) <==> y<<x """
        pass

    def __rmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmod__(y) <==> y%x """
        pass

    def __rmul__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmul__(y) <==> y*x """
        pass

    def __ror__(self, y): # real signature unknown; restored from __doc__
        """ x.__ror__(y) <==> y|x """
        pass

    def __round__(self, *args, **kwargs): # real signature unknown
        """
        Rounding an Integral returns itself.
        Rounding with an ndigits argument also returns an integer.
        """
        pass

    def __rpow__(self, x, z=None): # real signature unknown; restored from __doc__
        """ y.__rpow__(x[, z]) <==> pow(x, y[, z]) """
        pass

    def __rrshift__(self, y): # real signature unknown; restored from __doc__
        """ x.__rrshift__(y) <==> y>>x """
        pass

    def __rshift__(self, y): # real signature unknown; restored from __doc__
        """ x.__rshift__(y) <==> x>>y """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __rtruediv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rtruediv__(y) <==> y/x """
        pass

    def __rxor__(self, y): # real signature unknown; restored from __doc__
        """ x.__rxor__(y) <==> y^x """
        pass

    def __sizeof__(self, *args, **kwargs): # real signature unknown
        """ Returns size in memory, in bytes """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    def __truediv__(self, y): # real signature unknown; restored from __doc__
        """ x.__truediv__(y) <==> x/y """
        pass

    def __trunc__(self, *args, **kwargs): # real signature unknown
        """ Truncating an Integral returns itself. """
        pass

    def __xor__(self, y): # real signature unknown; restored from __doc__
        """ x.__xor__(y) <==> x^y """
        pass

    denominator = property(lambda self: object()) # default
    imag = property(lambda self: object()) # default
    numerator = property(lambda self: object()) # default
    real = property(lambda self: object()) # default


class bool(int):
    """
    bool(x) -> bool
    
    Returns True when the argument x is true, False otherwise.
    The builtins True and False are the only two instances of the class bool.
    The class bool is a subclass of the class int, and cannot be subclassed.
    """
    def __and__(self, y): # real signature unknown; restored from __doc__
        """ x.__and__(y) <==> x&y """
        pass

    def __init__(self, x): # real signature unknown; restored from __doc__
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __or__(self, y): # real signature unknown; restored from __doc__
        """ x.__or__(y) <==> x|y """
        pass

    def __rand__(self, y): # real signature unknown; restored from __doc__
        """ x.__rand__(y) <==> y&x """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __ror__(self, y): # real signature unknown; restored from __doc__
        """ x.__ror__(y) <==> y|x """
        pass

    def __rxor__(self, y): # real signature unknown; restored from __doc__
        """ x.__rxor__(y) <==> y^x """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __xor__(self, y): # real signature unknown; restored from __doc__
        """ x.__xor__(y) <==> x^y """
        pass


class BufferError(Exception):
    """ Buffer error. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class bytearray(object):
    """
    bytearray(iterable_of_ints) -> bytearray
    bytearray(string, encoding[, errors]) -> bytearray
    bytearray(bytes_or_bytearray) -> mutable copy of bytes_or_bytearray
    bytearray(memory_view) -> bytearray
    
    Construct an mutable bytearray object from:
      - an iterable yielding integers in range(256)
      - a text string encoded using the specified encoding
      - a bytes or a bytearray object
      - any object implementing the buffer API.
    
    bytearray(int) -> bytearray
    
    Construct a zero-initialized bytearray of the given length.
    """
    def append(self, p_int): # real signature unknown; restored from __doc__
        """
        B.append(int) -> None
        
        Append a single item to the end of B.
        """
        pass

    def capitalize(self): # real signature unknown; restored from __doc__
        """
        B.capitalize() -> copy of B
        
        Return a copy of B with only its first character capitalized (ASCII)
        and the rest lower-cased.
        """
        pass

    def center(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        B.center(width[, fillchar]) -> copy of B
        
        Return B centered in a string of length width.  Padding is
        done using the specified fill character (default is a space).
        """
        pass

    def count(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.count(sub[, start[, end]]) -> int
        
        Return the number of non-overlapping occurrences of subsection sub in
        bytes B[start:end].  Optional arguments start and end are interpreted
        as in slice notation.
        """
        return 0

    def decode(self, encoding='utf-8', errors='strict'): # real signature unknown; restored from __doc__
        """
        B.decode(encoding='utf-8', errors='strict') -> str
        
        Decode B using the codec registered for encoding. Default encoding
        is 'utf-8'. errors may be given to set a different error
        handling scheme.  Default is 'strict' meaning that encoding errors raise
        a UnicodeDecodeError.  Other possible values are 'ignore' and 'replace'
        as well as any other name registered with codecs.register_error that is
        able to handle UnicodeDecodeErrors.
        """
        return ""

    def endswith(self, suffix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.endswith(suffix[, start[, end]]) -> bool
        
        Return True if B ends with the specified suffix, False otherwise.
        With optional start, test B beginning at that position.
        With optional end, stop comparing B at that position.
        suffix can also be a tuple of bytes to try.
        """
        return False

    def expandtabs(self, tabsize=None): # real signature unknown; restored from __doc__
        """
        B.expandtabs([tabsize]) -> copy of B
        
        Return a copy of B where all tab characters are expanded using spaces.
        If tabsize is not given, a tab size of 8 characters is assumed.
        """
        pass

    def extend(self, iterable_of_ints): # real signature unknown; restored from __doc__
        """
        B.extend(iterable_of_ints) -> None
        
        Append all the elements from the iterator or sequence to the
        end of B.
        """
        pass

    def find(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.find(sub[, start[, end]]) -> int
        
        Return the lowest index in B where subsection sub is found,
        such that sub is contained within B[start,end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    @classmethod # known case
    def fromhex(cls, string): # real signature unknown; restored from __doc__
        """
        bytearray.fromhex(string) -> bytearray (static method)
        
        Create a bytearray object from a string of hexadecimal numbers.
        Spaces between two numbers are accepted.
        Example: bytearray.fromhex('B9 01EF') -> bytearray(b'\xb9\x01\xef').
        """
        return bytearray

    def index(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.index(sub[, start[, end]]) -> int
        
        Like B.find() but raise ValueError when the subsection is not found.
        """
        return 0

    def insert(self, index, p_int): # real signature unknown; restored from __doc__
        """
        B.insert(index, int) -> None
        
        Insert a single item into the bytearray before the given index.
        """
        pass

    def isalnum(self): # real signature unknown; restored from __doc__
        """
        B.isalnum() -> bool
        
        Return True if all characters in B are alphanumeric
        and there is at least one character in B, False otherwise.
        """
        return False

    def isalpha(self): # real signature unknown; restored from __doc__
        """
        B.isalpha() -> bool
        
        Return True if all characters in B are alphabetic
        and there is at least one character in B, False otherwise.
        """
        return False

    def isdigit(self): # real signature unknown; restored from __doc__
        """
        B.isdigit() -> bool
        
        Return True if all characters in B are digits
        and there is at least one character in B, False otherwise.
        """
        return False

    def islower(self): # real signature unknown; restored from __doc__
        """
        B.islower() -> bool
        
        Return True if all cased characters in B are lowercase and there is
        at least one cased character in B, False otherwise.
        """
        return False

    def isspace(self): # real signature unknown; restored from __doc__
        """
        B.isspace() -> bool
        
        Return True if all characters in B are whitespace
        and there is at least one character in B, False otherwise.
        """
        return False

    def istitle(self): # real signature unknown; restored from __doc__
        """
        B.istitle() -> bool
        
        Return True if B is a titlecased string and there is at least one
        character in B, i.e. uppercase characters may only follow uncased
        characters and lowercase characters only cased ones. Return False
        otherwise.
        """
        return False

    def isupper(self): # real signature unknown; restored from __doc__
        """
        B.isupper() -> bool
        
        Return True if all cased characters in B are uppercase and there is
        at least one cased character in B, False otherwise.
        """
        return False

    def join(self, iterable_of_bytes): # real signature unknown; restored from __doc__
        """
        B.join(iterable_of_bytes) -> bytearray
        
        Concatenate any number of bytes/bytearray objects, with B
        in between each pair, and return the result as a new bytearray.
        """
        return bytearray

    def ljust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        B.ljust(width[, fillchar]) -> copy of B
        
        Return B left justified in a string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        pass

    def lower(self): # real signature unknown; restored from __doc__
        """
        B.lower() -> copy of B
        
        Return a copy of B with all ASCII characters converted to lowercase.
        """
        pass

    def lstrip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.lstrip([bytes]) -> bytearray
        
        Strip leading bytes contained in the argument
        and return the result as a new bytearray.
        If the argument is omitted, strip leading ASCII whitespace.
        """
        return bytearray

    @staticmethod # known case
    def maketrans(frm, to): # real signature unknown; restored from __doc__
        """
        B.maketrans(frm, to) -> translation table
        
        Return a translation table (a bytes object of length 256) suitable
        for use in the bytes or bytearray translate method where each byte
        in frm is mapped to the byte at the same position in to.
        The bytes objects frm and to must be of the same length.
        """
        pass

    def partition(self, sep): # real signature unknown; restored from __doc__
        """
        B.partition(sep) -> (head, sep, tail)
        
        Search for the separator sep in B, and return the part before it,
        the separator itself, and the part after it.  If the separator is not
        found, returns B and two empty bytearray objects.
        """
        pass

    def pop(self, index=None): # real signature unknown; restored from __doc__
        """
        B.pop([index]) -> int
        
        Remove and return a single item from B. If no index
        argument is given, will pop the last value.
        """
        return 0

    def remove(self, p_int): # real signature unknown; restored from __doc__
        """
        B.remove(int) -> None
        
        Remove the first occurrence of a value in B.
        """
        pass

    def replace(self, old, new, count=None): # real signature unknown; restored from __doc__
        """
        B.replace(old, new[, count]) -> bytearray
        
        Return a copy of B with all occurrences of subsection
        old replaced by new.  If the optional argument count is
        given, only the first count occurrences are replaced.
        """
        return bytearray

    def reverse(self): # real signature unknown; restored from __doc__
        """
        B.reverse() -> None
        
        Reverse the order of the values in B in place.
        """
        pass

    def rfind(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.rfind(sub[, start[, end]]) -> int
        
        Return the highest index in B where subsection sub is found,
        such that sub is contained within B[start,end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def rindex(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.rindex(sub[, start[, end]]) -> int
        
        Like B.rfind() but raise ValueError when the subsection is not found.
        """
        return 0

    def rjust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        B.rjust(width[, fillchar]) -> copy of B
        
        Return B right justified in a string of length width. Padding is
        done using the specified fill character (default is a space)
        """
        pass

    def rpartition(self, sep): # real signature unknown; restored from __doc__
        """
        B.rpartition(sep) -> (head, sep, tail)
        
        Search for the separator sep in B, starting at the end of B,
        and return the part before it, the separator itself, and the
        part after it.  If the separator is not found, returns two empty
        bytearray objects and B.
        """
        pass

    def rsplit(self, sep, maxsplit=None): # real signature unknown; restored from __doc__
        """
        B.rsplit(sep[, maxsplit]) -> list of bytearrays
        
        Return a list of the sections in B, using sep as the delimiter,
        starting at the end of B and working to the front.
        If sep is not given, B is split on ASCII whitespace characters
        (space, tab, return, newline, formfeed, vertical tab).
        If maxsplit is given, at most maxsplit splits are done.
        """
        return []

    def rstrip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.rstrip([bytes]) -> bytearray
        
        Strip trailing bytes contained in the argument
        and return the result as a new bytearray.
        If the argument is omitted, strip trailing ASCII whitespace.
        """
        return bytearray

    def split(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        B.split([sep[, maxsplit]]) -> list of bytearrays
        
        Return a list of the sections in B, using sep as the delimiter.
        If sep is not given, B is split on ASCII whitespace characters
        (space, tab, return, newline, formfeed, vertical tab).
        If maxsplit is given, at most maxsplit splits are done.
        """
        return []

    def splitlines(self, keepends=None): # real signature unknown; restored from __doc__
        """
        B.splitlines([keepends]) -> list of lines
        
        Return a list of the lines in B, breaking at line boundaries.
        Line breaks are not included in the resulting list unless keepends
        is given and true.
        """
        return []

    def startswith(self, prefix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.startswith(prefix[, start[, end]]) -> bool
        
        Return True if B starts with the specified prefix, False otherwise.
        With optional start, test B beginning at that position.
        With optional end, stop comparing B at that position.
        prefix can also be a tuple of bytes to try.
        """
        return False

    def strip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.strip([bytes]) -> bytearray
        
        Strip leading and trailing bytes contained in the argument
        and return the result as a new bytearray.
        If the argument is omitted, strip ASCII whitespace.
        """
        return bytearray

    def swapcase(self): # real signature unknown; restored from __doc__
        """
        B.swapcase() -> copy of B
        
        Return a copy of B with uppercase ASCII characters converted
        to lowercase ASCII and vice versa.
        """
        pass

    def title(self): # real signature unknown; restored from __doc__
        """
        B.title() -> copy of B
        
        Return a titlecased version of B, i.e. ASCII words start with uppercase
        characters, all remaining cased characters have lowercase.
        """
        pass

    def translate(self, table, deletechars=None): # real signature unknown; restored from __doc__
        """
        B.translate(table[, deletechars]) -> bytearray
        
        Return a copy of B, where all characters occurring in the
        optional argument deletechars are removed, and the remaining
        characters have been mapped through the given translation
        table, which must be a bytes object of length 256.
        """
        return bytearray

    def upper(self): # real signature unknown; restored from __doc__
        """
        B.upper() -> copy of B
        
        Return a copy of B with all ASCII characters converted to uppercase.
        """
        pass

    def zfill(self, width): # real signature unknown; restored from __doc__
        """
        B.zfill(width) -> copy of B
        
        Pad a numeric string B with zeros on the left, to fill a field
        of the specified width.  B is never truncated.
        """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __alloc__(self): # real signature unknown; restored from __doc__
        """
        B.__alloc__() -> int
        
        Return the number of bytes actually allocated.
        """
        return 0

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x """
        pass

    def __delitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__delitem__(y) <==> del x[y] """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __iadd__(self, y): # real signature unknown; restored from __doc__
        """ x.__iadd__(y) <==> x+=y """
        pass

    def __imul__(self, y): # real signature unknown; restored from __doc__
        """ x.__imul__(y) <==> x*=y """
        pass

    def __init__(self, source=None, encoding=None, errors='strict'): # known special case of bytearray.__init__
        """
        bytearray(iterable_of_ints) -> bytearray
        bytearray(string, encoding[, errors]) -> bytearray
        bytearray(bytes_or_bytearray) -> mutable copy of bytes_or_bytearray
        bytearray(memory_view) -> bytearray
        
        Construct an mutable bytearray object from:
          - an iterable yielding integers in range(256)
          - a text string encoded using the specified encoding
          - a bytes or a bytearray object
          - any object implementing the buffer API.
        
        bytearray(int) -> bytearray
        
        Construct a zero-initialized bytearray of the given length.
        # (copied from class doc)
        """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mul__(self, n): # real signature unknown; restored from __doc__
        """ x.__mul__(n) <==> x*n """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ Return state information for pickling. """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rmul__(self, n): # real signature unknown; restored from __doc__
        """ x.__rmul__(n) <==> n*x """
        pass

    def __setitem__(self, i, y): # real signature unknown; restored from __doc__
        """ x.__setitem__(i, y) <==> x[i]=y """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """
        B.__sizeof__() -> int
         
        Returns the size of B in memory, in bytes
        """
        return 0

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    __hash__ = None


class bytes(object):
    """
    bytes(iterable_of_ints) -> bytes
    bytes(string, encoding[, errors]) -> bytes
    bytes(bytes_or_buffer) -> immutable copy of bytes_or_buffer
    bytes(memory_view) -> bytes
    
    Construct an immutable array of bytes from:
      - an iterable yielding integers in range(256)
      - a text string encoded using the specified encoding
      - a bytes or a buffer object
      - any object implementing the buffer API.
    """
    def capitalize(self): # real signature unknown; restored from __doc__
        """
        B.capitalize() -> copy of B
        
        Return a copy of B with only its first character capitalized (ASCII)
        and the rest lower-cased.
        """
        pass

    def center(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        B.center(width[, fillchar]) -> copy of B
        
        Return B centered in a string of length width.  Padding is
        done using the specified fill character (default is a space).
        """
        pass

    def count(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.count(sub[, start[, end]]) -> int
        
        Return the number of non-overlapping occurrences of substring sub in
        string B[start:end].  Optional arguments start and end are interpreted
        as in slice notation.
        """
        return 0

    def decode(self, encoding='utf-8', errors='strict'): # real signature unknown; restored from __doc__
        """
        B.decode(encoding='utf-8', errors='strict') -> str
        
        Decode B using the codec registered for encoding. Default encoding
        is 'utf-8'. errors may be given to set a different error
        handling scheme.  Default is 'strict' meaning that encoding errors raise
        a UnicodeDecodeError.  Other possible values are 'ignore' and 'replace'
        as well as any other name registerd with codecs.register_error that is
        able to handle UnicodeDecodeErrors.
        """
        return ""

    def endswith(self, suffix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.endswith(suffix[, start[, end]]) -> bool
        
        Return True if B ends with the specified suffix, False otherwise.
        With optional start, test B beginning at that position.
        With optional end, stop comparing B at that position.
        suffix can also be a tuple of bytes to try.
        """
        return False

    def expandtabs(self, tabsize=None): # real signature unknown; restored from __doc__
        """
        B.expandtabs([tabsize]) -> copy of B
        
        Return a copy of B where all tab characters are expanded using spaces.
        If tabsize is not given, a tab size of 8 characters is assumed.
        """
        pass

    def find(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.find(sub[, start[, end]]) -> int
        
        Return the lowest index in B where substring sub is found,
        such that sub is contained within B[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    @classmethod # known case
    def fromhex(cls, string): # real signature unknown; restored from __doc__
        """
        bytes.fromhex(string) -> bytes
        
        Create a bytes object from a string of hexadecimal numbers.
        Spaces between two numbers are accepted.
        Example: bytes.fromhex('B9 01EF') -> b'\xb9\x01\xef'.
        """
        return b""

    def index(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.index(sub[, start[, end]]) -> int
        
        Like B.find() but raise ValueError when the substring is not found.
        """
        return 0

    def isalnum(self): # real signature unknown; restored from __doc__
        """
        B.isalnum() -> bool
        
        Return True if all characters in B are alphanumeric
        and there is at least one character in B, False otherwise.
        """
        return False

    def isalpha(self): # real signature unknown; restored from __doc__
        """
        B.isalpha() -> bool
        
        Return True if all characters in B are alphabetic
        and there is at least one character in B, False otherwise.
        """
        return False

    def isdigit(self): # real signature unknown; restored from __doc__
        """
        B.isdigit() -> bool
        
        Return True if all characters in B are digits
        and there is at least one character in B, False otherwise.
        """
        return False

    def islower(self): # real signature unknown; restored from __doc__
        """
        B.islower() -> bool
        
        Return True if all cased characters in B are lowercase and there is
        at least one cased character in B, False otherwise.
        """
        return False

    def isspace(self): # real signature unknown; restored from __doc__
        """
        B.isspace() -> bool
        
        Return True if all characters in B are whitespace
        and there is at least one character in B, False otherwise.
        """
        return False

    def istitle(self): # real signature unknown; restored from __doc__
        """
        B.istitle() -> bool
        
        Return True if B is a titlecased string and there is at least one
        character in B, i.e. uppercase characters may only follow uncased
        characters and lowercase characters only cased ones. Return False
        otherwise.
        """
        return False

    def isupper(self): # real signature unknown; restored from __doc__
        """
        B.isupper() -> bool
        
        Return True if all cased characters in B are uppercase and there is
        at least one cased character in B, False otherwise.
        """
        return False

    def join(self, iterable_of_bytes): # real signature unknown; restored from __doc__
        """
        B.join(iterable_of_bytes) -> bytes
        
        Concatenate any number of bytes objects, with B in between each pair.
        Example: b'.'.join([b'ab', b'pq', b'rs']) -> b'ab.pq.rs'.
        """
        return b""

    def ljust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        B.ljust(width[, fillchar]) -> copy of B
        
        Return B left justified in a string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        pass

    def lower(self): # real signature unknown; restored from __doc__
        """
        B.lower() -> copy of B
        
        Return a copy of B with all ASCII characters converted to lowercase.
        """
        pass

    def lstrip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.lstrip([bytes]) -> bytes
        
        Strip leading bytes contained in the argument.
        If the argument is omitted, strip leading ASCII whitespace.
        """
        return b""

    @staticmethod # known case
    def maketrans(frm, to): # real signature unknown; restored from __doc__
        """
        B.maketrans(frm, to) -> translation table
        
        Return a translation table (a bytes object of length 256) suitable
        for use in the bytes or bytearray translate method where each byte
        in frm is mapped to the byte at the same position in to.
        The bytes objects frm and to must be of the same length.
        """
        pass

    def partition(self, sep): # real signature unknown; restored from __doc__
        """
        B.partition(sep) -> (head, sep, tail)
        
        Search for the separator sep in B, and return the part before it,
        the separator itself, and the part after it.  If the separator is not
        found, returns B and two empty bytes objects.
        """
        pass

    def replace(self, old, new, count=None): # real signature unknown; restored from __doc__
        """
        B.replace(old, new[, count]) -> bytes
        
        Return a copy of B with all occurrences of subsection
        old replaced by new.  If the optional argument count is
        given, only first count occurances are replaced.
        """
        return b""

    def rfind(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.rfind(sub[, start[, end]]) -> int
        
        Return the highest index in B where substring sub is found,
        such that sub is contained within B[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def rindex(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.rindex(sub[, start[, end]]) -> int
        
        Like B.rfind() but raise ValueError when the substring is not found.
        """
        return 0

    def rjust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        B.rjust(width[, fillchar]) -> copy of B
        
        Return B right justified in a string of length width. Padding is
        done using the specified fill character (default is a space)
        """
        pass

    def rpartition(self, sep): # real signature unknown; restored from __doc__
        """
        B.rpartition(sep) -> (head, sep, tail)
        
        Search for the separator sep in B, starting at the end of B,
        and return the part before it, the separator itself, and the
        part after it.  If the separator is not found, returns two empty
        bytes objects and B.
        """
        pass

    def rsplit(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        B.rsplit([sep[, maxsplit]]) -> list of bytes
        
        Return a list of the sections in B, using sep as the delimiter,
        starting at the end of B and working to the front.
        If sep is not given, B is split on ASCII whitespace characters
        (space, tab, return, newline, formfeed, vertical tab).
        If maxsplit is given, at most maxsplit splits are done.
        """
        return []

    def rstrip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.rstrip([bytes]) -> bytes
        
        Strip trailing bytes contained in the argument.
        If the argument is omitted, strip trailing ASCII whitespace.
        """
        return b""

    def split(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        B.split([sep[, maxsplit]]) -> list of bytes
        
        Return a list of the sections in B, using sep as the delimiter.
        If sep is not specified or is None, B is split on ASCII whitespace
        characters (space, tab, return, newline, formfeed, vertical tab).
        If maxsplit is given, at most maxsplit splits are done.
        """
        return []

    def splitlines(self, keepends=None): # real signature unknown; restored from __doc__
        """
        B.splitlines([keepends]) -> list of lines
        
        Return a list of the lines in B, breaking at line boundaries.
        Line breaks are not included in the resulting list unless keepends
        is given and true.
        """
        return []

    def startswith(self, prefix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.startswith(prefix[, start[, end]]) -> bool
        
        Return True if B starts with the specified prefix, False otherwise.
        With optional start, test B beginning at that position.
        With optional end, stop comparing B at that position.
        prefix can also be a tuple of bytes to try.
        """
        return False

    def strip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.strip([bytes]) -> bytes
        
        Strip leading and trailing bytes contained in the argument.
        If the argument is omitted, strip trailing ASCII whitespace.
        """
        return b""

    def swapcase(self): # real signature unknown; restored from __doc__
        """
        B.swapcase() -> copy of B
        
        Return a copy of B with uppercase ASCII characters converted
        to lowercase ASCII and vice versa.
        """
        pass

    def title(self): # real signature unknown; restored from __doc__
        """
        B.title() -> copy of B
        
        Return a titlecased version of B, i.e. ASCII words start with uppercase
        characters, all remaining cased characters have lowercase.
        """
        pass

    def translate(self, table, deletechars=None): # real signature unknown; restored from __doc__
        """
        B.translate(table[, deletechars]) -> bytes
        
        Return a copy of B, where all characters occurring in the
        optional argument deletechars are removed, and the remaining
        characters have been mapped through the given translation
        table, which must be a bytes object of length 256.
        """
        return b""

    def upper(self): # real signature unknown; restored from __doc__
        """
        B.upper() -> copy of B
        
        Return a copy of B with all ASCII characters converted to uppercase.
        """
        pass

    def zfill(self, width): # real signature unknown; restored from __doc__
        """
        B.zfill(width) -> copy of B
        
        Pad a numeric string B with zeros on the left, to fill a field
        of the specified width.  B is never truncated.
        """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, value=b'', encoding=None, errors='strict'): # known special case of bytes.__init__
        """
        bytes(iterable_of_ints) -> bytes
        bytes(string, encoding[, errors]) -> bytes
        bytes(bytes_or_buffer) -> immutable copy of bytes_or_buffer
        bytes(memory_view) -> bytes
        
        Construct an immutable array of bytes from:
          - an iterable yielding integers in range(256)
          - a text string encoded using the specified encoding
          - a bytes or a buffer object
          - any object implementing the buffer API.
        # (copied from class doc)
        """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mul__(self, n): # real signature unknown; restored from __doc__
        """ x.__mul__(n) <==> x*n """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rmul__(self, n): # real signature unknown; restored from __doc__
        """ x.__rmul__(n) <==> n*x """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ B.__sizeof__() -> size of B in memory, in bytes """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass


class Warning(Exception):
    """ Base class for warning categories. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class BytesWarning(Warning):
    """
    Base class for warnings about bytes and buffer related problems, mostly
    related to conversion from str or comparing to str.
    """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class classmethod(object):
    """
    classmethod(function) -> method
    
    Convert a function to be a class method.
    
    A class method receives the class as implicit first argument,
    just like an instance method receives the instance.
    To declare a class method, use this idiom:
    
      class C:
          def f(cls, arg1, arg2, ...): ...
          f = classmethod(f)
    
    It can be called either on the class (e.g. C.f()) or on an instance
    (e.g. C().f()).  The instance is ignored except for its class.
    If a class method is called for a derived class, the derived class
    object is passed as the implied first argument.
    
    Class methods are different than C++ or Java static methods.
    If you want those, see the staticmethod builtin.
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __get__(self, obj, type=None): # real signature unknown; restored from __doc__
        """ descr.__get__(obj[, type]) -> value """
        pass

    def __init__(self, function): # real signature unknown; restored from __doc__
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    __func__ = property(lambda self: object()) # default


class complex(object):
    """
    complex(real[, imag]) -> complex number
    
    Create a complex number from a real part and an optional imaginary part.
    This is equivalent to (real + imag*1j) where imag defaults to 0.
    """
    def conjugate(self): # real signature unknown; restored from __doc__
        """
        complex.conjugate() -> complex
        
        Returns the complex conjugate of its argument. (3-4j).conjugate() == 3+4j.
        """
        return complex

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __bool__(self): # real signature unknown; restored from __doc__
        """ x.__bool__() <==> x != 0 """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __float__(self): # real signature unknown; restored from __doc__
        """ x.__float__() <==> float(x) """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__floordiv__(y) <==> x//y """
        pass

    def __format__(self): # real signature unknown; restored from __doc__
        """
        complex.__format__() -> str
        
        Converts to a string according to format_spec.
        """
        return ""

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, real, imag=None): # real signature unknown; restored from __doc__
        pass

    def __int__(self): # real signature unknown; restored from __doc__
        """ x.__int__() <==> int(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mod__(self, y): # real signature unknown; restored from __doc__
        """ x.__mod__(y) <==> x%y """
        pass

    def __mul__(self, y): # real signature unknown; restored from __doc__
        """ x.__mul__(y) <==> x*y """
        pass

    def __neg__(self): # real signature unknown; restored from __doc__
        """ x.__neg__() <==> -x """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __pos__(self): # real signature unknown; restored from __doc__
        """ x.__pos__() <==> +x """
        pass

    def __pow__(self, y, z=None): # real signature unknown; restored from __doc__
        """ x.__pow__(y[, z]) <==> pow(x, y[, z]) """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """ x.__radd__(y) <==> y+x """
        pass

    def __rdivmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdivmod__(y) <==> divmod(y, x) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rfloordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rfloordiv__(y) <==> y//x """
        pass

    def __rmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmod__(y) <==> y%x """
        pass

    def __rmul__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmul__(y) <==> y*x """
        pass

    def __rpow__(self, x, z=None): # real signature unknown; restored from __doc__
        """ y.__rpow__(x[, z]) <==> pow(x, y[, z]) """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __rtruediv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rtruediv__(y) <==> y/x """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    def __truediv__(self, y): # real signature unknown; restored from __doc__
        """ x.__truediv__(y) <==> x/y """
        pass

    imag = property(lambda self: 0.0)
    real = property(lambda self: 0.0)


class DeprecationWarning(Warning):
    """ Base class for warnings about deprecated features. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class dict(object):
    """
    dict() -> new empty dictionary
    dict(mapping) -> new dictionary initialized from a mapping object's
        (key, value) pairs
    dict(iterable) -> new dictionary initialized as if via:
        d = {}
        for k, v in iterable:
            d[k] = v
    dict(**kwargs) -> new dictionary initialized with the name=value pairs
        in the keyword argument list.  For example:  dict(one=1, two=2)
    """
    def clear(self): # real signature unknown; restored from __doc__
        """ D.clear() -> None.  Remove all items from D. """
        pass

    def copy(self): # real signature unknown; restored from __doc__
        """ D.copy() -> a shallow copy of D """
        pass

    @staticmethod # known case
    def fromkeys(S, v=None): # real signature unknown; restored from __doc__
        """
        dict.fromkeys(S[,v]) -> New dict with keys from S and values equal to v.
        v defaults to None.
        """
        pass

    def get(self, k, d=None): # real signature unknown; restored from __doc__
        """ D.get(k[,d]) -> D[k] if k in D, else d.  d defaults to None. """
        pass

    def items(self): # real signature unknown; restored from __doc__
        """ D.items() -> a set-like object providing a view on D's items """
        pass

    def keys(self): # real signature unknown; restored from __doc__
        """ D.keys() -> a set-like object providing a view on D's keys """
        pass

    def pop(self, k, d=None): # real signature unknown; restored from __doc__
        """
        D.pop(k[,d]) -> v, remove specified key and return the corresponding value.
        If key is not found, d is returned if given, otherwise KeyError is raised
        """
        pass

    def popitem(self): # real signature unknown; restored from __doc__
        """
        D.popitem() -> (k, v), remove and return some (key, value) pair as a
        2-tuple; but raise KeyError if D is empty.
        """
        pass

    def setdefault(self, k, d=None): # real signature unknown; restored from __doc__
        """ D.setdefault(k[,d]) -> D.get(k,d), also set D[k]=d if k not in D """
        pass

    def update(self, E=None, **F): # known special case of dict.update
        """
        D.update(E, **F) -> None.  Update D from dict/iterable E and F.
        If E has a .keys() method, does:     for k in E: D[k] = E[k]
        If E lacks .keys() method, does:     for (k, v) in E: D[k] = v
        In either case, this is followed by: for k in F: D[k] = F[k]
        """
        pass

    def values(self): # real signature unknown; restored from __doc__
        """ D.values() -> an object providing a view on D's values """
        pass

    def __contains__(self, k): # real signature unknown; restored from __doc__
        """ D.__contains__(k) -> True if D has a key k, else False """
        return False

    def __delitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__delitem__(y) <==> del x[y] """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __init__(self, seq=None, **kwargs): # known special case of dict.__init__
        """
        dict() -> new empty dictionary
        dict(mapping) -> new dictionary initialized from a mapping object's
            (key, value) pairs
        dict(iterable) -> new dictionary initialized as if via:
            d = {}
            for k, v in iterable:
                d[k] = v
        dict(**kwargs) -> new dictionary initialized with the name=value pairs
            in the keyword argument list.  For example:  dict(one=1, two=2)
        # (copied from class doc)
        """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __setitem__(self, i, y): # real signature unknown; restored from __doc__
        """ x.__setitem__(i, y) <==> x[i]=y """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ D.__sizeof__() -> size of D in memory, in bytes """
        pass

    __hash__ = None


class enumerate(object):
    """
    enumerate(iterable[, start]) -> iterator for index, value of iterable
    
    Return an enumerate object.  iterable must be another object that supports
    iteration.  The enumerate object yields pairs containing a count (from
    start, which defaults to zero) and a value yielded by the iterable argument.
    enumerate is useful for obtaining an indexed list:
        (0, seq[0]), (1, seq[1]), (2, seq[2]), ...
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, iterable, start=0): # known special case of enumerate.__init__
        """ x.__init__(...) initializes x; see help(type(x)) for signature """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __next__(self): # real signature unknown; restored from __doc__
        """ x.__next__() <==> next(x) """
        pass


class EnvironmentError(Exception):
    """ Base class for I/O related errors. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    errno = property(lambda self: object()) # default
    filename = property(lambda self: object()) # default
    strerror = property(lambda self: object()) # default


class EOFError(Exception):
    """ Read beyond end of file. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class filter(object):
    """
    filter(function or None, iterable) --> filter object
    
    Return an iterator yielding those items of iterable for which function(item)
    is true. If function is None, return the items that are true.
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, function_or_None, iterable): # real signature unknown; restored from __doc__
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __next__(self): # real signature unknown; restored from __doc__
        """ x.__next__() <==> next(x) """
        pass


class float(object):
    """
    float(x) -> floating point number
    
    Convert a string or number to a floating point number, if possible.
    """
    def as_integer_ratio(self): # real signature unknown; restored from __doc__
        """
        float.as_integer_ratio() -> (int, int)
        
        Returns a pair of integers, whose ratio is exactly equal to the original
        float and with a positive denominator.
        Raises OverflowError on infinities and a ValueError on NaNs.
        
        >>> (10.0).as_integer_ratio()
        (10, 1)
        >>> (0.0).as_integer_ratio()
        (0, 1)
        >>> (-.25).as_integer_ratio()
        (-1, 4)
        """
        pass

    def conjugate(self, *args, **kwargs): # real signature unknown
        """ Returns self, the complex conjugate of any float. """
        pass

    def fromhex(self, string): # real signature unknown; restored from __doc__
        """
        float.fromhex(string) -> float
        
        Create a floating-point number from a hexadecimal string.
        >>> float.fromhex('0x1.ffffp10')
        2047.984375
        >>> float.fromhex('-0x1p-1074')
        -4.9406564584124654e-324
        """
        return 0.0

    def hex(self): # real signature unknown; restored from __doc__
        """
        float.hex() -> string
        
        Return a hexadecimal representation of a floating-point number.
        >>> (-0.1).hex()
        '-0x1.999999999999ap-4'
        >>> 3.14159.hex()
        '0x1.921f9f01b866ep+1'
        """
        return ""

    def is_integer(self, *args, **kwargs): # real signature unknown
        """ Returns True if the float is an integer. """
        pass

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __bool__(self): # real signature unknown; restored from __doc__
        """ x.__bool__() <==> x != 0 """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __float__(self): # real signature unknown; restored from __doc__
        """ x.__float__() <==> float(x) """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__floordiv__(y) <==> x//y """
        pass

    def __format__(self, format_spec): # real signature unknown; restored from __doc__
        """
        float.__format__(format_spec) -> string
        
        Formats the float according to format_spec.
        """
        return ""

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getformat__(self, typestr): # real signature unknown; restored from __doc__
        """
        float.__getformat__(typestr) -> string
        
        You probably don't want to use this function.  It exists mainly to be
        used in Python's test suite.
        
        typestr must be 'double' or 'float'.  This function returns whichever of
        'unknown', 'IEEE, big-endian' or 'IEEE, little-endian' best describes the
        format of floating point numbers used by the C type named by typestr.
        """
        return ""

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, x): # real signature unknown; restored from __doc__
        pass

    def __int__(self): # real signature unknown; restored from __doc__
        """ x.__int__() <==> int(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mod__(self, y): # real signature unknown; restored from __doc__
        """ x.__mod__(y) <==> x%y """
        pass

    def __mul__(self, y): # real signature unknown; restored from __doc__
        """ x.__mul__(y) <==> x*y """
        pass

    def __neg__(self): # real signature unknown; restored from __doc__
        """ x.__neg__() <==> -x """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __pos__(self): # real signature unknown; restored from __doc__
        """ x.__pos__() <==> +x """
        pass

    def __pow__(self, y, z=None): # real signature unknown; restored from __doc__
        """ x.__pow__(y[, z]) <==> pow(x, y[, z]) """
        pass

    def __radd__(self, y): # real signature unknown; restored from __doc__
        """ x.__radd__(y) <==> y+x """
        pass

    def __rdivmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdivmod__(y) <==> divmod(y, x) """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rfloordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rfloordiv__(y) <==> y//x """
        pass

    def __rmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmod__(y) <==> y%x """
        pass

    def __rmul__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmul__(y) <==> y*x """
        pass

    def __round__(self, *args, **kwargs): # real signature unknown
        """
        Returns the Integral closest to x, rounding half toward even.
        When an argument is passed, works like built-in round(x, ndigits).
        """
        pass

    def __rpow__(self, x, z=None): # real signature unknown; restored from __doc__
        """ y.__rpow__(x[, z]) <==> pow(x, y[, z]) """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __rtruediv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rtruediv__(y) <==> y/x """
        pass

    def __setformat__(self, typestr, fmt): # real signature unknown; restored from __doc__
        """
        float.__setformat__(typestr, fmt) -> None
        
        You probably don't want to use this function.  It exists mainly to be
        used in Python's test suite.
        
        typestr must be 'double' or 'float'.  fmt must be one of 'unknown',
        'IEEE, big-endian' or 'IEEE, little-endian', and in addition can only be
        one of the latter two if it appears to match the underlying C reality.
        
        Overrides the automatic determination of C-level floating point type.
        This affects how floats are converted to and from binary strings.
        """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    def __truediv__(self, y): # real signature unknown; restored from __doc__
        """ x.__truediv__(y) <==> x/y """
        pass

    def __trunc__(self, *args, **kwargs): # real signature unknown
        """ Returns the Integral closest to x between 0 and x. """
        pass

    imag = property(lambda self: object()) # default
    real = property(lambda self: object()) # default


class FloatingPointError(ArithmeticError):
    """ Floating point operation failed. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class frozenset(object):
    """
    frozenset() -> empty frozenset object
    frozenset(iterable) -> frozenset object
    
    Build an immutable unordered collection of unique elements.
    """
    def copy(self, *args, **kwargs): # real signature unknown
        """ Return a shallow copy of a set. """
        pass

    def difference(self, *args, **kwargs): # real signature unknown
        """
        Return the difference of two or more sets as a new set.
        
        (i.e. all elements that are in this set but not the others.)
        """
        pass

    def intersection(self, *args, **kwargs): # real signature unknown
        """
        Return the intersection of two sets as a new set.
        
        (i.e. all elements that are in both sets.)
        """
        pass

    def isdisjoint(self, *args, **kwargs): # real signature unknown
        """ Return True if two sets have a null intersection. """
        pass

    def issubset(self, *args, **kwargs): # real signature unknown
        """ Report whether another set contains this set. """
        pass

    def issuperset(self, *args, **kwargs): # real signature unknown
        """ Report whether this set contains another set. """
        pass

    def symmetric_difference(self, *args, **kwargs): # real signature unknown
        """
        Return the symmetric difference of two sets as a new set.
        
        (i.e. all elements that are in exactly one of the sets.)
        """
        pass

    def union(self, *args, **kwargs): # real signature unknown
        """
        Return the union of sets as a new set.
        
        (i.e. all elements that are in either set.)
        """
        pass

    def __and__(self, y): # real signature unknown; restored from __doc__
        """ x.__and__(y) <==> x&y """
        pass

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x. """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, seq=()): # known special case of frozenset.__init__
        """ x.__init__(...) initializes x; see help(type(x)) for signature """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __or__(self, y): # real signature unknown; restored from __doc__
        """ x.__or__(y) <==> x|y """
        pass

    def __rand__(self, y): # real signature unknown; restored from __doc__
        """ x.__rand__(y) <==> y&x """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ Return state information for pickling. """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __ror__(self, y): # real signature unknown; restored from __doc__
        """ x.__ror__(y) <==> y|x """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __rxor__(self, y): # real signature unknown; restored from __doc__
        """ x.__rxor__(y) <==> y^x """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ S.__sizeof__() -> size of S in memory, in bytes """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    def __xor__(self, y): # real signature unknown; restored from __doc__
        """ x.__xor__(y) <==> x^y """
        pass


class FutureWarning(Warning):
    """
    Base class for warnings about constructs that will change semantically
    in the future.
    """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class GeneratorExit(BaseException):
    """ Request that a generator exit. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class ImportError(Exception):
    """ Import can't find module, or can't find name in module. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class ImportWarning(Warning):
    """ Base class for warnings about probable mistakes in module imports """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class SyntaxError(Exception):
    """ Invalid syntax. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    filename = property(lambda self: object()) # default
    lineno = property(lambda self: object()) # default
    msg = property(lambda self: object()) # default
    offset = property(lambda self: object()) # default
    print_file_and_line = property(lambda self: object()) # default
    text = property(lambda self: object()) # default


class IndentationError(SyntaxError):
    """ Improper indentation. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class LookupError(Exception):
    """ Base class for lookup errors. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class IndexError(LookupError):
    """ Sequence index out of range. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class IOError(EnvironmentError):
    """ I/O operation failed. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class KeyboardInterrupt(BaseException):
    """ Program interrupted by user. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class KeyError(LookupError):
    """ Mapping key not found. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass


class list(object):
    """
    list() -> new empty list
    list(iterable) -> new list initialized from iterable's items
    """
    def append(self, p_object): # real signature unknown; restored from __doc__
        """ L.append(object) -- append object to end """
        pass

    def count(self, value): # real signature unknown; restored from __doc__
        """ L.count(value) -> integer -- return number of occurrences of value """
        return 0

    def extend(self, iterable): # real signature unknown; restored from __doc__
        """ L.extend(iterable) -- extend list by appending elements from the iterable """
        pass

    def index(self, value, start=None, stop=None): # real signature unknown; restored from __doc__
        """
        L.index(value, [start, [stop]]) -> integer -- return first index of value.
        Raises ValueError if the value is not present.
        """
        return 0

    def insert(self, index, p_object): # real signature unknown; restored from __doc__
        """ L.insert(index, object) -- insert object before index """
        pass

    def pop(self, index=None): # real signature unknown; restored from __doc__
        """
        L.pop([index]) -> item -- remove and return item at index (default last).
        Raises IndexError if list is empty or index is out of range.
        """
        pass

    def remove(self, value): # real signature unknown; restored from __doc__
        """
        L.remove(value) -- remove first occurrence of value.
        Raises ValueError if the value is not present.
        """
        pass

    def reverse(self): # real signature unknown; restored from __doc__
        """ L.reverse() -- reverse *IN PLACE* """
        pass

    def sort(self, key=None, reverse=False): # real signature unknown; restored from __doc__
        """ L.sort(key=None, reverse=False) -- stable sort *IN PLACE* """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x """
        pass

    def __delitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__delitem__(y) <==> del x[y] """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __iadd__(self, y): # real signature unknown; restored from __doc__
        """ x.__iadd__(y) <==> x+=y """
        pass

    def __imul__(self, y): # real signature unknown; restored from __doc__
        """ x.__imul__(y) <==> x*=y """
        pass

    def __init__(self, seq=()): # known special case of list.__init__
        """
        list() -> new empty list
        list(iterable) -> new list initialized from iterable's items
        # (copied from class doc)
        """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mul__(self, n): # real signature unknown; restored from __doc__
        """ x.__mul__(n) <==> x*n """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __reversed__(self): # real signature unknown; restored from __doc__
        """ L.__reversed__() -- return a reverse iterator over the list """
        pass

    def __rmul__(self, n): # real signature unknown; restored from __doc__
        """ x.__rmul__(n) <==> n*x """
        pass

    def __setitem__(self, i, y): # real signature unknown; restored from __doc__
        """ x.__setitem__(i, y) <==> x[i]=y """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ L.__sizeof__() -- size of L in memory, in bytes """
        pass

    __hash__ = None


class map(object):
    """
    map(func, *iterables) --> map object
    
    Make an iterator that computes the function using arguments from
    each of the iterables.  Stops when the shortest iterable is exhausted.
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, func, *iterables): # real signature unknown; restored from __doc__
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __next__(self): # real signature unknown; restored from __doc__
        """ x.__next__() <==> next(x) """
        pass


class MemoryError(Exception):
    """ Out of memory. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class memoryview(object):
    """
    memoryview(object)
    
    Create a new memoryview object which references the given object.
    """
    def release(self, *args, **kwargs): # real signature unknown
        pass

    def tobytes(self, *args, **kwargs): # real signature unknown
        pass

    def tolist(self, *args, **kwargs): # real signature unknown
        pass

    def __delitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__delitem__(y) <==> del x[y] """
        pass

    def __enter__(self, *args, **kwargs): # real signature unknown
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __exit__(self, *args, **kwargs): # real signature unknown
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __init__(self, p_object): # real signature unknown; restored from __doc__
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __setitem__(self, i, y): # real signature unknown; restored from __doc__
        """ x.__setitem__(i, y) <==> x[i]=y """
        pass

    format = property(lambda self: object()) # default
    itemsize = property(lambda self: object()) # default
    ndim = property(lambda self: object()) # default
    readonly = property(lambda self: object()) # default
    shape = property(lambda self: object()) # default
    strides = property(lambda self: object()) # default
    suboffsets = property(lambda self: object()) # default

    __hash__ = None


class NameError(Exception):
    """ Name not found globally. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class RuntimeError(Exception):
    """ Unspecified run-time error. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class NotImplementedError(RuntimeError):
    """ Method or function hasn't been implemented yet. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class OSError(EnvironmentError):
    """ OS system call failed. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class OverflowError(ArithmeticError):
    """ Result too large to be represented. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class PendingDeprecationWarning(Warning):
    """
    Base class for warnings about features which will be deprecated
    in the future.
    """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class property(object):
    """
    property(fget=None, fset=None, fdel=None, doc=None) -> property attribute
    
    fget is a function to be used for getting an attribute value, and likewise
    fset is a function for setting, and fdel a function for del'ing, an
    attribute.  Typical use is to define a managed attribute x:
    class C(object):
        def getx(self): return self._x
        def setx(self, value): self._x = value
        def delx(self): del self._x
        x = property(getx, setx, delx, "I'm the 'x' property.")
    
    Decorators make defining new properties or modifying existing ones easy:
    class C(object):
        @property
        def x(self): return self._x
        @x.setter
        def x(self, value): self._x = value
        @x.deleter
        def x(self): del self._x
    """
    def deleter(self, *args, **kwargs): # real signature unknown
        """ Descriptor to change the deleter on a property. """
        pass

    def getter(self, *args, **kwargs): # real signature unknown
        """ Descriptor to change the getter on a property. """
        pass

    def setter(self, *args, **kwargs): # real signature unknown
        """ Descriptor to change the setter on a property. """
        pass

    def __delete__(self, obj): # real signature unknown; restored from __doc__
        """ descr.__delete__(obj) """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __get__(self, obj, type=None): # real signature unknown; restored from __doc__
        """ descr.__get__(obj[, type]) -> value """
        pass

    def __init__(self, fget=None, fset=None, fdel=None, doc=None): # known special case of property.__init__
        """
        property(fget=None, fset=None, fdel=None, doc=None) -> property attribute
        
        fget is a function to be used for getting an attribute value, and likewise
        fset is a function for setting, and fdel a function for del'ing, an
        attribute.  Typical use is to define a managed attribute x:
        class C(object):
            def getx(self): return self._x
            def setx(self, value): self._x = value
            def delx(self): del self._x
            x = property(getx, setx, delx, "I'm the 'x' property.")
        
        Decorators make defining new properties or modifying existing ones easy:
        class C(object):
            @property
            def x(self): return self._x
            @x.setter
            def x(self, value): self._x = value
            @x.deleter
            def x(self): del self._x
        
        # (copied from class doc)
        """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __set__(self, obj, value): # real signature unknown; restored from __doc__
        """ descr.__set__(obj, value) """
        pass

    fdel = property(lambda self: object()) # default
    fget = property(lambda self: object()) # default
    fset = property(lambda self: object()) # default


class range(object):
    """
    range([start,] stop[, step]) -> range object
    
    Returns a virtual sequence of numbers from start to stop by step.
    """
    def count(self, value): # real signature unknown; restored from __doc__
        """ rangeobject.count(value) -> integer -- return number of occurrences of value """
        return 0

    def index(self, value, start=None, stop=None): # real signature unknown; restored from __doc__
        """
        rangeobject.index(value, [start, [stop]]) -> integer -- return index of value.
        Raises ValueError if the value is not present.
        """
        return 0

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __init__(self, start=None, *args, **kwargs): # real signature unknown; NOTE: unreliably restored from __doc__ 
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __reversed__(self, *args, **kwargs): # real signature unknown
        """ Returns a reverse iterator. """
        pass


class ReferenceError(Exception):
    """ Weak ref proxy used after referent went away. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class ResourceWarning(Warning):
    """ Base class for warnings about resource usage. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class reversed(object):
    """
    reversed(sequence) -> reverse iterator over values of the sequence
    
    Return a reverse iterator
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, sequence): # real signature unknown; restored from __doc__
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __length_hint__(self, *args, **kwargs): # real signature unknown
        """ Private method returning an estimate of len(list(it)). """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __next__(self): # real signature unknown; restored from __doc__
        """ x.__next__() <==> next(x) """
        pass


class RuntimeWarning(Warning):
    """ Base class for warnings about dubious runtime behavior. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class set(object):
    """
    set() -> new empty set object
    set(iterable) -> new set object
    
    Build an unordered collection of unique elements.
    """
    def add(self, *args, **kwargs): # real signature unknown
        """
        Add an element to a set.
        
        This has no effect if the element is already present.
        """
        pass

    def clear(self, *args, **kwargs): # real signature unknown
        """ Remove all elements from this set. """
        pass

    def copy(self, *args, **kwargs): # real signature unknown
        """ Return a shallow copy of a set. """
        pass

    def difference(self, *args, **kwargs): # real signature unknown
        """
        Return the difference of two or more sets as a new set.
        
        (i.e. all elements that are in this set but not the others.)
        """
        pass

    def difference_update(self, *args, **kwargs): # real signature unknown
        """ Remove all elements of another set from this set. """
        pass

    def discard(self, *args, **kwargs): # real signature unknown
        """
        Remove an element from a set if it is a member.
        
        If the element is not a member, do nothing.
        """
        pass

    def intersection(self, *args, **kwargs): # real signature unknown
        """
        Return the intersection of two sets as a new set.
        
        (i.e. all elements that are in both sets.)
        """
        pass

    def intersection_update(self, *args, **kwargs): # real signature unknown
        """ Update a set with the intersection of itself and another. """
        pass

    def isdisjoint(self, *args, **kwargs): # real signature unknown
        """ Return True if two sets have a null intersection. """
        pass

    def issubset(self, *args, **kwargs): # real signature unknown
        """ Report whether another set contains this set. """
        pass

    def issuperset(self, *args, **kwargs): # real signature unknown
        """ Report whether this set contains another set. """
        pass

    def pop(self, *args, **kwargs): # real signature unknown
        """
        Remove and return an arbitrary set element.
        Raises KeyError if the set is empty.
        """
        pass

    def remove(self, *args, **kwargs): # real signature unknown
        """
        Remove an element from a set; it must be a member.
        
        If the element is not a member, raise a KeyError.
        """
        pass

    def symmetric_difference(self, *args, **kwargs): # real signature unknown
        """
        Return the symmetric difference of two sets as a new set.
        
        (i.e. all elements that are in exactly one of the sets.)
        """
        pass

    def symmetric_difference_update(self, *args, **kwargs): # real signature unknown
        """ Update a set with the symmetric difference of itself and another. """
        pass

    def union(self, *args, **kwargs): # real signature unknown
        """
        Return the union of sets as a new set.
        
        (i.e. all elements that are in either set.)
        """
        pass

    def update(self, *args, **kwargs): # real signature unknown
        """ Update a set with the union of itself and others. """
        pass

    def __and__(self, y): # real signature unknown; restored from __doc__
        """ x.__and__(y) <==> x&y """
        pass

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x. """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __iand__(self, y): # real signature unknown; restored from __doc__
        """ x.__iand__(y) <==> x&y """
        pass

    def __init__(self, seq=()): # known special case of set.__init__
        """
        set() -> new empty set object
        set(iterable) -> new set object
        
        Build an unordered collection of unique elements.
        # (copied from class doc)
        """
        pass

    def __ior__(self, y): # real signature unknown; restored from __doc__
        """ x.__ior__(y) <==> x|y """
        pass

    def __isub__(self, y): # real signature unknown; restored from __doc__
        """ x.__isub__(y) <==> x-y """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __ixor__(self, y): # real signature unknown; restored from __doc__
        """ x.__ixor__(y) <==> x^y """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __or__(self, y): # real signature unknown; restored from __doc__
        """ x.__or__(y) <==> x|y """
        pass

    def __rand__(self, y): # real signature unknown; restored from __doc__
        """ x.__rand__(y) <==> y&x """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ Return state information for pickling. """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __ror__(self, y): # real signature unknown; restored from __doc__
        """ x.__ror__(y) <==> y|x """
        pass

    def __rsub__(self, y): # real signature unknown; restored from __doc__
        """ x.__rsub__(y) <==> y-x """
        pass

    def __rxor__(self, y): # real signature unknown; restored from __doc__
        """ x.__rxor__(y) <==> y^x """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ S.__sizeof__() -> size of S in memory, in bytes """
        pass

    def __sub__(self, y): # real signature unknown; restored from __doc__
        """ x.__sub__(y) <==> x-y """
        pass

    def __xor__(self, y): # real signature unknown; restored from __doc__
        """ x.__xor__(y) <==> x^y """
        pass

    __hash__ = None


class slice(object):
    """
    slice([start,] stop[, step])
    
    Create a slice object.  This is used for extended slicing (e.g. a[0:10:2]).
    """
    def indices(self, len): # real signature unknown; restored from __doc__
        """
        S.indices(len) -> (start, stop, stride)
        
        Assuming a sequence of length len, calculate the start and stop
        indices, and the stride length of the extended slice described by
        S. Out of bounds indices are clipped in a manner consistent with the
        handling of normal slices.
        """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __init__(self, start=None, *args, **kwargs): # real signature unknown; NOTE: unreliably restored from __doc__ 
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ Return state information for pickling. """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    start = property(lambda self: 0)
    step = property(lambda self: 0)
    stop = property(lambda self: 0)

    __hash__ = None


class staticmethod(object):
    """
    staticmethod(function) -> method
    
    Convert a function to be a static method.
    
    A static method does not receive an implicit first argument.
    To declare a static method, use this idiom:
    
         class C:
         def f(arg1, arg2, ...): ...
         f = staticmethod(f)
    
    It can be called either on the class (e.g. C.f()) or on an instance
    (e.g. C().f()).  The instance is ignored except for its class.
    
    Static methods in Python are similar to those found in Java or C++.
    For a more advanced concept, see the classmethod builtin.
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __get__(self, obj, type=None): # real signature unknown; restored from __doc__
        """ descr.__get__(obj[, type]) -> value """
        pass

    def __init__(self, function): # real signature unknown; restored from __doc__
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    __func__ = property(lambda self: object()) # default


class StopIteration(Exception):
    """ Signal the end from iterator.__next__(). """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class str(object):
    """
    str(string[, encoding[, errors]]) -> str
    
    Create a new string object from the given encoded string.
    encoding defaults to the current default string encoding.
    errors can be 'strict', 'replace' or 'ignore' and defaults to 'strict'.
    """
    def capitalize(self): # real signature unknown; restored from __doc__
        """
        S.capitalize() -> str
        
        Return a capitalized version of S, i.e. make the first character
        have upper case and the rest lower case.
        """
        return ""

    def center(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.center(width[, fillchar]) -> str
        
        Return S centered in a string of length width. Padding is
        done using the specified fill character (default is a space)
        """
        return ""

    def count(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.count(sub[, start[, end]]) -> int
        
        Return the number of non-overlapping occurrences of substring sub in
        string S[start:end].  Optional arguments start and end are
        interpreted as in slice notation.
        """
        return 0

    def encode(self, encoding='utf-8', errors='strict'): # real signature unknown; restored from __doc__
        """
        S.encode(encoding='utf-8', errors='strict') -> bytes
        
        Encode S using the codec registered for encoding. Default encoding
        is 'utf-8'. errors may be given to set a different error
        handling scheme. Default is 'strict' meaning that encoding errors raise
        a UnicodeEncodeError. Other possible values are 'ignore', 'replace' and
        'xmlcharrefreplace' as well as any other name registered with
        codecs.register_error that can handle UnicodeEncodeErrors.
        """
        return b""

    def endswith(self, suffix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.endswith(suffix[, start[, end]]) -> bool
        
        Return True if S ends with the specified suffix, False otherwise.
        With optional start, test S beginning at that position.
        With optional end, stop comparing S at that position.
        suffix can also be a tuple of strings to try.
        """
        return False

    def expandtabs(self, tabsize=None): # real signature unknown; restored from __doc__
        """
        S.expandtabs([tabsize]) -> str
        
        Return a copy of S where all tab characters are expanded using spaces.
        If tabsize is not given, a tab size of 8 characters is assumed.
        """
        return ""

    def find(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.find(sub[, start[, end]]) -> int
        
        Return the lowest index in S where substring sub is found,
        such that sub is contained within S[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def format(self, *args, **kwargs): # real signature unknown; restored from __doc__
        """
        S.format(*args, **kwargs) -> str
        
        Return a formatted version of S, using substitutions from args and kwargs.
        The substitutions are identified by braces ('{' and '}').
        """
        return ""

    def format_map(self, mapping): # real signature unknown; restored from __doc__
        """
        S.format_map(mapping) -> str
        
        Return a formatted version of S, using substitutions from mapping.
        The substitutions are identified by braces ('{' and '}').
        """
        return ""

    def index(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.index(sub[, start[, end]]) -> int
        
        Like S.find() but raise ValueError when the substring is not found.
        """
        return 0

    def isalnum(self): # real signature unknown; restored from __doc__
        """
        S.isalnum() -> bool
        
        Return True if all characters in S are alphanumeric
        and there is at least one character in S, False otherwise.
        """
        return False

    def isalpha(self): # real signature unknown; restored from __doc__
        """
        S.isalpha() -> bool
        
        Return True if all characters in S are alphabetic
        and there is at least one character in S, False otherwise.
        """
        return False

    def isdecimal(self): # real signature unknown; restored from __doc__
        """
        S.isdecimal() -> bool
        
        Return True if there are only decimal characters in S,
        False otherwise.
        """
        return False

    def isdigit(self): # real signature unknown; restored from __doc__
        """
        S.isdigit() -> bool
        
        Return True if all characters in S are digits
        and there is at least one character in S, False otherwise.
        """
        return False

    def isidentifier(self): # real signature unknown; restored from __doc__
        """
        S.isidentifier() -> bool
        
        Return True if S is a valid identifier according
        to the language definition.
        """
        return False

    def islower(self): # real signature unknown; restored from __doc__
        """
        S.islower() -> bool
        
        Return True if all cased characters in S are lowercase and there is
        at least one cased character in S, False otherwise.
        """
        return False

    def isnumeric(self): # real signature unknown; restored from __doc__
        """
        S.isnumeric() -> bool
        
        Return True if there are only numeric characters in S,
        False otherwise.
        """
        return False

    def isprintable(self): # real signature unknown; restored from __doc__
        """
        S.isprintable() -> bool
        
        Return True if all characters in S are considered
        printable in repr() or S is empty, False otherwise.
        """
        return False

    def isspace(self): # real signature unknown; restored from __doc__
        """
        S.isspace() -> bool
        
        Return True if all characters in S are whitespace
        and there is at least one character in S, False otherwise.
        """
        return False

    def istitle(self): # real signature unknown; restored from __doc__
        """
        S.istitle() -> bool
        
        Return True if S is a titlecased string and there is at least one
        character in S, i.e. upper- and titlecase characters may only
        follow uncased characters and lowercase characters only cased ones.
        Return False otherwise.
        """
        return False

    def isupper(self): # real signature unknown; restored from __doc__
        """
        S.isupper() -> bool
        
        Return True if all cased characters in S are uppercase and there is
        at least one cased character in S, False otherwise.
        """
        return False

    def join(self, iterable): # real signature unknown; restored from __doc__
        """
        S.join(iterable) -> str
        
        Return a string which is the concatenation of the strings in the
        iterable.  The separator between elements is S.
        """
        return ""

    def ljust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.ljust(width[, fillchar]) -> str
        
        Return S left-justified in a Unicode string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        return ""

    def lower(self): # real signature unknown; restored from __doc__
        """
        S.lower() -> str
        
        Return a copy of the string S converted to lowercase.
        """
        return ""

    def lstrip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.lstrip([chars]) -> str
        
        Return a copy of the string S with leading whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        """
        return ""

    def maketrans(self, x, y=None, z=None): # real signature unknown; restored from __doc__
        """
        str.maketrans(x[, y[, z]]) -> dict (static method)
        
        Return a translation table usable for str.translate().
        If there is only one argument, it must be a dictionary mapping Unicode
        ordinals (integers) or characters to Unicode ordinals, strings or None.
        Character keys will be then converted to ordinals.
        If there are two arguments, they must be strings of equal length, and
        in the resulting dictionary, each character in x will be mapped to the
        character at the same position in y. If there is a third argument, it
        must be a string, whose characters will be mapped to None in the result.
        """
        return {}

    def partition(self, sep): # real signature unknown; restored from __doc__
        """
        S.partition(sep) -> (head, sep, tail)
        
        Search for the separator sep in S, and return the part before it,
        the separator itself, and the part after it.  If the separator is not
        found, return S and two empty strings.
        """
        pass

    def replace(self, old, new, count=None): # real signature unknown; restored from __doc__
        """
        S.replace(old, new[, count]) -> str
        
        Return a copy of S with all occurrences of substring
        old replaced by new.  If the optional argument count is
        given, only the first count occurrences are replaced.
        """
        return ""

    def rfind(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.rfind(sub[, start[, end]]) -> int
        
        Return the highest index in S where substring sub is found,
        such that sub is contained within S[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def rindex(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.rindex(sub[, start[, end]]) -> int
        
        Like S.rfind() but raise ValueError when the substring is not found.
        """
        return 0

    def rjust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.rjust(width[, fillchar]) -> str
        
        Return S right-justified in a string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        return ""

    def rpartition(self, sep): # real signature unknown; restored from __doc__
        """
        S.rpartition(sep) -> (head, sep, tail)
        
        Search for the separator sep in S, starting at the end of S, and return
        the part before it, the separator itself, and the part after it.  If the
        separator is not found, return two empty strings and S.
        """
        pass

    def rsplit(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        S.rsplit([sep[, maxsplit]]) -> list of strings
        
        Return a list of the words in S, using sep as the
        delimiter string, starting at the end of the string and
        working to the front.  If maxsplit is given, at most maxsplit
        splits are done. If sep is not specified, any whitespace string
        is a separator.
        """
        return []

    def rstrip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.rstrip([chars]) -> str
        
        Return a copy of the string S with trailing whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        """
        return ""

    def split(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        S.split([sep[, maxsplit]]) -> list of strings
        
        Return a list of the words in S, using sep as the
        delimiter string.  If maxsplit is given, at most maxsplit
        splits are done. If sep is not specified or is None, any
        whitespace string is a separator and empty strings are
        removed from the result.
        """
        return []

    def splitlines(self, keepends=None): # real signature unknown; restored from __doc__
        """
        S.splitlines([keepends]) -> list of strings
        
        Return a list of the lines in S, breaking at line boundaries.
        Line breaks are not included in the resulting list unless keepends
        is given and true.
        """
        return []

    def startswith(self, prefix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.startswith(prefix[, start[, end]]) -> bool
        
        Return True if S starts with the specified prefix, False otherwise.
        With optional start, test S beginning at that position.
        With optional end, stop comparing S at that position.
        prefix can also be a tuple of strings to try.
        """
        return False

    def strip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.strip([chars]) -> str
        
        Return a copy of the string S with leading and trailing
        whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        """
        return ""

    def swapcase(self): # real signature unknown; restored from __doc__
        """
        S.swapcase() -> str
        
        Return a copy of S with uppercase characters converted to lowercase
        and vice versa.
        """
        return ""

    def title(self): # real signature unknown; restored from __doc__
        """
        S.title() -> str
        
        Return a titlecased version of S, i.e. words start with title case
        characters, all remaining cased characters have lower case.
        """
        return ""

    def translate(self, table): # real signature unknown; restored from __doc__
        """
        S.translate(table) -> str
        
        Return a copy of the string S, where all characters have been mapped
        through the given translation table, which must be a mapping of
        Unicode ordinals to Unicode ordinals, strings, or None.
        Unmapped characters are left untouched. Characters mapped to None
        are deleted.
        """
        return ""

    def upper(self): # real signature unknown; restored from __doc__
        """
        S.upper() -> str
        
        Return a copy of S converted to uppercase.
        """
        return ""

    def zfill(self, width): # real signature unknown; restored from __doc__
        """
        S.zfill(width) -> str
        
        Pad a numeric string S with zeros on the left, to fill a field
        of the specified width. The string S is never truncated.
        """
        return ""

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __format__(self, format_spec): # real signature unknown; restored from __doc__
        """
        S.__format__(format_spec) -> str
        
        Return a formatted version of S as described by format_spec.
        """
        return ""

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, value='', encoding=None, errors='strict'): # known special case of str.__init__
        """
        str(string[, encoding[, errors]]) -> str
        
        Create a new string object from the given encoded string.
        encoding defaults to the current default string encoding.
        errors can be 'strict', 'replace' or 'ignore' and defaults to 'strict'.
        # (copied from class doc)
        """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mod__(self, y): # real signature unknown; restored from __doc__
        """ x.__mod__(y) <==> x%y """
        pass

    def __mul__(self, n): # real signature unknown; restored from __doc__
        """ x.__mul__(n) <==> x*n """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__rmod__(y) <==> y%x """
        pass

    def __rmul__(self, n): # real signature unknown; restored from __doc__
        """ x.__rmul__(n) <==> n*x """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ S.__sizeof__() -> size of S in memory, in bytes """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass


class super(object):
    """
    super() -> same as super(__class__, <first argument>)
    super(type) -> unbound super object
    super(type, obj) -> bound super object; requires isinstance(obj, type)
    super(type, type2) -> bound super object; requires issubclass(type2, type)
    Typical use to call a cooperative superclass method:
    class C(B):
        def meth(self, arg):
            super().meth(arg)
    This works for class methods too:
    class C(B):
        @classmethod
        def cmeth(cls, arg):
            super().cmeth(arg)
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __get__(self, obj, type=None): # real signature unknown; restored from __doc__
        """ descr.__get__(obj[, type]) -> value """
        pass

    def __init__(self, type1=None, type2=None): # known special case of super.__init__
        """
        super() -> same as super(__class__, <first argument>)
        super(type) -> unbound super object
        super(type, obj) -> bound super object; requires isinstance(obj, type)
        super(type, type2) -> bound super object; requires issubclass(type2, type)
        Typical use to call a cooperative superclass method:
        class C(B):
            def meth(self, arg):
                super().meth(arg)
        This works for class methods too:
        class C(B):
            @classmethod
            def cmeth(cls, arg):
                super().cmeth(arg)
        
        # (copied from class doc)
        """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    __self_class__ = property(lambda self: type(object))
    __self__ = property(lambda self: type(object))
    __thisclass__ = property(lambda self: type(object))


class SyntaxWarning(Warning):
    """ Base class for warnings about dubious syntax. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class SystemError(Exception):
    """
    Internal error in the Python interpreter.
    
    Please report this to the Python maintainer, along with the traceback,
    the Python version, and the hardware/OS platform and version.
    """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class SystemExit(BaseException):
    """ Request to exit from the interpreter. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    code = property(lambda self: object()) # default


class TabError(IndentationError):
    """ Improper mixture of spaces and tabs. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class tuple(object):
    """
    tuple() -> empty tuple
    tuple(iterable) -> tuple initialized from iterable's items
    
    If the argument is a tuple, the return value is the same object.
    """
    def count(self, value): # real signature unknown; restored from __doc__
        """ T.count(value) -> integer -- return number of occurrences of value """
        return 0

    def index(self, value, start=None, stop=None): # real signature unknown; restored from __doc__
        """
        T.index(value, [start, [stop]]) -> integer -- return first index of value.
        Raises ValueError if the value is not present.
        """
        return 0

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __contains__(self, y): # real signature unknown; restored from __doc__
        """ x.__contains__(y) <==> y in x """
        pass

    def __eq__(self, y): # real signature unknown; restored from __doc__
        """ x.__eq__(y) <==> x==y """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __ge__(self, y): # real signature unknown; restored from __doc__
        """ x.__ge__(y) <==> x>=y """
        pass

    def __gt__(self, y): # real signature unknown; restored from __doc__
        """ x.__gt__(y) <==> x>y """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, seq=()): # known special case of tuple.__init__
        """
        tuple() -> empty tuple
        tuple(iterable) -> tuple initialized from iterable's items
        
        If the argument is a tuple, the return value is the same object.
        # (copied from class doc)
        """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __le__(self, y): # real signature unknown; restored from __doc__
        """ x.__le__(y) <==> x<=y """
        pass

    def __lt__(self, y): # real signature unknown; restored from __doc__
        """ x.__lt__(y) <==> x<y """
        pass

    def __mul__(self, n): # real signature unknown; restored from __doc__
        """ x.__mul__(n) <==> x*n """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __ne__(self, y): # real signature unknown; restored from __doc__
        """ x.__ne__(y) <==> x!=y """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __rmul__(self, n): # real signature unknown; restored from __doc__
        """ x.__rmul__(n) <==> n*x """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ T.__sizeof__() -- size of T in memory, in bytes """
        pass


class type(object):
    """
    type(object) -> the object's type
    type(name, bases, dict) -> a new type
    """
    def mro(self): # real signature unknown; restored from __doc__
        """
        mro() -> list
        return a type's method resolution order
        """
        return []

    def __call__(self, *more): # real signature unknown; restored from __doc__
        """ x.__call__(...) <==> x(...) """
        pass

    def __delattr__(self, name): # real signature unknown; restored from __doc__
        """ x.__delattr__('name') <==> del x.name """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(cls, what, bases=None, dict=None): # known special case of type.__init__
        """
        type(object) -> the object's type
        type(name, bases, dict) -> a new type
        # (copied from class doc)
        """
        pass

    def __instancecheck__(self): # real signature unknown; restored from __doc__
        """
        __instancecheck__() -> bool
        check if an object is an instance
        """
        return False

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __prepare__(self): # real signature unknown; restored from __doc__
        """
        __prepare__() -> dict
        used to create the namespace for the class statement
        """
        return {}

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __setattr__(self, name, value): # real signature unknown; restored from __doc__
        """ x.__setattr__('name', value) <==> x.name = value """
        pass

    def __subclasscheck__(self): # real signature unknown; restored from __doc__
        """
        __subclasscheck__() -> bool
        check if a class is a subclass
        """
        return False

    def __subclasses__(self): # real signature unknown; restored from __doc__
        """ __subclasses__() -> list of immediate subclasses """
        return []

    __abstractmethods__ = property(lambda self: object()) # default

    __bases__ = (
        object,
    )
    __base__ = object
    __basicsize__ = 800
    __dictoffset__ = 264
    __dict__ = None # (!) real value is ''
    __flags__ = 2148291584
    __itemsize__ = 40
    __mro__ = (
        None, # (!) forward: type, real value is ''
        object,
    )
    __name__ = 'type'
    __weakrefoffset__ = 368


class TypeError(Exception):
    """ Inappropriate argument type. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class UnboundLocalError(NameError):
    """ Local name referenced but not bound to a value. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class ValueError(Exception):
    """ Inappropriate argument value (of correct type). """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class UnicodeError(ValueError):
    """ Unicode related error. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class UnicodeDecodeError(UnicodeError):
    """ Unicode decoding error. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    encoding = property(lambda self: object()) # default
    end = property(lambda self: object()) # default
    object = property(lambda self: object()) # default
    reason = property(lambda self: object()) # default
    start = property(lambda self: object()) # default


class UnicodeEncodeError(UnicodeError):
    """ Unicode encoding error. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    encoding = property(lambda self: object()) # default
    end = property(lambda self: object()) # default
    object = property(lambda self: object()) # default
    reason = property(lambda self: object()) # default
    start = property(lambda self: object()) # default


class UnicodeTranslateError(UnicodeError):
    """ Unicode translation error. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass

    encoding = property(lambda self: object()) # default
    end = property(lambda self: object()) # default
    object = property(lambda self: object()) # default
    reason = property(lambda self: object()) # default
    start = property(lambda self: object()) # default


class UnicodeWarning(Warning):
    """
    Base class for warnings about Unicode related problems, mostly
    related to conversion problems.
    """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class UserWarning(Warning):
    """ Base class for warnings generated by user code. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class ZeroDivisionError(ArithmeticError):
    """ Second argument to a division or modulo operation was zero. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class zip(object):
    """
    zip(iter1 [,iter2 [...]]) --> zip object
    
    Return a zip object whose .__next__() method returns a tuple where
    the i-th element comes from the i-th iterable argument.  The .__next__()
    method continues until the shortest iterable in the argument sequence
    is exhausted and then it raises StopIteration.
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, iter1, iter2=None, *some): # real signature unknown; restored from __doc__
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __next__(self): # real signature unknown; restored from __doc__
        """ x.__next__() <==> next(x) """
        pass



class __generator(object):
    '''A mock class representing the generator function type.'''
    def __init__(self):
        self.gi_code = None
        self.gi_frame = None
        self.gi_running = 0

    def __iter__(self):
        '''Defined to support iteration over container.'''
        pass

    def __next__(self):
        '''Return the next item from the container.'''
        pass

    def close(self):
        '''Raises new GeneratorExit exception inside the generator to terminate the iteration.'''
        pass

    def send(self, value):
        '''Resumes the generator and "sends" a value that becomes the result of the current yield-expression.'''
        pass

    def throw(self, type, value=None, traceback=None):
        '''Used to raise an exception inside the generator.'''
        pass


class __namedtuple(tuple):
    '''A mock base class for named tuples.'''

    __slots__ = ()
    _fields = ()

    def __new__(cls, *args, **kwargs):
        'Create a new instance of the named tuple.'
        return tuple.__new__(cls, *args)

    @classmethod
    def _make(cls, iterable, new=tuple.__new__, len=len):
        'Make a new named tuple object from a sequence or iterable.'
        return new(cls, iterable)

    def __repr__(self):
        return ''

    def _asdict(self):
        'Return a new dict which maps field types to their values.'
        return {}

    def _replace(self, **kwargs):
        'Return a new named tuple object replacing specified fields with new values.'
        return self

    def __getnewargs__(self):
        return tuple(self)

# variables with complex values

Ellipsis = None # (!) real value is ''

NotImplemented = None # (!) real value is ''

