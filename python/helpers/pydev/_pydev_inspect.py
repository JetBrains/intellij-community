"""Get useful information from live Python objects.

This module encapsulates the interface provided by the internal special
attributes (func_*, co_*, im_*, tb_*, etc.) in a friendlier fashion.
It also provides some help for examining source code and class layout.

Here are some of the useful functions provided by this module:

    ismodule(), isclass(), ismethod(), isfunction(), istraceback(),
        isframe(), iscode(), isbuiltin(), isroutine() - check object types
    getmembers() - get members of an object that satisfy a given condition

    getfile(), getsourcefile(), getsource() - find an object's source code
    getdoc(), getcomments() - get documentation on an object
    getmodule() - determine the module that an object came from
    getclasstree() - arrange classes so as to represent their hierarchy

    getargspec(), getargvalues() - get info about function arguments
    formatargspec(), formatargvalues() - format an argument spec
    getouterframes(), getinnerframes() - get info about frames
    currentframe() - get the current stack frame
    stack(), trace() - get info about frames on the stack or in a traceback
"""

# This module is in the public domain.  No warranties.

__author__ = 'Ka-Ping Yee <ping@lfw.org>'
__date__ = '1 Jan 2001'

import sys, os, types, string, re, imp, tokenize

# ----------------------------------------------------------- type-checking
def ismodule(object):
    """Return true if the object is a module.

    Module objects provide these attributes:
        __doc__         documentation string
        __file__        filename (missing for built-in modules)"""
    return isinstance(object, types.ModuleType)

def isclass(object):
    """Return true if the object is a class.

    Class objects provide these attributes:
        __doc__         documentation string
        __module__      name of module in which this class was defined"""
    return isinstance(object, types.ClassType) or hasattr(object, '__bases__')

def ismethod(object):
    """Return true if the object is an instance method.

    Instance method objects provide these attributes:
        __doc__         documentation string
        __name__        name with which this method was defined
        im_class        class object in which this method belongs
        im_func         function object containing implementation of method
        im_self         instance to which this method is bound, or None"""
    return isinstance(object, types.MethodType)

def ismethoddescriptor(object):
    """Return true if the object is a method descriptor.

    But not if ismethod() or isclass() or isfunction() are true.

    This is new in Python 2.2, and, for example, is true of int.__add__.
    An object passing this test has a __get__ attribute but not a __set__
    attribute, but beyond that the set of attributes varies.  __name__ is
    usually sensible, and __doc__ often is.

    Methods implemented via descriptors that also pass one of the other
    tests return false from the ismethoddescriptor() test, simply because
    the other tests promise more -- you can, e.g., count on having the
    im_func attribute (etc) when an object passes ismethod()."""
    return (hasattr(object, "__get__")
            and not hasattr(object, "__set__") # else it's a data descriptor
            and not ismethod(object)           # mutual exclusion
            and not isfunction(object)
            and not isclass(object))

def isfunction(object):
    """Return true if the object is a user-defined function.

    Function objects provide these attributes:
        __doc__         documentation string
        __name__        name with which this function was defined
        func_code       code object containing compiled function bytecode
        func_defaults   tuple of any default values for arguments
        func_doc        (same as __doc__)
        func_globals    global namespace in which this function was defined
        func_name       (same as __name__)"""
    return isinstance(object, types.FunctionType)

def istraceback(object):
    """Return true if the object is a traceback.

    Traceback objects provide these attributes:
        tb_frame        frame object at this level
        tb_lasti        index of last attempted instruction in bytecode
        tb_lineno       current line number in Python source code
        tb_next         next inner traceback object (called by this level)"""
    return isinstance(object, types.TracebackType)

def isframe(object):
    """Return true if the object is a frame object.

    Frame objects provide these attributes:
        f_back          next outer frame object (this frame's caller)
        f_builtins      built-in namespace seen by this frame
        f_code          code object being executed in this frame
        f_exc_traceback traceback if raised in this frame, or None
        f_exc_type      exception type if raised in this frame, or None
        f_exc_value     exception value if raised in this frame, or None
        f_globals       global namespace seen by this frame
        f_lasti         index of last attempted instruction in bytecode
        f_lineno        current line number in Python source code
        f_locals        local namespace seen by this frame
        f_restricted    0 or 1 if frame is in restricted execution mode
        f_trace         tracing function for this frame, or None"""
    return isinstance(object, types.FrameType)

