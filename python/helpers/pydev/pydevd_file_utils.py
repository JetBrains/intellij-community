r'''
    This module provides utilities to get the absolute filenames so that we can be sure that:
        - The case of a file will match the actual file in the filesystem (otherwise breakpoints won't be hit).
        - Providing means for the user to make path conversions when doing a remote debugging session in
          one machine and debugging in another.

    To do that, the PATHS_FROM_ECLIPSE_TO_PYTHON constant must be filled with the appropriate paths.

    @note:
        in this context, the server is where your python process is running
        and the client is where eclipse is running.

    E.g.:
        If the server (your python process) has the structure
            /user/projects/my_project/src/package/module1.py

        and the client has:
            c:\my_project\src\package\module1.py

        the PATHS_FROM_ECLIPSE_TO_PYTHON would have to be:
            PATHS_FROM_ECLIPSE_TO_PYTHON = [(r'c:\my_project\src', r'/user/projects/my_project/src')]

        alternatively, this can be set with an environment variable from the command line:
           set PATHS_FROM_ECLIPSE_TO_PYTHON=[['c:\my_project\src','/user/projects/my_project/src']]

    @note: DEBUG_CLIENT_SERVER_TRANSLATION can be set to True to debug the result of those translations

    @note: the case of the paths is important! Note that this can be tricky to get right when one machine
    uses a case-independent filesystem and the other uses a case-dependent filesystem (if the system being
    debugged is case-independent, 'normcase()' should be used on the paths defined in PATHS_FROM_ECLIPSE_TO_PYTHON).

    @note: all the paths with breakpoints must be translated (otherwise they won't be found in the server)

    @note: to enable remote debugging in the target machine (pydev extensions in the eclipse installation)
        import pydevd;pydevd.settrace(host, stdoutToServer, stderrToServer, port, suspend)

        see parameter docs on pydevd.py

    @note: for doing a remote debugging session, all the pydevd_ files must be on the server accessible
        through the PYTHONPATH (and the PATHS_FROM_ECLIPSE_TO_PYTHON only needs to be set on the target
        machine for the paths that'll actually have breakpoints).
'''

from _pydev_bundle import pydev_log
from _pydev_bundle._pydev_filesystem_encoding import getfilesystemencoding
from _pydevd_bundle.pydevd_constants import IS_PY2, IS_PY3K, DebugInfoHolder, IS_WINDOWS, IS_JYTHON
from _pydevd_bundle.pydevd_comm_constants import file_system_encoding, filesystem_encoding_is_utf8
import json
import os.path
import sys
import traceback

_os_normcase = os.path.normcase
basename = os.path.basename
exists = os.path.exists
join = os.path.join

try:
    rPath = os.path.realpath  # @UndefinedVariable
except:
    # jython does not support os.path.realpath
    # realpath is a no-op on systems without islink support
    rPath = os.path.abspath

# defined as a list of tuples where the 1st element of the tuple is the path in the client machine
# and the 2nd element is the path in the server machine.
# see module docstring for more details.
try:
    PATHS_FROM_ECLIPSE_TO_PYTHON = json.loads(os.environ.get('PATHS_FROM_ECLIPSE_TO_PYTHON', '[]'))
except Exception:
    sys.stderr.write('Error loading PATHS_FROM_ECLIPSE_TO_PYTHON from environment variable.\n')
    traceback.print_exc()
    PATHS_FROM_ECLIPSE_TO_PYTHON = []
else:
    if not isinstance(PATHS_FROM_ECLIPSE_TO_PYTHON, list):
        sys.stderr.write('Expected PATHS_FROM_ECLIPSE_TO_PYTHON loaded from environment variable to be a list.\n')
        PATHS_FROM_ECLIPSE_TO_PYTHON = []
    else:
        # Converting json lists to tuple
        PATHS_FROM_ECLIPSE_TO_PYTHON = [tuple(x) for x in PATHS_FROM_ECLIPSE_TO_PYTHON]

# example:
# PATHS_FROM_ECLIPSE_TO_PYTHON = [
#  (r'd:\temp\temp_workspace_2\test_python\src\yyy\yyy',
#   r'd:\temp\temp_workspace_2\test_python\src\hhh\xxx')
# ]

convert_to_long_pathname = lambda filename:filename
convert_to_short_pathname = lambda filename:filename
get_path_with_real_case = lambda filename:filename

