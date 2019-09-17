# encoding: utf-8
import atexit
import zipfile

from pycharm_generator_utils.clr_tools import *
from pycharm_generator_utils.module_redeclarator import *
from pycharm_generator_utils.util_methods import *

# TODO: Move all CLR-specific functions to clr_tools
debug_mode = True
quiet = False


# TODO move to property of Generator3 as soon as tests finished
@cached
def version():
    return os.environ.get(ENV_VERSION, VERSION)


# TODO move to property of Generator3 as soon as tests finished
@cached
def required_gen_version_file_path():
    return os.environ.get(ENV_REQUIRED_GEN_VERSION_FILE, os.path.join(_helpers_dir, 'required_gen_version'))


@cached
def is_test_mode():
    return ENV_TEST_MODE_FLAG in os.environ


# Future generator mode where all the checks will be performed on Python side.
# Now it works in transitional mode where validity of existing SDK skeletons is checked on
# Java side (see PySkeletonRefresher), and generator itself inspects only the cache.
@cached
def is_standalone_mode():
    return ENV_STANDALONE_MODE_FLAG in os.environ


@cached
def is_pregeneration_mode():
    return ENV_PREGENERATION_MODE_FLAG in os.environ


_helpers_dir = os.path.dirname(os.path.abspath(__file__))


def redo_module(module_name, module_file_name, doing_builtins, cache_dir, sdk_dir=None):
    # gobject does 'del _gobject' in its __init__.py, so the chained attribute lookup code
    # fails to find 'gobject._gobject'. thus we need to pull the module directly out of
    # sys.modules
    mod = sys.modules.get(module_name)
    mod_path = module_name.split('.')
    if not mod and sys.platform == 'cli':
        # "import System.Collections" in IronPython 2.7 doesn't actually put System.Collections in sys.modules
        # instead, sys.modules['System'] get set to a Microsoft.Scripting.Actions.NamespaceTracker and Collections can be
        # accessed as its attribute
        mod = sys.modules[mod_path[0]]
        for component in mod_path[1:]:
            try:
                mod = getattr(mod, component)
            except AttributeError:
                mod = None
                report("Failed to find CLR module " + module_name)
                break
    if mod:
        action("restoring")
        r = ModuleRedeclarator(mod, module_name, module_file_name, cache_dir=cache_dir, doing_builtins=doing_builtins)
        create_failed_version_stamp(cache_dir, module_name)
        r.redo(module_name, ".".join(mod_path[:-1]) in MODULES_INSPECT_DIR)
        action("flushing")
        r.flush()
        delete_failed_version_stamp(cache_dir, module_name)
        if sdk_dir:
            # Incrementally copy whatever we managed to successfully generate so far
            copy_skeletons(cache_dir, sdk_dir, get_module_origin(module_file_name, module_name))
    else:
        report("Failed to find imported module in sys.modules " + module_name)


# find_binaries functionality
def cut_binary_lib_suffix(path, f):
    """
    @param path where f lives
    @param f file name of a possible binary lib file (no path)
    @return f without a binary suffix (that is, an importable name) if path+f is indeed a binary lib, or None.
    Note: if for .pyc or .pyo file a .py is found, None is returned.
    """
    if not f.endswith((".pyc", ".typelib", ".pyo", ".so", ".pyd")):
        return None
    ret = None
    match = BIN_MODULE_FNAME_PAT.match(f)
    if match:
        ret = match.group(1)
        modlen = len('module')
        retlen = len(ret)
        if ret.endswith('module') and retlen > modlen and f.endswith('.so'):  # what for?
            ret = ret[:(retlen - modlen)]
    if f.endswith('.pyc') or f.endswith('.pyo'):
        fullname = os.path.join(path, f[:-1])  # check for __pycache__ is made outside
        if os.path.exists(fullname):
            ret = None
    pat_match = TYPELIB_MODULE_FNAME_PAT.match(f)
    if pat_match:
        ret = "gi.repository." + pat_match.group(1)
    return ret


def is_posix_skipped_module(path, f):
    if os.name == 'posix':
        name = os.path.join(path, f)
        for mod in POSIX_SKIP_MODULES:
            if name.endswith(mod):
                return True
    return False