def iscode(object):
    """Return true if the object is a code object.

    Code objects provide these attributes:
        co_argcount     number of arguments (not including * or ** args)
        co_code         string of raw compiled bytecode
        co_consts       tuple of constants used in the bytecode
        co_filename     name of file in which this code object was created
        co_firstlineno  number of first line in Python source code
        co_flags        bitmap: 1=optimized | 2=newlocals | 4=*arg | 8=**arg
        co_lnotab       encoded mapping of line numbers to bytecode indices
        co_name         name with which this code object was defined
        co_names        tuple of names of local variables
        co_nlocals      number of local variables
        co_stacksize    virtual machine stack space required
        co_varnames     tuple of names of arguments and local variables"""
    return isinstance(object, types.CodeType)

def isbuiltin(object):
    """Return true if the object is a built-in function or method.

    Built-in functions and methods provide these attributes:
        __doc__         documentation string
        __name__        original name of this function or method
        __self__        instance to which a method is bound, or None"""
    return isinstance(object, types.BuiltinFunctionType)

def isroutine(object):
    """Return true if the object is any kind of function or method."""
    return (isbuiltin(object)
            or isfunction(object)
            or ismethod(object)
            or ismethoddescriptor(object))

def getmembers(object, predicate=None):
    """Return all members of an object as (name, value) pairs sorted by name.
    Optionally, only return members that satisfy a given predicate."""
    results = []
    for key in dir(object):
        value = getattr(object, key)
        if not predicate or predicate(value):
            results.append((key, value))
    results.sort()
    return results

def classify_class_attrs(cls):
    """Return list of attribute-descriptor tuples.

    For each name in dir(cls), the return list contains a 4-tuple
    with these elements:

        0. The name (a string).

        1. The kind of attribute this is, one of these strings:
               'class method'    created via classmethod()
               'static method'   created via staticmethod()
               'property'        created via property()
               'method'          any other flavor of method
               'data'            not a method

        2. The class which defined this attribute (a class).

        3. The object as obtained directly from the defining class's
           __dict__, not via getattr.  This is especially important for
           data attributes:  C.data is just a data object, but
           C.__dict__['data'] may be a data descriptor with additional
           info, like a __doc__ string.
    """

    mro = getmro(cls)
    names = dir(cls)
    result = []
    for name in names:
        # Get the object associated with the name.
        # Getting an obj from the __dict__ sometimes reveals more than
        # using getattr.  Static and class methods are dramatic examples.
        if name in cls.__dict__:
            obj = cls.__dict__[name]
        else:
            obj = getattr(cls, name)

        # Figure out where it was defined.
        homecls = getattr(obj, "__objclass__", None)
        if homecls is None:
            # search the dicts.
            for base in mro:
                if name in base.__dict__:
                    homecls = base
                    break

        # Get the object again, in order to get it from the defining
        # __dict__ instead of via getattr (if possible).
        if homecls is not None and name in homecls.__dict__:
            obj = homecls.__dict__[name]

        # Also get the object via getattr.
        obj_via_getattr = getattr(cls, name)

        # Classify the object.
        if isinstance(obj, staticmethod):
            kind = "static method"
        elif isinstance(obj, classmethod):
            kind = "class method"
        elif isinstance(obj, property):
            kind = "property"
        elif (ismethod(obj_via_getattr) or
              ismethoddescriptor(obj_via_getattr)):
            kind = "method"
        else:
            kind = "data"

        result.append((name, kind, homecls, obj))

    return result

# ----------------------------------------------------------- class helpers
def _searchbases(cls, accum):
    # Simulate the "classic class" search order.
    if cls in accum:
        return
    accum.append(cls)
    for base in cls.__bases__:
        _searchbases(base, accum)

def getmro(cls):
    "Return tuple of base classes (including cls) in method resolution order."
    if hasattr(cls, "__mro__"):
        return cls.__mro__
    else:
        result = []
        _searchbases(cls, result)
        return tuple(result)

# -------------------------------------------------- source code extraction
def indentsize(line):
    """Return the indent size, in spaces, at the start of a line of text."""
    expline = string.expandtabs(line)
    return len(expline) - len(string.lstrip(expline))

def getdoc(object):
    """Get the documentation string for an object.

    All tabs are expanded to spaces.  To clean up docstrings that are
    indented to line up with blocks of code, any whitespace than can be
    uniformly removed from the second line onwards is removed."""
    try:
        doc = object.__doc__
    except AttributeError:
        return None
    if not isinstance(doc, (str, unicode)):
        return None
    try:
        lines = string.split(string.expandtabs(doc), '\n')
    except UnicodeError:
        return None
    else:
        margin = None
        for line in lines[1:]:
            content = len(string.lstrip(line))
            if not content: continue
            indent = len(line) - content
            if margin is None: margin = indent
            else: margin = min(margin, indent)
        if margin is not None:
            for i in range(1, len(lines)): lines[i] = lines[i][margin:]
        return string.join(lines, '\n')

