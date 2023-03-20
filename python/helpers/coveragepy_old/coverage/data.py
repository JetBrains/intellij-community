# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Coverage data for coverage.py.

This file had the 4.x JSON data support, which is now gone.  This file still
has storage-agnostic helpers, and is kept to avoid changing too many imports.
CoverageData is now defined in sqldata.py, and imported here to keep the
imports working.

"""

import glob
import os.path

from coverage.misc import CoverageException, file_be_gone
from coverage.sqldata import CoverageData


def line_counts(data, fullpath=False):
    """Return a dict summarizing the line coverage data.

    Keys are based on the file names, and values are the number of executed
    lines.  If `fullpath` is true, then the keys are the full pathnames of
    the files, otherwise they are the basenames of the files.

    Returns a dict mapping file names to counts of lines.

    """
    summ = {}
    if fullpath:
        filename_fn = lambda f: f
    else:
        filename_fn = os.path.basename
    for filename in data.measured_files():
        summ[filename_fn(filename)] = len(data.lines(filename))
    return summ


def add_data_to_hash(data, filename, hasher):
    """Contribute `filename`'s data to the `hasher`.

    `hasher` is a `coverage.misc.Hasher` instance to be updated with
    the file's data.  It should only get the results data, not the run
    data.

    """
    if data.has_arcs():
        hasher.update(sorted(data.arcs(filename) or []))
    else:
        hasher.update(sorted(data.lines(filename) or []))
    hasher.update(data.file_tracer(filename))


def combine_parallel_data(data, aliases=None, data_paths=None, strict=False, keep=False):
    """Combine a number of data files together.

    Treat `data.filename` as a file prefix, and combine the data from all
    of the data files starting with that prefix plus a dot.

    If `aliases` is provided, it's a `PathAliases` object that is used to
    re-map paths to match the local machine's.

    If `data_paths` is provided, it is a list of directories or files to
    combine.  Directories are searched for files that start with
    `data.filename` plus dot as a prefix, and those files are combined.

    If `data_paths` is not provided, then the directory portion of
    `data.filename` is used as the directory to search for data files.

    Unless `keep` is True every data file found and combined is then deleted from disk. If a file
    cannot be read, a warning will be issued, and the file will not be
    deleted.

    If `strict` is true, and no files are found to combine, an error is
    raised.

    """
    # Because of the os.path.abspath in the constructor, data_dir will
    # never be an empty string.
    data_dir, local = os.path.split(data.base_filename())
    localdot = local + '.*'

    data_paths = data_paths or [data_dir]
    files_to_combine = []
    for p in data_paths:
        if os.path.isfile(p):
            files_to_combine.append(os.path.abspath(p))
        elif os.path.isdir(p):
            pattern = os.path.join(os.path.abspath(p), localdot)
            files_to_combine.extend(glob.glob(pattern))
        else:
            raise CoverageException("Couldn't combine from non-existent path '%s'" % (p,))

    if strict and not files_to_combine:
        raise CoverageException("No data to combine")

    files_combined = 0
    for f in files_to_combine:
        if f == data.data_filename():
            # Sometimes we are combining into a file which is one of the
            # parallel files.  Skip that file.
            if data._debug.should('dataio'):
                data._debug.write("Skipping combining ourself: %r" % (f,))
            continue
        if data._debug.should('dataio'):
            data._debug.write("Combining data file %r" % (f,))
        try:
            new_data = CoverageData(f, debug=data._debug)
            new_data.read()
        except CoverageException as exc:
            if data._warn:
                # The CoverageException has the file name in it, so just
                # use the message as the warning.
                data._warn(str(exc))
        else:
            data.update(new_data, aliases=aliases)
            files_combined += 1
            if not keep:
                if data._debug.should('dataio'):
                    data._debug.write("Deleting combined data file %r" % (f,))
                file_be_gone(f)

    if strict and not files_combined:
        raise CoverageException("No usable data files")