def is_mac_skipped_module(path, f):
    fullname = os.path.join(path, f)
    m = MAC_STDLIB_PATTERN.match(fullname)
    if not m: return 0
    relpath = m.group(2)
    for module in MAC_SKIP_MODULES:
        if relpath.startswith(module): return 1
    return 0


def is_tensorflow_contrib_ops_module(qname):
    # These modules cannot be imported directly. Instead tensorflow uses special
    # tensorflow.contrib.util.loader.load_op_library() to load them and create
    # Python modules at runtime. Their names in sys.modules are then md5 sums
    # of the list of exported Python definitions.
    return TENSORFLOW_CONTRIB_OPS_MODULE_PATTERN.match(qname)


def is_skipped_module(path, f, qname):
    return (is_mac_skipped_module(path, f) or
            is_posix_skipped_module(path, f[:f.rindex('.')]) or
            'pynestkernel' in f or
            is_tensorflow_contrib_ops_module(qname))


def is_module(d, root):
    return (os.path.exists(os.path.join(root, d, "__init__.py")) or
            os.path.exists(os.path.join(root, d, "__init__.pyc")) or
            os.path.exists(os.path.join(root, d, "__init__.pyo")) or
            is_valid_implicit_namespace_package_name(d))


def walk_python_path(path):
    for root, dirs, files in os.walk(path):
        if root.endswith('__pycache__'):
            continue
        dirs_copy = list(dirs)
        for d in dirs_copy:
            if d.endswith('__pycache__') or not is_module(d, root):
                dirs.remove(d)
        # some files show up but are actually non-existent symlinks
        yield root, [f for f in files if os.path.exists(os.path.join(root, f))]


def list_binaries(paths):
    """
    Finds binaries in the given list of paths.
    Understands nested paths, as sys.paths have it (both "a/b" and "a/b/c").
    Tries to be case-insensitive, but case-preserving.
    @param paths: list of paths.
    @return: dict[module_name, full_path]
    """
    SEP = os.path.sep
    res = {}  # {name.upper(): (name, full_path)} # b/c windows is case-oblivious
    if not paths:
        return {}
    if IS_JAVA:  # jython can't have binary modules
        return {}
    paths = sorted_no_case(paths)
    for path in paths:
        if path == os.path.dirname(sys.argv[0]): continue
        for root, files in walk_python_path(path):
            cutpoint = path.rfind(SEP)
            if cutpoint > 0:
                preprefix = path[(cutpoint + len(SEP)):] + '.'
            else:
                preprefix = ''
            prefix = root[(len(path) + len(SEP)):].replace(SEP, '.')
            if prefix:
                prefix += '.'
            binaries = ((f, cut_binary_lib_suffix(root, f)) for f in files)
            binaries = [(f, name) for (f, name) in binaries if name]
            if binaries:
                note("root: %s path: %s prefix: %s preprefix: %s", root, path, prefix, preprefix)
                for f, name in binaries:
                    the_name = prefix + name
                    if is_skipped_module(root, f, the_name):
                        note('skipping module %s' % the_name)
                        continue
                    note("cutout: %s", name)
                    if preprefix:
                        note("prefixes: %s %s", prefix, preprefix)
                        pre_name = (preprefix + prefix + name).upper()
                        if pre_name in res:
                            res.pop(pre_name)  # there might be a dupe, if paths got both a/b and a/b/c
                        note("done with %s", name)
                    file_path = os.path.join(root, f)

                    res[the_name.upper()] = (the_name,
                                             file_path,
                                             os.path.getsize(file_path),
                                             file_modification_timestamp(file_path))
    return list(res.values())


def file_modification_timestamp(path):
    return int(os.stat(path).st_mtime)


def is_source_file(path):
    # Skip directories, character and block special devices, named pipes
    # Do not skip regular files and symbolic links to regular files
    if not os.path.isfile(path):
        return False

    # Want to see that files regardless of their encoding.
    if path.endswith(('-nspkg.pth', '.html', '.pxd', '.py', '.pyi', '.pyx')):
        return True
    has_bad_extension = path.endswith((
            # plotlywidget/static/index.js.map is 8.7 MiB.
            # Many map files from notebook are near 2 MiB.
            '.js.map',

            # uvloop/loop.c contains 6.4 MiB of code.
            # Some header files from tensorflow has size more than 1 MiB.
            '.h', '.c',

            # Test data of pycrypto, many files are near 1 MiB.
            '.rsp',

            # No need to read these files even if they are small.
            '.dll', '.pyc', '.pyd', '.pyo', '.so',
    ))
    if has_bad_extension:
        return False
    return is_text_file(path)


