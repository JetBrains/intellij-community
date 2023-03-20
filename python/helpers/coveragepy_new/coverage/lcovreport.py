# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""LCOV reporting for coverage.py."""

import sys
import base64
from hashlib import md5

from coverage.report import get_analysis_to_report


class LcovReporter:
    """A reporter for writing LCOV coverage reports."""

    report_type = "LCOV report"

    def __init__(self, coverage):
        self.coverage = coverage
        self.config = self.coverage.config

    def report(self, morfs, outfile=None):
        """Renders the full lcov report.

        'morfs' is a list of modules or filenames

        outfile is the file object to write the file into.
        """

        self.coverage.get_data()
        outfile = outfile or sys.stdout

        for fr, analysis in get_analysis_to_report(self.coverage, morfs):
            self.get_lcov(fr, analysis, outfile)

    def get_lcov(self, fr, analysis, outfile=None):
        """Produces the lcov data for a single file.

        This currently supports both line and branch coverage,
        however function coverage is not supported.
        """
        outfile.write("TN:\n")
        outfile.write(f"SF:{fr.relative_filename()}\n")
        source_lines = fr.source().splitlines()

        for covered in sorted(analysis.executed):
            # Note: Coverage.py currently only supports checking *if* a line
            # has been executed, not how many times, so we set this to 1 for
            # nice output even if it's technically incorrect.

            # The lines below calculate a 64-bit encoded md5 hash of the line
            # corresponding to the DA lines in the lcov file, for either case
            # of the line being covered or missed in coverage.py. The final two
            # characters of the encoding ("==") are removed from the hash to
            # allow genhtml to run on the resulting lcov file.
            if source_lines:
                line = source_lines[covered-1].encode("utf-8")
            else:
                line = b""
            hashed = base64.b64encode(md5(line).digest()).decode().rstrip("=")
            outfile.write(f"DA:{covered},1,{hashed}\n")

        for missed in sorted(analysis.missing):
            assert source_lines
            line = source_lines[missed-1].encode("utf-8")
            hashed = base64.b64encode(md5(line).digest()).decode().rstrip("=")
            outfile.write(f"DA:{missed},0,{hashed}\n")

        outfile.write(f"LF:{len(analysis.statements)}\n")
        outfile.write(f"LH:{len(analysis.executed)}\n")

        # More information dense branch coverage data.
        missing_arcs = analysis.missing_branch_arcs()
        executed_arcs = analysis.executed_branch_arcs()
        for block_number, block_line_number in enumerate(
            sorted(analysis.branch_stats().keys())
        ):
            for branch_number, line_number in enumerate(
                sorted(missing_arcs[block_line_number])
            ):
                # The exit branches have a negative line number,
                # this will not produce valid lcov. Setting
                # the line number of the exit branch to 0 will allow
                # for valid lcov, while preserving the data.
                line_number = max(line_number, 0)
                outfile.write(f"BRDA:{line_number},{block_number},{branch_number},-\n")

            # The start value below allows for the block number to be
            # preserved between these two for loops (stopping the loop from
            # resetting the value of the block number to 0).
            for branch_number, line_number in enumerate(
                sorted(executed_arcs[block_line_number]),
                start=len(missing_arcs[block_line_number]),
            ):
                line_number = max(line_number, 0)
                outfile.write(f"BRDA:{line_number},{block_number},{branch_number},1\n")

        # Summary of the branch coverage.
        if analysis.has_arcs():
            branch_stats = analysis.branch_stats()
            brf = sum(t for t, k in branch_stats.values())
            brh = brf - sum(t - k for t, k in branch_stats.values())
            outfile.write(f"BRF:{brf}\n")
            outfile.write(f"BRH:{brh}\n")

        outfile.write("end_of_record\n")
