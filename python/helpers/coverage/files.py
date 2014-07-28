"""File wrangling."""

from coverage.backward import to_string
from coverage.misc import CoverageException
import fnmatch, os, os.path, re, sys
import ntpath, posixpath

class FileLocator(object):
    """Understand how filenames work."""

    def __init__(self):
        # The absolute path to our current directory.
        self.relative_dir = os.path.normcase(abs_file(os.curdir) + os.sep)

        # Cache of results of calling the canonical_filename() method, to
        # avoid duplicating work.
        self.canonical_filename_cache = {}

    def relative_filename(self, filename):
        """Return the relative form of `filename`.

        The filename will be relative to the current directory when the
        `FileLocator` was constructed.

        """
        fnorm = os.path.normcase(filename)
        if fnorm.startswith(self.relative_dir):
            filename = filename[len(self.relative_dir):]
        return filename

    def canonical_filename(self, filename):
        """Return a canonical filename for `filename`.

        An absolute path with no redundant components and normalized case.

        """
        if filename not in self.canonical_filename_cache:
            if not os.path.isabs(filename):
                for path in [os.curdir] + sys.path:
                    if path is None:
                        continue
                    f = os.path.join(path, filename)
                    if os.path.exists(f):
                        filename = f
                        break
            cf = abs_file(filename)
            self.canonical_filename_cache[filename] = cf
        return self.canonical_filename_cache[filename]

    def get_zip_data(self, filename):
        """Get data from `filename` if it is a zip file path.

        Returns the string data read from the zip file, or None if no zip file
        could be found or `filename` isn't in it.  The data returned will be
        an empty string if the file is empty.

        """
        import zipimport
        markers = ['.zip'+os.sep, '.egg'+os.sep]
        for marker in markers:
            if marker in filename:
                parts = filename.split(marker)
                try:
                    zi = zipimport.zipimporter(parts[0]+marker[:-1])
                except zipimport.ZipImportError:
                    continue
                try:
                    data = zi.get_data(parts[1])
                except IOError:
                    continue
                return to_string(data)
        return None


if sys.platform == 'win32':

    def actual_path(path):
        """Get the actual path of `path`, including the correct case."""
        if path in actual_path.cache:
            return actual_path.cache[path]

        head, tail = os.path.split(path)
        if not tail:
            actpath = head
        elif not head:
            actpath = tail
        else:
            head = actual_path(head)
            if head in actual_path.list_cache:
                files = actual_path.list_cache[head]
            else:
                try:
                    files = os.listdir(head)
                except OSError:
                    files = []
                actual_path.list_cache[head] = files
            normtail = os.path.normcase(tail)
            for f in files:
                if os.path.normcase(f) == normtail:
                    tail = f
                    break
            actpath = os.path.join(head, tail)
        actual_path.cache[path] = actpath
        return actpath

    actual_path.cache = {}
    actual_path.list_cache = {}

else:
    def actual_path(filename):
        """The actual path for non-Windows platforms."""
        return filename


def abs_file(filename):
    """Return the absolute normalized form of `filename`."""
    path = os.path.expandvars(os.path.expanduser(filename))
    path = os.path.abspath(os.path.realpath(path))
    path = actual_path(path)
    return path


def isabs_anywhere(filename):
    """Is `filename` an absolute path on any OS?"""
    return ntpath.isabs(filename) or posixpath.isabs(filename)


def prep_patterns(patterns):
    """Prepare the file patterns for use in a `FnmatchMatcher`.

    If a pattern starts with a wildcard, it is used as a pattern
    as-is.  If it does not start with a wildcard, then it is made
    absolute with the current directory.

    If `patterns` is None, an empty list is returned.

    """
    prepped = []
    for p in patterns or []:
        if p.startswith("*") or p.startswith("?"):
            prepped.append(p)
        else:
            prepped.append(abs_file(p))
    return prepped


class TreeMatcher(object):
    """A matcher for files in a tree."""
    def __init__(self, directories):
        self.dirs = directories[:]

    def __repr__(self):
        return "<TreeMatcher %r>" % self.dirs

    def info(self):
        """A list of strings for displaying when dumping state."""
        return self.dirs

    def add(self, directory):
        """Add another directory to the list we match for."""
        self.dirs.append(directory)

    def match(self, fpath):
        """Does `fpath` indicate a file in one of our trees?"""
        for d in self.dirs:
            if fpath.startswith(d):
                if fpath == d:
                    # This is the same file!
                    return True
                if fpath[len(d)] == os.sep:
                    # This is a file in the directory
                    return True
        return False


