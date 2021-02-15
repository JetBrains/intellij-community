from __future__ import nested_scopes

import os
import traceback
import warnings

import pydevd_file_utils

try:
    from urllib import quote
except:
    from urllib.parse import quote  # @UnresolvedImport

try:
    from collections import OrderedDict
except:
    OrderedDict = dict

import inspect
from _pydevd_bundle.pydevd_constants import BUILTINS_MODULE_NAME, IS_PY38_OR_GREATER, dict_iter_items, get_global_debugger, IS_PY3K, LOAD_VALUES_POLICY, \
    ValuesPolicy
import sys
from _pydev_bundle import pydev_log
from _pydev_imps._pydev_saved_modules import threading


def _normpath(filename):
    return pydevd_file_utils.get_abs_path_real_path_and_base_from_file(filename)[0]


def save_main_module(file, module_name):
    # patch provided by: Scott Schlesier - when script is run, it does not
    # use globals from pydevd:
    # This will prevent the pydevd script from contaminating the namespace for the script to be debugged
    # pretend pydevd is not the main module, and
    # convince the file to be debugged that it was loaded as main
    sys.modules[module_name] = sys.modules['__main__']
    sys.modules[module_name].__name__ = module_name

    with warnings.catch_warnings():
        warnings.simplefilter("ignore", category=DeprecationWarning)
        warnings.simplefilter("ignore", category=PendingDeprecationWarning)
        from imp import new_module

    m = new_module('__main__')
    sys.modules['__main__'] = m
    if hasattr(sys.modules[module_name], '__loader__'):
        m.__loader__ = getattr(sys.modules[module_name], '__loader__')
    m.__file__ = file

    return m


def to_number(x):
    if is_string(x):
        try:
            n = float(x)
            return n
        except ValueError:
            pass

        l = x.find('(')
        if l != -1:
            y = x[0:l - 1]
            # print y
            try:
                n = float(y)
                return n
            except ValueError:
                pass
    return None


def compare_object_attrs_key(x):
    if '__len__' == x:
        # __len__ should appear after other attributes in a list.
        num = 99999999
    else:
        num = to_number(x)
    if num is not None:
        return 1, num
    else:
        return -1, to_string(x)



if IS_PY3K:

    def is_string(x):
        return isinstance(x, str)

else:

    def is_string(x):
        return isinstance(x, basestring)


def to_string(x):
    if is_string(x):
        return x
    else:
        return str(x)


def print_exc():
    if traceback:
        traceback.print_exc()


if IS_PY3K:

    def quote_smart(s, safe='/'):
        return quote(s, safe)

else:

    def quote_smart(s, safe='/'):
        if isinstance(s, unicode):
            s = s.encode('utf-8')

        return quote(s, safe)


def get_clsname_for_code(code, frame):
    clsname = None
    if len(code.co_varnames) > 0:
        # We are checking the first argument of the function
        # (`self` or `cls` for methods).
        first_arg_name = code.co_varnames[0]
        if first_arg_name in frame.f_locals:
            first_arg_obj = frame.f_locals[first_arg_name]
            if inspect.isclass(first_arg_obj):  # class method
                first_arg_class = first_arg_obj
            else:  # instance method
                first_arg_class = first_arg_obj.__class__
            func_name = code.co_name
            if hasattr(first_arg_class, func_name):
                method = getattr(first_arg_class, func_name)
                func_code = None
                if hasattr(method, 'func_code'):  # Python2
                    func_code = method.func_code
                elif hasattr(method, '__code__'):  # Python3
                    func_code = method.__code__
                if func_code and func_code == code:
                    clsname = first_arg_class.__name__

    return clsname


_PROJECT_ROOTS_CACHE = []
_LIBRARY_ROOTS_CACHE = []
_FILENAME_TO_IN_SCOPE_CACHE = {}


