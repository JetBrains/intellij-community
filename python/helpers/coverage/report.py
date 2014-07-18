"""Reporter foundation for Coverage."""

import fnmatch, os
from coverage.codeunit import code_unit_factory
from coverage.files import prep_patterns
from coverage.misc import CoverageException, NoSource, NotPython

class Reporter(object):
    """A base class for all reporters."""

    def __init__(self, coverage, config):
        """Create a reporter.

        `coverage` is the coverage instance. `config` is an instance  of
        CoverageConfig, for controlling all sorts of behavior.

        """
        self.coverage = coverage
        self.config = config

        # The code units to report on.  Set by find_code_units.
        self.code_units = []

        # The directory into which to place the report, used by some derived
        # classes.
        self.directory = None

    def find_code_units(self, morfs):
        """Find the code units we'll report on.

        `morfs` is a list of modules or filenames.

        """
        morfs = morfs or self.coverage.data.measured_files()
        file_locator = self.coverage.file_locator
        self.code_units = code_unit_factory(morfs, file_locator)

        if self.config.include:
            patterns = prep_patterns(self.config.include)
            filtered = []
            for cu in self.code_units:
                for pattern in patterns:
                    if fnmatch.fnmatch(cu.filename, pattern):
                        filtered.append(cu)
                        break
            self.code_units = filtered

        if self.config.omit:
            patterns = prep_patterns(self.config.omit)
            filtered = []
            for cu in self.code_units:
                for pattern in patterns:
                    if fnmatch.fnmatch(cu.filename, pattern):
                        break
                else:
                    filtered.append(cu)
            self.code_units = filtered

        self.code_units.sort()

    def report_files(self, report_fn, morfs, directory=None):
        """Run a reporting function on a number of morfs.

        `report_fn` is called for each relative morf in `morfs`.  It is called
        as::

            report_fn(code_unit, analysis)

        where `code_unit` is the `CodeUnit` for the morf, and `analysis` is
        the `Analysis` for the morf.

        """
        self.find_code_units(morfs)

        if not self.code_units:
            raise CoverageException("No data to report.")

        self.directory = directory
        if self.directory and not os.path.exists(self.directory):
            os.makedirs(self.directory)

        for cu in self.code_units:
            try:
                report_fn(cu, self.coverage._analyze(cu))
            except NoSource:
                if not self.config.ignore_errors:
                    raise
            except NotPython:
                # Only report errors for .py files, and only if we didn't
                # explicitly suppress those errors.
                if cu.should_be_python() and not self.config.ignore_errors:
                    raise