def getfile(object):
    """Work out which source or compiled file an object was defined in."""
    if ismodule(object):
        if hasattr(object, '__file__'):
            return object.__file__
        raise TypeError, 'arg is a built-in module'
    if isclass(object):
        object = sys.modules.get(object.__module__)
        if hasattr(object, '__file__'):
            return object.__file__
        raise TypeError, 'arg is a built-in class'
    if ismethod(object):
        object = object.im_func
    if isfunction(object):
        object = object.func_code
    if istraceback(object):
        object = object.tb_frame
    if isframe(object):
        object = object.f_code
    if iscode(object):
        return object.co_filename
    raise TypeError, 'arg is not a module, class, method, ' \
                     'function, traceback, frame, or code object'

def getmoduleinfo(path):
    """Get the module name, suffix, mode, and module type for a given file."""
    filename = os.path.basename(path)
    suffixes = map(lambda (suffix, mode, mtype):
                   (-len(suffix), suffix, mode, mtype), imp.get_suffixes())
    suffixes.sort() # try longest suffixes first, in case they overlap
    for neglen, suffix, mode, mtype in suffixes:
        if filename[neglen:] == suffix:
            return filename[:neglen], suffix, mode, mtype

def getmodulename(path):
    """Return the module name for a given file, or None."""
    info = getmoduleinfo(path)
    if info: return info[0]

def getsourcefile(object):
    """Return the Python source file an object was defined in, if it exists."""
    filename = getfile(object)
    if string.lower(filename[-4:]) in ['.pyc', '.pyo']:
        filename = filename[:-4] + '.py'
    for suffix, mode, kind in imp.get_suffixes():
        if 'b' in mode and string.lower(filename[-len(suffix):]) == suffix:
            # Looks like a binary file.  We want to only return a text file.
            return None
    if os.path.exists(filename):
        return filename

def getabsfile(object):
    """Return an absolute path to the source or compiled file for an object.

    The idea is for each object to have a unique origin, so this routine
    normalizes the result as much as possible."""
    return os.path.normcase(
        os.path.abspath(getsourcefile(object) or getfile(object)))

modulesbyfile = {}

def getmodule(object):
    """Return the module an object was defined in, or None if not found."""
    if ismodule(object):
        return object
    if isclass(object):
        return sys.modules.get(object.__module__)
    try:
        file = getabsfile(object)
    except TypeError:
        return None
    if modulesbyfile.has_key(file):
        return sys.modules[modulesbyfile[file]]
    for module in sys.modules.values():
        if hasattr(module, '__file__'):
            modulesbyfile[getabsfile(module)] = module.__name__
    if modulesbyfile.has_key(file):
        return sys.modules[modulesbyfile[file]]
    main = sys.modules['__main__']
    if hasattr(main, object.__name__):
        mainobject = getattr(main, object.__name__)
        if mainobject is object:
            return main
    builtin = sys.modules['__builtin__']
    if hasattr(builtin, object.__name__):
        builtinobject = getattr(builtin, object.__name__)
        if builtinobject is object:
            return builtin

def findsource(object):
    """Return the entire source file and starting line number for an object.

    The argument may be a module, class, method, function, traceback, frame,
    or code object.  The source code is returned as a list of all the lines
    in the file and the line number indexes a line in that list.  An IOError
    is raised if the source code cannot be retrieved."""
    try:
        file = open(getsourcefile(object))
    except (TypeError, IOError):
        raise IOError, 'could not get source code'
    lines = file.readlines()
    file.close()

    if ismodule(object):
        return lines, 0

    if isclass(object):
        name = object.__name__
        pat = re.compile(r'^\s*class\s*' + name + r'\b')
        for i in range(len(lines)):
            if pat.match(lines[i]): return lines, i
        else: raise IOError, 'could not find class definition'

    if ismethod(object):
        object = object.im_func
    if isfunction(object):
        object = object.func_code
    if istraceback(object):
        object = object.tb_frame
    if isframe(object):
        object = object.f_code
    if iscode(object):
        if not hasattr(object, 'co_firstlineno'):
            raise IOError, 'could not find function definition'
        lnum = object.co_firstlineno - 1
        pat = re.compile(r'^(\s*def\s)|(.*\slambda(:|\s))')
        while lnum > 0:
            if pat.match(lines[lnum]): break
            lnum = lnum - 1
        return lines, lnum
    raise IOError, 'could not find code object'

