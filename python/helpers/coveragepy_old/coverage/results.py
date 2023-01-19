# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Results of coverage measurement."""

import collections

from coverage.backward import iitems
from coverage.debug import SimpleReprMixin
from coverage.misc import contract, CoverageException, nice_pair


class Analysis(object):
    """The results of analyzing a FileReporter."""

    def __init__(self, data, file_reporter, file_mapper):
        self.data = data
        self.file_reporter = file_reporter
        self.filename = file_mapper(self.file_reporter.filename)
        self.statements = self.file_reporter.lines()
        self.excluded = self.file_reporter.excluded_lines()

        # Identify missing statements.
        executed = self.data.lines(self.filename) or []
        executed = self.file_reporter.translate_lines(executed)
        self.executed = executed
        self.missing = self.statements - self.executed

        if self.data.has_arcs():
            self._arc_possibilities = sorted(self.file_reporter.arcs())
            self.exit_counts = self.file_reporter.exit_counts()
            self.no_branch = self.file_reporter.no_branch_lines()
            n_branches = self._total_branches()
            mba = self.missing_branch_arcs()
            n_partial_branches = sum(len(v) for k,v in iitems(mba) if k not in self.missing)
            n_missing_branches = sum(len(v) for k,v in iitems(mba))
        else:
            self._arc_possibilities = []
            self.exit_counts = {}
            self.no_branch = set()
            n_branches = n_partial_branches = n_missing_branches = 0

        self.numbers = Numbers(
            n_files=1,
            n_statements=len(self.statements),
            n_excluded=len(self.excluded),
            n_missing=len(self.missing),
            n_branches=n_branches,
            n_partial_branches=n_partial_branches,
            n_missing_branches=n_missing_branches,
        )

    def missing_formatted(self, branches=False):
        """The missing line numbers, formatted nicely.

        Returns a string like "1-2, 5-11, 13-14".

        If `branches` is true, includes the missing branch arcs also.

        """
        if branches and self.has_arcs():
            arcs = iitems(self.missing_branch_arcs())
        else:
            arcs = None

        return format_lines(self.statements, self.missing, arcs=arcs)

    def has_arcs(self):
        """Were arcs measured in this result?"""
        return self.data.has_arcs()

    @contract(returns='list(tuple(int, int))')
    def arc_possibilities(self):
        """Returns a sorted list of the arcs in the code."""
        return self._arc_possibilities

    @contract(returns='list(tuple(int, int))')
    def arcs_executed(self):
        """Returns a sorted list of the arcs actually executed in the code."""
        executed = self.data.arcs(self.filename) or []
        executed = self.file_reporter.translate_arcs(executed)
        return sorted(executed)

    @contract(returns='list(tuple(int, int))')
    def arcs_missing(self):
        """Returns a sorted list of the arcs in the code not executed."""
        possible = self.arc_possibilities()
        executed = self.arcs_executed()
        missing = (
            p for p in possible
                if p not in executed
                    and p[0] not in self.no_branch
        )
        return sorted(missing)

    @contract(returns='list(tuple(int, int))')
    def arcs_unpredicted(self):
        """Returns a sorted list of the executed arcs missing from the code."""
        possible = self.arc_possibilities()
        executed = self.arcs_executed()
        # Exclude arcs here which connect a line to itself.  They can occur
        # in executed data in some cases.  This is where they can cause
        # trouble, and here is where it's the least burden to remove them.
        # Also, generators can somehow cause arcs from "enter" to "exit", so
        # make sure we have at least one positive value.
        unpredicted = (
            e for e in executed
                if e not in possible
                    and e[0] != e[1]
                    and (e[0] > 0 or e[1] > 0)
        )
        return sorted(unpredicted)

    def _branch_lines(self):
        """Returns a list of line numbers that have more than one exit."""
        return [l1 for l1,count in iitems(self.exit_counts) if count > 1]

    def _total_branches(self):
        """How many total branches are there?"""
        return sum(count for count in self.exit_counts.values() if count > 1)

    @contract(returns='dict(int: list(int))')
    def missing_branch_arcs(self):
        """Return arcs that weren't executed from branch lines.

        Returns {l1:[l2a,l2b,...], ...}

        """
        missing = self.arcs_missing()
        branch_lines = set(self._branch_lines())
        mba = collections.defaultdict(list)
        for l1, l2 in missing:
            if l1 in branch_lines:
                mba[l1].append(l2)
        return mba

    @contract(returns='dict(int: tuple(int, int))')
    def branch_stats(self):
        """Get stats about branches.

        Returns a dict mapping line numbers to a tuple:
        (total_exits, taken_exits).
        """

        missing_arcs = self.missing_branch_arcs()
        stats = {}
        for lnum in self._branch_lines():
            exits = self.exit_counts[lnum]
            missing = len(missing_arcs[lnum])
            stats[lnum] = (exits, exits - missing)
        return stats


