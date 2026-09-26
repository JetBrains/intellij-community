# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Miscellaneous stuff for coverage.py."""

import contextlib
import errno
import hashlib
import importlib
import importlib.util
import inspect
import locale
import os
import os.path
import re
import sys
import types

from coverage import env
from coverage.exceptions import CoverageException

# In 6.0, the exceptions moved from misc.py to exceptions.py.  But a number of
# other packages were importing the exceptions from misc, so import them here.
# pylint: disable=unused-wildcard-import
from coverage.exceptions import *   # pylint: disable=wildcard-import

ISOLATED_MODULES = {}


def isolate_module(mod):
    """Copy a module so that we are isolated from aggressive mocking.

    If a test suite mocks os.path.exists (for example), and then we need to use
    it during the test, everything will get tangled up if we use their mock.
    Making a copy of the module when we import it will isolate coverage.py from
    those complications.
    """
    if mod not in ISOLATED_MODULES:
        new_mod = types.ModuleType(mod.__name__)
        ISOLATED_MODULES[mod] = new_mod
        for name in dir(mod):
            value = getattr(mod, name)
            if isinstance(value, types.ModuleType):
                value = isolate_module(value)
            setattr(new_mod, name, value)
    return ISOLATED_MODULES[mod]

os = isolate_module(os)


class SysModuleSaver:
    """Saves the contents of sys.modules, and removes new modules later."""
    def __init__(self):
        self.old_modules = set(sys.modules)

    def restore(self):
        """Remove any modules imported since this object started."""
        new_modules = set(sys.modules) - self.old_modules
        for m in new_modules:
            del sys.modules[m]


@contextlib.contextmanager
def sys_modules_saved():
    """A context manager to remove any modules imported during a block."""
    saver = SysModuleSaver()
    try:
        yield
    finally:
        saver.restore()


def import_third_party(modname):
    """Import a third-party module we need, but might not be installed.

    This also cleans out the module after the import, so that coverage won't
    appear to have imported it.  This lets the third party use coverage for
    their own tests.

    Arguments:
        modname (str): the name of the module to import.

    Returns:
        The imported module, or None if the module couldn't be imported.

    """
    with sys_modules_saved():
        try:
            return importlib.import_module(modname)
        except ImportError:
            return None


def dummy_decorator_with_args(*args_unused, **kwargs_unused):
    """Dummy no-op implementation of a decorator with arguments."""
    def _decorator(func):
        return func
    return _decorator


# Use PyContracts for assertion testing on parameters and returns, but only if
# we are running our own test suite.
if env.USE_CONTRACTS:
    from contracts import contract              # pylint: disable=unused-import
    from contracts import new_contract as raw_new_contract

    def new_contract(*args, **kwargs):
        """A proxy for contracts.new_contract that doesn't mind happening twice."""
        try:
            raw_new_contract(*args, **kwargs)
        except ValueError:
            # During meta-coverage, this module is imported twice, and
            # PyContracts doesn't like redefining contracts. It's OK.
            pass

    # Define contract words that PyContract doesn't have.
    new_contract('bytes', lambda v: isinstance(v, bytes))
    new_contract('unicode', lambda v: isinstance(v, str))

    def one_of(argnames):
        """Ensure that only one of the argnames is non-None."""
        def _decorator(func):
            argnameset = {name.strip() for name in argnames.split(",")}
            def _wrapper(*args, **kwargs):
                vals = [kwargs.get(name) for name in argnameset]
                assert sum(val is not None for val in vals) == 1
                return func(*args, **kwargs)
            return _wrapper
        return _decorator
else:                                           # pragma: not testing
    # We aren't using real PyContracts, so just define our decorators as
    # stunt-double no-ops.
    contract = dummy_decorator_with_args
    one_of = dummy_decorator_with_args

    def new_contract(*args_unused, **kwargs_unused):
        """Dummy no-op implementation of `new_contract`."""
        pass


def nice_pair(pair):
    """Make a nice string representation of a pair of numbers.

    If the numbers are equal, just return the number, otherwise return the pair
    with a dash between them, indicating the range.

    """
    start, end = pair
    if start == end:
        return "%d" % start
    else:
        return "%d-%d" % (start, end)


def expensive(fn):
    """A decorator to indicate that a method shouldn't be called more than once.

    Normally, this does nothing.  During testing, this raises an exception if
    called more than once.

    """
    if env.TESTING:
        attr = "_once_" + fn.__name__

        def _wrapper(self):
            if hasattr(self, attr):
                raise AssertionError(f"Shouldn't have called {fn.__name__} more than once")
            setattr(self, attr, True)
            return fn(self)
        return _wrapper
    else:
        return fn                   # pragma: not testing


def bool_or_none(b):
    """Return bool(b), but preserve None."""
    if b is None:
        return None
    else:
        return bool(b)


def join_regex(regexes):
    """Combine a list of regexes into one that matches any of them."""
    return "|".join(f"(?:{r})" for r in regexes)


def file_be_gone(path):
    """Remove a file, and don't get annoyed if it doesn't exist."""
    try:
        os.remove(path)
    except OSError as e:
        if e.errno != errno.ENOENT:
            raise


def ensure_dir(directory):
    """Make sure the directory exists.

    If `directory` is None or empty, do nothing.
    """
    if directory:
        os.makedirs(directory, exist_ok=True)


def ensure_dir_for_file(path):
    """Make sure the directory for the path exists."""
    ensure_dir(os.path.dirname(path))


