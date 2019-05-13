# encoding: utf-8
# module sys
# from (built-in)
# by generator 1.145
"""
This module provides access to some objects used or maintained by the
interpreter and to functions that interact strongly with the interpreter.

Dynamic objects:

argv -- command line arguments; argv[0] is the script pathname if known
path -- module search path; path[0] is the script directory, else ''
modules -- dictionary of loaded modules

displayhook -- called to show results in an interactive session
excepthook -- called to handle any uncaught exception other than SystemExit
  To customize printing in an interactive session or to install a custom
  top-level exception handler, assign other functions to replace these.

stdin -- standard input file object; used by input()
stdout -- standard output file object; used by print()
stderr -- standard error object; used for error messages
  By assigning other file objects (or objects that behave like files)
  to these, it is possible to redirect all of the interpreter's I/O.

last_type -- type of last uncaught exception
last_value -- value of last uncaught exception
last_traceback -- traceback of last uncaught exception
  These three are only available in an interactive session after a
  traceback has been printed.

Static objects:

builtin_module_names -- tuple of module names built into this interpreter
copyright -- copyright notice pertaining to this interpreter
exec_prefix -- prefix used to find the machine-specific Python library
executable -- absolute path of the executable binary of the Python interpreter
float_info -- a struct sequence with information about the float implementation.
float_repr_style -- string indicating the style of repr() output for floats
hash_info -- a struct sequence with information about the hash algorithm.
hexversion -- version information encoded as a single integer
implementation -- Python implementation information.
int_info -- a struct sequence with information about the int implementation.
maxsize -- the largest supported length of containers.
maxunicode -- the value of the largest Unicode code point
platform -- platform identifier
prefix -- prefix used to find the Python library
thread_info -- a struct sequence with information about the thread implementation.
version -- the version of this interpreter as a string
version_info -- version information as a named tuple
dllhandle -- [Windows only] integer handle of the Python DLL
winver -- [Windows only] version number of the Python DLL
_enablelegacywindowsfsencoding -- [Windows only] 
__stdin__ -- the original stdin; don't touch!
__stdout__ -- the original stdout; don't touch!
__stderr__ -- the original stderr; don't touch!
__displayhook__ -- the original displayhook; don't touch!
__excepthook__ -- the original excepthook; don't touch!

Functions:

displayhook() -- print an object to the screen, and save it in builtins._
excepthook() -- print an exception and its traceback to sys.stderr
exc_info() -- return thread-safe information about the current exception
exit() -- exit the interpreter by raising SystemExit
getdlopenflags() -- returns flags to be used for dlopen() calls
getprofile() -- get the global profiling function
getrefcount() -- return the reference count for an object (plus one :-)
getrecursionlimit() -- return the max recursion depth for the interpreter
getsizeof() -- return the size of an object in bytes
gettrace() -- get the global debug tracing function
setcheckinterval() -- control how often the interpreter checks for events
setdlopenflags() -- set the flags to be used for dlopen() calls
setprofile() -- set the global profiling function
setrecursionlimit() -- set the max recursion depth for the interpreter
settrace() -- set the global debug tracing function
"""
# no imports

# Variables with simple values

api_version = 1013

base_exec_prefix = 'C:\\Python37'

base_prefix = 'C:\\Python37'

byteorder = 'little'

copyright = 'Copyright (c) 2001-2018 Python Software Foundation.\nAll Rights Reserved.\n\nCopyright (c) 2000 BeOpen.com.\nAll Rights Reserved.\n\nCopyright (c) 1995-2001 Corporation for National Research Initiatives.\nAll Rights Reserved.\n\nCopyright (c) 1991-1995 Stichting Mathematisch Centrum, Amsterdam.\nAll Rights Reserved.'

dllhandle = 140705079492608

dont_write_bytecode = True

executable = 'C:\\Python37\\python.exe'

exec_prefix = 'C:\\Python37'

float_repr_style = 'short'

hexversion = 50790896

maxsize = 9223372036854775807
maxunicode = 1114111

platform = 'win32'

prefix = 'C:\\Python37'

version = '3.7.1 (v3.7.1:260ec2c36a, Oct 20 2018, 14:57:15) [MSC v.1915 64 bit (AMD64)]'

winver = '3.7'

_framework = ''

_home = None

# functions

def breakpointhook(*args, **kws): # real signature unknown; restored from __doc__
    """
    breakpointhook(*args, **kws)
    
    This hook function is called by built-in breakpoint().
    """
    pass

