# encoding: utf-8
import collections
import fnmatch
import json
import logging
from copy import deepcopy

from generator3.util_methods import *

# We need such conditional import always disabled at runtime in order to use
# "typing" without the need to actually bundle the module with PyCharm.
# It's similar to what Mypy recommends with its "MYPY" flag for compatibility
# with Python 3.5.1 (https://mypy.readthedocs.io/en/latest/common_issues.html#import-cycles).
TYPE_CHECKING = False
if TYPE_CHECKING:
    from typing import List, Dict, Any, NewType, Tuple, Optional, TextIO

    SkeletonStatusId = NewType('SkeletonStatusId', str)
    GenerationStatusId = NewType('GenerationStatusId', str)
    GeneratorVersion = Tuple[int, int]

# TODO: Move all CLR-specific functions to clr_tools
quiet = False
_parent_dir = os.path.dirname(os.path.abspath(__file__))


# TODO move to property of Generator3 as soon as tests finished
@cached
def version():
    env_version = os.environ.get(ENV_VERSION)
    if env_version:
        return env_version

    with fopen(os.path.join(_parent_dir, 'version.txt'), 'r') as f:
        return f.read().strip()


# TODO move to property of Generator3 as soon as tests finished
@cached
def required_gen_version_file_path():
    return os.environ.get(ENV_REQUIRED_GEN_VERSION_FILE, os.path.join(_parent_dir, 'required_gen_version'))


@cached
def is_test_mode():
    return ENV_TEST_MODE_FLAG in os.environ


@cached
def is_pregeneration_mode():
    return ENV_PREGENERATION_MODE_FLAG in os.environ


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
            os.path.exists(os.path.join(root, d, "__init__.pyi")) or
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


def file_modification_timestamp(path):
    return int(os.stat(path).st_mtime)


def build_cache_dir_path(subdir, mod_qname, mod_path):
    return os.path.join(subdir, module_hash(mod_qname, mod_path))


def module_hash(mod_qname, mod_path):
    # Hash the content of a physical module
    if mod_path:
        hash_ = physical_module_hash(mod_path)
    else:
        hash_ = builtin_module_hash()
    # Use shorter hashes in test data as it might affect developers on Windows
    if is_test_mode():
        return hash_[:10]
    return hash_


def builtin_module_hash():
    return sha256_digest(sys.version.encode(encoding='utf-8'))


def physical_module_hash(mod_path):
    with fopen(mod_path, 'rb') as f:
        return sha256_digest(f)


def version_to_tuple(version):
    # type: (str) -> GeneratorVersion
    # noinspection PyTypeChecker
    return tuple(map(int, version.split('.')))


class OriginType(object):
    FILE = 'FILE'
    BUILTIN = '(built-in)'
    PREGENERATED = '(pre-generated)'


class SkeletonStatus(object):
    UP_TO_DATE = 'UP_TO_DATE'  # type: SkeletonStatusId
    """
    Skeleton is up-to-date and doesn't need to be regenerated.
    """
    FAILING = 'FAILING'  # type: SkeletonStatusId
    """
    Skeleton generation is known to fail for this module.
    """
    OUTDATED = 'OUTDATED'  # type: SkeletonStatusId
    """
    Skeleton needs to be regenerated.
    """


