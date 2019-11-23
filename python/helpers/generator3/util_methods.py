import ast
import errno
import functools
import hashlib
import keyword
import shutil
from contextlib import contextmanager

from generator3.constants import *

try:
    import inspect
except ImportError:
    inspect = None

BIN_READ_BLOCK = 64 * 1024


def create_named_tuple():   #TODO: user-skeleton
    return """
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
"""

def create_generator():
    # Fake <type 'generator'>
    if version[0] < 3:
        next_name = "next"
    else:
        next_name = "__next__"
    txt = """
class __generator(object):
    '''A mock class representing the generator function type.'''
    def __init__(self):
        self.gi_code = None
        self.gi_frame = None
        self.gi_running = 0

    def __iter__(self):
        '''Defined to support iteration over container.'''
        pass

    def %s(self):
        '''Return the next item from the container.'''
        pass
""" % (next_name,)
    if version[0] >= 3 or (version[0] == 2 and version[1] >= 5):
        txt += """
    def close(self):
        '''Raises new GeneratorExit exception inside the generator to terminate the iteration.'''
        pass

    def send(self, value):
        '''Resumes the generator and "sends" a value that becomes the result of the current yield-expression.'''
        pass

    def throw(self, type, value=None, traceback=None):
        '''Used to raise an exception inside the generator.'''
        pass
"""
    return txt

def create_async_generator():
    # Fake <type 'asyncgenerator'>
    txt = """
class __asyncgenerator(object):
    '''A mock class representing the async generator function type.'''
    def __init__(self):
        '''Create an async generator object.'''
        self.__name__ = ''
        self.__qualname__ = ''
        self.ag_await = None
        self.ag_frame = None
        self.ag_running = False
        self.ag_code = None

    def __aiter__(self):
        '''Defined to support iteration over container.'''
        pass

    def __anext__(self):
        '''Returns an awaitable, that performs one asynchronous generator iteration when awaited.'''
        pass

    def aclose(self):
        '''Returns an awaitable, that throws a GeneratorExit exception into generator.'''
        pass

    def asend(self, value):
        '''Returns an awaitable, that pushes the value object in generator.'''
        pass

    def athrow(self, type, value=None, traceback=None):
        '''Returns an awaitable, that throws an exception into generator.'''
        pass
"""
    return txt

def create_function():
    txt = """
class __function(object):
    '''A mock class representing function type.'''

    def __init__(self):
        self.__name__ = ''
        self.__doc__ = ''
        self.__dict__ = ''
        self.__module__ = ''
"""
    if version[0] == 2:
        txt += """
        self.func_defaults = {}
        self.func_globals = {}
        self.func_closure = None
        self.func_code = None
        self.func_name = ''
        self.func_doc = ''
        self.func_dict = ''
"""
    if version[0] >= 3 or (version[0] == 2 and version[1] >= 6):
        txt += """
        self.__defaults__ = {}
        self.__globals__ = {}
        self.__closure__ = None
        self.__code__ = None
        self.__name__ = ''
"""
    if version[0] >= 3:
        txt += """
        self.__annotations__ = {}
        self.__kwdefaults__ = {}
"""
    if version[0] >= 3 and version[1] >= 3:
        txt += """
        self.__qualname__ = ''
"""
    return txt

def create_method():
    txt = """
class __method(object):
    '''A mock class representing method type.'''

    def __init__(self):
"""
    if version[0] == 2:
        txt += """
        self.im_class = None
        self.im_self = None
        self.im_func = None
"""
    if version[0] >= 3 or (version[0] == 2 and version[1] >= 6):
        txt += """
        self.__func__ = None
        self.__self__ = None
"""
    return txt


def create_coroutine():
    if version[0] == 3 and version[1] >= 5:
        return """
class __coroutine(object):
    '''A mock class representing coroutine type.'''

    def __init__(self):
        self.__name__ = ''
        self.__qualname__ = ''
        self.cr_await = None
        self.cr_frame = None
        self.cr_running = False
        self.cr_code = None

    def __await__(self):
        return []

    def close(self):
        pass

    def send(self, value):
        pass

    def throw(self, type, value=None, traceback=None):
        pass
"""
    return ""