def _convert_to_str_and_clear_empty(roots):
    if sys.version_info[0] <= 2:
        # In py2 we need bytes for the files.
        roots = [
            root if not isinstance(root, unicode) else root.encode(sys.getfilesystemencoding())
            for root in roots
        ]

    new_roots = []
    for root in roots:
        assert isinstance(root, str), '%s not str (found: %s)' % (root, type(root))
        if root:
            new_roots.append(root)
    return new_roots


def _clear_caches_related_to_scope_changes():
    # Clear related caches.
    _FILENAME_TO_IN_SCOPE_CACHE.clear()
    debugger = get_global_debugger()
    if debugger is not None:
        debugger.clear_skip_caches()


def _set_roots(roots, cache):
    roots = _convert_to_str_and_clear_empty(roots)
    new_roots = []
    for root in roots:
        new_roots.append(_normpath(root))
    cache.append(new_roots)
    # Leave only the last one added.
    del cache[:-1]
    _clear_caches_related_to_scope_changes()
    return new_roots


def _get_roots(cache, env_var, set_when_not_cached, get_default_val=None):
    if not cache:
        roots = os.getenv(env_var, None)
        if roots is not None:
            roots = roots.split(os.pathsep)
        else:
            if not get_default_val:
                roots = []
            else:
                roots = get_default_val()
        if not roots:
            pydev_log.warn('%s being set to empty list.' % (env_var,))
        set_when_not_cached(roots)
    return cache[-1]  # returns the roots with case normalized


def _get_default_library_roots():
    # Provide sensible defaults if not in env vars.
    import site
    roots = [sys.prefix]
    if hasattr(sys, 'base_prefix'):
        roots.append(sys.base_prefix)
    if hasattr(sys, 'real_prefix'):
        roots.append(sys.real_prefix)

    if hasattr(site, 'getusersitepackages'):
        site_paths = site.getusersitepackages()
        if isinstance(site_paths, (list, tuple)):
            for site_path in site_paths:
                roots.append(site_path)
        else:
            roots.append(site_paths)

    if hasattr(site, 'getsitepackages'):
        site_paths = site.getsitepackages()
        if isinstance(site_paths, (list, tuple)):
            for site_path in site_paths:
                roots.append(site_path)
        else:
            roots.append(site_paths)

    for path in sys.path:
        if os.path.exists(path) and os.path.basename(path) == 'site-packages':
            roots.append(path)

    return sorted(set(roots))


# --- Project roots
def set_project_roots(project_roots):
    project_roots = _set_roots(project_roots, _PROJECT_ROOTS_CACHE)
    pydev_log.debug("IDE_PROJECT_ROOTS %s\n" % project_roots)


def _get_project_roots(project_roots_cache=_PROJECT_ROOTS_CACHE):
    return _get_roots(project_roots_cache, 'IDE_PROJECT_ROOTS', set_project_roots)


# --- Library roots
def set_library_roots(roots):
    roots = _set_roots(roots, _LIBRARY_ROOTS_CACHE)
    pydev_log.debug("LIBRARY_ROOTS %s\n" % roots)


def _get_library_roots(library_roots_cache=_LIBRARY_ROOTS_CACHE):
    return _get_roots(library_roots_cache, 'LIBRARY_ROOTS', set_library_roots, _get_default_library_roots)


def in_project_roots(filename, filename_to_in_scope_cache=_FILENAME_TO_IN_SCOPE_CACHE):
    # Note: the filename_to_in_scope_cache is the same instance among the many calls to the method
    try:
        return filename_to_in_scope_cache[filename]
    except:
        project_roots = _get_project_roots()
        original_filename = filename
        if not filename.endswith('>'):
            filename = _normpath(filename)

        found_in_project = []
        for root in project_roots:
            if root and filename.startswith(root):
                found_in_project.append(root)

        found_in_library = []
        library_roots = _get_library_roots()
        for root in library_roots:
            if root and filename.startswith(root):
                found_in_library.append(root)

        if not project_roots:
            # If we have no project roots configured, consider it being in the project
            # roots if it's not found in site-packages (because we have defaults for those
            # and not the other way around).
            if filename.endswith('>'):
                in_project = False
            else:
                in_project = not found_in_library
        else:
            in_project = False
            if found_in_project:
                if not found_in_library:
                    in_project = True
                else:
                    # Found in both, let's see which one has the bigger path matched.
                    if max(len(x) for x in found_in_project) > max(len(x) for x in found_in_library):
                        in_project = True

        filename_to_in_scope_cache[original_filename] = in_project
        return in_project