if sys.platform == 'win32':
    try:
        import ctypes
        from ctypes.wintypes import MAX_PATH, LPCWSTR, LPWSTR, DWORD

        GetLongPathName = ctypes.windll.kernel32.GetLongPathNameW
        GetLongPathName.argtypes = [LPCWSTR, LPWSTR, DWORD]
        GetLongPathName.restype = DWORD

        GetShortPathName = ctypes.windll.kernel32.GetShortPathNameW
        GetShortPathName.argtypes = [LPCWSTR, LPWSTR, DWORD]
        GetShortPathName.restype = DWORD

        def _convert_to_long_pathname(filename):
            buf = ctypes.create_unicode_buffer(MAX_PATH)

            if IS_PY2 and isinstance(filename, str):
                filename = filename.decode(getfilesystemencoding())
            rv = GetLongPathName(filename, buf, MAX_PATH)
            if rv != 0 and rv <= MAX_PATH:
                filename = buf.value

            if IS_PY2:
                filename = filename.encode(getfilesystemencoding())
            return filename

        def _convert_to_short_pathname(filename):
            buf = ctypes.create_unicode_buffer(MAX_PATH)

            if IS_PY2 and isinstance(filename, str):
                filename = filename.decode(getfilesystemencoding())
            rv = GetShortPathName(filename, buf, MAX_PATH)
            if rv != 0 and rv <= MAX_PATH:
                filename = buf.value

            if IS_PY2:
                filename = filename.encode(getfilesystemencoding())
            return filename

        def _get_path_with_real_case(filename):
            ret = convert_to_long_pathname(convert_to_short_pathname(filename))
            # This doesn't handle the drive letter properly (it'll be unchanged).
            # Make sure the drive letter is always uppercase.
            if len(ret) > 1 and ret[1] == ':' and ret[0].islower():
                return ret[0].upper() + ret[1:]
            return ret

        # Check that it actually works
        _get_path_with_real_case(__file__)
    except:
        # Something didn't quite work out, leave no-op conversions in place.
        if DebugInfoHolder.DEBUG_TRACE_LEVEL > 2:
            traceback.print_exc()
    else:
        convert_to_long_pathname = _convert_to_long_pathname
        convert_to_short_pathname = _convert_to_short_pathname
        get_path_with_real_case = _get_path_with_real_case


elif IS_JYTHON and IS_WINDOWS:

    def get_path_with_real_case(filename):
        from java.io import File
        f = File(filename)
        ret = f.getCanonicalPath()
        if IS_PY2 and not isinstance(ret, str):
            return ret.encode(getfilesystemencoding())
        return ret


if IS_WINDOWS:

    if IS_JYTHON:

        def normcase(filename):
            return filename.lower()

    else:

        def normcase(filename):
            # `normcase` doesn't lower case on Python 2 for non-English locale, but Java
            # side does it, so we should do it manually.
            if '~' in filename:
                filename = convert_to_long_pathname(filename)

            filename = _os_normcase(filename)
            return filename.lower()

else:

    def normcase(filename):
        return filename  # no-op

_ide_os = 'WINDOWS' if IS_WINDOWS else 'UNIX'


def set_ide_os(os):
    '''
    We need to set the IDE os because the host where the code is running may be
    actually different from the client (and the point is that we want the proper
    paths to translate from the client to the server).

    :param os:
        'UNIX' or 'WINDOWS'
    '''
    global _ide_os
    prev = _ide_os
    if os == 'WIN':  # Apparently PyCharm uses 'WIN' (https://github.com/fabioz/PyDev.Debugger/issues/116)
        os = 'WINDOWS'

    assert os in ('WINDOWS', 'UNIX')

    if prev != os:
        _ide_os = os
        # We need to (re)setup how the client <-> server translation works to provide proper separators.
        setup_client_server_paths(_last_client_server_paths_set)


DEBUG_CLIENT_SERVER_TRANSLATION = os.environ.get('DEBUG_PYDEVD_PATHS_TRANSLATION', 'False').lower() in ('1', 'true')

# Caches filled as requested during the debug session.
NORM_PATHS_CONTAINER = {}
NORM_PATHS_AND_BASE_CONTAINER = {}


def _NormFile(filename):
    abs_path, real_path = _NormPaths(filename)
    return real_path


def _AbsFile(filename):
    abs_path, real_path = _NormPaths(filename)
    return abs_path


