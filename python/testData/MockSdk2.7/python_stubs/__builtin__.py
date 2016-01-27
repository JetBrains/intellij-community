# encoding: utf-8
# module __builtin__
# from (built-in)
# by generator 1.138
from __future__ import print_function
"""
Built-in functions, exceptions, and other objects.

Noteworthy: None is the `nil' object; Ellipsis represents `...' in slices.
"""

# imports
from exceptions import (ArithmeticError, AssertionError, AttributeError, 
    BaseException, BufferError, BytesWarning, DeprecationWarning, EOFError, 
    EnvironmentError, Exception, FloatingPointError, FutureWarning, 
    GeneratorExit, IOError, ImportError, ImportWarning, IndentationError, 
    IndexError, KeyError, KeyboardInterrupt, LookupError, MemoryError, 
    NameError, NotImplementedError, OSError, OverflowError, 
    PendingDeprecationWarning, ReferenceError, RuntimeError, RuntimeWarning, 
    StandardError, StopIteration, SyntaxError, SyntaxWarning, SystemError, 
    SystemExit, TabError, TypeError, UnboundLocalError, UnicodeDecodeError, 
    UnicodeEncodeError, UnicodeError, UnicodeTranslateError, UnicodeWarning, 
    UserWarning, ValueError, Warning, ZeroDivisionError)


# Variables with simple values

False = False

None = object() # real value of type <type 'NoneType'> replaced

True = True

__debug__ = True

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
    If the iterable is empty, return True.
    """
    return False

def any(iterable): # real signature unknown; restored from __doc__
    """
    any(iterable) -> bool
    
    Return True if bool(x) is True for any x in the iterable.
    If the iterable is empty, return False.
    """
    return False

def apply(p_object, args=None, kwargs=None): # real signature unknown; restored from __doc__
    """
    apply(object[, args[, kwargs]]) -> value
    
    Call a callable object with positional arguments taken from the tuple args,
    and keyword arguments taken from the optional dictionary kwargs.
    Note that classes are callable, as are instances with a __call__() method.
    
    Deprecated since release 2.3. Instead, use the extended call syntax:
        function(*args, **keywords).
    """
    pass

def bin(number): # real signature unknown; restored from __doc__
    """
    bin(number) -> string
    
    Return the binary representation of an integer or long integer.
    """
    return ""

def callable(p_object): # real signature unknown; restored from __doc__
    """
    callable(object) -> bool
    
    Return whether the object is callable (i.e., some kind of function).
    Note that classes are callable, as are instances with a __call__() method.
    """
    return False

def chr(i): # real signature unknown; restored from __doc__
    """
    chr(i) -> character
    
    Return a string of one character with ordinal i; 0 <= i < 256.
    """
    return ""

def cmp(x, y): # real signature unknown; restored from __doc__
    """
    cmp(x, y) -> integer
    
    Return negative if x<y, zero if x==y, positive if x>y.
    """
    return 0

def coerce(x, y): # real signature unknown; restored from __doc__
    """
    coerce(x, y) -> (x1, y1)
    
    Return a tuple consisting of the two numeric arguments converted to
    a common type, using the same rules as used by arithmetic operations.
    If coercion is not possible, raise TypeError.
    """
    pass

def compile(source, filename, mode, flags=None, dont_inherit=None): # real signature unknown; restored from __doc__
    """
    compile(source, filename, mode[, flags[, dont_inherit]]) -> code object
    
    Compile the source string (a Python module, statement or expression)
    into a code object that can be executed by the exec statement or eval().
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

def divmod(x, y): # known case of __builtin__.divmod
    """
    divmod(x, y) -> (quotient, remainder)
    
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

def execfile(filename, globals=None, locals=None): # real signature unknown; restored from __doc__
    """
    execfile(filename[, globals[, locals]])
    
    Read and execute a Python script from a file.
    The globals and locals are dictionaries, defaulting to the current
    globals and locals.  If only globals is given, locals defaults to it.
    """
    pass

def exit(*args, **kwargs): # real signature unknown
    pass

def filter(function_or_none, sequence): # known special case of filter
    """
    filter(function or None, sequence) -> list, tuple, or string
    
    Return those items of sequence for which function(item) is true.  If
    function is None, return the items that are true.  If sequence is a tuple
    or string, return the same type, else return a list.
    """
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
    (This is done by calling getattr(object, name) and catching exceptions.)
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
    Define the built-in 'help'.
        This is a wrapper around pydoc.help (with a twist).
    """
    pass

def hex(number): # real signature unknown; restored from __doc__
    """
    hex(number) -> string
    
    Return the hexadecimal representation of an integer or long integer.
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
    input([prompt]) -> value
    
    Equivalent to eval(raw_input(prompt)).
    """
    pass

def intern(string): # real signature unknown; restored from __doc__
    """
    intern(string) -> string
    
    ``Intern'' the given string.  This enters the string in the (global)
    table of interned strings whose purpose is to speed up dictionary lookups.
    Return the string itself or the previously interned string object with the
    same value.
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
    iter(collection) -> iterator
    iter(callable, sentinel) -> iterator
    
    Get an iterator from an object.  In the first form, the argument must
    supply its own iterator, or be a sequence.
    In the second form, the callable is called until it returns the sentinel.
    """
    pass

def len(p_object): # real signature unknown; restored from __doc__
    """
    len(object) -> integer
    
    Return the number of items of a sequence or collection.
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

def map(function, sequence, *sequence_1): # real signature unknown; restored from __doc__
    """
    map(function, sequence[, sequence, ...]) -> list
    
    Return a list of the results of applying the function to the items of
    the argument sequence(s).  If more than one sequence is given, the
    function is called with an argument list consisting of the corresponding
    item of each sequence, substituting None for missing values when not all
    sequences have the same length.  If the function is None, return a list of
    the items of the sequence (or a list of tuples if more than one sequence).
    """
    return []

def max(*args, **kwargs): # known special case of max
    """
    max(iterable[, key=func]) -> value
    max(a, b, c, ...[, key=func]) -> value
    
    With a single iterable argument, return its largest item.
    With two or more arguments, return the largest argument.
    """
    pass

def min(*args, **kwargs): # known special case of min
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
    
    Return the octal representation of an integer or long integer.
    """
    return ""

def open(name, mode=None, buffering=None): # real signature unknown; restored from __doc__
    """
    open(name[, mode[, buffering]]) -> file object
    
    Open a file using the file() type, returns a file object.  This is the
    preferred way to open a file.  See file.__doc__ for further information.
    """
    return file('/dev/null')

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

def print(*args, **kwargs): # known special case of print
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

def range(start=None, stop=None, step=None): # known special case of range
    """
    range(stop) -> list of integers
    range(start, stop[, step]) -> list of integers
    
    Return a list containing an arithmetic progression of integers.
    range(i, j) returns [i, i+1, i+2, ..., j-1]; start (!) defaults to 0.
    When step is given, it specifies the increment (or decrement).
    For example, range(4) returns [0, 1, 2, 3].  The end point is omitted!
    These are exactly the valid indices for a list of 4 elements.
    """
    pass