def output_encoding(outfile=None):
    """Determine the encoding to use for output written to `outfile` or stdout."""
    if outfile is None:
        outfile = sys.stdout
    encoding = (
        getattr(outfile, "encoding", None) or
        getattr(sys.__stdout__, "encoding", None) or
        locale.getpreferredencoding()
    )
    return encoding


class Hasher:
    """Hashes Python data for fingerprinting."""
    def __init__(self):
        self.hash = hashlib.new("sha3_256")

    def update(self, v):
        """Add `v` to the hash, recursively if needed."""
        self.hash.update(str(type(v)).encode("utf-8"))
        if isinstance(v, str):
            self.hash.update(v.encode("utf-8"))
        elif isinstance(v, bytes):
            self.hash.update(v)
        elif v is None:
            pass
        elif isinstance(v, (int, float)):
            self.hash.update(str(v).encode("utf-8"))
        elif isinstance(v, (tuple, list)):
            for e in v:
                self.update(e)
        elif isinstance(v, dict):
            keys = v.keys()
            for k in sorted(keys):
                self.update(k)
                self.update(v[k])
        else:
            for k in dir(v):
                if k.startswith('__'):
                    continue
                a = getattr(v, k)
                if inspect.isroutine(a):
                    continue
                self.update(k)
                self.update(a)
        self.hash.update(b'.')

    def hexdigest(self):
        """Retrieve the hex digest of the hash."""
        return self.hash.hexdigest()[:32]


def _needs_to_implement(that, func_name):
    """Helper to raise NotImplementedError in interface stubs."""
    if hasattr(that, "_coverage_plugin_name"):
        thing = "Plugin"
        name = that._coverage_plugin_name
    else:
        thing = "Class"
        klass = that.__class__
        name = f"{klass.__module__}.{klass.__name__}"

    raise NotImplementedError(
        f"{thing} {name!r} needs to implement {func_name}()"
    )


class DefaultValue:
    """A sentinel object to use for unusual default-value needs.

    Construct with a string that will be used as the repr, for display in help
    and Sphinx output.

    """
    def __init__(self, display_as):
        self.display_as = display_as

    def __repr__(self):
        return self.display_as


def substitute_variables(text, variables):
    """Substitute ``${VAR}`` variables in `text` with their values.

    Variables in the text can take a number of shell-inspired forms::

        $VAR
        ${VAR}
        ${VAR?}             strict: an error if VAR isn't defined.
        ${VAR-missing}      defaulted: "missing" if VAR isn't defined.
        $$                  just a dollar sign.

    `variables` is a dictionary of variable values.

    Returns the resulting text with values substituted.

    """
    dollar_pattern = r"""(?x)   # Use extended regex syntax
        \$                      # A dollar sign,
        (?:                     # then
            (?P<dollar>\$) |        # a dollar sign, or
            (?P<word1>\w+) |        # a plain word, or
            {                       # a {-wrapped
                (?P<word2>\w+)          # word,
                (?:
                    (?P<strict>\?) |        # with a strict marker
                    -(?P<defval>[^}]*)      # or a default value
                )?                      # maybe.
            }
        )
        """

    dollar_groups = ('dollar', 'word1', 'word2')

    def dollar_replace(match):
        """Called for each $replacement."""
        # Only one of the dollar_groups will have matched, just get its text.
        word = next(g for g in match.group(*dollar_groups) if g)    # pragma: always breaks
        if word == "$":
            return "$"
        elif word in variables:
            return variables[word]
        elif match['strict']:
            msg = f"Variable {word} is undefined: {text!r}"
            raise CoverageException(msg)
        else:
            return match['defval']

    text = re.sub(dollar_pattern, dollar_replace, text)
    return text


def format_local_datetime(dt):
    """Return a string with local timezone representing the date.
    """
    return dt.astimezone().strftime('%Y-%m-%d %H:%M %z')


def import_local_file(modname, modfile=None):
    """Import a local file as a module.

    Opens a file in the current directory named `modname`.py, imports it
    as `modname`, and returns the module object.  `modfile` is the file to
    import if it isn't in the current directory.

    """
    if modfile is None:
        modfile = modname + '.py'
    spec = importlib.util.spec_from_file_location(modname, modfile)
    mod = importlib.util.module_from_spec(spec)
    sys.modules[modname] = mod
    spec.loader.exec_module(mod)

    return mod


def human_key(s):
    """Turn a string into a list of string and number chunks.
        "z23a" -> ["z", 23, "a"]
    """
    def tryint(s):
        """If `s` is a number, return an int, else `s` unchanged."""
        try:
            return int(s)
        except ValueError:
            return s

    return [tryint(c) for c in re.split(r"(\d+)", s)]

def human_sorted(strings):
    """Sort the given iterable of strings the way that humans expect.

    Numeric components in the strings are sorted as numbers.

    Returns the sorted list.

    """
    return sorted(strings, key=human_key)

def human_sorted_items(items, reverse=False):
    """Sort the (string, value) items the way humans expect.

    Returns the sorted list of items.
    """
    return sorted(items, key=lambda pair: (human_key(pair[0]), pair[1]), reverse=reverse)


def plural(n, thing="", things=""):
    """Pluralize a word.

    If n is 1, return thing.  Otherwise return things, or thing+s.
    """
    if n == 1:
        return thing
    else:
        return things or (thing + "s")