# Returns tuple of absolute path and real path for given filename
def _NormPaths(filename):
    try:
        return NORM_PATHS_CONTAINER[filename]
    except KeyError:
        if filename.__class__ != str:
            filename = _path_to_expected_str(filename)
        if filename.__class__ != str:
            pydev_log.warn('Failed to convert filename to str: %s (%s)' % (filename, type(filename)))
            return '', ''
        abs_path = _NormPath(filename, os.path.abspath)
        real_path = _NormPath(filename, rPath)

        # cache it for fast access later
        NORM_PATHS_CONTAINER[filename] = abs_path, real_path
        return abs_path, real_path


def _NormPath(filename, normpath):
    r = normpath(filename)
    ind = r.find('.zip')
    if ind == -1:
        ind = r.find('.egg')
    if ind != -1:
        ind += 4
        zip_path = r[:ind]
        inner_path = r[ind:]
        if inner_path.startswith('!'):
            # Note (fabioz): although I can replicate this by creating a file ending as
            # .zip! or .egg!, I don't really know what's the real-world case for this
            # (still kept as it was added by @jetbrains, but it should probably be reviewed
            # later on).
            # Note 2: it goes hand-in-hand with 'exists'.
            inner_path = inner_path[1:]
            zip_path = zip_path + '!'

        if inner_path.startswith('/') or inner_path.startswith('\\'):
            inner_path = inner_path[1:]
        if inner_path:
            r = join(normcase(zip_path), inner_path)
            return r

    r = normcase(r)
    return r


_ZIP_SEARCH_CACHE = {}
_NOT_FOUND_SENTINEL = object()


def exists(file):
    if os.path.exists(file):
        return file

    ind = file.find('.zip')
    if ind == -1:
        ind = file.find('.egg')

    if ind != -1:
        ind += 4
        zip_path = file[:ind]
        inner_path = file[ind:]
        if inner_path.startswith("!"):
            # Note (fabioz): although I can replicate this by creating a file ending as
            # .zip! or .egg!, I don't really know what's the real-world case for this
            # (still kept as it was added by @jetbrains, but it should probably be reviewed
            # later on).
            # Note 2: it goes hand-in-hand with '_NormPath'.
            inner_path = inner_path[1:]
            zip_path = zip_path + '!'

        zip_file_obj = _ZIP_SEARCH_CACHE.get(zip_path, _NOT_FOUND_SENTINEL)
        if zip_file_obj is None:
            return False
        elif zip_file_obj is _NOT_FOUND_SENTINEL:
            try:
                import zipfile
                zip_file_obj = zipfile.ZipFile(zip_path, 'r')
                _ZIP_SEARCH_CACHE[zip_path] = zip_file_obj
            except:
                _ZIP_SEARCH_CACHE[zip_path] = _NOT_FOUND_SENTINEL
                return False

        try:
            if inner_path.startswith('/') or inner_path.startswith('\\'):
                inner_path = inner_path[1:]

            _info = zip_file_obj.getinfo(inner_path.replace('\\', '/'))

            return join(zip_path, inner_path)
        except KeyError:
            return None
    return None


# Now, let's do a quick test to see if we're working with a version of python that has no problems
# related to the names generated...
try:
    try:
        code = rPath.func_code
    except AttributeError:
        code = rPath.__code__

    report = pydev_log.debug

    if code.co_filename.startswith('<frozen'):
        # See: https://github.com/fabioz/PyDev.Debugger/issues/213
        report('Debugger warning: It seems that frozen modules are being used, which may')
        report('make the debugger miss breakpoints. Please pass -Xfrozen_modules=off')
        report('to python to disable frozen modules.')
        report('Note: Debugging will proceed.')
    elif not exists(_NormFile(code.co_filename)):
        report('Debugger warning: It seems the debugger cannot find os.path.realpath.__code__.co_filename (%s).' % code.co_filename)
        report('This may make the debugger miss breakpoints in the standard library.')
        report('Note: Debugging will proceed. Set PYDEVD_DISABLE_FILE_VALIDATION=1 to disable this validation.')

        NORM_SEARCH_CACHE = {}

        initial_norm_paths = _NormPaths

        def _NormPaths(filename):  # Let's redefine _NormPaths to work with paths that may be incorrect
            try:
                return NORM_SEARCH_CACHE[filename]
            except KeyError:
                abs_path, real_path = initial_norm_paths(filename)
                if not exists(real_path):
                    # We must actually go on and check if we can find it as if it was a relative path for some of the paths in the pythonpath
                    for path in sys.path:
                        abs_path, real_path = initial_norm_paths(join(path, filename))
                        if exists(real_path):
                            break
                    else:
                        sys.stderr.write('pydev debugger: Unable to find real location for: %s\n' % (filename,))
                        abs_path = filename
                        real_path = filename

                NORM_SEARCH_CACHE[filename] = abs_path, real_path
                return abs_path, real_path