def callstats(): # real signature unknown; restored from __doc__
    """
    callstats() -> tuple of integers
    
    Return a tuple of function call statistics, if CALL_PROFILE was defined
    when Python was built.  Otherwise, return None.
    
    When enabled, this function returns detailed, implementation-specific
    details about the number of function calls executed. The return value is
    a 11-tuple where the entries in the tuple are counts of:
    0. all function calls
    1. calls to PyFunction_Type objects
    2. PyFunction calls that do not create an argument tuple
    3. PyFunction calls that do not create an argument tuple
       and bypass PyEval_EvalCodeEx()
    4. PyMethod calls
    5. PyMethod calls on bound methods
    6. PyType calls
    7. PyCFunction calls
    8. generator calls
    9. All other calls
    10. Number of stack pops performed by call_function()
    """
    return ()

def call_tracing(func, args): # real signature unknown; restored from __doc__
    """
    call_tracing(func, args) -> object
    
    Call func(*args), while tracing is enabled.  The tracing state is
    saved, and restored afterwards.  This is intended to be called from
    a debugger from a checkpoint, to recursively debug some other code.
    """
    return object()

def displayhook(p_object): # real signature unknown; restored from __doc__
    """
    displayhook(object) -> None
    
    Print an object to sys.stdout and also save it in builtins._
    """
    pass

def excepthook(exctype, value, traceback): # real signature unknown; restored from __doc__
    """
    excepthook(exctype, value, traceback) -> None
    
    Handle an exception by displaying it with a traceback on sys.stderr.
    """
    pass

def exc_info(): # real signature unknown; restored from __doc__
    """
    exc_info() -> (type, value, traceback)
    
    Return information about the most recent exception caught by an except
    clause in the current stack frame or in an older stack frame.
    """
    pass

def exit(status=None): # real signature unknown; restored from __doc__
    """
    exit([status])
    
    Exit the interpreter by raising SystemExit(status).
    If the status is omitted or None, it defaults to zero (i.e., success).
    If the status is an integer, it will be used as the system exit status.
    If it is another kind of object, it will be printed and the system
    exit status will be one (i.e., failure).
    """
    pass

def getallocatedblocks(): # real signature unknown; restored from __doc__
    """
    getallocatedblocks() -> integer
    
    Return the number of memory blocks currently allocated, regardless of their
    size.
    """
    return 0

def getcheckinterval(): # real signature unknown; restored from __doc__
    """ getcheckinterval() -> current check interval; see setcheckinterval(). """
    pass

def getdefaultencoding(): # real signature unknown; restored from __doc__
    """
    getdefaultencoding() -> string
    
    Return the current default string encoding used by the Unicode 
    implementation.
    """
    return ""

def getfilesystemencodeerrors(): # real signature unknown; restored from __doc__
    """
    getfilesystemencodeerrors() -> string
    
    Return the error mode used to convert Unicode filenames in
    operating system filenames.
    """
    return ""

def getfilesystemencoding(): # real signature unknown; restored from __doc__
    """
    getfilesystemencoding() -> string
    
    Return the encoding used to convert Unicode filenames in
    operating system filenames.
    """
    return ""

def getprofile(): # real signature unknown; restored from __doc__
    """
    getprofile()
    
    Return the profiling function set with sys.setprofile.
    See the profiler chapter in the library manual.
    """
    pass

def getrecursionlimit(): # real signature unknown; restored from __doc__
    """
    getrecursionlimit()
    
    Return the current value of the recursion limit, the maximum depth
    of the Python interpreter stack.  This limit prevents infinite
    recursion from causing an overflow of the C stack and crashing Python.
    """
    pass

def getrefcount(p_object): # real signature unknown; restored from __doc__
    """
    getrefcount(object) -> integer
    
    Return the reference count of object.  The count returned is generally
    one higher than you might expect, because it includes the (temporary)
    reference as an argument to getrefcount().
    """
    return 0

def getsizeof(p_object, default): # real signature unknown; restored from __doc__
    """
    getsizeof(object, default) -> int
    
    Return the size of object in bytes.
    """
    return 0

def getswitchinterval(): # real signature unknown; restored from __doc__
    """ getswitchinterval() -> current thread switch interval; see setswitchinterval(). """
    pass

def gettrace(): # real signature unknown; restored from __doc__
    """
    gettrace()
    
    Return the global debug tracing function set with sys.settrace.
    See the debugger chapter in the library manual.
    """
    pass

def getwindowsversion(): # real signature unknown; restored from __doc__
    """
    getwindowsversion()
    
    Return information about the running version of Windows as a named tuple.
    The members are named: major, minor, build, platform, service_pack,
    service_pack_major, service_pack_minor, suite_mask, and product_type. For
    backward compatibility, only the first 5 items are available by indexing.
    All elements are numbers, except service_pack and platform_type which are
    strings, and platform_version which is a 3-tuple. Platform is always 2.
    Product_type may be 1 for a workstation, 2 for a domain controller, 3 for a
    server. Platform_version is a 3-tuple containing a version number that is
    intended for identifying the OS rather than feature detection.
    """
    pass