def is_exception_trace_in_project_scope(trace):
    if trace is None:
        return False
    elif in_project_roots(trace.tb_frame.f_code.co_filename):
        return True
    else:
        while trace is not None:
            if not in_project_roots(trace.tb_frame.f_code.co_filename):
                return False
            trace = trace.tb_next
        return True


def is_top_level_trace_in_project_scope(trace):
    if trace is not None and trace.tb_next is not None:
        return is_exception_trace_in_project_scope(trace) and not is_exception_trace_in_project_scope(trace.tb_next)
    return is_exception_trace_in_project_scope(trace)


def is_test_item_or_set_up_caller(trace):
    """Check if the frame is the test item or set up caller.

    A test function caller is a function that calls actual test code which can be, for example,
    `unittest.TestCase` test method or function `pytest` assumes to be a test. A caller function
    is the one we want to trace to catch failed test events. Tracing test functions
    themselves is not possible because some exceptions can be caught in the test code, and
    we are interested only in exceptions that are propagated to the test framework level.
    """
    if not trace:
        return False

    frame = trace.tb_frame

    abs_path, _, _ = pydevd_file_utils.get_abs_path_real_path_and_base_from_frame(frame)
    if in_project_roots(abs_path):
        # We are interested in exceptions made it to the test framework scope.
        return False

    if not trace.tb_next:
        # This can happen when the exception has been raised inside a test item or set up caller.
        return False

    if not _is_next_stack_trace_in_project_roots(trace):
        # The next stack frame must be the frame of a project scope function, otherwise we risk stopping
        # at a line a few times since multiple test framework functions we are looking for may appear in the stack.
        return False

    # Set up and tear down methods can be checked immediately, since they are shared by both `pytest` and `unittest`.
    unittest_set_up_and_tear_down_methods = ('_callSetUp', '_callTearDown')
    if frame.f_code.co_name in unittest_set_up_and_tear_down_methods:
        return True

    # It is important to check if the tests are run with `pytest` first because it can run `unittest` code
    # internally. This may lead to stopping on  broken tests twice: one in the `pytest` test runner
    # and second in the `unittest` runner.
    is_pytest = False

    f = frame
    while f:
        # noinspection SpellCheckingInspection
        if f.f_code.co_name == 'pytest_cmdline_main':
            is_pytest = True
        f = f.f_back

    unittest_caller_names = ['_callTestMethod', 'runTest', 'run']
    if IS_PY3K:
        unittest_caller_names.append('subTest')

    if is_pytest:
        # noinspection SpellCheckingInspection
        if frame.f_code.co_name in ('pytest_pyfunc_call', 'call_fixture_func', '_eval_scope_callable', '_teardown_yield_fixture'):
            return True
        else:
            return frame.f_code.co_name in unittest_caller_names

    else:
        import unittest
        test_case_obj = frame.f_locals.get('self')

        # Check for `_FailedTest` is important to detect cases when tests cannot be run on the first place,
        # e.g. there was an import error in the test module. Can happen both in Python 3.8 and earlier versions.
        if isinstance(test_case_obj, getattr(getattr(unittest, 'loader', None), '_FailedTest', None)):
            return False

        if frame.f_code.co_name in unittest_caller_names:
            # unittest and nose
            return True

    return False


def _is_next_stack_trace_in_project_roots(trace):
    if trace and trace.tb_next and trace.tb_next.tb_frame:
        frame = trace.tb_next.tb_frame
        return in_project_roots(pydevd_file_utils.get_abs_path_real_path_and_base_from_frame(frame)[0])
    return False