def skeleton_status(base_dir, mod_qname, mod_path, sdk_skeleton_state=None):
    # type: (str, str, str, Dict[str, Any]) -> SkeletonStatusId
    gen_version = version_to_tuple(version())
    used_version = None

    skeleton_meta = sdk_skeleton_state if sdk_skeleton_state is not None else {}
    if 'gen_version' not in skeleton_meta:
        # Such stamps exist only in the cache
        failed_version = read_failed_version_from_stamp(base_dir, mod_qname)
        if failed_version:
            used_version = failed_version
            skeleton_meta['status'] = GenerationStatus.FAILED

        # Black list exists only in a per-sdk skeletons directory
        blacklist_record = read_failed_version_and_mtime_from_legacy_blacklist(base_dir, mod_path)
        if blacklist_record:
            used_version, mtime = blacklist_record
            skeleton_meta['status'] = GenerationStatus.FAILED
            skeleton_meta['bin_mtime'] = mtime

        existing_skeleton_version = read_used_generator_version_from_skeleton_header(base_dir, mod_qname)
        if existing_skeleton_version:
            skeleton_meta['status'] = GenerationStatus.GENERATED
            used_version = existing_skeleton_version

        if used_version:
            skeleton_meta['gen_version'] = '.'.join(map(str, used_version))

    used_version = skeleton_meta.get('gen_version')
    if used_version:
        used_version = version_to_tuple(used_version)

    used_bin_mtime = skeleton_meta.get('bin_mtime')
    # state.json is normally passed for remote skeletons only. Since we have neither cache,
    # nor physical sdk skeletons there, we have to rely on binary modification time to detect
    # outdated skeletons.
    if mod_path and used_bin_mtime is not None and used_bin_mtime < file_modification_timestamp(mod_path):
        return SkeletonStatus.OUTDATED

    if skeleton_meta.get('status') == GenerationStatus.FAILED:
        return SkeletonStatus.OUTDATED if used_version < gen_version else SkeletonStatus.FAILING

    required_version = read_required_version(mod_qname)
    if required_version and used_version:
        return SkeletonStatus.OUTDATED if used_version < required_version else SkeletonStatus.UP_TO_DATE

    # Either missing altogether or corrupted in some way
    return SkeletonStatus.OUTDATED


def read_used_generator_version_from_skeleton_header(base_dir, mod_qname):
    # type: (str, str) -> Optional[GeneratorVersion]
    for path in skeleton_path_candidates(base_dir, mod_qname, init_for_pkg=True):
        with ignored_os_errors(errno.ENOENT):
            with fopen(path, 'r') as f:
                return read_generator_version_from_header(f)
    return None


def read_generator_version_from_header(skeleton_file):
    # type: (TextIO) -> Optional[GeneratorVersion]
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
    # type: (str, str) -> Optional[GeneratorVersion]
    with ignored_os_errors(errno.ENOENT):
        with fopen(os.path.join(base_dir, FAILED_VERSION_STAMP_PREFIX + mod_qname), 'r') as f:
            return version_to_tuple(f.read().strip())
    # noinspection PyUnreachableCode
    return None


def read_failed_version_and_mtime_from_legacy_blacklist(sdk_skeletons_dir, mod_path):
    # type: (str, str) -> Optional[Tuple[GeneratorVersion, int]]
    blacklist = read_legacy_blacklist_file(sdk_skeletons_dir, mod_path)
    return blacklist.get(mod_path)


def read_legacy_blacklist_file(sdk_skeletons_dir, mod_path):
    # type: (str, str) -> Dict[str, Tuple[GeneratorVersion, int]]
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
    # type: (str) -> Optional[GeneratorVersion]
    mod_id = '(built-in)' if mod_qname in sys.builtin_module_names else mod_qname
    versions = read_required_gen_version_file()
    # TODO use glob patterns here
    return versions.get(mod_id, versions.get('(default)'))


def read_required_gen_version_file():
    # type: () -> Dict[str, GeneratorVersion]
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
    FAILED = 'FAILED'  # type: GenerationStatusId
    """
    Either generation of a skeleton was attempted and failed or cache markers and/or .blacklist indicate that
    it was impossible to generate it for the current version of the generator last time.
    """

    GENERATED = 'GENERATED'  # type: GenerationStatusId
    """
    Skeleton was successfully generated anew and copied both to the cache and a per-sdk skeletons directory.
    """

    COPIED = 'COPIED'  # type: GenerationStatusId
    """
    Skeleton was successfully copied from the cache to a per-sdk skeletons directory.
    """

    UP_TO_DATE = 'UP_TO_DATE'  # type: GenerationStatusId
    """
    Existing skeleton is up to date and, therefore, wasn't touched.
    """


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
        return get_portable_test_module_path(mod_path, mod_qname)
    return mod_path