def _searchbases(cls, accum):
    # logic copied from inspect.py
    if cls not in accum:
        accum.append(cls)
        for x in cls.__bases__:
            _searchbases(x, accum)


def get_mro(a_class):
    # logic copied from inspect.py
    """Returns a tuple of MRO classes."""
    if hasattr(a_class, "__mro__"):
        return a_class.__mro__
    elif hasattr(a_class, "__bases__"):
        bases = []
        _searchbases(a_class, bases)
        return tuple(bases)
    else:
        return tuple()


def get_bases(a_class): # TODO: test for classes that don't fit this scheme
    """Returns a sequence of class's bases."""
    if hasattr(a_class, "__bases__"):
        return a_class.__bases__
    else:
        return ()


def is_callable(x):
    return hasattr(x, '__call__')


def sorted_no_case(p_array):
    """Sort an array case insensitively, returns a sorted copy"""
    p_array = list(p_array)
    p_array = sorted(p_array, key=lambda x: x.upper())
    return p_array


def cleanup(value):
    result = []
    prev = i = 0
    length = len(value)
    last_ascii = chr(127)
    while i < length:
        char = value[i]
        replacement = None
        if char == '\n':
            replacement = '\\n'
        elif char == '\r':
            replacement = '\\r'
        elif char < ' ' or char > last_ascii:
            replacement = '?' # NOTE: such chars are rare; long swaths could be precessed differently
        if replacement:
            result.append(value[prev:i])
            result.append(replacement)
            prev = i + 1
        i += 1
    result.append(value[prev:])
    return "".join(result)


def is_valid_expr(s):
    try:
        compile(s, '<unknown>', 'eval', ast.PyCF_ONLY_AST)
    except SyntaxError:
        return False
    return True


_prop_types = [type(property())]
#noinspection PyBroadException
try:
    _prop_types.append(types.GetSetDescriptorType)
except:
    pass

#noinspection PyBroadException
try:
    _prop_types.append(types.MemberDescriptorType)
except:
    pass

_prop_types = tuple(_prop_types)


def is_property(x):
    return isinstance(x, _prop_types)


def reliable_repr(value):
    # some subclasses of built-in types (see PyGtk) may provide invalid __repr__ implementations,
    # so we need to sanitize the output
    if type(bool) == type and isinstance(value, bool):
        return repr(bool(value))
    for num_type in NUM_TYPES:
        if isinstance(value, num_type):
            return repr(num_type(value))
    return repr(value)


def sanitize_value(p_value):
    """Returns p_value or its part if it represents a sane simple value, else returns 'None'"""
    if isinstance(p_value, STR_TYPES):
        match = SIMPLE_VALUE_RE.match(p_value)
        if match:
            return match.groups()[match.lastindex - 1]
        else:
            return 'None'
    elif isinstance(p_value, NUM_TYPES):
        return reliable_repr(p_value)
    elif p_value is None:
        return 'None'
    else:
        if hasattr(p_value, "__name__") and hasattr(p_value, "__module__") and p_value.__module__ == BUILTIN_MOD_NAME:
            return p_value.__name__ # float -> "float"
        else:
            return repr(repr(p_value)) # function -> "<function ...>", etc


def report(msg, *data):
    """Say something at error level (stderr)"""
    sys.stderr.write(msg % data)
    sys.stderr.write("\n")


def say(msg, *data):
    """Say something at info level (stdout)"""
    sys.stdout.write(msg % data)
    sys.stdout.write("\n")
    sys.stdout.flush()


def flatten(seq):
    """Transforms tree lists like ['a', ['b', 'c'], 'd'] to strings like '(a, (b, c), d)', enclosing each tree level in parens."""
    ret = []
    for one in seq:
        if type(one) is list:
            ret.append(flatten(one))
        else:
            ret.append(one)
    return "(" + ", ".join(ret) + ")"