except:
    # Don't fail if there's something not correct here -- but at least print it to the user so that we can correct that
    traceback.print_exc()

# Note: as these functions may be rebound, users should always import
# pydevd_file_utils and then use:
#
# pydevd_file_utils.norm_file_to_client
# pydevd_file_utils.norm_file_to_server
#
# instead of importing any of those names to a given scope.


def _path_to_expected_str(filename):
    if IS_PY2:
        if not filesystem_encoding_is_utf8 and hasattr(filename, "decode"):
            # filename_in_utf8 is a byte string encoded using the file system encoding
            # convert it to utf8
            filename = filename.decode(file_system_encoding)

        if not isinstance(filename, bytes):
            filename = filename.encode('utf-8')

    else:  # py3
        if isinstance(filename, bytes):
            filename = filename.decode(file_system_encoding)

    return filename


def _original_file_to_client(filename, cache={}):
    try:
        return cache[filename]
    except KeyError:
        cache[filename] = get_path_with_real_case(_AbsFile(filename))
    return cache[filename]

_original_file_to_server = _NormFile

norm_file_to_client = _original_file_to_client
norm_file_to_server = _original_file_to_server


def _fix_path(path, sep):
    if path.endswith('/') or path.endswith('\\'):
        path = path[:-1]

    if sep != '/':
        path = path.replace('/', sep)
    return path


_last_client_server_paths_set = []


def setup_client_server_paths(paths):
    '''paths is the same format as PATHS_FROM_ECLIPSE_TO_PYTHON'''

    global norm_file_to_client
    global norm_file_to_server
    global _last_client_server_paths_set
    _last_client_server_paths_set = paths[:]

    # Work on the client and server slashes.
    python_sep = '\\' if IS_WINDOWS else '/'
    eclipse_sep = '\\' if _ide_os == 'WINDOWS' else '/'

    norm_filename_to_server_container = {}
    norm_filename_to_client_container = {}
    initial_paths = list(paths)
    paths_from_eclipse_to_python = initial_paths[:]

    # Apply normcase to the existing paths to follow the os preferences.

    for i, (path0, path1) in enumerate(paths_from_eclipse_to_python[:]):
        if IS_PY2:
            if isinstance(path0, unicode):
                path0 = path0.encode(sys.getfilesystemencoding())
            if isinstance(path1, unicode):
                path1 = path1.encode(sys.getfilesystemencoding())

        path0 = _fix_path(path0, eclipse_sep)
        path1 = _fix_path(path1, python_sep)
        initial_paths[i] = (path0, path1)

        paths_from_eclipse_to_python[i] = (normcase(path0), normcase(path1))

    if not paths_from_eclipse_to_python:
        # no translation step needed (just inline the calls)
        norm_file_to_client = _original_file_to_client
        norm_file_to_server = _original_file_to_server
        return

    # only setup translation functions if absolutely needed!
    def _norm_file_to_server(filename, cache=norm_filename_to_server_container):
        # Eclipse will send the passed filename to be translated to the python process
        # So, this would be 'NormFileFromEclipseToPython'
        try:
            return cache[filename]
        except KeyError:
            if eclipse_sep != python_sep:
                # Make sure that the separators are what we expect from the IDE.
                filename = filename.replace(python_sep, eclipse_sep)

            # used to translate a path from the client to the debug server
            translated = normcase(filename)
            for eclipse_prefix, server_prefix in paths_from_eclipse_to_python:
                if translated.startswith(eclipse_prefix):
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: replacing to server: %s\n' % (translated,))
                    translated = translated.replace(eclipse_prefix, server_prefix)
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: sent to server: %s\n' % (translated,))
                    break
            else:
                if DEBUG_CLIENT_SERVER_TRANSLATION:
                    sys.stderr.write('pydev debugger: to server: unable to find matching prefix for: %s in %s\n' % \
                                     (translated, [x[0] for x in paths_from_eclipse_to_python]))

            # Note that when going to the server, we do the replace first and only later do the norm file.
            if eclipse_sep != python_sep:
                translated = translated.replace(eclipse_sep, python_sep)
            translated = _NormFile(translated)

            cache[filename] = translated
            return translated

    def _norm_file_to_client(filename, cache=norm_filename_to_client_container):
        # The result of this method will be passed to eclipse
        # So, this would be 'NormFileFromPythonToEclipse'
        try:
            return cache[filename]
        except KeyError:
            # used to translate a path from the debug server to the client
            translated = _NormFile(filename)

            # After getting the real path, let's get it with the path with
            # the real case and then obtain a new normalized copy, just in case
            # the path is different now.
            translated_proper_case = get_path_with_real_case(translated)
            translated = _NormFile(translated_proper_case)

            if IS_WINDOWS:
                if translated.lower() != translated_proper_case.lower():
                    translated_proper_case = translated
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write(
                            'pydev debugger: _NormFile changed path (from: %s to %s)\n' % (
                                translated_proper_case, translated))

            for i, (eclipse_prefix, python_prefix) in enumerate(paths_from_eclipse_to_python):
                if translated.startswith(python_prefix):
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: replacing to client: %s\n' % (translated,))

                    # Note: use the non-normalized version.
                    eclipse_prefix = initial_paths[i][0]
                    translated = eclipse_prefix + translated_proper_case[len(python_prefix):]
                    if DEBUG_CLIENT_SERVER_TRANSLATION:
                        sys.stderr.write('pydev debugger: sent to client: %s\n' % (translated,))
                    break
            else:
                if DEBUG_CLIENT_SERVER_TRANSLATION:
                    sys.stderr.write('pydev debugger: to client: unable to find matching prefix for: %s in %s\n' % \
                                     (translated, [x[1] for x in paths_from_eclipse_to_python]))
                    translated = translated_proper_case

            if eclipse_sep != python_sep:
                translated = translated.replace(python_sep, eclipse_sep)

            # The resulting path is not in the python process, so, we cannot do a _NormFile here,
            # only at the beginning of this method.
            cache[filename] = translated
            return translated

    norm_file_to_server = _norm_file_to_server
    norm_file_to_client = _norm_file_to_client