class FnmatchMatcher(object):
    """A matcher for files by filename pattern."""
    def __init__(self, pats):
        self.pats = pats[:]

    def __repr__(self):
        return "<FnmatchMatcher %r>" % self.pats

    def info(self):
        """A list of strings for displaying when dumping state."""
        return self.pats

    def match(self, fpath):
        """Does `fpath` match one of our filename patterns?"""
        for pat in self.pats:
            if fnmatch.fnmatch(fpath, pat):
                return True
        return False


def sep(s):
    """Find the path separator used in this string, or os.sep if none."""
    sep_match = re.search(r"[\\/]", s)
    if sep_match:
        the_sep = sep_match.group(0)
    else:
        the_sep = os.sep
    return the_sep


class PathAliases(object):
    """A collection of aliases for paths.

    When combining data files from remote machines, often the paths to source
    code are different, for example, due to OS differences, or because of
    serialized checkouts on continuous integration machines.

    A `PathAliases` object tracks a list of pattern/result pairs, and can
    map a path through those aliases to produce a unified path.

    `locator` is a FileLocator that is used to canonicalize the results.

    """
    def __init__(self, locator=None):
        self.aliases = []
        self.locator = locator

    def add(self, pattern, result):
        """Add the `pattern`/`result` pair to the list of aliases.

        `pattern` is an `fnmatch`-style pattern.  `result` is a simple
        string.  When mapping paths, if a path starts with a match against
        `pattern`, then that match is replaced with `result`.  This models
        isomorphic source trees being rooted at different places on two
        different machines.

        `pattern` can't end with a wildcard component, since that would
        match an entire tree, and not just its root.

        """
        # The pattern can't end with a wildcard component.
        pattern = pattern.rstrip(r"\/")
        if pattern.endswith("*"):
            raise CoverageException("Pattern must not end with wildcards.")
        pattern_sep = sep(pattern)

        # The pattern is meant to match a filepath.  Let's make it absolute
        # unless it already is, or is meant to match any prefix.
        if not pattern.startswith('*') and not isabs_anywhere(pattern):
            pattern = abs_file(pattern)
        pattern += pattern_sep

        # Make a regex from the pattern.  fnmatch always adds a \Z or $ to
        # match the whole string, which we don't want.
        regex_pat = fnmatch.translate(pattern).replace(r'\Z(', '(')
        if regex_pat.endswith("$"):
            regex_pat = regex_pat[:-1]
        # We want */a/b.py to match on Windows too, so change slash to match
        # either separator.
        regex_pat = regex_pat.replace(r"\/", r"[\\/]")
        # We want case-insensitive matching, so add that flag.
        regex = re.compile(r"(?i)" + regex_pat)

        # Normalize the result: it must end with a path separator.
        result_sep = sep(result)
        result = result.rstrip(r"\/") + result_sep
        self.aliases.append((regex, result, pattern_sep, result_sep))

    def map(self, path):
        """Map `path` through the aliases.

        `path` is checked against all of the patterns.  The first pattern to
        match is used to replace the root of the path with the result root.
        Only one pattern is ever used.  If no patterns match, `path` is
        returned unchanged.

        The separator style in the result is made to match that of the result
        in the alias.

        """
        for regex, result, pattern_sep, result_sep in self.aliases:
            m = regex.match(path)
            if m:
                new = path.replace(m.group(0), result)
                if pattern_sep != result_sep:
                    new = new.replace(pattern_sep, result_sep)
                if self.locator:
                    new = self.locator.canonical_filename(new)
                return new
        return path


def find_python_files(dirname):
    """Yield all of the importable Python files in `dirname`, recursively.

    To be importable, the files have to be in a directory with a __init__.py,
    except for `dirname` itself, which isn't required to have one.  The
    assumption is that `dirname` was specified directly, so the user knows
    best, but subdirectories are checked for a __init__.py to be sure we only
    find the importable files.

    """
    for i, (dirpath, dirnames, filenames) in enumerate(os.walk(dirname)):
        if i > 0 and '__init__.py' not in filenames:
            # If a directory doesn't have __init__.py, then it isn't
            # importable and neither are its files
            del dirnames[:]
            continue
        for filename in filenames:
            # We're only interested in files that look like reasonable Python
            # files: Must end with .py or .pyw, and must not have certain funny
            # characters that probably mean they are editor junk.
            if re.match(r"^[^.#~!$@%^&*()+=,]+\.pyw?$", filename):
                yield os.path.join(dirpath, filename)