def make_names_unique(seq, name_map=None):
    """
    Returns a copy of tree list seq where all clashing names are modified by numeric suffixes:
    ['a', 'b', 'a', 'b'] becomes ['a', 'b', 'a_1', 'b_1'].
    Each repeating name has its own counter in the name_map.
    """
    ret = []
    if not name_map:
        name_map = {}
    for one in seq:
        if type(one) is list:
            ret.append(make_names_unique(one, name_map))
        else:
            if keyword.iskeyword(one):
                one += "_"
            one_key = lstrip(one, "*") # starred parameters are unique sans stars
            if one_key in name_map:
                old_one = one_key
                one = one + "_" + str(name_map[old_one])
                name_map[old_one] += 1
            else:
                name_map[one_key] = 1
            ret.append(one)
    return ret


def out_docstring(out_func, docstring, indent):
    if not isinstance(docstring, str): return
    lines = docstring.strip().split("\n")
    if lines:
        if len(lines) == 1:
            out_func(indent, '""" ' + lines[0] + ' """')
        else:
            out_func(indent, '"""')
            for line in lines:
                try:
                    out_func(indent, line)
                except UnicodeEncodeError:
                    continue
            out_func(indent, '"""')

def out_doc_attr(out_func, p_object, indent, p_class=None):
    the_doc = getattr(p_object, "__doc__", None)
    if the_doc:
        if p_class and the_doc == object.__init__.__doc__ and p_object is not object.__init__ and p_class.__doc__:
            the_doc = str(p_class.__doc__) # replace stock init's doc with class's; make it a certain string.
            the_doc += "\n# (copied from class doc)"
        out_docstring(out_func, the_doc, indent)
    else:
        out_func(indent, "# no doc")

def is_skipped_in_module(p_module, p_value):
    """
    Returns True if p_value's value must be skipped for module p_module.
    """
    skip_list = SKIP_VALUE_IN_MODULE.get(p_module, [])
    if p_value in skip_list:
        return True
    skip_list = SKIP_VALUE_IN_MODULE.get("*", [])
    if p_value in skip_list:
        return True
    return False

def restore_predefined_builtin(class_name, func_name):
    spec = func_name + PREDEFINED_BUILTIN_SIGS[(class_name, func_name)]
    note = "known special case of " + (class_name and class_name + "." or "") + func_name
    return (spec, note)

def restore_by_inspect(p_func):
    """
    Returns paramlist restored by inspect.
    """
    args, varg, kwarg, defaults = inspect.getargspec(p_func)
    spec = []
    if defaults:
        dcnt = len(defaults) - 1
    else:
        dcnt = -1
    args = args or []
    args.reverse() # backwards, for easier defaults handling
    for arg in args:
        if dcnt >= 0:
            arg += "=" + sanitize_value(defaults[dcnt])
            dcnt -= 1
        spec.insert(0, arg)
    if varg:
        spec.append("*" + varg)
    if kwarg:
        spec.append("**" + kwarg)
    return flatten(spec)

def restore_parameters_for_overloads(parameter_lists):
    param_index = 0
    star_args = False
    optional = False
    params = []
    while True:
        parameter_lists_copy = [pl for pl in parameter_lists]
        for pl in parameter_lists_copy:
            if param_index >= len(pl):
                parameter_lists.remove(pl)
                optional = True
        if not parameter_lists:
            break
        name = parameter_lists[0][param_index]
        for pl in parameter_lists[1:]:
            if pl[param_index] != name:
                star_args = True
                break
        if star_args: break
        if optional and not '=' in name:
            params.append(name + '=None')
        else:
            params.append(name)
        param_index += 1
    if star_args:
        params.append("*__args")
    return params

def build_signature(p_name, params):
    return p_name + '(' + ', '.join(params) + ')'


def propose_first_param(deco):
    """@return: name of missing first paramater, considering a decorator"""
    if deco is None:
        return "self"
    if deco == "classmethod":
        return "cls"
        # if deco == "staticmethod":
    return None