def getcomments(object):
    """Get lines of comments immediately preceding an object's source code."""
    try: lines, lnum = findsource(object)
    except IOError: return None

    if ismodule(object):
        # Look for a comment block at the top of the file.
        start = 0
        if lines and lines[0][:2] == '#!': start = 1
        while start < len(lines) and string.strip(lines[start]) in ['', '#']:
            start = start + 1
        if start < len(lines) and lines[start][:1] == '#':
            comments = []
            end = start
            while end < len(lines) and lines[end][:1] == '#':
                comments.append(string.expandtabs(lines[end]))
                end = end + 1
            return string.join(comments, '')

    # Look for a preceding block of comments at the same indentation.
    elif lnum > 0:
        indent = indentsize(lines[lnum])
        end = lnum - 1
        if end >= 0 and string.lstrip(lines[end])[:1] == '#' and \
            indentsize(lines[end]) == indent:
            comments = [string.lstrip(string.expandtabs(lines[end]))]
            if end > 0:
                end = end - 1
                comment = string.lstrip(string.expandtabs(lines[end]))
                while comment[:1] == '#' and indentsize(lines[end]) == indent:
                    comments[:0] = [comment]
                    end = end - 1
                    if end < 0: break
                    comment = string.lstrip(string.expandtabs(lines[end]))
            while comments and string.strip(comments[0]) == '#':
                comments[:1] = []
            while comments and string.strip(comments[-1]) == '#':
                comments[-1:] = []
            return string.join(comments, '')

class ListReader:
    """Provide a readline() method to return lines from a list of strings."""
    def __init__(self, lines):
        self.lines = lines
        self.index = 0

    def readline(self):
        i = self.index
        if i < len(self.lines):
            self.index = i + 1
            return self.lines[i]
        else: return ''

class EndOfBlock(Exception): pass

class BlockFinder:
    """Provide a tokeneater() method to detect the end of a code block."""
    def __init__(self):
        self.indent = 0
        self.started = 0
        self.last = 0

    def tokeneater(self, type, token, (srow, scol), (erow, ecol), line):
        if not self.started:
            if type == tokenize.NAME: self.started = 1
        elif type == tokenize.NEWLINE:
            self.last = srow
        elif type == tokenize.INDENT:
            self.indent = self.indent + 1
        elif type == tokenize.DEDENT:
            self.indent = self.indent - 1
            if self.indent == 0: raise EndOfBlock, self.last
        elif type == tokenize.NAME and scol == 0:
            raise EndOfBlock, self.last

def getblock(lines):
    """Extract the block of code at the top of the given list of lines."""
    try:
        tokenize.tokenize(ListReader(lines).readline, BlockFinder().tokeneater)
    except EndOfBlock, eob:
        return lines[:eob.args[0]]
    # Fooling the indent/dedent logic implies a one-line definition
    return lines[:1]

def getsourcelines(object):
    """Return a list of source lines and starting line number for an object.

    The argument may be a module, class, method, function, traceback, frame,
    or code object.  The source code is returned as a list of the lines
    corresponding to the object and the line number indicates where in the
    original source file the first line of code was found.  An IOError is
    raised if the source code cannot be retrieved."""
    lines, lnum = findsource(object)

    if ismodule(object): return lines, 0
    else: return getblock(lines[lnum:]), lnum + 1

def getsource(object):
    """Return the text of the source code for an object.

    The argument may be a module, class, method, function, traceback, frame,
    or code object.  The source code is returned as a single string.  An
    IOError is raised if the source code cannot be retrieved."""
    lines, lnum = getsourcelines(object)
    return string.join(lines, '')

# --------------------------------------------------- class tree extraction
def walktree(classes, children, parent):
    """Recursive helper function for getclasstree()."""
    results = []
    classes.sort(lambda a, b: cmp(a.__name__, b.__name__))
    for c in classes:
        results.append((c, c.__bases__))
        if children.has_key(c):
            results.append(walktree(children[c], children, c))
    return results

