#!/usr/bin/env python
r"""Test runner for typeshed.

Depends on pytype being installed.

If pytype is installed:
    1. For every pyi, do nothing if it is in pytype_blacklist.txt.
    2. If the blacklist line has a "# parse only" comment run
      "pytd <foo.pyi>" in a separate process.
    3. If the file is not in the blacklist run
      "pytype --typeshed-location=typeshed_location --module-name=foo \
      --parse-pyi <foo.pyi>.
Option two will parse the file, mostly syntactical correctness. Option three
will load the file and all the builtins, typeshed dependencies. This will
also discover incorrect usage of imported modules.
"""

import argparse
import collections
import itertools
import os
import re
import subprocess
import sys

parser = argparse.ArgumentParser(description='Pytype/typeshed tests.')
parser.add_argument('-n', '--dry-run', action='store_true',
                    help="Don't actually run tests")
parser.add_argument('--num-parallel', type=int, default=1,
                    help='Number of test processes to spawn')
# Default to '' so that symlinking typeshed subdirs in cwd will work.
parser.add_argument('--typeshed-location', type=str, default='',
                    help='Path to typeshed installation.')
# Default to '' so that finding pytype in path will work.
parser.add_argument('--pytype-bin-dir', type=str, default='',
                    help='Path to directory with pytype and pytd executables.')
# Set to true to print a stack trace every time an exception is thrown.
parser.add_argument('--print-stderr', type=bool, default=False,
                    help='Print stderr every time an error is encountered.')
# We need to invoke python2.7 and 3.6.
parser.add_argument('--python27-exe', type=str, default='python2.7',
                    help='Path to a python 2.7 interpreter.')
parser.add_argument('--python36-exe', type=str, default='python3.6',
                    help='Path to a python 3.6 interpreter.')

Dirs = collections.namedtuple('Dirs', ['pytype', 'typeshed'])


TYPESHED_SUBDIRS = ['stdlib', 'third_party']


def main():
    args = parser.parse_args()
    code, runs = pytype_test(args)

    if code:
        print('--- exit status %d ---' % code)
        sys.exit(code)
    if not runs:
        print('--- nothing to do; exit 1 ---')
        sys.exit(1)


def get_project_dirs(args):
    """Top-level project directories for pytype executables and typeshed."""
    typeshed_location = args.typeshed_location or os.getcwd()
    return Dirs(args.pytype_bin_dir, typeshed_location)


class PathMatcher(object):
    def __init__(self, patterns):
        if patterns:
            self.matcher = re.compile('(%s)$' % '|'.join(patterns))
        else:
            self.matcher = None

    def search(self, path):
        if not self.matcher:
            return False
        return self.matcher.search(path)


def load_blacklist(dirs):
    filename = os.path.join(dirs.typeshed, 'tests', 'pytype_blacklist.txt')
    skip_re = re.compile(r'^\s*([^\s#]+)\s*(?:#.*)?$')
    parse_only_re = re.compile(r'^\s*([^\s#]+)\s*#\s*parse only\s*')
    skip = []
    parse_only = []

    with open(filename) as f:
        for line in f:
            parse_only_match = parse_only_re.match(line)
            skip_match = skip_re.match(line)
            if parse_only_match:
                parse_only.append(parse_only_match.group(1))
            elif skip_match:
                skip.append(skip_match.group(1))

    return skip, parse_only


class BinaryRun(object):
    def __init__(self, args, dry_run=False, env=None):
        self.args = args
        self.results = None

        if dry_run:
            self.results = (0, '', '')
        else:
            if env is not None:
                full_env = os.environ.copy()
                full_env.update(env)
            else:
                full_env = None
            self.proc = subprocess.Popen(
                self.args,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                env=full_env)

    def communicate(self):
        if self.results:
            return self.results

        stdout, stderr = self.proc.communicate()
        self.results = self.proc.returncode, stdout, stderr
        return self.results


def _get_relative(filename):
    top = 0
    for d in TYPESHED_SUBDIRS:
        try:
            top = filename.index(d)
        except ValueError:
            continue
        else:
            break
    return filename[top:]


def _get_module_name(filename):
    """Converts a filename {subdir}/m.n/module/foo to module.foo."""
    return '.'.join(_get_relative(filename).split(os.path.sep)[2:]).replace(
        '.pyi', '').replace('.__init__', '')


def can_run(path, exe, *args):
    exe = os.path.join(path, exe)
    try:
        BinaryRun([exe] + list(args)).communicate()
        return True
    except OSError:
        return False


def _is_version(path, version):
    return any('%s/%s' % (d, version) in path for d in TYPESHED_SUBDIRS)