def raw_input(prompt=None): # real signature unknown; restored from __doc__
    """
    raw_input([prompt]) -> string
    
    Read a string from standard input.  The trailing newline is stripped.
    If the user hits EOF (Unix: Ctl-D, Windows: Ctl-Z+Return), raise EOFError.
    On Unix, GNU readline is used if enabled.  The prompt string, if given,
    is printed without a trailing newline before reading.
    """
    return ""

def reduce(function, sequence, initial=None): # real signature unknown; restored from __doc__
    """
    reduce(function, sequence[, initial]) -> value
    
    Apply a function of two arguments cumulatively to the items of a sequence,
    from left to right, so as to reduce the sequence to a single value.
    For example, reduce(lambda x, y: x+y, [1, 2, 3, 4, 5]) calculates
    ((((1+2)+3)+4)+5).  If initial is present, it is placed before the items
    of the sequence in the calculation, and serves as a default when the
    sequence is empty.
    """
    pass

def reload(module): # real signature unknown; restored from __doc__
    """
    reload(module) -> module
    
    Reload the module.  The module must have been successfully imported before.
    """
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
    round(number[, ndigits]) -> floating point number
    
    Round a number to a given precision in decimal digits (default 0 digits).
    This always returns a floating point number.  Precision may be negative.
    """
    return 0.0

def setattr(p_object, name, value): # real signature unknown; restored from __doc__
    """
    setattr(object, name, value)
    
    Set a named attribute on an object; setattr(x, 'y', v) is equivalent to
    ``x.y = v''.
    """
    pass

def sorted(iterable, cmp=None, key=None, reverse=False): # real signature unknown; restored from __doc__
    """ sorted(iterable, cmp=None, key=None, reverse=False) --> new sorted list """
    pass

def sum(sequence, start=None): # real signature unknown; restored from __doc__
    """
    sum(sequence[, start]) -> value
    
    Return the sum of a sequence of numbers (NOT strings) plus the value
    of parameter 'start' (which defaults to 0).  When the sequence is
    empty, return start.
    """
    pass

def unichr(i): # real signature unknown; restored from __doc__
    """
    unichr(i) -> Unicode character
    
    Return a Unicode string of one character with ordinal i; 0 <= i <= 0x10ffff.
    """
    return u""

def vars(p_object=None): # real signature unknown; restored from __doc__
    """
    vars([object]) -> dictionary
    
    Without arguments, equivalent to locals().
    With an argument, equivalent to object.__dict__.
    """
    return {}

def zip(seq1, seq2, *more_seqs): # known special case of zip
    """
    zip(seq1 [, seq2 [...]]) -> [(seq1[0], seq2[0] ...), (...)]
    
    Return a list of tuples, where each tuple contains the i-th element
    from each of the argument sequences.  The returned list is truncated
    in length to the length of the shortest argument sequence.
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

class ___Classobj:
    '''A mock class representing the old style class base.'''
    __module__ = ''
    __class__ = None

    def __init__(self):
        pass
    __dict__ = {}
    __doc__ = ''


class __generator(object):
    '''A mock class representing the generator function type.'''
    def __init__(self):
        self.gi_code = None
        self.gi_frame = None
        self.gi_running = 0

    def __iter__(self):
        '''Defined to support iteration over container.'''
        pass

    def next(self):
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


class __function(object):
    '''A mock class representing function type.'''

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

        self.__defaults__ = {}
        self.__globals__ = {}
        self.__closure__ = None
        self.__code__ = None
        self.__name__ = ''


class __method(object):
    '''A mock class representing method type.'''

    def __init__(self):

        self.im_class = None
        self.im_self = None
        self.im_func = None

        self.__func__ = None
        self.__self__ = None



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

class object:
    """ The most base type """
    def __delattr__(self, name): # real signature unknown; restored from __doc__
        """ x.__delattr__('name') <==> del x.name """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        """ default object formatter """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self): # known special case of object.__init__
        """ x.__init__(...) initializes x; see help(type(x)) for signature """
        pass

    @staticmethod # known case of __new__
    def __new__(cls, *more): # known special case of object.__new__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
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


class basestring(object):
    """ Type basestring cannot be instantiated; it is the base for str and unicode. """
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass


class int(object):
    """
    int(x=0) -> int or long
    int(x, base=10) -> int or long
    
    Convert a number or string to an integer, or return 0 if no arguments
    are given.  If x is floating point, the conversion truncates towards zero.
    If x is outside the integer range, the function returns a long instead.
    
    If x is not a number or if base is given, then x must be a string or
    Unicode object representing an integer literal in the given base.  The
    literal can be preceded by '+' or '-' and be surrounded by whitespace.
    The base defaults to 10.  Valid bases are 0 and 2-36.  Base 0 means to
    interpret the base from the string as an integer literal.
    >>> int('0b100', base=0)
    4
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

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __and__(self, y): # real signature unknown; restored from __doc__
        """ x.__and__(y) <==> x&y """
        pass

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
        pass

    def __coerce__(self, y): # real signature unknown; restored from __doc__
        """ x.__coerce__(y) <==> coerce(x, y) """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __div__(self, y): # real signature unknown; restored from __doc__
        """ x.__div__(y) <==> x/y """
        pass

    def __float__(self): # real signature unknown; restored from __doc__
        """ x.__float__() <==> float(x) """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__floordiv__(y) <==> x//y """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __hex__(self): # real signature unknown; restored from __doc__
        """ x.__hex__() <==> hex(x) """
        pass

    def __index__(self): # real signature unknown; restored from __doc__
        """ x[y:z] <==> x[y.__index__():z.__index__()] """
        pass

    def __init__(self, x, base=10): # known special case of int.__init__
        """
        int(x=0) -> int or long
        int(x, base=10) -> int or long
        
        Convert a number or string to an integer, or return 0 if no arguments
        are given.  If x is floating point, the conversion truncates towards zero.
        If x is outside the integer range, the function returns a long instead.
        
        If x is not a number or if base is given, then x must be a string or
        Unicode object representing an integer literal in the given base.  The
        literal can be preceded by '+' or '-' and be surrounded by whitespace.
        The base defaults to 10.  Valid bases are 0 and 2-36.  Base 0 means to
        interpret the base from the string as an integer literal.
        >>> int('0b100', base=0)
        4
        # (copied from class doc)
        """
        pass

    def __int__(self): # real signature unknown; restored from __doc__
        """ x.__int__() <==> int(x) """
        pass

    def __invert__(self): # real signature unknown; restored from __doc__
        """ x.__invert__() <==> ~x """
        pass

    def __long__(self): # real signature unknown; restored from __doc__
        """ x.__long__() <==> long(x) """
        pass

    def __lshift__(self, y): # real signature unknown; restored from __doc__
        """ x.__lshift__(y) <==> x<<y """
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

    def __nonzero__(self): # real signature unknown; restored from __doc__
        """ x.__nonzero__() <==> x != 0 """
        pass

    def __oct__(self): # real signature unknown; restored from __doc__
        """ x.__oct__() <==> oct(x) """
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

    def __rdiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdiv__(y) <==> y/x """
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

    denominator = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the denominator of a rational number in lowest terms"""

    imag = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the imaginary part of a complex number"""

    numerator = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the numerator of a rational number in lowest terms"""

    real = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the real part of a complex number"""



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


class buffer(object):
    """
    buffer(object [, offset[, size]])
    
    Create a new buffer object which references the given object.
    The buffer will reference a slice of the target object from the
    start of the object (or at the specified offset). The slice will
    extend to the end of the target object (or with the specified size).
    """
    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
        pass

    def __delitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__delitem__(y) <==> del x[y] """
        pass

    def __delslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__delslice__(i, j) <==> del x[i:j]
                   
                   Use of negative indices is not supported.
        """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __getslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__getslice__(i, j) <==> x[i:j]
                   
                   Use of negative indices is not supported.
        """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, p_object, offset=None, size=None): # real signature unknown; restored from __doc__
        pass

    def __len__(self): # real signature unknown; restored from __doc__
        """ x.__len__() <==> len(x) """
        pass

    def __mul__(self, n): # real signature unknown; restored from __doc__
        """ x.__mul__(n) <==> x*n """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
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

    def __setslice__(self, i, j, y): # real signature unknown; restored from __doc__
        """
        x.__setslice__(i, j, y) <==> x[i:j]=y
                   
                   Use  of negative indices is not supported.
        """
        pass

    def __str__(self): # real signature unknown; restored from __doc__
        """ x.__str__() <==> str(x) """
        pass


