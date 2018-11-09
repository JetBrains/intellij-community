# encoding: utf-8
# module cPickle
# from (built-in)
# by generator 1.147
""" C implementation and optimization of the Python pickle module. """

# imports
import __builtin__ as __builtins__ # <module '__builtin__' (built-in)>

# Variables with simple values

format_version = '2.0'

HIGHEST_PROTOCOL = 2

__version__ = '1.71'

# functions

def dump(obj, file, protocol=0): # real signature unknown; restored from __doc__
    """
    dump(obj, file, protocol=0) -- Write an object in pickle format to the given file.
    
    See the Pickler docstring for the meaning of optional argument proto.
    """
    pass

def dumps(obj, protocol=0): # real signature unknown; restored from __doc__
    """
    dumps(obj, protocol=0) -- Return a string containing an object in pickle format.
    
    See the Pickler docstring for the meaning of optional argument proto.
    """
    pass

def load(file): # real signature unknown; restored from __doc__
    """ load(file) -- Load a pickle from the given file """
    pass

def loads(string): # real signature unknown; restored from __doc__
    """ loads(string) -- Load a pickle from the given string """
    pass

def Pickler(file, protocol=0): # real signature unknown; restored from __doc__
    """
    Pickler(file, protocol=0) -- Create a pickler.
    
    This takes a file-like object for writing a pickle data stream.
    The optional proto argument tells the pickler to use the given
    protocol; supported protocols are 0, 1, 2.  The default
    protocol is 0, to be backwards compatible.  (Protocol 0 is the
    only protocol that can be written to a file opened in text
    mode and read back successfully.  When using a protocol higher
    than 0, make sure the file is opened in binary mode, both when
    pickling and unpickling.)
    
    Protocol 1 is more efficient than protocol 0; protocol 2 is
    more efficient than protocol 1.
    
    Specifying a negative protocol version selects the highest
    protocol version supported.  The higher the protocol used, the
    more recent the version of Python needed to read the pickle
    produced.
    
    The file parameter must have a write() method that accepts a single
    string argument.  It can thus be an open file object, a StringIO
    object, or any other custom object that meets this interface.
    """
    pass

def Unpickler(file): # real signature unknown; restored from __doc__
    """ Unpickler(file) -- Create an unpickler. """
    pass

# classes

class PickleError(Exception):
    # no doc
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    def __str__(self, *args, **kwargs): # real signature unknown
        pass

    __weakref__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """list of weak references to the object (if defined)"""



class UnpicklingError(PickleError):
    # no doc
    def __init__(self, *args, **kwargs): # real signature unknown
        pass


class BadPickleGet(UnpicklingError):
    # no doc
    def __init__(self, *args, **kwargs): # real signature unknown
        pass


class PicklingError(PickleError):
    # no doc
    def __init__(self, *args, **kwargs): # real signature unknown
        pass


class UnpickleableError(PicklingError):
    # no doc
    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    def __str__(self, *args, **kwargs): # real signature unknown
        pass


# variables with complex values

compatible_formats = [
    '1.0',
    '1.1',
    '1.2',
    '1.3',
    '2.0',
]