def create_failed_version_stamp(base_dir, mod_qname):
    failed_version_stamp = os.path.join(base_dir, FAILED_VERSION_STAMP_PREFIX + mod_qname)
    with fopen(failed_version_stamp, 'w') as f:
        f.write(version())
    return failed_version_stamp


def delete_failed_version_stamp(base_dir, mod_qname):
    delete(os.path.join(base_dir, FAILED_VERSION_STAMP_PREFIX + mod_qname))


BinaryModule = collections.namedtuple('BinaryModule', ['qname', 'path'])


def progress(text=None, fraction=None, minor=False):
    data = {}

    if text is not None:
        data['text'] = text
        data['minor'] = minor

    if fraction is not None:
        data['fraction'] = round(fraction, 2)

    control_message('progress', data)


def control_message(msg_type, data):
    data['type'] = msg_type
    say(json.dumps(data))


def trace(msg, *args, **kwargs):
    logging.log(logging.getLevelName('TRACE'), msg, *args, **kwargs)


class SkeletonGenerator(object):
    def __init__(self,
                 output_dir,  # type: str
                 roots=None,  # type: List[str]
                 state_json=None,  # type: Dict[str, Any]
                 write_state_json=False,
                 ):
        self.output_dir = output_dir.rstrip(os.path.sep)
        # TODO make cache directory configurable via CLI
        self.cache_dir = os.path.join(os.path.dirname(self.output_dir), CACHE_DIR_NAME)
        self.roots = roots
        self.in_state_json = state_json
        self.out_state_json = {'sdk_skeletons': {}}
        self.write_state_json = write_state_json

    def discover_and_process_all_modules(self, name_pattern=None, builtins_only=False):
        if name_pattern is None:
            name_pattern = '*'

        all_modules = sorted(self.collect_builtin_modules(), key=(lambda b: b.qname))

        if not builtins_only:
            progress("Discovering binary modules...")
            all_modules.extend(sorted(self.discover_binary_modules(), key=(lambda b: b.qname)))

        matching_modules = [m for m in all_modules if fnmatch.fnmatchcase(m.qname, name_pattern)]

        progress("Updating skeletons...")
        for i, mod in enumerate(matching_modules):
            progress(text=mod.qname, fraction=float(i) / len(matching_modules), minor=True)
            self.process_module(mod.qname, mod.path)
        progress(fraction=1.0)

        if self.write_state_json:
            mkdir(self.output_dir)
            state_json_path = os.path.join(self.output_dir, STATE_FILE_NAME)
            logging.info('Writing skeletons state to %r', state_json_path)
            with fopen(state_json_path, 'w') as f:
                json.dump(self.out_state_json, f, sort_keys=True)

    @staticmethod
    def collect_builtin_modules():
        # type: () -> List[BinaryModule]
        names = list(sys.builtin_module_names)
        if BUILTIN_MOD_NAME not in names:
            names.append(BUILTIN_MOD_NAME)
        if '__main__' in names:
            names.remove('__main__')
        return [BinaryModule(name, None) for name in names]

    def discover_binary_modules(self):
        # type: () -> List[BinaryModule]
        """
        Finds binaries in the given list of paths.
        Understands nested paths, as sys.paths have it (both "a/b" and "a/b/c").
        Tries to be case-insensitive, but case-preserving.
        """
        SEP = os.path.sep
        res = {}  # {name.upper(): (name, full_path)} # b/c windows is case-oblivious
        if not self.roots:
            return []
        # TODO Move to future InterpreterHandler
        if IS_JAVA:  # jython can't have binary modules
            return []
        paths = sorted_no_case(self.roots)
        for path in paths:
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
                    trace("root: %s path: %s prefix: %s preprefix: %s", root, path, prefix, preprefix)
                    for f, name in binaries:
                        the_name = prefix + name
                        if is_skipped_module(root, f, the_name):
                            trace('skipping module %s', the_name)
                            continue
                        trace("cutout: %s", name)
                        if preprefix:
                            trace("prefixes: %s %s", prefix, preprefix)
                            pre_name = (preprefix + prefix + name).upper()
                            if pre_name in res:
                                res.pop(pre_name)  # there might be a dupe, if paths got both a/b and a/b/c
                            trace("done with %s", name)
                        file_path = os.path.join(root, f)

                        res[the_name.upper()] = BinaryModule(the_name, file_path)
        return list(res.values())

    def process_module(self, mod_name, mod_path=None):
        # type: (str, str) -> GenerationStatusId
        if self.in_state_json:
            existing_skeleton_meta = self.in_state_json['sdk_skeletons'].get(mod_name, {})
            sdk_skeleton_state = self.out_state_json['sdk_skeletons'][mod_name] = deepcopy(existing_skeleton_meta)
        else:
            sdk_skeleton_state = self.out_state_json['sdk_skeletons'][mod_name] = {}

        status = self.reuse_or_generate_skeleton(mod_name, mod_path, sdk_skeleton_state)
        control_message('generation_result', {
            'module_name': mod_name,
            'module_origin': get_module_origin(mod_path, mod_name),
            'generation_status': status
        })
        if mod_path:
            sdk_skeleton_state['bin_mtime'] = file_modification_timestamp(mod_path)

        # If we skipped generation for already failing module, we can safely set
        # the current generator version in ".state.json" as skipping means that this
        # version is not greater (i.e. we don't need to distinguish between "skipped as failing"
        # and "failed during generation").
        if status not in (GenerationStatus.UP_TO_DATE, GenerationStatus.COPIED):
            # TODO don't update state_json inplace
            sdk_skeleton_state['gen_version'] = version()

        sdk_skeleton_state['status'] = status

        if is_test_mode():
            sdk_skeleton_state.pop('bin_mtime', None)
        return status

    def reuse_or_generate_skeleton(self, mod_name, mod_path, mod_state_json):
        # type: (str, str, Dict[str, Any]) -> GenerationStatusId
        if not quiet:
            logging.info('%s (%r)', mod_name, mod_path or 'built-in')
        action("doing nothing")

        try:
            sdk_skeleton_status = skeleton_status(self.output_dir, mod_name, mod_path, mod_state_json)
            if sdk_skeleton_status == SkeletonStatus.UP_TO_DATE:
                return GenerationStatus.UP_TO_DATE
            elif sdk_skeleton_status == SkeletonStatus.FAILING:
                return GenerationStatus.FAILED

            # At this point we will either generate skeleton anew all take it from the cache.
            # In either case state.json is supposed to be populated by this results.
            if mod_state_json:
                mod_state_json.clear()

            mod_cache_dir = build_cache_dir_path(self.cache_dir, mod_name, mod_path)
            cached_skeleton_status = skeleton_status(mod_cache_dir, mod_name, mod_path, mod_state_json)
            if cached_skeleton_status == SkeletonStatus.OUTDATED:
                return execute_in_subprocess_synchronously(name='Skeleton Generator Worker',
                                                           func=generate_skeleton,
                                                           args=(mod_name,
                                                                 mod_path,
                                                                 mod_cache_dir,
                                                                 self.output_dir),
                                                           kwargs={},
                                                           failure_result=GenerationStatus.FAILED)
            elif cached_skeleton_status == SkeletonStatus.FAILING:
                logging.info('Cache entry for %s at %r indicates failed generation', mod_name, mod_cache_dir)
                return GenerationStatus.FAILED
            else:
                # Copy entire skeletons directory if nothing needs to be updated
                logging.info('Copying cached stubs for %s from %r to %r', mod_name, mod_cache_dir, self.output_dir)
                copy_skeletons(mod_cache_dir, self.output_dir, get_module_origin(mod_path, mod_name))
                return GenerationStatus.COPIED
        except:
            exctype, value = sys.exc_info()[:2]
            msg = "Failed to process %r while %s: %s"
            args = mod_name, CURRENT_ACTION, str(value)
            report(msg, *args)
            if sys.platform == 'cli':
                import traceback
                traceback.print_exc(file=sys.stderr)
            raise