# noinspection SpellCheckingInspection
def should_stop_on_failed_test(exc_info):
    """Check if the debugger should stop on failed test. Some failed tests can be marked as expected failures
    and should be ignored because of that.

    :param exc_info: exception type, value, and traceback
    :return: `False` if test is marked as an expected failure, ``True`` otherwise.
    """
    exc_type, _, trace = exc_info

    # unittest
    test_item = trace.tb_frame.f_locals.get('method') if IS_PY38_OR_GREATER else trace.tb_frame.f_locals.get('testMethod')
    if test_item:
        return not getattr(test_item, '__unittest_expecting_failure__', False)

    # pytest
    testfunction = trace.tb_frame.f_locals.get('testfunction')
    if testfunction and hasattr(testfunction, 'pytestmark'):
        # noinspection PyBroadException
        try:
            for attr in testfunction.pytestmark:
                # noinspection PyUnresolvedReferences
                if attr.name == 'xfail':
                    # noinspection PyUnresolvedReferences
                    exc_to_ignore = attr.kwargs.get('raises')
                    if not exc_to_ignore:
                        # All exceptions should be ignored, if no type is specified.
                        return False
                    elif hasattr(exc_to_ignore, '__iter__'):
                        return exc_type not in exc_to_ignore
                    else:
                        return exc_type is not exc_to_ignore
        except BaseException:
            pass
    return True


def is_exception_in_test_unit_can_be_ignored(exception):
    return exception.__name__ == 'SkipTest'


def get_top_level_trace_in_project_scope(trace):
    while trace:
        if is_top_level_trace_in_project_scope(trace):
            break
        trace = trace.tb_next
    return trace


def is_filter_enabled():
    return os.getenv('PYDEVD_FILTERS') is not None


def is_filter_libraries():
    is_filter = os.getenv('PYDEVD_FILTER_LIBRARIES') is not None
    pydev_log.debug("PYDEVD_FILTER_LIBRARIES %s\n" % is_filter)
    return is_filter


def _get_stepping_filters(filters_cache=[]):
    if not filters_cache:
        filters = os.getenv('PYDEVD_FILTERS', '').split(';')
        pydev_log.debug("PYDEVD_FILTERS %s\n" % filters)
        new_filters = []
        for new_filter in filters:
            new_filters.append(new_filter)
        filters_cache.append(new_filters)
    return filters_cache[-1]


def is_ignored_by_filter(filename, filename_to_ignored_by_filters_cache={}):
    try:
        return filename_to_ignored_by_filters_cache[filename]
    except:
        import fnmatch
        for stepping_filter in _get_stepping_filters():
            if fnmatch.fnmatch(filename, stepping_filter):
                pydev_log.debug("File %s ignored by filter %s" % (filename, stepping_filter))
                filename_to_ignored_by_filters_cache[filename] = True
                break
        else:
            filename_to_ignored_by_filters_cache[filename] = False

        return filename_to_ignored_by_filters_cache[filename]


def get_non_pydevd_threads():
    threads = threading.enumerate()
    return [t for t in threads if t and not getattr(t, 'is_pydev_daemon_thread', False)]


def dump_threads(stream=None):
    '''
    Helper to dump thread info.
    '''
    if stream is None:
        stream = sys.stderr
    thread_id_to_name = {}
    try:
        for t in threading.enumerate():
            thread_id_to_name[t.ident] = '%s  (daemon: %s, pydevd thread: %s)' % (
                t.name, t.daemon, getattr(t, 'is_pydev_daemon_thread', False))
    except:
        pass

    from _pydevd_bundle.pydevd_additional_thread_info_regular import _current_frames

    stream.write('===============================================================================\n')
    stream.write('Threads running\n')
    stream.write('================================= Thread Dump =================================\n')
    stream.flush()

    for thread_id, stack in _current_frames().items():
        stream.write('\n-------------------------------------------------------------------------------\n')
        stream.write(" Thread %s" % thread_id_to_name.get(thread_id, thread_id))
        stream.write('\n\n')

        for i, (filename, lineno, name, line) in enumerate(traceback.extract_stack(stack)):

            stream.write(' File "%s", line %d, in %s\n' % (filename, lineno, name))
            if line:
                stream.write("   %s\n" % (line.strip()))

            if i == 0 and 'self' in stack.f_locals:
                stream.write('   self: ')
                try:
                    stream.write(str(stack.f_locals['self']))
                except:
                    stream.write('Unable to get str of: %s' % (type(stack.f_locals['self']),))
                stream.write('\n')
        stream.flush()

    stream.write('\n=============================== END Thread Dump ===============================')
    stream.flush()