def list_sources(paths):
    # noinspection PyBroadException
    try:
        for path in paths:
            if path == os.path.dirname(sys.argv[0]): continue

            path = os.path.normpath(path)

            if path.endswith('.egg') and os.path.isfile(path):
                say("%s\t%s\t%d", path, path, os.path.getsize(path))

            for root, files in walk_python_path(path):
                for name in files:
                    file_path = os.path.join(root, name)
                    if is_source_file(file_path):
                        say("%s\t%s\t%d", os.path.normpath(file_path), path, os.path.getsize(file_path))
        say('END')
        sys.stdout.flush()
    except:
        import traceback

        traceback.print_exc()
        sys.exit(1)


# noinspection PyBroadException
def zip_sources(zip_path):
    if not os.path.exists(zip_path):
        os.makedirs(zip_path)

    zip_filename = os.path.normpath(os.path.sep.join([zip_path, "skeletons.zip"]))

    try:
        zip = zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED)
    except:
        zip = zipfile.ZipFile(zip_filename, 'w')

    try:
        try:
            while True:
                line = sys.stdin.readline()

                if not line:
                    # TextIOWrapper.readline returns an empty string if EOF is hit immediately.
                    break

                line = line.strip()

                if line == '-':
                    break

                if line:
                    # This line will break the split:
                    # /.../dist-packages/setuptools/script template (dev).py setuptools/script template (dev).py
                    split_items = line.split()
                    if len(split_items) > 2:
                        match_two_files = re.match(r'^(.+\.py)\s+(.+\.py)$', line)
                        if not match_two_files:
                            report("Error(zip_sources): invalid line '%s'" % line)
                            continue
                        split_items = match_two_files.group(1, 2)
                    (path, arcpath) = split_items
                    zip.write(path, arcpath)
            say('OK: ' + zip_filename)
            sys.stdout.flush()
        except:
            import traceback

            traceback.print_exc()
            say('Error creating archive.')

            sys.exit(1)
    finally:
        zip.close()


def add_to_zip(zip, paths):
    # noinspection PyBroadException
    try:
        for path in paths:
            print("Walking root %s" % path)
            if path == os.path.dirname(sys.argv[0]): continue

            path = os.path.normpath(path)

            if path.endswith('.egg') and os.path.isfile(path):
                pass  # TODO: handle eggs

            for root, files in walk_python_path(path):
                for name in files:
                    if name.endswith('.py') or name.endswith('-nspkg.pth'):
                        file_path = os.path.join(root, name)
                        arcpath = os.path.relpath(file_path, path)

                        zip.write(file_path, os.path.join(str(hash(path)), arcpath))
    except:
        import traceback

        traceback.print_exc()
        sys.exit(1)


def zip_stdlib(zip_path):
    if not os.path.exists(zip_path):
        os.makedirs(zip_path)

    import platform

    zip_filename = os.path.normpath(os.path.sep.join([zip_path, "%s-%s-stdlib-%s.zip" % (
        'Anaconda' if sys.version.find('Anaconda') != -1 else 'Python',
        '.'.join(map(str, sys.version_info)),
        platform.platform())]))

    print("Adding file to %s" % zip_filename)

    try:
        zip = zipfile.ZipFile(zip_filename, 'w', zipfile.ZIP_DEFLATED)
    except:
        zip = zipfile.ZipFile(zip_filename, 'w')

    try:
        add_to_zip(zip, sys.path)
    finally:
        zip.close()


def build_cache_dir_path(subdir, mod_qname, mod_path):
    return os.path.join(subdir, module_hash(mod_qname, mod_path))


def module_hash(mod_qname, mod_path):
    # Hash the content of a physical module
    if mod_path:
        hash_ = physical_module_hash(mod_path)
    else:
        hash_ = builtin_module_hash(mod_qname)
    # Use shorter hashes in test data as it might affect developers on Windows
    if is_test_mode():
        return hash_[:10]
    return hash_


