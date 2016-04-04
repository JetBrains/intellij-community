from __future__ import nested_scopes
import traceback
import os

try:
    from urllib import quote
except:
    from urllib.parse import quote  # @UnresolvedImport

from _pydevd_bundle import pydevd_constants
import sys
from _pydev_bundle import pydev_log

def save_main_module(file, module_name):
    # patch provided by: Scott Schlesier - when script is run, it does not
    # use globals from pydevd:
    # This will prevent the pydevd script from contaminating the namespace for the script to be debugged
    # pretend pydevd is not the main module, and
    # convince the file to be debugged that it was loaded as main
    sys.modules[module_name] = sys.modules['__main__']
    sys.modules[module_name].__name__ = module_name
    from imp import new_module

    m = new_module('__main__')
    sys.modules['__main__'] = m
    if hasattr(sys.modules[module_name], '__loader__'):
        setattr(m, '__loader__', getattr(sys.modules[module_name], '__loader__'))
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
            y = x[0:l-1]
            #print y
            try:
                n = float(y)
                return n
            except ValueError:
                pass
    return None

def compare_object_attrs(x, y):
    try:
        if x == y:
            return 0
        x_num = to_number(x)
        y_num = to_number(y)
        if x_num is not None and y_num is not None:
            if x_num - y_num<0:
                return -1
            else:
                return 1
        if '__len__' == x:
            return -1
        if '__len__' == y:
            return 1

        return x.__cmp__(y)
    except:
        if pydevd_constants.IS_PY3K:
            return (to_string(x) > to_string(y)) - (to_string(x) < to_string(y))
        else:
            return cmp(to_string(x), to_string(y))

def cmp_to_key(mycmp):
    'Convert a cmp= function into a key= function'
    class K(object):
        def __init__(self, obj, *args):
            self.obj = obj
        def __lt__(self, other):
            return mycmp(self.obj, other.obj) < 0
        def __gt__(self, other):
            return mycmp(self.obj, other.obj) > 0
        def __eq__(self, other):
            return mycmp(self.obj, other.obj) == 0
        def __le__(self, other):
            return mycmp(self.obj, other.obj) <= 0
        def __ge__(self, other):
            return mycmp(self.obj, other.obj) >= 0
        def __ne__(self, other):
            return mycmp(self.obj, other.obj) != 0
    return K

if pydevd_constants.IS_PY3K:
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

if pydevd_constants.IS_PY3K:
    def quote_smart(s, safe='/'):
        return quote(s, safe)
else:
    def quote_smart(s, safe='/'):
        if isinstance(s, unicode):
            s =  s.encode('utf-8')

        return quote(s, safe)


def _get_project_roots(project_roots_cache=[]):
    # Note: the project_roots_cache is the same instance among the many calls to the method
    if not project_roots_cache:
        roots = os.getenv('IDE_PROJECT_ROOTS', '').split(os.pathsep)
        pydev_log.debug("IDE_PROJECT_ROOTS %s\n" % roots)
        new_roots = []
        for root in roots:
            new_roots.append(os.path.normcase(root))
        project_roots_cache.append(new_roots)
    return project_roots_cache[-1] # returns the project roots with case normalized


def not_in_project_roots(filename, filename_to_not_in_scope_cache={}):
    # Note: the filename_to_not_in_scope_cache is the same instance among the many calls to the method
    try:
        return filename_to_not_in_scope_cache[filename]
    except:
        project_roots = _get_project_roots()
        filename = os.path.normcase(filename)
        for root in project_roots:
            if filename.startswith(root):
                filename_to_not_in_scope_cache[filename] = False
                break
        else: # for else (only called if the break wasn't reached).
            filename_to_not_in_scope_cache[filename] = True

        # at this point it must be loaded.
        return filename_to_not_in_scope_cache[filename]


def is_filter_enabled():
    return os.getenv('PYDEVD_FILTERS') is not None


def is_filter_libraries():
    return os.getenv('PYDEVD_FILTER_LIBRARIES') is not None


def _get_stepping_filters(filters_cache=[]):
    if not filters_cache:
        filters = os.getenv('PYDEVD_FILTERS', '').split(';')
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