@contextmanager
def imported_names_collected():
    imported_names = set()

    class MyFinder(object):
        # noinspection PyMethodMayBeStatic
        def find_module(self, fullname, path=None):
            imported_names.add(fullname)
            return None

    my_finder = MyFinder()
    sys.meta_path.insert(0, my_finder)
    try:
        yield imported_names
    finally:
        sys.meta_path.remove(my_finder)


def generate_skeleton(name, mod_file_name, mod_cache_dir, output_dir):
    # type: (str, str, str, str) -> GenerationStatusId

    logging.info('Updating cache for %s at %r', name, mod_cache_dir)
    doing_builtins = mod_file_name is None
    # All builtin modules go into the same directory
    if not doing_builtins:
        delete(mod_cache_dir)
    mkdir(mod_cache_dir)

    create_failed_version_stamp(mod_cache_dir, name)

    action("importing")
    old_modules = list(sys.modules.keys())
    with imported_names_collected() as imported_module_names:
        __import__(name)  # sys.modules will fill up with what we want

    redo_module(name, mod_file_name, mod_cache_dir, output_dir)
    # The C library may have called Py_InitModule() multiple times to define several modules (gtk._gtk and gtk.gdk);
    # restore all of them
    path = name.split(".")
    redo_imports = not ".".join(path[:-1]) in MODULES_INSPECT_DIR
    if redo_imports:
        initial_module_set = set(sys.modules)
        for m in list(sys.modules):
            if not m.startswith(name):
                continue
            # Python 2 puts dummy None entries in sys.modules for imports of
            # top-level modules made from inside packages unless absolute
            # imports are explicitly enabled.
            # See https://www.python.org/dev/peps/pep-0328/#relative-imports-and-indirection-entries-in-sys-modules
            if not sys.modules[m] or m.startswith("generator3"):
                continue
            action("looking at possible submodule %r", m)
            if m == name or m in old_modules or m in sys.builtin_module_names:
                continue
            # Synthetic module, not explicitly imported
            if m not in imported_module_names and not hasattr(sys.modules[m], '__file__'):
                if not quiet:
                    logging.info('Processing submodule %s of %s', m, name)
                action("opening %r", mod_cache_dir)
                try:
                    redo_module(m, mod_file_name, cache_dir=mod_cache_dir, output_dir=output_dir)
                    extra_modules = set(sys.modules) - initial_module_set
                    if extra_modules:
                        report('Introspecting submodule %r of %r led to extra content of sys.modules: %s',
                               m, name, ', '.join(extra_modules))
                finally:
                    action("closing %r", mod_cache_dir)
    return GenerationStatus.GENERATED


def redo_module(module_name, module_file_name, cache_dir, output_dir):
    # type: (str, str, str, str) -> None
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
        from generator3.module_redeclarator import ModuleRedeclarator
        r = ModuleRedeclarator(mod, module_name, module_file_name, cache_dir=cache_dir,
                               doing_builtins=(module_file_name is None))
        create_failed_version_stamp(cache_dir, module_name)
        r.redo(module_name, ".".join(mod_path[:-1]) in MODULES_INSPECT_DIR)
        action("flushing")
        r.flush()
        delete_failed_version_stamp(cache_dir, module_name)
        # Incrementally copy whatever we managed to successfully generate so far
        copy_skeletons(cache_dir, output_dir, get_module_origin(module_file_name, module_name))
    else:
        report("Failed to find imported module in sys.modules " + module_name)