def builtin_module_hash(mod_qname):
    # Hash the content of interpreter executable, i.e. it will be the same for all built-in modules.
    # Also, it's the same for a virtualenv interpreter and its base.
    with fopen(sys.executable, 'rb') as f:
        return sha256_digest(f)


def physical_module_hash(mod_path):
    with fopen(mod_path, 'rb') as f:
        return sha256_digest(f)


def version_to_tuple(version):
    return tuple(map(int, version.split('.')))


class SkeletonStatus(object):
    UP_TO_DATE = 'UP_TO_DATE'
    """
    Skeleton is up-to-date and doesn't need to be regenerated.
    """
    FAILING = 'FAILING'
    """
    Skeleton generation is known to fail for this module.
    """
    OUTDATED = 'OUTDATED'
    """
    Skeleton needs to be regenerated.
    """


def skeleton_status(base_dir, mod_qname, mod_path):
    gen_version = version_to_tuple(version())

    failed_version = read_failed_version_from_stamp(base_dir, mod_qname)
    if failed_version:
        return SkeletonStatus.OUTDATED if failed_version < gen_version else SkeletonStatus.FAILING

    record = read_failed_version_and_mtime_from_legacy_blacklist(base_dir, mod_path)
    if record:
        failed_version, mtime = record
        outdated = failed_version < gen_version or (mod_path and mtime < file_modification_timestamp(mod_path))
        return SkeletonStatus.OUTDATED if outdated else SkeletonStatus.FAILING

    required_version = read_required_version(mod_qname)
    used_version = read_used_generator_version_from_skeleton_header(base_dir, mod_qname)
    if required_version and used_version:
        return SkeletonStatus.OUTDATED if used_version < required_version else SkeletonStatus.UP_TO_DATE

    # Either missing altogether or corrupted in some way
    return SkeletonStatus.OUTDATED


def read_used_generator_version_from_skeleton_header(base_dir, mod_qname):
    for path in skeleton_path_candidates(base_dir, mod_qname, init_for_pkg=True):
        with ignored_os_errors(errno.ENOENT):
            with fopen(path, 'r') as f:
                return read_generator_version_from_header(f)
    return None


def read_generator_version_from_header(skeleton_file):
    for line in skeleton_file:
        if not line.startswith('#'):
            break

        m = SKELETON_HEADER_VERSION_LINE.match(line)
        if m:
            return version_to_tuple(m.group('version'))
    return None


def skeleton_path_candidates(base_dir, mod_qname, init_for_pkg=False):
    base_path = os.path.join(base_dir, *mod_qname.split('.'))
    if init_for_pkg:
        yield os.path.join(base_path, '__init__.py')
    else:
        yield base_path
    yield base_path + '.py'


def read_failed_version_from_stamp(base_dir, mod_qname):
    with ignored_os_errors(errno.ENOENT):
        with fopen(os.path.join(base_dir, FAILED_VERSION_STAMP_PREFIX + mod_qname), 'r') as f:
            return version_to_tuple(f.read().strip())
    # noinspection PyUnreachableCode
    return None


def read_failed_version_and_mtime_from_legacy_blacklist(sdk_skeletons_dir, mod_path):
    blacklist = read_legacy_blacklist_file(sdk_skeletons_dir, mod_path)
    return blacklist.get(mod_path)


def read_legacy_blacklist_file(sdk_skeletons_dir, mod_path):
    results = {}
    with ignored_os_errors(errno.ENOENT):
        with fopen(os.path.join(sdk_skeletons_dir, '.blacklist'), 'r') as f:
            for line in f:
                if not line or line.startswith('#'):
                    continue

                m = BLACKLIST_VERSION_LINE.match(line)
                if m:
                    bin_path = m.group('path')
                    bin_mtime = m.group('mtime')
                    if is_test_mode() and bin_path == '{mod_path}':
                        bin_path = mod_path
                    if is_test_mode() and bin_mtime == '{mod_mtime}':
                        bin_mtime = file_modification_timestamp(mod_path)
                    else:
                        # On Java side modification time stored in milliseconds.
                        # Python API uses seconds for resolution in os.stat results.
                        bin_mtime = int(m.group('mtime')) / 1000
                    results[bin_path] = (version_to_tuple(m.group('version')), bin_mtime)
    return results


