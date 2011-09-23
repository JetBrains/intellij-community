"""File wrangling."""

from coverage.backward import to_string
import fnmatch, os, sys

class FileLocator(object):
    """Understand how filenames work."""

    def __init__(self):
        # The absolute path to our current directory.
        self.relative_dir = self.abs_file(os.curdir) + os.sep

        # Cache of results of calling the canonical_filename() method, to
        # avoid duplicating work.
        self.canonical_filename_cache = {}

    def abs_file(self, filename):
        """Return the absolute normalized form of `filename`."""
        return os.path.normcase(os.path.abspath(os.path.realpath(filename)))

    def relative_filename(self, filename):
        """Return the relative form of `filename`.

        The filename will be relative to the current directory when the
        `FileLocator` was constructed.

        """
        if filename.startswith(self.relative_dir):
            filename = filename.replace(self.relative_dir, "")
        return filename

    def canonical_filename(self, filename):
        """Return a canonical filename for `filename`.

        An absolute path with no redundant components and normalized case.

        """
        if filename not in self.canonical_filename_cache:
            f = filename
            if os.path.isabs(f) and not os.path.exists(f):
                if self.get_zip_data(f) is None:
                    f = os.path.basename(f)
            if not os.path.isabs(f):
                for path in [os.curdir] + sys.path:
                    if path is None:
                        continue
                    g = os.path.join(path, f)
                    if os.path.exists(g):
                        f = g
                        break
            cf = self.abs_file(f)
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


class TreeMatcher(object):
    """A matcher for files in a tree."""
    def __init__(self, directories):
        self.dirs = directories[:]

    def __repr__(self):
        return "<TreeMatcher %r>" % self.dirs

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

    def match(self, fpath):
        """Does `fpath` match one of our filename patterns?"""
        for pat in self.pats:
            if fnmatch.fnmatch(fpath, pat):
                return True
        return False


def find_python_files(dirname):
    """Yield all of the importable Python files in `dirname`, recursively."""
    for dirpath, dirnames, filenames in os.walk(dirname, topdown=True):
        if '__init__.py' not in filenames:
            # If a directory doesn't have __init__.py, then it isn't
            # importable and neither are its files
            del dirnames[:]
            continue
        for filename in filenames:
            if fnmatch.fnmatch(filename, "*.py"):
                yield os.path.join(dirpath, filename)
