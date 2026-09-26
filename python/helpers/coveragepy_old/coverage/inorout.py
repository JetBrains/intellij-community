# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Determining whether files are being measured/reported or not."""

# For finding the stdlib
import atexit
import inspect
import itertools
import os
import platform
import re
import sys
import traceback

from coverage import env
from coverage.backward import code_object
from coverage.disposition import FileDisposition, disposition_init
from coverage.files import TreeMatcher, FnmatchMatcher, ModuleMatcher
from coverage.files import prep_patterns, find_python_files, canonical_filename
from coverage.misc import CoverageException
from coverage.python import source_for_file, source_for_morf


# Pypy has some unusual stuff in the "stdlib".  Consider those locations
# when deciding where the stdlib is.  These modules are not used for anything,
# they are modules importable from the pypy lib directories, so that we can
# find those directories.
_structseq = _pypy_irc_topic = None
if env.PYPY:
    try:
        import _structseq
    except ImportError:
        pass

    try:
        import _pypy_irc_topic
    except ImportError:
        pass


def canonical_path(morf, directory=False):
    """Return the canonical path of the module or file `morf`.

    If the module is a package, then return its directory. If it is a
    module, then return its file, unless `directory` is True, in which
    case return its enclosing directory.

    """
    morf_path = canonical_filename(source_for_morf(morf))
    if morf_path.endswith("__init__.py") or directory:
        morf_path = os.path.split(morf_path)[0]
    return morf_path


def name_for_module(filename, frame):
    """Get the name of the module for a filename and frame.

    For configurability's sake, we allow __main__ modules to be matched by
    their importable name.

    If loaded via runpy (aka -m), we can usually recover the "original"
    full dotted module name, otherwise, we resort to interpreting the
    file name to get the module's name.  In the case that the module name
    can't be determined, None is returned.

    """
    module_globals = frame.f_globals if frame is not None else {}
    if module_globals is None:          # pragma: only ironpython
        # IronPython doesn't provide globals: https://github.com/IronLanguages/main/issues/1296
        module_globals = {}

    dunder_name = module_globals.get('__name__', None)

    if isinstance(dunder_name, str) and dunder_name != '__main__':
        # This is the usual case: an imported module.
        return dunder_name

    loader = module_globals.get('__loader__', None)
    for attrname in ('fullname', 'name'):   # attribute renamed in py3.2
        if hasattr(loader, attrname):
            fullname = getattr(loader, attrname)
        else:
            continue

        if isinstance(fullname, str) and fullname != '__main__':
            # Module loaded via: runpy -m
            return fullname

    # Script as first argument to Python command line.
    inspectedname = inspect.getmodulename(filename)
    if inspectedname is not None:
        return inspectedname
    else:
        return dunder_name


def module_is_namespace(mod):
    """Is the module object `mod` a PEP420 namespace module?"""
    return hasattr(mod, '__path__') and getattr(mod, '__file__', None) is None


def module_has_file(mod):
    """Does the module object `mod` have an existing __file__ ?"""
    mod__file__ = getattr(mod, '__file__', None)
    if mod__file__ is None:
        return False
    return os.path.exists(mod__file__)