def get_asyncgen_hooks(): # real signature unknown; restored from __doc__
    """
    get_asyncgen_hooks()
    
    Return a namedtuple of installed asynchronous generators hooks (firstiter, finalizer).
    """
    pass

def get_coroutine_origin_tracking_depth(*args, **kwargs): # real signature unknown
    """ Check status of origin tracking for coroutine objects in this thread. """
    pass

def get_coroutine_wrapper(): # real signature unknown; restored from __doc__
    """
    get_coroutine_wrapper()
    
    Return the wrapper for coroutine objects set by sys.set_coroutine_wrapper.
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

def is_finalizing(): # real signature unknown; restored from __doc__
    """
    is_finalizing()
    Return True if Python is exiting.
    """
    pass

def setcheckinterval(n): # real signature unknown; restored from __doc__
    """
    setcheckinterval(n)
    
    Tell the Python interpreter to check for asynchronous events every
    n instructions.  This also affects how often thread switches occur.
    """
    pass

def setprofile(function): # real signature unknown; restored from __doc__
    """
    setprofile(function)
    
    Set the profiling function.  It will be called on each function call
    and return.  See the profiler chapter in the library manual.
    """
    pass

def setrecursionlimit(n): # real signature unknown; restored from __doc__
    """
    setrecursionlimit(n)
    
    Set the maximum depth of the Python interpreter stack to n.  This
    limit prevents infinite recursion from causing an overflow of the C
    stack and crashing Python.  The highest possible limit is platform-
    dependent.
    """
    pass

def setswitchinterval(n): # real signature unknown; restored from __doc__
    """
    setswitchinterval(n)
    
    Set the ideal thread switching delay inside the Python interpreter
    The actual frequency of switching threads can be lower if the
    interpreter executes long sequences of uninterruptible code
    (this is implementation-specific and workload-dependent).
    
    The parameter must represent the desired switching delay in seconds
    A typical value is 0.005 (5 milliseconds).
    """
    pass

def settrace(function): # real signature unknown; restored from __doc__
    """
    settrace(function)
    
    Set the global debug tracing function.  It will be called on each
    function call.  See the debugger chapter in the library manual.
    """
    pass

def set_asyncgen_hooks(*args, **kwargs): # real signature unknown; NOTE: unreliably restored from __doc__ 
    """
    set_asyncgen_hooks(*, firstiter=None, finalizer=None)
    
    Set a finalizer for async generators objects.
    """
    pass

def set_coroutine_origin_tracking_depth(*args, **kwargs): # real signature unknown
    """
    Enable or disable origin tracking for coroutine objects in this thread.
    
    Coroutine objects will track 'depth' frames of traceback information about
    where they came from, available in their cr_origin attribute. Set depth of 0
    to disable.
    """
    pass

def set_coroutine_wrapper(wrapper): # real signature unknown; restored from __doc__
    """
    set_coroutine_wrapper(wrapper)
    
    Set a wrapper for coroutine objects.
    """
    pass

def _clear_type_cache(): # real signature unknown; restored from __doc__
    """
    _clear_type_cache() -> None
    Clear the internal type lookup cache.
    """
    pass

def _current_frames(): # real signature unknown; restored from __doc__
    """
    _current_frames() -> dictionary
    
    Return a dictionary mapping each current thread T's thread id to T's
    current stack frame.
    
    This function should be used for specialized purposes only.
    """
    return {}

def _debugmallocstats(): # real signature unknown; restored from __doc__
    """
    _debugmallocstats()
    
    Print summary info to stderr about the state of
    pymalloc's structures.
    
    In Py_DEBUG mode, also perform some expensive internal consistency
    checks.
    """
    pass

def _enablelegacywindowsfsencoding(): # real signature unknown; restored from __doc__
    """
    _enablelegacywindowsfsencoding()
    
    Changes the default filesystem encoding to mbcs:replace for consistency
    with earlier versions of Python. See PEP 529 for more information.
    
    This is equivalent to defining the PYTHONLEGACYWINDOWSFSENCODING 
    environment variable before launching Python.
    """
    pass

def _getframe(depth=None): # real signature unknown; restored from __doc__
    """
    _getframe([depth]) -> frameobject
    
    Return a frame object from the call stack.  If optional integer depth is
    given, return the frame object that many calls below the top of the stack.
    If that is deeper than the call stack, ValueError is raised.  The default
    for depth is zero, returning the frame at the top of the call stack.
    
    This function should be used for internal and specialized
    purposes only.
    """
    pass

def __breakpointhook__(*args, **kwargs): # real signature unknown
    """
    breakpointhook(*args, **kws)
    
    This hook function is called by built-in breakpoint().
    """
    pass

def __displayhook__(*args, **kwargs): # real signature unknown
    """
    displayhook(object) -> None
    
    Print an object to sys.stdout and also save it in builtins._
    """
    pass

def __excepthook__(*args, **kwargs): # real signature unknown
    """
    excepthook(exctype, value, traceback) -> None
    
    Handle an exception by displaying it with a traceback on sys.stderr.
    """
    pass

def __interactivehook__(): # reliably restored by inspect
    # no doc
    pass

# classes

class __loader__(object):
    """
    Meta path import for built-in modules.
    
        All methods are either class or static methods to avoid the need to
        instantiate the class.
    """
    @classmethod
    def create_module(cls, *args, **kwargs): # real signature unknown
        """ Create a built-in module """
        pass

    @classmethod
    def exec_module(cls, *args, **kwargs): # real signature unknown
        """ Exec a built-in module """
        pass

    @classmethod
    def find_module(cls, *args, **kwargs): # real signature unknown
        """
        Find the built-in module.
        
                If 'path' is ever specified then the search is considered a failure.
        
                This method is deprecated.  Use find_spec() instead.
        """
        pass

    @classmethod
    def find_spec(cls, *args, **kwargs): # real signature unknown
        pass

    @classmethod
    def get_code(cls, *args, **kwargs): # real signature unknown
        """ Return None as built-in modules do not have code objects. """
        pass

    @classmethod
    def get_source(cls, *args, **kwargs): # real signature unknown
        """ Return None as built-in modules do not have source code. """
        pass

    @classmethod
    def is_package(cls, *args, **kwargs): # real signature unknown
        """ Return False as built-in modules are never packages. """
        pass

    @classmethod
    def load_module(cls, *args, **kwargs): # real signature unknown
        """
        Load the specified module into sys.modules and return it.
        
            This method is deprecated.  Use loader.exec_module instead.
        """
        pass

    def module_repr(module): # reliably restored by inspect
        """
        Return repr for the module.
        
                The method is deprecated.  The import machinery does the job itself.
        """
        pass

    def __init__(self, *args, **kwargs): # real signature unknown
        pass

    __weakref__ = property(lambda self: object(), lambda self, v: None, lambda self: None)  # default
    """list of weak references to the object (if defined)"""


    __dict__ = None # (!) real value is ''


# variables with complex values

argv = [] # real value of type <class 'list'> skipped

builtin_module_names = () # real value of type <class 'tuple'> skipped

flags = (
    0,
    0,
    0,
    0,
    1,
    0,
    0,
    0,
    0,
    0,
    0,
    1,
    0,
    False,
    0,
)

float_info = (
    1.7976931348623157e+308,
    1024,
    308,
    2.2250738585072014e-308,
    -1021,
    -307,
    15,
    53,
    2.220446049250313e-16,
    2,
    1,
)

hash_info = (
    64,
    2305843009213693951,
    314159,
    0,
    1000003,
    'siphash24',
    64,
    128,
    0,
)

implementation = None # (!) real value is ''

int_info = (
    30,
    4,
)

meta_path = [
    __loader__,
    None, # (!) real value is ''
    None, # (!) real value is ''
]

modules = {} # real value of type <class 'dict'> skipped

path = [
    'C:\\Projects\\IDEA\\out\\classes\\production\\intellij.python.helpers',
    'C:\\Python37\\python37.zip',
    'C:\\Python37\\DLLs',
    'C:\\Python37\\lib',
    'C:\\Python37',
    'C:\\Python37\\lib\\site-packages',
]

path_hooks = [
    None, # (!) real value is ''
    None, # (!) real value is ''
]

path_importer_cache = {} # real value of type <class 'dict'> skipped

stderr = None # (!) real value is ''

stdin = None # (!) forward: __stdin__, real value is ''

stdout = None # (!) forward: __stdout__, real value is ''

thread_info = (
    'nt',
    None,
    None,
)

version_info = (
    3,
    7,
    1,
    'final',
    0,
)

warnoptions = []

_git = (
    'CPython',
    'v3.7.1',
    '260ec2c36a',
)

_xoptions = {}

__spec__ = None # (!) real value is ''

__stderr__ = stderr

__stdin__ = None # (!) real value is ''

__stdout__ = None # (!) real value is ''

# intermittent names
exc_value = Exception()
exc_traceback=None