def qualifier_of(cls, qualifiers_to_skip):
    m = getattr(cls, "__module__", None)
    if m in qualifiers_to_skip:
        return ""
    return m

def handle_error_func(item_name, out):
    exctype, value = sys.exc_info()[:2]
    msg = "Error generating skeleton for function %s: %s"
    args = item_name, value
    report(msg, *args)
    out(0, "# " + msg % args)
    out(0, "")

def format_accessors(accessor_line, getter, setter, deleter):
    """Nicely format accessors, like 'getter, fdel=deleter'"""
    ret = []
    consecutive = True
    for key, arg, par in (('r', 'fget', getter), ('w', 'fset', setter), ('d', 'fdel', deleter)):
        if key in accessor_line:
            if consecutive:
                ret.append(par)
            else:
                ret.append(arg + "=" + par)
        else:
            consecutive = False
    return ", ".join(ret)


def has_regular_python_ext(file_name):
    """Does name end with .py?"""
    return file_name.endswith(".py")
    # Note that the standard library on MacOS X 10.6 is shipped only as .pyc files, so we need to
    # have them processed by the generator in order to have any code insight for the standard library.


def detect_constructor(p_class):
    # try to inspect the thing
    constr = getattr(p_class, "__init__")
    if constr and inspect and inspect.isfunction(constr):
        args, _, _, _ = inspect.getargspec(constr)
        return ", ".join(args)
    else:
        return None

##############  notes, actions #################################################################
_is_verbose = False # controlled by -v

CURRENT_ACTION = "nothing yet"

def action(msg, *data):
    global CURRENT_ACTION
    CURRENT_ACTION = msg % data
    note(msg, *data)


def set_verbose(verbose):
    global _is_verbose
    _is_verbose = verbose


def note(msg, *data):
    """Say something at debug info level (stderr)"""
    if _is_verbose:
        sys.stderr.write(msg % data)
        sys.stderr.write("\n")


##############  plaform-specific methods    #######################################################
import sys
if sys.platform == 'cli':
    #noinspection PyUnresolvedReferences
    import clr

# http://blogs.msdn.com/curth/archive/2009/03/29/an-ironpython-profiler.aspx
def print_profile():
    data = []
    data.extend(clr.GetProfilerData())
    data.sort(lambda x, y: -cmp(x.ExclusiveTime, y.ExclusiveTime))

    for pd in data:
        say('%s\t%d\t%d\t%d', pd.Name, pd.InclusiveTime, pd.ExclusiveTime, pd.Calls)

def is_clr_type(clr_type):
    if not clr_type: return False
    try:
        clr.GetClrType(clr_type)
        return True
    except TypeError:
        return False

def restore_clr(p_name, p_class):
    """
    Restore the function signature by the CLR type signature
    :return (is_static, spec, sig_note)
    """
    clr_type = clr.GetClrType(p_class)
    if p_name == '__new__':
        methods = [c for c in clr_type.GetConstructors()]
        if not methods:
            return False, p_name + '(self, *args)', 'cannot find CLR constructor' # "self" is always first argument of any non-static method
    else:
        methods = [m for m in clr_type.GetMethods() if m.Name == p_name]
        if not methods:
            bases = p_class.__bases__
            if len(bases) == 1 and p_name in dir(bases[0]):
                # skip inherited methods
                return False, None, None
            return False, p_name + '(self, *args)', 'cannot find CLR method'
            # "self" is always first argument of any non-static method

    parameter_lists = []
    for m in methods:
        parameter_lists.append([p.Name for p in m.GetParameters()])
    params = restore_parameters_for_overloads(parameter_lists)
    is_static = False
    if not methods[0].IsStatic:
        params = ['self'] + params
    else:
        is_static = True
    return is_static, build_signature(p_name, params), None