class Numbers(SimpleReprMixin):
    """The numerical results of measuring coverage.

    This holds the basic statistics from `Analysis`, and is used to roll
    up statistics across files.

    """
    # A global to determine the precision on coverage percentages, the number
    # of decimal places.
    _precision = 0
    _near0 = 1.0              # These will change when _precision is changed.
    _near100 = 99.0

    def __init__(self, n_files=0, n_statements=0, n_excluded=0, n_missing=0,
                    n_branches=0, n_partial_branches=0, n_missing_branches=0
                    ):
        self.n_files = n_files
        self.n_statements = n_statements
        self.n_excluded = n_excluded
        self.n_missing = n_missing
        self.n_branches = n_branches
        self.n_partial_branches = n_partial_branches
        self.n_missing_branches = n_missing_branches

    def init_args(self):
        """Return a list for __init__(*args) to recreate this object."""
        return [
            self.n_files, self.n_statements, self.n_excluded, self.n_missing,
            self.n_branches, self.n_partial_branches, self.n_missing_branches,
        ]

    @classmethod
    def set_precision(cls, precision):
        """Set the number of decimal places used to report percentages."""
        assert 0 <= precision < 10
        cls._precision = precision
        cls._near0 = 1.0 / 10**precision
        cls._near100 = 100.0 - cls._near0

    @property
    def n_executed(self):
        """Returns the number of executed statements."""
        return self.n_statements - self.n_missing

    @property
    def n_executed_branches(self):
        """Returns the number of executed branches."""
        return self.n_branches - self.n_missing_branches

    @property
    def pc_covered(self):
        """Returns a single percentage value for coverage."""
        if self.n_statements > 0:
            numerator, denominator = self.ratio_covered
            pc_cov = (100.0 * numerator) / denominator
        else:
            pc_cov = 100.0
        return pc_cov

    @property
    def pc_covered_str(self):
        """Returns the percent covered, as a string, without a percent sign.

        Note that "0" is only returned when the value is truly zero, and "100"
        is only returned when the value is truly 100.  Rounding can never
        result in either "0" or "100".

        """
        pc = self.pc_covered
        if 0 < pc < self._near0:
            pc = self._near0
        elif self._near100 < pc < 100:
            pc = self._near100
        else:
            pc = round(pc, self._precision)
        return "%.*f" % (self._precision, pc)

    @classmethod
    def pc_str_width(cls):
        """How many characters wide can pc_covered_str be?"""
        width = 3   # "100"
        if cls._precision > 0:
            width += 1 + cls._precision
        return width

    @property
    def ratio_covered(self):
        """Return a numerator and denominator for the coverage ratio."""
        numerator = self.n_executed + self.n_executed_branches
        denominator = self.n_statements + self.n_branches
        return numerator, denominator

    def __add__(self, other):
        nums = Numbers()
        nums.n_files = self.n_files + other.n_files
        nums.n_statements = self.n_statements + other.n_statements
        nums.n_excluded = self.n_excluded + other.n_excluded
        nums.n_missing = self.n_missing + other.n_missing
        nums.n_branches = self.n_branches + other.n_branches
        nums.n_partial_branches = (
            self.n_partial_branches + other.n_partial_branches
            )
        nums.n_missing_branches = (
            self.n_missing_branches + other.n_missing_branches
            )
        return nums

    def __radd__(self, other):
        # Implementing 0+Numbers allows us to sum() a list of Numbers.
        if other == 0:
            return self
        return NotImplemented       # pragma: not covered (we never call it this way)


def _line_ranges(statements, lines):
    """Produce a list of ranges for `format_lines`."""
    statements = sorted(statements)
    lines = sorted(lines)

    pairs = []
    start = None
    lidx = 0
    for stmt in statements:
        if lidx >= len(lines):
            break
        if stmt == lines[lidx]:
            lidx += 1
            if not start:
                start = stmt
            end = stmt
        elif start:
            pairs.append((start, end))
            start = None
    if start:
        pairs.append((start, end))
    return pairs


def format_lines(statements, lines, arcs=None):
    """Nicely format a list of line numbers.

    Format a list of line numbers for printing by coalescing groups of lines as
    long as the lines represent consecutive statements.  This will coalesce
    even if there are gaps between statements.

    For example, if `statements` is [1,2,3,4,5,10,11,12,13,14] and
    `lines` is [1,2,5,10,11,13,14] then the result will be "1-2, 5-11, 13-14".

    Both `lines` and `statements` can be any iterable. All of the elements of
    `lines` must be in `statements`, and all of the values must be positive
    integers.

    If `arcs` is provided, they are (start,[end,end,end]) pairs that will be
    included in the output as long as start isn't in `lines`.

    """
    line_items = [(pair[0], nice_pair(pair)) for pair in _line_ranges(statements, lines)]
    if arcs:
        line_exits = sorted(arcs)
        for line, exits in line_exits:
            for ex in sorted(exits):
                if line not in lines and ex not in lines:
                    dest = (ex if ex > 0 else "exit")
                    line_items.append((line, "%d->%s" % (line, dest)))

    ret = ', '.join(t[-1] for t in sorted(line_items))
    return ret


@contract(total='number', fail_under='number', precision=int, returns=bool)
def should_fail_under(total, fail_under, precision):
    """Determine if a total should fail due to fail-under.

    `total` is a float, the coverage measurement total. `fail_under` is the
    fail_under setting to compare with. `precision` is the number of digits
    to consider after the decimal point.

    Returns True if the total should fail.

    """
    # We can never achieve higher than 100% coverage, or less than zero.
    if not (0 <= fail_under <= 100.0):
        msg = "fail_under={} is invalid. Must be between 0 and 100.".format(fail_under)
        raise CoverageException(msg)

    # Special case for fail_under=100, it must really be 100.
    if fail_under == 100.0 and total != 100.0:
        return True

    return round(total, precision) < fail_under