def pytype_test(args):
    dirs = get_project_dirs(args)
    pytype_exe = os.path.join(dirs.pytype, 'pytype-single')
    paths = [os.path.join(dirs.typeshed, d) for d in TYPESHED_SUBDIRS]

    for p in paths:
        if not os.path.isdir(p):
            print('Cannot find typeshed subdir at %s '
                  '(specify parent dir via --typeshed-location)' % p)
            return 0, 0

    if can_run(dirs.pytype, 'pytd', '-h'):
        pytd_exe = os.path.join(dirs.pytype, 'pytd')
    elif can_run(dirs.pytype, 'pytd_tool', '-h'):
        pytd_exe = os.path.join(dirs.pytype, 'pytd_tool')
    else:
        print('Cannot run pytd. Did you install pytype?')
        return 0, 0

    for python_version_str in ('27', '36'):
        dest = 'python%s_exe' % python_version_str
        version = '.'.join(list(python_version_str))
        arg = '--python%s-exe' % python_version_str
        if not can_run('', getattr(args, dest), '--version'):
            print('Cannot run Python {version}. (point to a valid executable '
                  'via {arg})'.format(version=version, arg=arg))
            return 0, 0

    # TODO(rchen152): Keep expanding our third_party/ coverage so we can move
    # to a small blacklist rather than an ever-growing whitelist.
    wanted = [
        'stdlib/',
        'third_party/.*/mypy_extensions',
        'third_party/.*/pkg_resources',
        'third_party/.*/six/',
        'third_party/.*/yaml/',
    ]
    wanted_re = re.compile(r'(?:%s).*\.pyi$' % '|'.join(wanted))
    skip, parse_only = load_blacklist(dirs)
    skipped = PathMatcher(skip)
    parse_only = PathMatcher(parse_only)

    pytype_run = []
    pytd_run = []
    bad = []

    def _make_test(filename, major_version):
        if major_version == 3:
            version = '3.6'
            exe = args.python36_exe
        else:
            version = '2.7'
            exe = args.python27_exe
        run_cmd = [
            pytype_exe,
            '--module-name=%s' % _get_module_name(filename),
            '--parse-pyi',
            '-V %s' % version,
            '--python_exe=%s' % exe,
        ]
        return BinaryRun(run_cmd + [filename],
                         dry_run=args.dry_run,
                         env={"TYPESHED_HOME": dirs.typeshed})

    for root, _, filenames in itertools.chain.from_iterable(
            os.walk(p) for p in paths):
        for f in sorted(filenames):
            f = os.path.join(root, f)
            rel = _get_relative(f)
            if wanted_re.search(rel):
                if parse_only.search(rel):
                    pytd_run.append(f)
                elif not skipped.search(rel):
                    pytype_run.append(f)

    running_tests = collections.deque()
    max_code, runs, errors = 0, 0, 0
    files = pytype_run + pytd_run
    total_tests = len(files)
    # Files in {subdir}/2and3 get tested twice
    total_tests += sum(1 for f in pytype_run if _is_version(f, '2and3'))
    print("Testing files with pytype...")
    while 1:
        while files and len(running_tests) < args.num_parallel:
            f = files.pop()
            if f in pytype_run:
                if _is_version(f, '2and3'):
                    running_tests.append(_make_test(f, 2))
                    running_tests.append(_make_test(f, 3))
                elif _is_version(f, '2'):
                    running_tests.append(_make_test(f, 2))
                elif _is_version(f, '3'):
                    running_tests.append(_make_test(f, 3))
                else:
                    print("Unrecognised path: %s" % f)
            elif f in pytd_run:
                test_run = BinaryRun([pytd_exe, f], dry_run=args.dry_run)
                running_tests.append(test_run)
            else:
                raise ValueError('Unknown action for file: %s' % f)

        if not running_tests:
            break

        test_run = running_tests.popleft()
        code, _, stderr = test_run.communicate()
        max_code = max(max_code, code)
        runs += 1

        if code:
            if args.print_stderr:
                print(stderr)
            errors += 1
            # We strip off the stack trace and just leave the last line with the
            # actual error; to see the stack traces use --print_stderr.
            bad.append((_get_relative(test_run.args[-1]),
                        stderr.rstrip().rsplit(b'\n', 1)[-1]))

        if runs % 25 == 0:
            print("  %3d/%d with %3d errors" % (runs, total_tests, errors))

    print('Ran pytype with %d pyis, got %d errors.' % (runs, errors))
    for f, err in bad:
        print('%s: %s' % (f, err))
    return max_code, runs

if __name__ == '__main__':
    main()