def build_pkg_structure(base_dir, qname):
    if not qname:
        return base_dir

    subdirname = base_dir
    for part in qname.split("."):
        subdirname = os.path.join(subdirname, part)
        if not os.path.isdir(subdirname):
            action("creating subdir %r", subdirname)
            os.makedirs(subdirname)
        init_py = os.path.join(subdirname, "__init__.py")
        if os.path.isfile(subdirname + ".py"):
            os.rename(subdirname + ".py", init_py)
        elif not os.path.isfile(init_py):
            fopen(init_py, "w").close()

    return subdirname


def is_valid_implicit_namespace_package_name(s):
    """
    Checks whether provided string could represent implicit namespace package name.
    :param s: string to check
    :return: True if provided string could represent implicit namespace package name and False otherwise
    """
    return isidentifier(s) and not keyword.iskeyword(s)


def isidentifier(s):
    """
    Checks whether provided string complies Python identifier syntax requirements.
    :param s: string to check
    :return: True if provided string comply Python identifier syntax requirements and False otherwise
    """
    if version[0] >= 3:
        return s.isidentifier()
    else:
        # quick test on provided string to comply major Python identifier syntax requirements
        return (s and
                not s[:1].isdigit() and
                "-" not in s and
                " " not in s)


@contextmanager
def ignored_os_errors(*errno):
    try:
        yield
    # Since Python 3.3 IOError and OSError were merged into OSError
    except EnvironmentError as e:
        if e.errno not in errno:
            raise


def mkdir(path):
    try:
        os.makedirs(path)
    except EnvironmentError as e:
        if e.errno != errno.EEXIST or not os.path.isdir(path):
            raise


def copy(src, dst, merge=False, pre_copy_hook=None, conflict_handler=None, post_copy_hook=None):
    if pre_copy_hook is None:
        def pre_copy_hook(p1, p2):
            return True

    if conflict_handler is None:
        def conflict_handler(p1, p2):
            return False

    if post_copy_hook is None:
        def post_copy_hook(p1, p2):
            pass

    if not pre_copy_hook(src, dst):
        return

    if os.path.isdir(src):
        if not merge:
            shutil.copytree(src, dst)
        else:
            mkdir(dst)
            for child in os.listdir(src):
                child_src = os.path.join(src, child)
                child_dst = os.path.join(dst, child)
                try:
                    copy(child_src, child_dst, merge=merge,
                         pre_copy_hook=pre_copy_hook,
                         conflict_handler=conflict_handler,
                         post_copy_hook=post_copy_hook)
                except OSError as e:
                    if e.errno == errno.EEXIST and not (os.path.isdir(child_src) and os.path.isdir(child_dst)):
                        if conflict_handler(child_src, child_dst):
                            continue
                    raise
    else:
        mkdir(os.path.dirname(dst))
        shutil.copy2(src, dst)
    post_copy_hook(src, dst)


def copy_skeletons(src_dir, dst_dir, new_origin=None):
    def overwrite(src, dst):
        delete(dst)
        copy(src, dst)
        return True

    # Remove packages/modules with the same import name
    def mod_pkg_cleanup(src, dst):
        dst_dir = os.path.dirname(dst)
        name, ext = os.path.splitext(os.path.basename(src))
        if ext == '.py':
            delete(os.path.join(dst_dir, name))
        elif not ext:
            delete(dst + '.py')

    def override_origin_stamp(src, dst):
        _, ext = os.path.splitext(dst)
        if ext == '.py' and new_origin:
            with fopen(dst, 'r') as f:
                lines = f.readlines()
                for i, line in enumerate(lines):
                    if not line.startswith('#'):
                        return

                    m = SKELETON_HEADER_ORIGIN_LINE.match(line)
                    if m:
                        break
                else:
                    return
            with fopen(dst, 'w') as f:
                lines[i] = '# from ' + new_origin + '\n'
                f.writelines(lines)

    def post_copy_hook(src, dst):
        override_origin_stamp(src, dst)
        mod_pkg_cleanup(src, dst)

    def ignore_failed_version_stamps(src, dst):
        return not os.path.basename(src).startswith(FAILED_VERSION_STAMP_PREFIX)

    copy(src_dir, dst_dir, merge=True,
         pre_copy_hook=ignore_failed_version_stamps,
         conflict_handler=overwrite,
         post_copy_hook=post_copy_hook)