def getclasstree(classes, unique=0):
    """Arrange the given list of classes into a hierarchy of nested lists.

    Where a nested list appears, it contains classes derived from the class
    whose entry immediately precedes the list.  Each entry is a 2-tuple
    containing a class and a tuple of its base classes.  If the 'unique'
    argument is true, exactly one entry appears in the returned structure
    for each class in the given list.  Otherwise, classes using multiple
    inheritance and their descendants will appear multiple times."""
    children = {}
    roots = []
    for c in classes:
        if c.__bases__:
            for parent in c.__bases__:
                if not children.has_key(parent):
                    children[parent] = []
                children[parent].append(c)
                if unique and parent in classes: break
        elif c not in roots:
            roots.append(c)
    for parent in children.keys():
        if parent not in classes:
            roots.append(parent)
    return walktree(roots, children, None)

# ------------------------------------------------ argument list extraction
# These constants are from Python's compile.h.
CO_OPTIMIZED, CO_NEWLOCALS, CO_VARARGS, CO_VARKEYWORDS = 1, 2, 4, 8

def getargs(co):
    """Get information about the arguments accepted by a code object.

    Three things are returned: (args, varargs, varkw), where 'args' is
    a list of argument names (possibly containing nested lists), and
    'varargs' and 'varkw' are the names of the * and ** arguments or None."""
    if not iscode(co): raise TypeError, 'arg is not a code object'

    nargs = co.co_argcount
    names = co.co_varnames
    args = list(names[:nargs])
    step = 0

    # The following acrobatics are for anonymous (tuple) arguments.
    if not sys.platform.startswith('java'):#Jython doesn't have co_code
        code = co.co_code
        import dis
        for i in range(nargs):
            if args[i][:1] in ['', '.']:
                stack, remain, count = [], [], []
                while step < len(code):
                    op = ord(code[step])
                    step = step + 1
                    if op >= dis.HAVE_ARGUMENT:
                        opname = dis.opname[op]
                        value = ord(code[step]) + ord(code[step + 1]) * 256
                        step = step + 2
                        if opname in ['UNPACK_TUPLE', 'UNPACK_SEQUENCE']:
                            remain.append(value)
                            count.append(value)
                        elif opname == 'STORE_FAST':
                            stack.append(names[value])
                            remain[-1] = remain[-1] - 1
                            while remain[-1] == 0:
                                remain.pop()
                                size = count.pop()
                                stack[-size:] = [stack[-size:]]
                                if not remain: break
                                remain[-1] = remain[-1] - 1
                            if not remain: break
                args[i] = stack[0]

    varargs = None
    if co.co_flags & CO_VARARGS:
        varargs = co.co_varnames[nargs]
        nargs = nargs + 1
    varkw = None
    if co.co_flags & CO_VARKEYWORDS:
        varkw = co.co_varnames[nargs]
    return args, varargs, varkw

def getargspec(func):
    """Get the names and default values of a function's arguments.

    A tuple of four things is returned: (args, varargs, varkw, defaults).
    'args' is a list of the argument names (it may contain nested lists).
    'varargs' and 'varkw' are the names of the * and ** arguments or None.
    'defaults' is an n-tuple of the default values of the last n arguments."""
    if ismethod(func):
        func = func.im_func
    if not isfunction(func): raise TypeError, 'arg is not a Python function'
    args, varargs, varkw = getargs(func.func_code)
    return args, varargs, varkw, func.func_defaults

def getargvalues(frame):
    """Get information about arguments passed into a particular frame.

    A tuple of four things is returned: (args, varargs, varkw, locals).
    'args' is a list of the argument names (it may contain nested lists).
    'varargs' and 'varkw' are the names of the * and ** arguments or None.
    'locals' is the locals dictionary of the given frame."""
    args, varargs, varkw = getargs(frame.f_code)
    return args, varargs, varkw, frame.f_locals

def joinseq(seq):
    if len(seq) == 1:
        return '(' + seq[0] + ',)'
    else:
        return '(' + string.join(seq, ', ') + ')'

def strseq(object, convert, join=joinseq):
    """Recursively walk a sequence, stringifying each element."""
    if type(object) in [types.ListType, types.TupleType]:
        return join(map(lambda o, c=convert, j=join: strseq(o, c, j), object))
    else:
        return convert(object)

