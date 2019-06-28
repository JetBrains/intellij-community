from __future__ import nested_scopes
import traceback
import os
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
from _pydevd_bundle.pydevd_constants import IS_PY3K, dict_iter_items, get_global_debugger
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