def read_required_version(mod_qname):
    mod_id = '(built-in)' if mod_qname in sys.builtin_module_names else mod_qname
    versions = read_required_gen_version_file()
    # TODO use glob patterns here
    for pattern, version in versions.items():
        if mod_id == pattern:
            return version
    return versions.get('(default)')


def read_required_gen_version_file():
    result = {}
    with fopen(required_gen_version_file_path(), 'r') as f:
        for line in f:
            if not line or line.startswith('#'):
                continue
            m = REQUIRED_GEN_VERSION_LINE.match(line)
            if m:
                result[m.group('name')] = version_to_tuple(m.group('version'))

    return result


class GenerationStatus(object):
    FAILED = 'FAILED'
    """
    Either generation of a skeleton was attempted and failed or cache markers and/or .blacklist indicate that
    it was impossible to generate it for the current version of the generator last time.
    """

    GENERATED = 'GENERATED'
    """
    Skeleton was successfully generated anew and copied both to the cache and a per-sdk skeletons directory.
    """

    COPIED = 'COPIED'
    """
    Skeleton was successfully copied from the cache to a per-sdk skeletons directory.
    """

    UP_TO_DATE = 'UP_TO_DATE'
    """
    Existing skeleton is up to date and, therefore, wasn't touched.
    """


# command-line interface
# noinspection PyBroadException
def process_one(name, mod_file_name, doing_builtins, sdk_skeletons_dir):
    """
    Processes a single module named name defined in file_name (autodetect if not given).
    Returns True on success.
    """
    if has_regular_python_ext(name):
        report("Ignored a regular Python file %r", name)
        return True
    if not quiet:
        say('%s (%r)', name, mod_file_name or 'built-in')
        sys.stdout.flush()
    action("doing nothing")

    # Normalize the path to directory for os.path functions
    sdk_skeletons_dir = sdk_skeletons_dir.rstrip(os.path.sep)
    try:
        python_stubs_dir = os.path.dirname(sdk_skeletons_dir)
        global_cache_dir = os.path.join(python_stubs_dir, CACHE_DIR_NAME)
        mod_cache_dir = build_cache_dir_path(global_cache_dir, name, mod_file_name)
        # At the moment this is actually enforced on Java-side
        if is_standalone_mode():
            sdk_skeleton_status = skeleton_status(sdk_skeletons_dir, name, mod_file_name)
            if sdk_skeleton_status == SkeletonStatus.UP_TO_DATE:
                return GenerationStatus.UP_TO_DATE
            elif sdk_skeleton_status == SkeletonStatus.FAILING:
                return GenerationStatus.FAILED

        cached_skeleton_status = skeleton_status(mod_cache_dir, name, mod_file_name)
        if cached_skeleton_status == SkeletonStatus.OUTDATED:
            say('Updating cache for %s at %r', name, mod_cache_dir)
            # All builtin modules go into the same directory
            if not doing_builtins:
                delete(mod_cache_dir)
            mkdir(mod_cache_dir)

            old_modules = list(sys.modules.keys())
            imported_module_names = set()

            class MyFinder:
                # noinspection PyMethodMayBeStatic
                def find_module(self, fullname, path=None):
                    if fullname != name:
                        imported_module_names.add(fullname)
                    return None

            my_finder = None
            if hasattr(sys, 'meta_path'):
                my_finder = MyFinder()
                sys.meta_path.insert(0, my_finder)
            else:
                imported_module_names = None

            create_failed_version_stamp(mod_cache_dir, name)

            action("importing")
            __import__(name)  # sys.modules will fill up with what we want

            if my_finder:
                sys.meta_path.remove(my_finder)
            if imported_module_names is None:
                imported_module_names = set(sys.modules.keys()) - set(old_modules)

            redo_module(name, mod_file_name, doing_builtins, mod_cache_dir, sdk_skeletons_dir)
            # The C library may have called Py_InitModule() multiple times to define several modules (gtk._gtk and gtk.gdk);
            # restore all of them
            path = name.split(".")
            redo_imports = not ".".join(path[:-1]) in MODULES_INSPECT_DIR
            if redo_imports:
                for m in sys.modules.keys():
                    if m.startswith("pycharm_generator_utils"): continue
                    action("looking at possible submodule %r", m)
                    if m == name or m in old_modules or m in sys.builtin_module_names:
                        continue
                    # Synthetic module, not explicitly imported
                    if m not in imported_module_names and not hasattr(sys.modules[m], '__file__'):
                        if not quiet:
                            say(m)
                            sys.stdout.flush()
                        action("opening %r", mod_cache_dir)
                        try:
                            redo_module(m, mod_file_name, doing_builtins, cache_dir=mod_cache_dir,
                                        sdk_dir=sdk_skeletons_dir)
                        finally:
                            action("closing %r", mod_cache_dir)
            return GenerationStatus.GENERATED
        elif cached_skeleton_status == SkeletonStatus.FAILING:
            say('Cache entry for %s at %r indicates failed generation', name, mod_cache_dir)
            return GenerationStatus.FAILED
        else:
            # Copy entire skeletons directory if nothing needs to be updated
            say('Copying cached stubs for %s from %r to %r', name, mod_cache_dir, sdk_skeletons_dir)
            copy_skeletons(mod_cache_dir, sdk_skeletons_dir, get_module_origin(mod_file_name, name))
            return GenerationStatus.COPIED
    except:
        exctype, value = sys.exc_info()[:2]
        msg = "Failed to process %r while %s: %s"
        args = name, CURRENT_ACTION, str(value)
        report(msg, *args)
        if debug_mode:
            if sys.platform == 'cli':
                import traceback
                traceback.print_exc(file=sys.stderr)
            raise
        return GenerationStatus.FAILED