def take_first_n_coll_elements(coll, n):
    if coll.__class__ in (list, tuple):
        return coll[:n]
    elif coll.__class__ in (set, frozenset):
        buf = []
        for i, x in enumerate(coll):
            if i >= n:
                break
            buf.append(x)
        return type(coll)(buf)
    elif coll.__class__ in (dict, OrderedDict):
        ret = type(coll)()
        for i, (k, v) in enumerate(dict_iter_items(coll)):
            if i >= n:
                break
            ret[k] = v
        return ret
    else:
        raise TypeError("Unsupported collection type: '%s'" % str(coll.__class__))


class VariableWithOffset(object):
    def __init__(self, data, offset):
        self.data, self.offset = data, offset


def get_var_and_offset(var):
    if isinstance(var, VariableWithOffset):
        return var.data, var.offset
    return var, 0


def is_pandas_container(type_qualifier, var_type, var):
    return var_type in ("DataFrame", "Series") and type_qualifier.startswith("pandas") and hasattr(var, "shape")


def is_numpy_container(type_qualifier, var_type, var):
    return var_type == "ndarray" and type_qualifier == "numpy" and hasattr(var, "shape")


def is_builtin(x):
    return getattr(x, '__module__', None) == BUILTINS_MODULE_NAME


def is_numpy(x):
    if not getattr(x, '__module__', None) == 'numpy':
        return False
    type_name = x.__name__
    return type_name == 'dtype' or type_name == 'bool_' or type_name == 'str_' or 'int' in type_name or 'uint' in type_name \
           or 'float' in type_name or 'complex' in type_name


def should_evaluate_full_value(val):
    return LOAD_VALUES_POLICY == ValuesPolicy.SYNC \
           or ((is_builtin(type(val)) or is_numpy(type(val))) and not isinstance(val, (list, tuple, dict, set, frozenset))) \
           or (is_in_unittests_debugging_mode() and isinstance(val, Exception))


def should_evaluate_shape():
    return LOAD_VALUES_POLICY != ValuesPolicy.ON_DEMAND


def _series_to_str(s, max_items):
    res = []
    s = s[:max_items]
    for item in s.iteritems():
        # item: (index, value)
        res.append(str(item))
    return ' '.join(res)


def _df_to_str(value):
    # Avoid using df.iteritems() or df.values[i], because it works very slow for large data frames
    # df.__str__() is already optimised and works fast enough
    res = []
    rows = value.split('\n')
    for (i, r) in enumerate(rows):
        if i == 0:
            res.append(r.strip())
        else:
            res.append("[%s]" % r)
    return ' '.join(res)


def pandas_to_str(df, type_name, str_value, max_items):
    try:
        if type_name == "Series":
            return _series_to_str(df, max_items)
        elif type_name == "DataFrame":
            return _df_to_str(str_value)
        else:
            return str(df)
    except Exception as e:
        pydev_log.warn("Failed to format pandas variable: " + str(e))
        return str(df)


def format_numpy_array(num_array, max_items):
    return str(num_array[:max_items]).replace('\n', ',').strip()


def is_in_unittests_debugging_mode():
    debugger = get_global_debugger()
    if debugger:
        return debugger.stop_on_failed_tests