def formatargspec(args, varargs=None, varkw=None, defaults=None,
                  formatarg=str,
                  formatvarargs=lambda name: '*' + name,
                  formatvarkw=lambda name: '**' + name,
                  formatvalue=lambda value: '=' + repr(value),
                  join=joinseq):
    """Format an argument spec from the 4 values returned by getargspec.

    The first four arguments are (args, varargs, varkw, defaults).  The
    other four arguments are the corresponding optional formatting functions
    that are called to turn names and values into strings.  The ninth
    argument is an optional function to format the sequence of arguments."""
    specs = []
    if defaults:
        firstdefault = len(args) - len(defaults)
    for i in range(len(args)):
        spec = strseq(args[i], formatarg, join)
        if defaults and i >= firstdefault:
            spec = spec + formatvalue(defaults[i - firstdefault])
        specs.append(spec)
    if varargs:
        specs.append(formatvarargs(varargs))
    if varkw:
        specs.append(formatvarkw(varkw))
    return '(' + string.join(specs, ', ') + ')'

def formatargvalues(args, varargs, varkw, locals,
                    formatarg=str,
                    formatvarargs=lambda name: '*' + name,
                    formatvarkw=lambda name: '**' + name,
                    formatvalue=lambda value: '=' + repr(value),
                    join=joinseq):
    """Format an argument spec from the 4 values returned by getargvalues.

    The first four arguments are (args, varargs, varkw, locals).  The
    next four arguments are the corresponding optional formatting functions
    that are called to turn names and values into strings.  The ninth
    argument is an optional function to format the sequence of arguments."""
    def convert(name, locals=locals,
                formatarg=formatarg, formatvalue=formatvalue):
        return formatarg(name) + formatvalue(locals[name])
    specs = []
    for i in range(len(args)):
        specs.append(strseq(args[i], convert, join))
    if varargs:
        specs.append(formatvarargs(varargs) + formatvalue(locals[varargs]))
    if varkw:
        specs.append(formatvarkw(varkw) + formatvalue(locals[varkw]))
    return '(' + string.join(specs, ', ') + ')'

# -------------------------------------------------- stack frame extraction
def getframeinfo(frame, context=1):
    """Get information about a frame or traceback object.

    A tuple of five things is returned: the filename, the line number of
    the current line, the function name, a list of lines of context from
    the source code, and the index of the current line within that list.
    The optional second argument specifies the number of lines of context
    to return, which are centered around the current line."""
    raise NotImplementedError
#    if istraceback(frame):
#        frame = frame.tb_frame
#    if not isframe(frame):
#        raise TypeError, 'arg is not a frame or traceback object'
#
#    filename = getsourcefile(frame)
#    lineno = getlineno(frame)
#    if context > 0:
#        start = lineno - 1 - context//2
#        try:
#            lines, lnum = findsource(frame)
#        except IOError:
#            lines = index = None
#        else:
#            start = max(start, 1)
#            start = min(start, len(lines) - context)
#            lines = lines[start:start+context]
#            index = lineno - 1 - start
#    else:
#        lines = index = None
#
#    return (filename, lineno, frame.f_code.co_name, lines, index)

def getlineno(frame):
    """Get the line number from a frame object, allowing for optimization."""
    # Written by Marc-Andr Lemburg; revised by Jim Hugunin and Fredrik Lundh.
    lineno = frame.f_lineno
    code = frame.f_code
    if hasattr(code, 'co_lnotab'):
        table = code.co_lnotab
        lineno = code.co_firstlineno
        addr = 0
        for i in range(0, len(table), 2):
            addr = addr + ord(table[i])
            if addr > frame.f_lasti: break
            lineno = lineno + ord(table[i + 1])
    return lineno

def getouterframes(frame, context=1):
    """Get a list of records for a frame and all higher (calling) frames.

    Each record contains a frame object, filename, line number, function
    name, a list of lines of context, and index within the context."""
    framelist = []
    while frame:
        framelist.append((frame,) + getframeinfo(frame, context))
        frame = frame.f_back
    return framelist

def getinnerframes(tb, context=1):
    """Get a list of records for a traceback's frame and all lower frames.

    Each record contains a frame object, filename, line number, function
    name, a list of lines of context, and index within the context."""
    framelist = []
    while tb:
        framelist.append((tb.tb_frame,) + getframeinfo(tb, context))
        tb = tb.tb_next
    return framelist

def currentframe():
    """Return the frame object for the caller's stack frame."""
    try:
        raise 'catch me'
    except:
        return sys.exc_traceback.tb_frame.f_back #@UndefinedVariable

if hasattr(sys, '_getframe'): currentframe = sys._getframe

def stack(context=1):
    """Return a list of records for the stack above the caller's frame."""
    return getouterframes(currentframe().f_back, context)

def trace(context=1):
    """Return a list of records for the stack below the current exception."""
    return getinnerframes(sys.exc_traceback, context) #@UndefinedVariable