def delete(path, content=False):
    with ignored_os_errors(errno.ENOENT):
        if os.path.isdir(path):
            if not content:
                shutil.rmtree(path)
            else:
                for child in os.listdir(path):
                    delete(child)
        else:
            os.remove(path)


def cached(func):
    func._results = {}
    unknown = object()

    # noinspection PyProtectedMember
    @functools.wraps(func)
    def wrapper(*args):
        result = func._results.get(args, unknown)
        if result is unknown:
            result = func._results[args] = func(*args)
        return result

    return wrapper


def sha256_digest(binary_or_file):
    # "bytes" type is available in Python 2.7
    if isinstance(binary_or_file, bytes):
        return hashlib.sha256(binary_or_file).hexdigest()
    else:
        acc = hashlib.sha256()
        while True:
            block = binary_or_file.read(BIN_READ_BLOCK)
            if not block:
                break
            acc.update(block)
        return acc.hexdigest()


def get_relative_path_by_qname(abs_path, qname):
    abs_path_components = os.path.split(abs_path)
    qname_components_count = len(qname.split('.'))
    if os.path.splitext(abs_path_components[-1])[0] == '__init__':
        rel_path_components_count = qname_components_count + 1
    else:
        rel_path_components_count = qname_components_count
    return os.path.join(*abs_path_components[-rel_path_components_count:])

def is_text_file(path):
    """
    Verify that some path is a text file (not a binary file).
    Ideally there should be usage of libmagic but it can be not
    installed on a target machine.

    Actually this algorithm is inspired by function `file_encoding`
    from libmagic.
    """
    try:
        with open(path, 'rb') as candidate_stream:
            # Buffer size like in libmagic
            buffer = candidate_stream.read(256 * 1024)
    except EnvironmentError:
        return False

    # Verify that it looks like ASCII, UTF-8 or UTF-16.
    for encoding in 'utf-8', 'utf-16', 'utf-16-be', 'utf-16-le':
        try:
            buffer.decode(encoding)
        except UnicodeDecodeError as err:
            if err.args[0].endswith(('truncated data', 'unexpected end of data')):
                return True
        else:
            return True

    # Verify that it looks like ISO-8859 or non-ISO extended ASCII.
    return all(c not in _bytes_that_never_appears_in_text for c in buffer)


_bytes_that_never_appears_in_text = set(range(7)) | {11} | set(range(14, 27)) | set(range(28, 32)) | {127}


# This wrapper is intentionally made top-level: local functions can't be pickled.
def _multiprocessing_wrapper(result_conn, func, *args, **kwargs):
    result_conn.send(func(*args, **kwargs))


def execute_in_subprocess_synchronously(name, func, args, kwargs, failure_result=None):
    import multiprocessing as mp

    extra_process_kwargs = {}
    if sys.version_info[0] >= 3:
        extra_process_kwargs['daemon'] = True

    # There is no need to use a full-blown queue for single producer/single consumer scenario.
    # Also, Pipes don't suffer from issues such as https://bugs.python.org/issue35797.
    # TODO experiment with a shared queue maintained by multiprocessing.Manager
    #  (it will require an additional service process)
    recv_conn, send_conn = mp.Pipe(duplex=False)
    p = mp.Process(name=name,
                   target=_multiprocessing_wrapper,
                   args=(send_conn, func) + args,
                   kwargs=kwargs,
                   **extra_process_kwargs)
    p.start()
    # This is actually against the multiprocessing guidelines
    # https://docs.python.org/3/library/multiprocessing.html#programming-guidelines
    # but allows us to fail-fast if the child process terminated abnormally with a segfault
    # (otherwise we would have to wait by timeout on acquiring the result) and should work
    # fine for small result values such as generation status.
    p.join()
    if recv_conn.poll():
        return recv_conn.recv()
    else:
        return failure_result