def get_module_origin(mod_path, mod_qname):
    if mod_qname in sys.builtin_module_names:
        return OriginType.BUILTIN

    # Unless it's a builtin module all bundled skeletons should have
    # file system independent "(pre-generated)" marker in their header
    if is_pregeneration_mode():
        return OriginType.PREGENERATED

    if not mod_path:
        return None

    if is_test_mode():
        return get_relative_path_by_qname(mod_path, mod_qname)
    return mod_path


def create_failed_version_stamp(base_dir, mod_qname):
    failed_version_stamp = os.path.join(base_dir, FAILED_VERSION_STAMP_PREFIX + mod_qname)
    with fopen(failed_version_stamp, 'w') as f:
        f.write(version())
    return failed_version_stamp


def delete_failed_version_stamp(base_dir, mod_qname):
    delete(os.path.join(base_dir, FAILED_VERSION_STAMP_PREFIX + mod_qname))


def get_help_text():
    return (
        # 01234567890123456789012345678901234567890123456789012345678901234567890123456789
        'Generates interface skeletons for python modules.' '\n'
        'Usage: ' '\n'
        '  generator [options] [module_name [file_name]]' '\n'
        '  generator [options] -L ' '\n'
        'module_name is fully qualified, and file_name is where the module is defined.' '\n'
        'E.g. foo.bar /usr/lib/python/foo_bar.so' '\n'
        'For built-in modules file_name is not provided.' '\n'
        'Output files will be named as modules plus ".py" suffix.' '\n'
        'Normally every name processed will be printed and stdout flushed.' '\n'
        'directory_list is one string separated by OS-specific path separtors.' '\n'
        '\n'
        'Options are:' '\n'
        ' -h -- prints this help message.' '\n'
        ' -d dir -- output dir, must be writable. If not given, current dir is used.' '\n'
        ' -b -- use names from sys.builtin_module_names' '\n'
        ' -q -- quiet, do not print anything on stdout. Errors still go to stderr.' '\n'
        ' -x -- die on exceptions with a stacktrace; only for debugging.' '\n'
        ' -v -- be verbose, print lots of debug output to stderr' '\n'
        ' -c modules -- import CLR assemblies with specified names' '\n'
        ' -p -- run CLR profiler ' '\n'
        ' -s path_list -- add paths to sys.path before run; path_list lists directories' '\n'
        '    separated by path separator char, e.g. "c:\\foo;d:\\bar;c:\\with space"' '\n'
        ' -L -- print version and then a list of binary module files found ' '\n'
        '    on sys.path and in directories in directory_list;' '\n'
        '    lines are "qualified.module.name /full/path/to/module_file.{pyd,dll,so}"' '\n'
        ' -i -- read module_name, file_name and list of imported CLR assemblies from stdin line-by-line' '\n'
        ' -S -- lists all python sources found in sys.path and in directories in directory_list\n'
        ' -z archive_name -- zip files to archive_name. Accepts files to be archived from stdin in format <filepath> <name in archive>'
    )