setup_client_server_paths(PATHS_FROM_ECLIPSE_TO_PYTHON)


def _is_int(filename):
    # isdigit() doesn't support negative numbers
    try:
        int(filename)
        return True
    except:
        return False

def is_real_file(filename):
    # Check for Jupyter cells
    return not _is_int(filename) and not filename.startswith("<ipython-input")

# For given file f returns tuple of its absolute path, real path and base name
def get_abs_path_real_path_and_base_from_file(f):
    try:
        return NORM_PATHS_AND_BASE_CONTAINER[f]
    except:
        if _NormPaths is None:  # Interpreter shutdown
            return f

        if f is not None:
            if f.endswith('.pyc'):
                f = f[:-1]
            elif f.endswith('$py.class'):
                f = f[:-len('$py.class')] + '.py'

        if not is_real_file(f):
            abs_path, real_path, base = f, f, f
        else:
            abs_path, real_path = _NormPaths(f)
        base = basename(real_path)
        ret = abs_path, real_path, base
        NORM_PATHS_AND_BASE_CONTAINER[f] = ret
        return ret


def get_abs_path_real_path_and_base_from_frame(frame):
    try:
        return NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename]
    except:
        # This one is just internal (so, does not need any kind of client-server translation)
        f = frame.f_code.co_filename
        if f is not None and f.startswith (('build/bdist.', 'build\\bdist.')):
            # files from eggs in Python 2.7 have paths like build/bdist.linux-x86_64/egg/<path-inside-egg>
            f = frame.f_globals['__file__']
        if get_abs_path_real_path_and_base_from_file is None:  # Interpreter shutdown
            return f

        ret = get_abs_path_real_path_and_base_from_file(f)
        # Also cache based on the frame.f_code.co_filename (if we had it inside build/bdist it can make a difference).
        NORM_PATHS_AND_BASE_CONTAINER[frame.f_code.co_filename] = ret
        return ret


def get_fullname(mod_name):
    if IS_PY3K:
        import pkgutil
    else:
        from _pydev_imps import _pydev_pkgutil_old as pkgutil
    try:
        loader = pkgutil.get_loader(mod_name)
    except:
        return None
    if loader is not None:
        for attr in ("get_filename", "_get_filename"):
            meth = getattr(loader, attr, None)
            if meth is not None:
                return meth(mod_name)
    return None


def get_package_dir(mod_name):
    for path in sys.path:
        mod_path = join(path, mod_name.replace('.', '/'))
        if os.path.isdir(mod_path):
            return mod_path
    return None
