#!/usr/bin/env python
"""Test runner for typeshed.

Depends on mypy and pytype being installed.

If pytype is installed:
    1. For every pyi, run "pytd <foo.pyi>" in a separate process
"""

import os
import re
import sys
import argparse
import subprocess
import collections

parser = argparse.ArgumentParser(description="Pytype tests.")
parser.add_argument('-n', '--dry-run', action='store_true', help="Don't actually run tests")
parser.add_argument('--num-parallel', type=int, default=1,
                    help="Number of test processes to spawn")


def main():
    args = parser.parse_args()
    code, runs = pytype_test(args)

    if code:
        print("--- exit status %d ---" % code)
        sys.exit(code)
    if not runs:
        print("--- nothing to do; exit 1 ---")
        sys.exit(1)


def load_blacklist():
    filename = os.path.join(os.path.dirname(__file__), "pytype_blacklist.txt")
    regex = r"^\s*([^\s#]+)\s*(?:#.*)?$"

    with open(filename) as f:
        return re.findall(regex, f.read(), flags=re.M)


class PytdRun(object):
    def __init__(self, args, dry_run=False):
        self.args = args
        self.dry_run = dry_run
        self.results = None

        if dry_run:
            self.results = (0, "", "")
        else:
            self.proc = subprocess.Popen(
                ["pytd"] + args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE)

    def communicate(self):
        if self.results:
            return self.results

        stdout, stderr = self.proc.communicate()
        self.results = self.proc.returncode, stdout, stderr
        return self.results


def pytype_test(args):
    try:
        PytdRun(["-h"]).communicate()
    except OSError:
        print("Cannot run pytd. Did you install pytype?")
        return 0, 0

    wanted = re.compile(r"stdlib/(2|2\.7|2and3)/.*\.pyi$")
    skipped = re.compile("(%s)$" % "|".join(load_blacklist()))
    files = []

    for root, _, filenames in os.walk("stdlib"):
        for f in sorted(filenames):
            f = os.path.join(root, f)
            if wanted.search(f) and not skipped.search(f):
                files.append(f)

    running_tests = collections.deque()
    max_code, runs, errors = 0, 0, 0
    print("Running pytype tests...")
    while 1:
        while files and len(running_tests) < args.num_parallel:
            test_run = PytdRun([files.pop()], dry_run=args.dry_run)
            running_tests.append(test_run)

        if not running_tests:
            break

        test_run = running_tests.popleft()
        code, stdout, stderr = test_run.communicate()
        max_code = max(max_code, code)
        runs += 1

        if code:
            print("pytd error processing \"%s\":" % test_run.args[0])
            print(stderr)
            errors += 1

    print("Ran pytype with %d pyis, got %d errors." % (runs, errors))
    return max_code, runs


if __name__ == '__main__':
    main()