if __name__ == "__main__":
    try:
        # Get traces after segmentation faults
        import faulthandler

        faulthandler.enable()
    except ImportError:
        pass

    from getopt import getopt

    helptext = get_help_text()
    opts, args = getopt(sys.argv[1:], "d:hbqxvc:ps:LiSzu")
    opts = dict(opts)

    quiet = '-q' in opts
    set_verbose('-v' in opts)
    subdir = opts.get('-d', '')

    if not opts or '-h' in opts:
        say(helptext)
        sys.exit(0)

    if '-L' not in opts and '-b' not in opts and '-S' not in opts and '-i' not in opts and '-u' not in opts and not args:
        report("Neither -L nor -b nor -S nor any module name given")
        sys.exit(1)

    if "-x" in opts:
        debug_mode = True

    # patch sys.path?
    extra_path = opts.get('-s', None)
    if extra_path:
        source_dirs = extra_path.split(os.path.pathsep)
        for p in source_dirs:
            if p and p not in sys.path:
                sys.path.append(p)  # we need this to make things in additional dirs importable
        note("Altered sys.path: %r", sys.path)

    # find binaries?
    if "-L" in opts:
        if len(args) > 0:
            report("Expected no args with -L, got %d args", len(args))
            sys.exit(1)
        say(version())
        results = list(list_binaries(sys.path))
        results.sort()
        for name, path, size, last_modified in results:
            say("%s\t%s\t%d\t%d", name, path, size, last_modified)
        sys.exit(0)

    if "-S" in opts:
        if len(args) > 0:
            report("Expected no args with -S, got %d args", len(args))
            sys.exit(1)
        say(version())
        list_sources(sys.path)
        sys.exit(0)

    if "-z" in opts:
        if len(args) != 1:
            report("Expected 1 arg with -z, got %d args", len(args))
            sys.exit(1)
        zip_sources(args[0])
        sys.exit(0)

    if "-u" in opts:
        if len(args) != 1:
            report("Expected 1 arg with -u, got %d args", len(args))
            sys.exit(1)
        zip_stdlib(args[0])
        sys.exit(0)

    # build skeleton(s)

    timer = Timer()
    # determine names
    if '-b' in opts:
        if args:
            report("No names should be specified with -b")
            sys.exit(1)
        names = list(sys.builtin_module_names)
        if not BUILTIN_MOD_NAME in names:
            names.append(BUILTIN_MOD_NAME)
        if '__main__' in names:
            names.remove('__main__')  # we don't want ourselves processed
        ok = True
        for name in names:
            status = process_one(name, None, True, subdir)
            # Assume that if a skeleton for one built-in module was copied, all of them were copied.
            if status == GenerationStatus.COPIED:
                break
            elif status == GenerationStatus.FAILED:
                ok = False
        if not ok:
            sys.exit(1)

    else:
        if '-i' in opts:
            if args:
                report("No names should be specified with -i")
                sys.exit(1)
            name = sys.stdin.readline().strip()

            mod_file_name = sys.stdin.readline().strip()
            if not mod_file_name:
                mod_file_name = None

            refs = sys.stdin.readline().strip()
        else:
            if len(args) > 2:
                report("Only module_name or module_name and file_name should be specified; got %d args", len(args))
                sys.exit(1)
            name = args[0]

            if len(args) == 2:
                mod_file_name = args[1]
            else:
                mod_file_name = None

            refs = opts.get('-c', '')

        if sys.platform == 'cli':
            # noinspection PyUnresolvedReferences
            import clr

            if refs:
                for ref in refs.split(';'): clr.AddReferenceByPartialName(ref)

            if '-p' in opts:
                atexit.register(print_profile)

            # We take module name from import statement
            name = get_namespace_by_name(name)

        if process_one(name, mod_file_name, False, subdir) == GenerationStatus.FAILED:
            sys.exit(1)

    say("Generation completed in %d ms", timer.elapsed())