class bytearray(object):
    """
    bytearray(iterable_of_ints) -> bytearray.
    bytearray(string, encoding[, errors]) -> bytearray.
    bytearray(bytes_or_bytearray) -> mutable copy of bytes_or_bytearray.
    bytearray(memory_view) -> bytearray.
    
    Construct an mutable bytearray object from:
      - an iterable yielding integers in range(256)
      - a text string encoded using the specified encoding
      - a bytes or a bytearray object
      - any object implementing the buffer API.
    
    bytearray(int) -> bytearray.
    
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
        B.count(sub [,start [,end]]) -> int
        
        Return the number of non-overlapping occurrences of subsection sub in
        bytes B[start:end].  Optional arguments start and end are interpreted
        as in slice notation.
        """
        return 0

    def decode(self, encoding=None, errors=None): # real signature unknown; restored from __doc__
        """
        B.decode([encoding[, errors]]) -> unicode object.
        
        Decodes B using the codec registered for encoding. encoding defaults
        to the default encoding. errors may be given to set a different error
        handling scheme.  Default is 'strict' meaning that encoding errors raise
        a UnicodeDecodeError.  Other possible values are 'ignore' and 'replace'
        as well as any other name registered with codecs.register_error that is
        able to handle UnicodeDecodeErrors.
        """
        return u""

    def endswith(self, suffix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.endswith(suffix [,start [,end]]) -> bool
        
        Return True if B ends with the specified suffix, False otherwise.
        With optional start, test B beginning at that position.
        With optional end, stop comparing B at that position.
        suffix can also be a tuple of strings to try.
        """
        return False

    def expandtabs(self, tabsize=None): # real signature unknown; restored from __doc__
        """
        B.expandtabs([tabsize]) -> copy of B
        
        Return a copy of B where all tab characters are expanded using spaces.
        If tabsize is not given, a tab size of 8 characters is assumed.
        """
        pass

    def extend(self, iterable_int): # real signature unknown; restored from __doc__
        """
        B.extend(iterable int) -> None
        
        Append all the elements from the iterator or sequence to the
        end of B.
        """
        pass

    def find(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.find(sub [,start [,end]]) -> int
        
        Return the lowest index in B where subsection sub is found,
        such that sub is contained within B[start,end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    @classmethod # known case
    def fromhex(cls, string): # real signature unknown; restored from __doc__
        """
        bytearray.fromhex(string) -> bytearray
        
        Create a bytearray object from a string of hexadecimal numbers.
        Spaces between two numbers are accepted.
        Example: bytearray.fromhex('B9 01EF') -> bytearray(b'\xb9\x01\xef').
        """
        return bytearray

    def index(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.index(sub [,start [,end]]) -> int
        
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
        B.join(iterable_of_bytes) -> bytes
        
        Concatenates any number of bytearray objects, with B in between each pair.
        """
        return ""

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
        
        Strip leading bytes contained in the argument.
        If the argument is omitted, strip leading ASCII whitespace.
        """
        return bytearray

    def partition(self, sep): # real signature unknown; restored from __doc__
        """
        B.partition(sep) -> (head, sep, tail)
        
        Searches for the separator sep in B, and returns the part before it,
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
        
        Remove the first occurance of a value in B.
        """
        pass

    def replace(self, old, new, count=None): # real signature unknown; restored from __doc__
        """
        B.replace(old, new[, count]) -> bytes
        
        Return a copy of B with all occurrences of subsection
        old replaced by new.  If the optional argument count is
        given, only the first count occurrences are replaced.
        """
        return ""

    def reverse(self): # real signature unknown; restored from __doc__
        """
        B.reverse() -> None
        
        Reverse the order of the values in B in place.
        """
        pass

    def rfind(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.rfind(sub [,start [,end]]) -> int
        
        Return the highest index in B where subsection sub is found,
        such that sub is contained within B[start,end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def rindex(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.rindex(sub [,start [,end]]) -> int
        
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
        
        Searches for the separator sep in B, starting at the end of B,
        and returns the part before it, the separator itself, and the
        part after it.  If the separator is not found, returns two empty
        bytearray objects and B.
        """
        pass

    def rsplit(self, sep, maxsplit=None): # real signature unknown; restored from __doc__
        """
        B.rsplit(sep[, maxsplit]) -> list of bytearray
        
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
        
        Strip trailing bytes contained in the argument.
        If the argument is omitted, strip trailing ASCII whitespace.
        """
        return bytearray

    def split(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        B.split([sep[, maxsplit]]) -> list of bytearray
        
        Return a list of the sections in B, using sep as the delimiter.
        If sep is not given, B is split on ASCII whitespace characters
        (space, tab, return, newline, formfeed, vertical tab).
        If maxsplit is given, at most maxsplit splits are done.
        """
        return []

    def splitlines(self, keepends=False): # real signature unknown; restored from __doc__
        """
        B.splitlines(keepends=False) -> list of lines
        
        Return a list of the lines in B, breaking at line boundaries.
        Line breaks are not included in the resulting list unless keepends
        is given and true.
        """
        return []

    def startswith(self, prefix, start=None, end=None): # real signature unknown; restored from __doc__
        """
        B.startswith(prefix [,start [,end]]) -> bool
        
        Return True if B starts with the specified prefix, False otherwise.
        With optional start, test B beginning at that position.
        With optional end, stop comparing B at that position.
        prefix can also be a tuple of strings to try.
        """
        return False

    def strip(self, bytes=None): # real signature unknown; restored from __doc__
        """
        B.strip([bytes]) -> bytearray
        
        Strip leading and trailing bytes contained in the argument.
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
        
        Returns the number of bytes actually allocated.
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
        bytearray(iterable_of_ints) -> bytearray.
        bytearray(string, encoding[, errors]) -> bytearray.
        bytearray(bytes_or_bytearray) -> mutable copy of bytes_or_bytearray.
        bytearray(memory_view) -> bytearray.
        
        Construct an mutable bytearray object from:
          - an iterable yielding integers in range(256)
          - a text string encoded using the specified encoding
          - a bytes or a bytearray object
          - any object implementing the buffer API.
        
        bytearray(int) -> bytearray.
        
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


class str(basestring):
    """
    str(object='') -> string
    
    Return a nice string representation of the object.
    If the argument is a string, the return value is the same object.
    """
    def capitalize(self): # real signature unknown; restored from __doc__
        """
        S.capitalize() -> string
        
        Return a copy of the string S with only its first character
        capitalized.
        """
        return ""

    def center(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.center(width[, fillchar]) -> string
        
        Return S centered in a string of length width. Padding is
        done using the specified fill character (default is a space)
        """
        return ""

    def count(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.count(sub[, start[, end]]) -> int
        
        Return the number of non-overlapping occurrences of substring sub in
        string S[start:end].  Optional arguments start and end are interpreted
        as in slice notation.
        """
        return 0

    def decode(self, encoding=None, errors=None): # real signature unknown; restored from __doc__
        """
        S.decode([encoding[,errors]]) -> object
        
        Decodes S using the codec registered for encoding. encoding defaults
        to the default encoding. errors may be given to set a different error
        handling scheme. Default is 'strict' meaning that encoding errors raise
        a UnicodeDecodeError. Other possible values are 'ignore' and 'replace'
        as well as any other name registered with codecs.register_error that is
        able to handle UnicodeDecodeErrors.
        """
        return object()

    def encode(self, encoding=None, errors=None): # real signature unknown; restored from __doc__
        """
        S.encode([encoding[,errors]]) -> object
        
        Encodes S using the codec registered for encoding. encoding defaults
        to the default encoding. errors may be given to set a different error
        handling scheme. Default is 'strict' meaning that encoding errors raise
        a UnicodeEncodeError. Other possible values are 'ignore', 'replace' and
        'xmlcharrefreplace' as well as any other name registered with
        codecs.register_error that is able to handle UnicodeEncodeErrors.
        """
        return object()

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
        S.expandtabs([tabsize]) -> string
        
        Return a copy of S where all tab characters are expanded using spaces.
        If tabsize is not given, a tab size of 8 characters is assumed.
        """
        return ""

    def find(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.find(sub [,start [,end]]) -> int
        
        Return the lowest index in S where substring sub is found,
        such that sub is contained within S[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def format(self, *args, **kwargs): # known special case of str.format
        """
        S.format(*args, **kwargs) -> string
        
        Return a formatted version of S, using substitutions from args and kwargs.
        The substitutions are identified by braces ('{' and '}').
        """
        pass

    def index(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.index(sub [,start [,end]]) -> int
        
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

    def isdigit(self): # real signature unknown; restored from __doc__
        """
        S.isdigit() -> bool
        
        Return True if all characters in S are digits
        and there is at least one character in S, False otherwise.
        """
        return False

    def islower(self): # real signature unknown; restored from __doc__
        """
        S.islower() -> bool
        
        Return True if all cased characters in S are lowercase and there is
        at least one cased character in S, False otherwise.
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
        character in S, i.e. uppercase characters may only follow uncased
        characters and lowercase characters only cased ones. Return False
        otherwise.
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
        S.join(iterable) -> string
        
        Return a string which is the concatenation of the strings in the
        iterable.  The separator between elements is S.
        """
        return ""

    def ljust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.ljust(width[, fillchar]) -> string
        
        Return S left-justified in a string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        return ""

    def lower(self): # real signature unknown; restored from __doc__
        """
        S.lower() -> string
        
        Return a copy of the string S converted to lowercase.
        """
        return ""

    def lstrip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.lstrip([chars]) -> string or unicode
        
        Return a copy of the string S with leading whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        If chars is unicode, S will be converted to unicode before stripping
        """
        return ""

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
        S.replace(old, new[, count]) -> string
        
        Return a copy of string S with all occurrences of substring
        old replaced by new.  If the optional argument count is
        given, only the first count occurrences are replaced.
        """
        return ""

    def rfind(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.rfind(sub [,start [,end]]) -> int
        
        Return the highest index in S where substring sub is found,
        such that sub is contained within S[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def rindex(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.rindex(sub [,start [,end]]) -> int
        
        Like S.rfind() but raise ValueError when the substring is not found.
        """
        return 0

    def rjust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.rjust(width[, fillchar]) -> string
        
        Return S right-justified in a string of length width. Padding is
        done using the specified fill character (default is a space)
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
        S.rsplit([sep [,maxsplit]]) -> list of strings
        
        Return a list of the words in the string S, using sep as the
        delimiter string, starting at the end of the string and working
        to the front.  If maxsplit is given, at most maxsplit splits are
        done. If sep is not specified or is None, any whitespace string
        is a separator.
        """
        return []

    def rstrip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.rstrip([chars]) -> string or unicode
        
        Return a copy of the string S with trailing whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        If chars is unicode, S will be converted to unicode before stripping
        """
        return ""

    def split(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        S.split([sep [,maxsplit]]) -> list of strings
        
        Return a list of the words in the string S, using sep as the
        delimiter string.  If maxsplit is given, at most maxsplit
        splits are done. If sep is not specified or is None, any
        whitespace string is a separator and empty strings are removed
        from the result.
        """
        return []

    def splitlines(self, keepends=False): # real signature unknown; restored from __doc__
        """
        S.splitlines(keepends=False) -> list of strings
        
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
        S.strip([chars]) -> string or unicode
        
        Return a copy of the string S with leading and trailing
        whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        If chars is unicode, S will be converted to unicode before stripping
        """
        return ""

    def swapcase(self): # real signature unknown; restored from __doc__
        """
        S.swapcase() -> string
        
        Return a copy of the string S with uppercase characters
        converted to lowercase and vice versa.
        """
        return ""

    def title(self): # real signature unknown; restored from __doc__
        """
        S.title() -> string
        
        Return a titlecased version of S, i.e. words start with uppercase
        characters, all remaining cased characters have lowercase.
        """
        return ""

    def translate(self, table, deletechars=None): # real signature unknown; restored from __doc__
        """
        S.translate(table [,deletechars]) -> string
        
        Return a copy of the string S, where all characters occurring
        in the optional argument deletechars are removed, and the
        remaining characters have been mapped through the given
        translation table, which must be a string of length 256 or None.
        If the table argument is None, no translation is applied and
        the operation simply removes the characters in deletechars.
        """
        return ""

    def upper(self): # real signature unknown; restored from __doc__
        """
        S.upper() -> string
        
        Return a copy of the string S converted to uppercase.
        """
        return ""

    def zfill(self, width): # real signature unknown; restored from __doc__
        """
        S.zfill(width) -> string
        
        Pad a numeric string S with zeros on the left, to fill a field
        of the specified width.  The string S is never truncated.
        """
        return ""

    def _formatter_field_name_split(self, *args, **kwargs): # real signature unknown
        pass

    def _formatter_parser(self, *args, **kwargs): # real signature unknown
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

    def __format__(self, format_spec): # real signature unknown; restored from __doc__
        """
        S.__format__(format_spec) -> string
        
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

    def __getslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__getslice__(i, j) <==> x[i:j]
                   
                   Use of negative indices is not supported.
        """
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

    def __init__(self, string=''): # known special case of str.__init__
        """
        str(object='') -> string
        
        Return a nice string representation of the object.
        If the argument is a string, the return value is the same object.
        # (copied from class doc)
        """
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


bytes = str


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

    __func__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class complex(object):
    """
    complex(real[, imag]) -> complex number
    
    Create a complex number from a real part and an optional imaginary part.
    This is equivalent to (real + imag*1j) where imag defaults to 0.
    """
    def conjugate(self): # real signature unknown; restored from __doc__
        """
        complex.conjugate() -> complex
        
        Return the complex conjugate of its argument. (3-4j).conjugate() == 3+4j.
        """
        return complex

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __coerce__(self, y): # real signature unknown; restored from __doc__
        """ x.__coerce__(y) <==> coerce(x, y) """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __div__(self, y): # real signature unknown; restored from __doc__
        """ x.__div__(y) <==> x/y """
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
        
        Convert to a string according to format_spec.
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

    def __long__(self): # real signature unknown; restored from __doc__
        """ x.__long__() <==> long(x) """
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

    def __nonzero__(self): # real signature unknown; restored from __doc__
        """ x.__nonzero__() <==> x != 0 """
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

    def __rdiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdiv__(y) <==> y/x """
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
    """the imaginary part of a complex number

    :type: float
    """

    real = property(lambda self: 0.0)
    """the real part of a complex number

    :type: float
    """



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

    def has_key(self, k): # real signature unknown; restored from __doc__
        """ D.has_key(k) -> True if D has a key k, else False """
        return False

    def items(self): # real signature unknown; restored from __doc__
        """ D.items() -> list of D's (key, value) pairs, as 2-tuples """
        return []

    def iteritems(self): # real signature unknown; restored from __doc__
        """ D.iteritems() -> an iterator over the (key, value) items of D """
        pass

    def iterkeys(self): # real signature unknown; restored from __doc__
        """ D.iterkeys() -> an iterator over the keys of D """
        pass

    def itervalues(self): # real signature unknown; restored from __doc__
        """ D.itervalues() -> an iterator over the values of D """
        pass

    def keys(self): # real signature unknown; restored from __doc__
        """ D.keys() -> list of D's keys """
        return []

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
        D.update([E, ]**F) -> None.  Update D from dict/iterable E and F.
        If E present and has a .keys() method, does:     for k in E: D[k] = E[k]
        If E present and lacks .keys() method, does:     for (k, v) in E: D[k] = v
        In either case, this is followed by: for k in F: D[k] = F[k]
        """
        pass

    def values(self): # real signature unknown; restored from __doc__
        """ D.values() -> list of D's values """
        return []

    def viewitems(self): # real signature unknown; restored from __doc__
        """ D.viewitems() -> a set-like object providing a view on D's items """
        pass

    def viewkeys(self): # real signature unknown; restored from __doc__
        """ D.viewkeys() -> a set-like object providing a view on D's keys """
        pass

    def viewvalues(self): # real signature unknown; restored from __doc__
        """ D.viewvalues() -> an object providing a view on D's values """
        pass

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
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
    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

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


class file(object):
    """
    file(name[, mode[, buffering]]) -> file object
    
    Open a file.  The mode can be 'r', 'w' or 'a' for reading (default),
    writing or appending.  The file will be created if it doesn't exist
    when opened for writing or appending; it will be truncated when
    opened for writing.  Add a 'b' to the mode for binary files.
    Add a '+' to the mode to allow simultaneous reading and writing.
    If the buffering argument is given, 0 means unbuffered, 1 means line
    buffered, and larger numbers specify the buffer size.  The preferred way
    to open a file is with the builtin open() function.
    Add a 'U' to mode to open the file for input with universal newline
    support.  Any line ending in the input file will be seen as a '\n'
    in Python.  Also, a file so opened gains the attribute 'newlines';
    the value for this attribute is one of None (no newline read yet),
    '\r', '\n', '\r\n' or a tuple containing all the newline types seen.
    
    'U' cannot be combined with 'w' or '+' mode.
    """
    def close(self): # real signature unknown; restored from __doc__
        """
        close() -> None or (perhaps) an integer.  Close the file.
        
        Sets data attribute .closed to True.  A closed file cannot be used for
        further I/O operations.  close() may be called more than once without
        error.  Some kinds of file objects (for example, opened by popen())
        may return an exit status upon closing.
        """
        pass

    def fileno(self): # real signature unknown; restored from __doc__
        """
        fileno() -> integer "file descriptor".
        
        This is needed for lower-level file interfaces, such os.read().
        """
        return 0

    def flush(self): # real signature unknown; restored from __doc__
        """ flush() -> None.  Flush the internal I/O buffer. """
        pass

    def isatty(self): # real signature unknown; restored from __doc__
        """ isatty() -> true or false.  True if the file is connected to a tty device. """
        return False

    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

    def read(self, size=None): # real signature unknown; restored from __doc__
        """
        read([size]) -> read at most size bytes, returned as a string.
        
        If the size argument is negative or omitted, read until EOF is reached.
        Notice that when in non-blocking mode, less data than what was requested
        may be returned, even if no size parameter was given.
        """
        pass

    def readinto(self): # real signature unknown; restored from __doc__
        """ readinto() -> Undocumented.  Don't use this; it may go away. """
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

    def seek(self, offset, whence=None): # real signature unknown; restored from __doc__
        """
        seek(offset[, whence]) -> None.  Move to new file position.
        
        Argument offset is a byte count.  Optional argument whence defaults to
        0 (offset from start of file, offset should be >= 0); other values are 1
        (move relative to current position, positive or negative), and 2 (move
        relative to end of file, usually negative, although many platforms allow
        seeking beyond the end of a file).  If the file is opened in text mode,
        only offsets returned by tell() are legal.  Use of other offsets causes
        undefined behavior.
        Note that not all file objects are seekable.
        """
        pass

    def tell(self): # real signature unknown; restored from __doc__
        """ tell() -> current file position, an integer (may be a long integer). """
        pass

    def truncate(self, size=None): # real signature unknown; restored from __doc__
        """
        truncate([size]) -> None.  Truncate the file to at most size bytes.
        
        Size defaults to the current file position, as returned by tell().
        """
        pass

    def write(self, p_str): # real signature unknown; restored from __doc__
        """
        write(str) -> None.  Write string str to file.
        
        Note that due to buffering, flush() or close() may be needed before
        the file on disk reflects the data written.
        """
        pass

    def writelines(self, sequence_of_strings): # real signature unknown; restored from __doc__
        """
        writelines(sequence_of_strings) -> None.  Write the strings to the file.
        
        Note that newlines are not added.  The sequence can be any iterable object
        producing strings. This is equivalent to calling write() for each string.
        """
        pass

    def xreadlines(self): # real signature unknown; restored from __doc__
        """
        xreadlines() -> returns self.
        
        For backward compatibility. File objects now include the performance
        optimizations previously implemented in the xreadlines module.
        """
        pass

    def __delattr__(self, name): # real signature unknown; restored from __doc__
        """ x.__delattr__('name') <==> del x.name """
        pass

    def __enter__(self): # real signature unknown; restored from __doc__
        """ __enter__() -> self. """
        return self

    def __exit__(self, *excinfo): # real signature unknown; restored from __doc__
        """ __exit__(*excinfo) -> None.  Closes the file. """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __init__(self, name, mode=None, buffering=None): # real signature unknown; restored from __doc__
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    def __setattr__(self, name, value): # real signature unknown; restored from __doc__
        """ x.__setattr__('name', value) <==> x.name = value """
        pass

    closed = property(lambda self: True)
    """True if the file is closed

    :type: bool
    """

    encoding = property(lambda self: '')
    """file encoding

    :type: string
    """

    errors = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """Unicode error handler"""

    mode = property(lambda self: '')
    """file mode ('r', 'U', 'w', 'a', possibly with 'b' or '+' added)

    :type: string
    """

    name = property(lambda self: '')
    """file name

    :type: string
    """

    newlines = property(lambda self: '')
    """end-of-line convention used in this file

    :type: string
    """

    softspace = property(lambda self: True)
    """flag indicating that a space needs to be printed; used by print

    :type: bool
    """



class float(object):
    """
    float(x) -> floating point number
    
    Convert a string or number to a floating point number, if possible.
    """
    def as_integer_ratio(self): # real signature unknown; restored from __doc__
        """
        float.as_integer_ratio() -> (int, int)
        
        Return a pair of integers, whose ratio is exactly equal to the original
        float and with a positive denominator.
        Raise OverflowError on infinities and a ValueError on NaNs.
        
        >>> (10.0).as_integer_ratio()
        (10, 1)
        >>> (0.0).as_integer_ratio()
        (0, 1)
        >>> (-.25).as_integer_ratio()
        (-1, 4)
        """
        pass

    def conjugate(self, *args, **kwargs): # real signature unknown
        """ Return self, the complex conjugate of any float. """
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
        """ Return True if the float is an integer. """
        pass

    def __abs__(self): # real signature unknown; restored from __doc__
        """ x.__abs__() <==> abs(x) """
        pass

    def __add__(self, y): # real signature unknown; restored from __doc__
        """ x.__add__(y) <==> x+y """
        pass

    def __coerce__(self, y): # real signature unknown; restored from __doc__
        """ x.__coerce__(y) <==> coerce(x, y) """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __div__(self, y): # real signature unknown; restored from __doc__
        """ x.__div__(y) <==> x/y """
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

    def __long__(self): # real signature unknown; restored from __doc__
        """ x.__long__() <==> long(x) """
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

    def __nonzero__(self): # real signature unknown; restored from __doc__
        """ x.__nonzero__() <==> x != 0 """
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

    def __rdiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdiv__(y) <==> y/x """
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

    def __setformat__(self, typestr, fmt): # real signature unknown; restored from __doc__
        """
        float.__setformat__(typestr, fmt) -> None
        
        You probably don't want to use this function.  It exists mainly to be
        used in Python's test suite.
        
        typestr must be 'double' or 'float'.  fmt must be one of 'unknown',
        'IEEE, big-endian' or 'IEEE, little-endian', and in addition can only be
        one of the latter two if it appears to match the underlying C reality.
        
        Override the automatic determination of C-level floating point type.
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
        """ Return the Integral closest to x between 0 and x. """
        pass

    imag = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the imaginary part of a complex number"""

    real = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the real part of a complex number"""



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
        Return the intersection of two or more sets as a new set.
        
        (i.e. elements that are common to all of the sets.)
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

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
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

    def sort(self, cmp=None, key=None, reverse=False): # real signature unknown; restored from __doc__
        """
        L.sort(cmp=None, key=None, reverse=False) -- stable sort *IN PLACE*;
        cmp(x, y) -> -1, 0, 1
        """
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

    def __delslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__delslice__(i, j) <==> del x[i:j]
                   
                   Use of negative indices is not supported.
        """
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

    def __getslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__getslice__(i, j) <==> x[i:j]
                   
                   Use of negative indices is not supported.
        """
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

    def __setslice__(self, i, j, y): # real signature unknown; restored from __doc__
        """
        x.__setslice__(i, j, y) <==> x[i:j]=y
                   
                   Use  of negative indices is not supported.
        """
        pass

    def __sizeof__(self): # real signature unknown; restored from __doc__
        """ L.__sizeof__() -- size of L in memory, in bytes """
        pass

    __hash__ = None


class long(object):
    """
    long(x=0) -> long
    long(x, base=10) -> long
    
    Convert a number or string to a long integer, or return 0L if no arguments
    are given.  If x is floating point, the conversion truncates towards zero.
    
    If x is not a number or if base is given, then x must be a string or
    Unicode object representing an integer literal in the given base.  The
    literal can be preceded by '+' or '-' and be surrounded by whitespace.
    The base defaults to 10.  Valid bases are 0 and 2-36.  Base 0 means to
    interpret the base from the string as an integer literal.
    >>> int('0b100', base=0)
    4L
    """
    def bit_length(self): # real signature unknown; restored from __doc__
        """
        long.bit_length() -> int or long
        
        Number of bits necessary to represent self in binary.
        >>> bin(37L)
        '0b100101'
        >>> (37L).bit_length()
        6
        """
        return 0

    def conjugate(self, *args, **kwargs): # real signature unknown
        """ Returns self, the complex conjugate of any long. """
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

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
        pass

    def __coerce__(self, y): # real signature unknown; restored from __doc__
        """ x.__coerce__(y) <==> coerce(x, y) """
        pass

    def __divmod__(self, y): # real signature unknown; restored from __doc__
        """ x.__divmod__(y) <==> divmod(x, y) """
        pass

    def __div__(self, y): # real signature unknown; restored from __doc__
        """ x.__div__(y) <==> x/y """
        pass

    def __float__(self): # real signature unknown; restored from __doc__
        """ x.__float__() <==> float(x) """
        pass

    def __floordiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__floordiv__(y) <==> x//y """
        pass

    def __format__(self, *args, **kwargs): # real signature unknown
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __hex__(self): # real signature unknown; restored from __doc__
        """ x.__hex__() <==> hex(x) """
        pass

    def __index__(self): # real signature unknown; restored from __doc__
        """ x[y:z] <==> x[y.__index__():z.__index__()] """
        pass

    def __init__(self, x=0): # real signature unknown; restored from __doc__
        pass

    def __int__(self): # real signature unknown; restored from __doc__
        """ x.__int__() <==> int(x) """
        pass

    def __invert__(self): # real signature unknown; restored from __doc__
        """ x.__invert__() <==> ~x """
        pass

    def __long__(self): # real signature unknown; restored from __doc__
        """ x.__long__() <==> long(x) """
        pass

    def __lshift__(self, y): # real signature unknown; restored from __doc__
        """ x.__lshift__(y) <==> x<<y """
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

    def __nonzero__(self): # real signature unknown; restored from __doc__
        """ x.__nonzero__() <==> x != 0 """
        pass

    def __oct__(self): # real signature unknown; restored from __doc__
        """ x.__oct__() <==> oct(x) """
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

    def __rdiv__(self, y): # real signature unknown; restored from __doc__
        """ x.__rdiv__(y) <==> y/x """
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

    denominator = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the denominator of a rational number in lowest terms"""

    imag = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the imaginary part of a complex number"""

    numerator = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the numerator of a rational number in lowest terms"""

    real = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """the real part of a complex number"""



class memoryview(object):
    """
    memoryview(object)
    
    Create a new memoryview object which references the given object.
    """
    def tobytes(self, *args, **kwargs): # real signature unknown
        pass

    def tolist(self, *args, **kwargs): # real signature unknown
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

    format = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    itemsize = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    ndim = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    readonly = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    shape = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    strides = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    suboffsets = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



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
        def x(self):
            "I am the 'x' property."
            return self._x
        @x.setter
        def x(self, value):
            self._x = value
        @x.deleter
        def x(self):
            del self._x
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
            def x(self):
                "I am the 'x' property."
                return self._x
            @x.setter
            def x(self, value):
                self._x = value
            @x.deleter
            def x(self):
                del self._x
        
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

    fdel = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    fget = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default

    fset = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class reversed(object):
    """
    reversed(sequence) -> reverse iterator over values of the sequence
    
    Return a reverse iterator
    """
    def next(self): # real signature unknown; restored from __doc__
        """ x.next() -> the next value, or raise StopIteration """
        pass

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
        Return the intersection of two or more sets as a new set.
        
        (i.e. elements that are common to all of the sets.)
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

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
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
        """ x.__iand__(y) <==> x&=y """
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
        """ x.__ior__(y) <==> x|=y """
        pass

    def __isub__(self, y): # real signature unknown; restored from __doc__
        """ x.__isub__(y) <==> x-=y """
        pass

    def __iter__(self): # real signature unknown; restored from __doc__
        """ x.__iter__() <==> iter(x) """
        pass

    def __ixor__(self, y): # real signature unknown; restored from __doc__
        """ x.__ixor__(y) <==> x^=y """
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
    slice(stop)
    slice(start, stop[, step])
    
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

    def __cmp__(self, y): # real signature unknown; restored from __doc__
        """ x.__cmp__(y) <==> cmp(x,y) """
        pass

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __hash__(self): # real signature unknown; restored from __doc__
        """ x.__hash__() <==> hash(x) """
        pass

    def __init__(self, stop): # real signature unknown; restored from __doc__
        pass

    @staticmethod # known case of __new__
    def __new__(S, *more): # real signature unknown; restored from __doc__
        """ T.__new__(S, ...) -> a new object with type S, a subtype of T """
        pass

    def __reduce__(self, *args, **kwargs): # real signature unknown
        """ Return state information for pickling. """
        pass

    def __repr__(self): # real signature unknown; restored from __doc__
        """ x.__repr__() <==> repr(x) """
        pass

    start = property(lambda self: 0)
    """:type: int"""

    step = property(lambda self: 0)
    """:type: int"""

    stop = property(lambda self: 0)
    """:type: int"""



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

    __func__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default



class super(object):
    """
    super(type, obj) -> bound super object; requires isinstance(obj, type)
    super(type) -> unbound super object
    super(type, type2) -> bound super object; requires issubclass(type2, type)
    Typical use to call a cooperative superclass method:
    class C(B):
        def meth(self, arg):
            super(C, self).meth(arg)
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __get__(self, obj, type=None): # real signature unknown; restored from __doc__
        """ descr.__get__(obj[, type]) -> value """
        pass

    def __init__(self, type1, type2=None): # known special case of super.__init__
        """
        super(type, obj) -> bound super object; requires isinstance(obj, type)
        super(type) -> unbound super object
        super(type, type2) -> bound super object; requires issubclass(type2, type)
        Typical use to call a cooperative superclass method:
        class C(B):
            def meth(self, arg):
                super(C, self).meth(arg)
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
    """the type of the instance invoking super(); may be None

    :type: type
    """

    __self__ = property(lambda self: type(object))
    """the instance invoking super(); may be None

    :type: type
    """

    __thisclass__ = property(lambda self: type(object))
    """the class invoking super()

    :type: type
    """



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

    def __getslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__getslice__(i, j) <==> x[i:j]
                   
                   Use of negative indices is not supported.
        """
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

    __abstractmethods__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default


    __bases__ = (
        object,
    )
    __base__ = object
    __basicsize__ = 872
    __dictoffset__ = 264
    __dict__ = None # (!) real value is ''
    __flags__ = 2148423147
    __itemsize__ = 40
    __mro__ = (
        None, # (!) forward: type, real value is ''
        object,
    )
    __name__ = 'type'
    __weakrefoffset__ = 368


class unicode(basestring):
    """
    unicode(object='') -> unicode object
    unicode(string[, encoding[, errors]]) -> unicode object
    
    Create a new Unicode object from the given encoded string.
    encoding defaults to the current default string encoding.
    errors can be 'strict', 'replace' or 'ignore' and defaults to 'strict'.
    """
    def capitalize(self): # real signature unknown; restored from __doc__
        """
        S.capitalize() -> unicode
        
        Return a capitalized version of S, i.e. make the first character
        have upper case and the rest lower case.
        """
        return u""

    def center(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.center(width[, fillchar]) -> unicode
        
        Return S centered in a Unicode string of length width. Padding is
        done using the specified fill character (default is a space)
        """
        return u""

    def count(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.count(sub[, start[, end]]) -> int
        
        Return the number of non-overlapping occurrences of substring sub in
        Unicode string S[start:end].  Optional arguments start and end are
        interpreted as in slice notation.
        """
        return 0

    def decode(self, encoding=None, errors=None): # real signature unknown; restored from __doc__
        """
        S.decode([encoding[,errors]]) -> string or unicode
        
        Decodes S using the codec registered for encoding. encoding defaults
        to the default encoding. errors may be given to set a different error
        handling scheme. Default is 'strict' meaning that encoding errors raise
        a UnicodeDecodeError. Other possible values are 'ignore' and 'replace'
        as well as any other name registered with codecs.register_error that is
        able to handle UnicodeDecodeErrors.
        """
        return ""

    def encode(self, encoding=None, errors=None): # real signature unknown; restored from __doc__
        """
        S.encode([encoding[,errors]]) -> string or unicode
        
        Encodes S using the codec registered for encoding. encoding defaults
        to the default encoding. errors may be given to set a different error
        handling scheme. Default is 'strict' meaning that encoding errors raise
        a UnicodeEncodeError. Other possible values are 'ignore', 'replace' and
        'xmlcharrefreplace' as well as any other name registered with
        codecs.register_error that can handle UnicodeEncodeErrors.
        """
        return ""

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
        S.expandtabs([tabsize]) -> unicode
        
        Return a copy of S where all tab characters are expanded using spaces.
        If tabsize is not given, a tab size of 8 characters is assumed.
        """
        return u""

    def find(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.find(sub [,start [,end]]) -> int
        
        Return the lowest index in S where substring sub is found,
        such that sub is contained within S[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def format(self, *args, **kwargs): # known special case of unicode.format
        """
        S.format(*args, **kwargs) -> unicode
        
        Return a formatted version of S, using substitutions from args and kwargs.
        The substitutions are identified by braces ('{' and '}').
        """
        pass

    def index(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.index(sub [,start [,end]]) -> int
        
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
        S.join(iterable) -> unicode
        
        Return a string which is the concatenation of the strings in the
        iterable.  The separator between elements is S.
        """
        return u""

    def ljust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.ljust(width[, fillchar]) -> int
        
        Return S left-justified in a Unicode string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        return 0

    def lower(self): # real signature unknown; restored from __doc__
        """
        S.lower() -> unicode
        
        Return a copy of the string S converted to lowercase.
        """
        return u""

    def lstrip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.lstrip([chars]) -> unicode
        
        Return a copy of the string S with leading whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        If chars is a str, it will be converted to unicode before stripping
        """
        return u""

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
        S.replace(old, new[, count]) -> unicode
        
        Return a copy of S with all occurrences of substring
        old replaced by new.  If the optional argument count is
        given, only the first count occurrences are replaced.
        """
        return u""

    def rfind(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.rfind(sub [,start [,end]]) -> int
        
        Return the highest index in S where substring sub is found,
        such that sub is contained within S[start:end].  Optional
        arguments start and end are interpreted as in slice notation.
        
        Return -1 on failure.
        """
        return 0

    def rindex(self, sub, start=None, end=None): # real signature unknown; restored from __doc__
        """
        S.rindex(sub [,start [,end]]) -> int
        
        Like S.rfind() but raise ValueError when the substring is not found.
        """
        return 0

    def rjust(self, width, fillchar=None): # real signature unknown; restored from __doc__
        """
        S.rjust(width[, fillchar]) -> unicode
        
        Return S right-justified in a Unicode string of length width. Padding is
        done using the specified fill character (default is a space).
        """
        return u""

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
        S.rsplit([sep [,maxsplit]]) -> list of strings
        
        Return a list of the words in S, using sep as the
        delimiter string, starting at the end of the string and
        working to the front.  If maxsplit is given, at most maxsplit
        splits are done. If sep is not specified, any whitespace string
        is a separator.
        """
        return []

    def rstrip(self, chars=None): # real signature unknown; restored from __doc__
        """
        S.rstrip([chars]) -> unicode
        
        Return a copy of the string S with trailing whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        If chars is a str, it will be converted to unicode before stripping
        """
        return u""

    def split(self, sep=None, maxsplit=None): # real signature unknown; restored from __doc__
        """
        S.split([sep [,maxsplit]]) -> list of strings
        
        Return a list of the words in S, using sep as the
        delimiter string.  If maxsplit is given, at most maxsplit
        splits are done. If sep is not specified or is None, any
        whitespace string is a separator and empty strings are
        removed from the result.
        """
        return []

    def splitlines(self, keepends=False): # real signature unknown; restored from __doc__
        """
        S.splitlines(keepends=False) -> list of strings
        
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
        S.strip([chars]) -> unicode
        
        Return a copy of the string S with leading and trailing
        whitespace removed.
        If chars is given and not None, remove characters in chars instead.
        If chars is a str, it will be converted to unicode before stripping
        """
        return u""

    def swapcase(self): # real signature unknown; restored from __doc__
        """
        S.swapcase() -> unicode
        
        Return a copy of S with uppercase characters converted to lowercase
        and vice versa.
        """
        return u""

    def title(self): # real signature unknown; restored from __doc__
        """
        S.title() -> unicode
        
        Return a titlecased version of S, i.e. words start with title case
        characters, all remaining cased characters have lower case.
        """
        return u""

    def translate(self, table): # real signature unknown; restored from __doc__
        """
        S.translate(table) -> unicode
        
        Return a copy of the string S, where all characters have been mapped
        through the given translation table, which must be a mapping of
        Unicode ordinals to Unicode ordinals, Unicode strings or None.
        Unmapped characters are left untouched. Characters mapped to None
        are deleted.
        """
        return u""

    def upper(self): # real signature unknown; restored from __doc__
        """
        S.upper() -> unicode
        
        Return a copy of S converted to uppercase.
        """
        return u""

    def zfill(self, width): # real signature unknown; restored from __doc__
        """
        S.zfill(width) -> unicode
        
        Pad a numeric string S with zeros on the left, to fill a field
        of the specified width. The string S is never truncated.
        """
        return u""

    def _formatter_field_name_split(self, *args, **kwargs): # real signature unknown
        pass

    def _formatter_parser(self, *args, **kwargs): # real signature unknown
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

    def __format__(self, format_spec): # real signature unknown; restored from __doc__
        """
        S.__format__(format_spec) -> unicode
        
        Return a formatted version of S as described by format_spec.
        """
        return u""

    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __getnewargs__(self, *args, **kwargs): # real signature unknown
        pass

    def __getslice__(self, i, j): # real signature unknown; restored from __doc__
        """
        x.__getslice__(i, j) <==> x[i:j]
                   
                   Use of negative indices is not supported.
        """
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

    def __init__(self, string=u'', encoding=None, errors='strict'): # known special case of unicode.__init__
        """
        unicode(object='') -> unicode object
        unicode(string[, encoding[, errors]]) -> unicode object
        
        Create a new Unicode object from the given encoded string.
        encoding defaults to the current default string encoding.
        errors can be 'strict', 'replace' or 'ignore' and defaults to 'strict'.
        # (copied from class doc)
        """
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


class xrange(object):
    """
    xrange(stop) -> xrange object
    xrange(start, stop[, step]) -> xrange object
    
    Like range(), but instead of returning a list, returns an object that
    generates the numbers in the range on demand.  For looping, this is 
    slightly faster than range() and more memory efficient.
    """
    def __getattribute__(self, name): # real signature unknown; restored from __doc__
        """ x.__getattribute__('name') <==> x.name """
        pass

    def __getitem__(self, y): # real signature unknown; restored from __doc__
        """ x.__getitem__(y) <==> x[y] """
        pass

    def __init__(self, stop): # real signature unknown; restored from __doc__
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


# variables with complex values

Ellipsis = None # (!) real value is ''

NotImplemented = None # (!) real value is ''