class InOrOut(object):
    """Machinery for determining what files to measure."""

    def __init__(self, warn, debug):
        self.warn = warn
        self.debug = debug

        # The matchers for should_trace.
        self.source_match = None
        self.source_pkgs_match = None
        self.pylib_paths = self.cover_paths = None
        self.pylib_match = self.cover_match = None
        self.include_match = self.omit_match = None
        self.plugins = []
        self.disp_class = FileDisposition

        # The source argument can be directories or package names.
        self.source = []
        self.source_pkgs = []
        self.source_pkgs_unmatched = []
        self.omit = self.include = None

    def configure(self, config):
        """Apply the configuration to get ready for decision-time."""
        self.source_pkgs.extend(config.source_pkgs)
        for src in config.source or []:
            if os.path.isdir(src):
                self.source.append(canonical_filename(src))
            else:
                self.source_pkgs.append(src)
        self.source_pkgs_unmatched = self.source_pkgs[:]

        self.omit = prep_patterns(config.run_omit)
        self.include = prep_patterns(config.run_include)

        # The directories for files considered "installed with the interpreter".
        self.pylib_paths = set()
        if not config.cover_pylib:
            # Look at where some standard modules are located. That's the
            # indication for "installed with the interpreter". In some
            # environments (virtualenv, for example), these modules may be
            # spread across a few locations. Look at all the candidate modules
            # we've imported, and take all the different ones.
            for m in (atexit, inspect, os, platform, _pypy_irc_topic, re, _structseq, traceback):
                if m is not None and hasattr(m, "__file__"):
                    self.pylib_paths.add(canonical_path(m, directory=True))

            if _structseq and not hasattr(_structseq, '__file__'):
                # PyPy 2.4 has no __file__ in the builtin modules, but the code
                # objects still have the file names.  So dig into one to find
                # the path to exclude.  The "filename" might be synthetic,
                # don't be fooled by those.
                structseq_file = code_object(_structseq.structseq_new).co_filename
                if not structseq_file.startswith("<"):
                    self.pylib_paths.add(canonical_path(structseq_file))

        # To avoid tracing the coverage.py code itself, we skip anything
        # located where we are.
        self.cover_paths = [canonical_path(__file__, directory=True)]
        if env.TESTING:
            # Don't include our own test code.
            self.cover_paths.append(os.path.join(self.cover_paths[0], "tests"))

            # When testing, we use PyContracts, which should be considered
            # part of coverage.py, and it uses six. Exclude those directories
            # just as we exclude ourselves.
            import contracts
            import six
            for mod in [contracts, six]:
                self.cover_paths.append(canonical_path(mod))

        def debug(msg):
            if self.debug:
                self.debug.write(msg)

        # Create the matchers we need for should_trace
        if self.source or self.source_pkgs:
            against = []
            if self.source:
                self.source_match = TreeMatcher(self.source)
                against.append("trees {!r}".format(self.source_match))
            if self.source_pkgs:
                self.source_pkgs_match = ModuleMatcher(self.source_pkgs)
                against.append("modules {!r}".format(self.source_pkgs_match))
            debug("Source matching against " + " and ".join(against))
        else:
            if self.cover_paths:
                self.cover_match = TreeMatcher(self.cover_paths)
                debug("Coverage code matching: {!r}".format(self.cover_match))
            if self.pylib_paths:
                self.pylib_match = TreeMatcher(self.pylib_paths)
                debug("Python stdlib matching: {!r}".format(self.pylib_match))
        if self.include:
            self.include_match = FnmatchMatcher(self.include)
            debug("Include matching: {!r}".format(self.include_match))
        if self.omit:
            self.omit_match = FnmatchMatcher(self.omit)
            debug("Omit matching: {!r}".format(self.omit_match))

    def should_trace(self, filename, frame=None):
        """Decide whether to trace execution in `filename`, with a reason.

        This function is called from the trace function.  As each new file name
        is encountered, this function determines whether it is traced or not.

        Returns a FileDisposition object.

        """
        original_filename = filename
        disp = disposition_init(self.disp_class, filename)

        def nope(disp, reason):
            """Simple helper to make it easy to return NO."""
            disp.trace = False
            disp.reason = reason
            return disp

        if frame is not None:
            # Compiled Python files have two file names: frame.f_code.co_filename is
            # the file name at the time the .pyc was compiled.  The second name is
            # __file__, which is where the .pyc was actually loaded from.  Since
            # .pyc files can be moved after compilation (for example, by being
            # installed), we look for __file__ in the frame and prefer it to the
            # co_filename value.
            dunder_file = frame.f_globals and frame.f_globals.get('__file__')
            if dunder_file:
                filename = source_for_file(dunder_file)
                if original_filename and not original_filename.startswith('<'):
                    orig = os.path.basename(original_filename)
                    if orig != os.path.basename(filename):
                        # Files shouldn't be renamed when moved. This happens when
                        # exec'ing code.  If it seems like something is wrong with
                        # the frame's file name, then just use the original.
                        filename = original_filename

        if not filename:
            # Empty string is pretty useless.
            return nope(disp, "empty string isn't a file name")

        if filename.startswith('memory:'):
            return nope(disp, "memory isn't traceable")

        if filename.startswith('<'):
            # Lots of non-file execution is represented with artificial
            # file names like "<string>", "<doctest readme.txt[0]>", or
            # "<exec_function>".  Don't ever trace these executions, since we
            # can't do anything with the data later anyway.
            return nope(disp, "not a real file name")

        # pyexpat does a dumb thing, calling the trace function explicitly from
        # C code with a C file name.
        if re.search(r"[/\\]Modules[/\\]pyexpat.c", filename):
            return nope(disp, "pyexpat lies about itself")

        # Jython reports the .class file to the tracer, use the source file.
        if filename.endswith("$py.class"):
            filename = filename[:-9] + ".py"

        canonical = canonical_filename(filename)
        disp.canonical_filename = canonical

        # Try the plugins, see if they have an opinion about the file.
        plugin = None
        for plugin in self.plugins.file_tracers:
            if not plugin._coverage_enabled:
                continue

            try:
                file_tracer = plugin.file_tracer(canonical)
                if file_tracer is not None:
                    file_tracer._coverage_plugin = plugin
                    disp.trace = True
                    disp.file_tracer = file_tracer
                    if file_tracer.has_dynamic_source_filename():
                        disp.has_dynamic_filename = True
                    else:
                        disp.source_filename = canonical_filename(
                            file_tracer.source_filename()
                        )
                    break
            except Exception:
                self.warn(
                    "Disabling plug-in %r due to an exception:" % (plugin._coverage_plugin_name)
                )
                traceback.print_exc()
                plugin._coverage_enabled = False
                continue
        else:
            # No plugin wanted it: it's Python.
            disp.trace = True
            disp.source_filename = canonical

        if not disp.has_dynamic_filename:
            if not disp.source_filename:
                raise CoverageException(
                    "Plugin %r didn't set source_filename for %r" %
                    (plugin, disp.original_filename)
                )
            reason = self.check_include_omit_etc(disp.source_filename, frame)
            if reason:
                nope(disp, reason)

        return disp

    def check_include_omit_etc(self, filename, frame):
        """Check a file name against the include, omit, etc, rules.

        Returns a string or None.  String means, don't trace, and is the reason
        why.  None means no reason found to not trace.

        """
        modulename = name_for_module(filename, frame)

        # If the user specified source or include, then that's authoritative
        # about the outer bound of what to measure and we don't have to apply
        # any canned exclusions. If they didn't, then we have to exclude the
        # stdlib and coverage.py directories.
        if self.source_match or self.source_pkgs_match:
            extra = ""
            ok = False
            if self.source_pkgs_match:
                if self.source_pkgs_match.match(modulename):
                    ok = True
                    if modulename in self.source_pkgs_unmatched:
                        self.source_pkgs_unmatched.remove(modulename)
                else:
                    extra = "module {!r} ".format(modulename)
            if not ok and self.source_match:
                if self.source_match.match(filename):
                    ok = True
            if not ok:
                return extra + "falls outside the --source spec"
        elif self.include_match:
            if not self.include_match.match(filename):
                return "falls outside the --include trees"
        else:
            # If we aren't supposed to trace installed code, then check if this
            # is near the Python standard library and skip it if so.
            if self.pylib_match and self.pylib_match.match(filename):
                return "is in the stdlib"

            # We exclude the coverage.py code itself, since a little of it
            # will be measured otherwise.
            if self.cover_match and self.cover_match.match(filename):
                return "is part of coverage.py"

        # Check the file against the omit pattern.
        if self.omit_match and self.omit_match.match(filename):
            return "is inside an --omit pattern"

        # No point tracing a file we can't later write to SQLite.
        try:
            filename.encode("utf8")
        except UnicodeEncodeError:
            return "non-encodable filename"

        # No reason found to skip this file.
        return None

    def warn_conflicting_settings(self):
        """Warn if there are settings that conflict."""
        if self.include:
            if self.source or self.source_pkgs:
                self.warn("--include is ignored because --source is set", slug="include-ignored")

    def warn_already_imported_files(self):
        """Warn if files have already been imported that we will be measuring."""
        if self.include or self.source or self.source_pkgs:
            warned = set()
            for mod in list(sys.modules.values()):
                filename = getattr(mod, "__file__", None)
                if filename is None:
                    continue
                if filename in warned:
                    continue

                disp = self.should_trace(filename)
                if disp.trace:
                    msg = "Already imported a file that will be measured: {}".format(filename)
                    self.warn(msg, slug="already-imported")
                    warned.add(filename)

    def warn_unimported_source(self):
        """Warn about source packages that were of interest, but never traced."""
        for pkg in self.source_pkgs_unmatched:
            self._warn_about_unmeasured_code(pkg)

    def _warn_about_unmeasured_code(self, pkg):
        """Warn about a package or module that we never traced.

        `pkg` is a string, the name of the package or module.

        """
        mod = sys.modules.get(pkg)
        if mod is None:
            self.warn("Module %s was never imported." % pkg, slug="module-not-imported")
            return

        if module_is_namespace(mod):
            # A namespace package. It's OK for this not to have been traced,
            # since there is no code directly in it.
            return

        if not module_has_file(mod):
            self.warn("Module %s has no Python source." % pkg, slug="module-not-python")
            return

        # The module was in sys.modules, and seems like a module with code, but
        # we never measured it. I guess that means it was imported before
        # coverage even started.
        self.warn(
            "Module %s was previously imported, but not measured" % pkg,
            slug="module-not-measured",
        )

    def find_possibly_unexecuted_files(self):
        """Find files in the areas of interest that might be untraced.

        Yields pairs: file path, and responsible plug-in name.
        """
        for pkg in self.source_pkgs:
            if (not pkg in sys.modules or
                not module_has_file(sys.modules[pkg])):
                continue
            pkg_file = source_for_file(sys.modules[pkg].__file__)
            for ret in self._find_executable_files(canonical_path(pkg_file)):
                yield ret

        for src in self.source:
            for ret in self._find_executable_files(src):
                yield ret

    def _find_plugin_files(self, src_dir):
        """Get executable files from the plugins."""
        for plugin in self.plugins.file_tracers:
            for x_file in plugin.find_executable_files(src_dir):
                yield x_file, plugin._coverage_plugin_name

    def _find_executable_files(self, src_dir):
        """Find executable files in `src_dir`.

        Search for files in `src_dir` that can be executed because they
        are probably importable. Don't include ones that have been omitted
        by the configuration.

        Yield the file path, and the plugin name that handles the file.

        """
        py_files = ((py_file, None) for py_file in find_python_files(src_dir))
        plugin_files = self._find_plugin_files(src_dir)

        for file_path, plugin_name in itertools.chain(py_files, plugin_files):
            file_path = canonical_filename(file_path)
            if self.omit_match and self.omit_match.match(file_path):
                # Turns out this file was omitted, so don't pull it back
                # in as unexecuted.
                continue
            yield file_path, plugin_name

    def sys_info(self):
        """Our information for Coverage.sys_info.

        Returns a list of (key, value) pairs.
        """
        info = [
            ('cover_paths', self.cover_paths),
            ('pylib_paths', self.pylib_paths),
        ]

        matcher_names = [
            'source_match', 'source_pkgs_match',
            'include_match', 'omit_match',
            'cover_match', 'pylib_match',
            ]

        for matcher_name in matcher_names:
            matcher = getattr(self, matcher_name)
            if matcher:
                matcher_info = matcher.info()
            else:
                matcher_info = '-none-'
            info.append((matcher_name, matcher_info))

        return info
