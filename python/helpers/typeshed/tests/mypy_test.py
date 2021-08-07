#!/usr/bin/env python3
"""Test runner for typeshed.

Depends on mypy being installed.

Approach:

1. Parse sys.argv
2. Compute appropriate arguments for mypy
3. Stuff those arguments into sys.argv
4. Run mypy.main('')
5. Repeat steps 2-4 for other mypy runs (e.g. --py2)
"""

import argparse
import os
import re
import sys
import toml
import tempfile
from typing import Dict, NamedTuple

PY2_NAMESPACE = "@python2"
THIRD_PARTY_NAMESPACE = "stubs"

parser = argparse.ArgumentParser(
    description="Test runner for typeshed. Patterns are unanchored regexps on the full path."
)
parser.add_argument("-v", "--verbose", action="count", default=0, help="More output")
parser.add_argument("-n", "--dry-run", action="store_true", help="Don't actually run mypy")
parser.add_argument("-x", "--exclude", type=str, nargs="*", help="Exclude pattern")
parser.add_argument("-p", "--python-version", type=str, nargs="*", help="These versions only (major[.minor])")
parser.add_argument("--platform", help="Run mypy for a certain OS platform (defaults to sys.platform)")
parser.add_argument(
    "--warn-unused-ignores",
    action="store_true",
    help="Run mypy with --warn-unused-ignores "
    "(hint: only get rid of warnings that are "
    "unused for all platforms and Python versions)",
)

parser.add_argument("filter", type=str, nargs="*", help="Include pattern (default all)")


def log(args, *varargs):
    if args.verbose >= 2:
        print(*varargs)


def match(fn, args, exclude_list):
    if exclude_list.match(fn):
        log(args, fn, "exluded by exclude list")
        return False
    if not args.filter and not args.exclude:
        log(args, fn, "accept by default")
        return True
    if args.exclude:
        for f in args.exclude:
            if re.search(f, fn):
                log(args, fn, "excluded by pattern", f)
                return False
    if args.filter:
        for f in args.filter:
            if re.search(f, fn):
                log(args, fn, "accepted by pattern", f)
                return True
    if args.filter:
        log(args, fn, "rejected (no pattern matches)")
        return False
    log(args, fn, "accepted (no exclude pattern matches)")
    return True


_VERSION_LINE_RE = re.compile(r"^([a-zA-Z_][a-zA-Z0-9_.]*): ([23]\.\d{1,2})-([23]\.\d{1,2})?$")


def parse_versions(fname):
    result = {}
    with open(fname) as f:
        for line in f:
            # Allow having some comments or empty lines.
            line = line.split("#")[0].strip()
            if line == "":
                continue
            m = _VERSION_LINE_RE.match(line)
            assert m, "invalid VERSIONS line: " + line
            mod = m.group(1)
            min_version = parse_version(m.group(2))
            max_version = parse_version(m.group(3)) if m.group(3) else (99, 99)
            result[mod] = min_version, max_version
    return result


_VERSION_RE = re.compile(r"^([23])\.(\d+)$")


def parse_version(v_str):
    m = _VERSION_RE.match(v_str)
    assert m, "invalid version: " + v_str
    return int(m.group(1)), int(m.group(2))


def is_supported(distribution, major):
    with open(os.path.join(THIRD_PARTY_NAMESPACE, distribution, "METADATA.toml")) as f:
        data = dict(toml.loads(f.read()))
    if major == 2:
        # Python 2 is not supported by default.
        return bool(data.get("python2", False))
    # Python 3 is supported by default.
    return bool(data.get("python3", True))


def add_files(files, seen, root, name, args, exclude_list):
    """Add all files in package or module represented by 'name' located in 'root'."""
    full = os.path.join(root, name)
    mod, ext = os.path.splitext(name)
    if ext in [".pyi", ".py"]:
        if match(full, args, exclude_list):
            seen.add(mod)
            files.append(full)
    elif (
        os.path.isfile(os.path.join(full, "__init__.pyi")) or
        os.path.isfile(os.path.join(full, "__init__.py"))
    ):
        for r, ds, fs in os.walk(full):
            ds.sort()
            fs.sort()
            for f in fs:
                m, x = os.path.splitext(f)
                if x in [".pyi", ".py"]:
                    fn = os.path.join(r, f)
                    if match(fn, args, exclude_list):
                        seen.add(mod)
                        files.append(fn)


class MypyDistConf(NamedTuple):
    module_name: str
    values: Dict

# The configuration section in the metadata file looks like the following, with multiple module sections possible
# [mypy-tests]
# [mypy-tests.yaml]
# module_name = "yaml"
# [mypy-tests.yaml.values]
# disallow_incomplete_defs = true
# disallow_untyped_defs = true


def add_configuration(configurations, seen_dist_configs, distribution):
    if distribution in seen_dist_configs:
        return

    with open(os.path.join(THIRD_PARTY_NAMESPACE, distribution, "METADATA.toml")) as f:
        data = dict(toml.loads(f.read()))

    mypy_tests_conf = data.get("mypy-tests")
    if not mypy_tests_conf:
        return

    assert isinstance(mypy_tests_conf, dict), "mypy-tests should be a section"
    for section_name, mypy_section in mypy_tests_conf.items():
        assert isinstance(mypy_section, dict), "{} should be a section".format(section_name)
        module_name = mypy_section.get("module_name")

        assert module_name is not None, "{} should have a module_name key".format(section_name)
        assert isinstance(module_name, str), "{} should be a key-value pair".format(section_name)

        values = mypy_section.get("values")
        assert values is not None, "{} should have a values section".format(section_name)
        assert isinstance(values, dict), "values should be a section"

        configurations.append(MypyDistConf(module_name, values.copy()))
    seen_dist_configs.add(distribution)


def main():
    args = parser.parse_args()

    with open(os.path.join(os.path.dirname(__file__), "mypy_exclude_list.txt")) as f:
        exclude_list = re.compile("(%s)$" % "|".join(re.findall(r"^\s*([^\s#]+)\s*(?:#.*)?$", f.read(), flags=re.M)))

    try:
        from mypy.main import main as mypy_main
    except ImportError:
        print("Cannot import mypy. Did you install it?")
        sys.exit(1)

    versions = [(3, 10), (3, 9), (3, 8), (3, 7), (3, 6), (2, 7)]
    if args.python_version:
        versions = [v for v in versions if any(("%d.%d" % v).startswith(av) for av in args.python_version)]
        if not versions:
            print("--- no versions selected ---")
            sys.exit(1)

    code = 0
    runs = 0
    for major, minor in versions:
        files = []
        seen = {"__builtin__", "builtins", "typing"}  # Always ignore these.
        configurations = []
        seen_dist_configs = set()

        # First add standard library files.
        if major == 2:
            root = os.path.join("stdlib", PY2_NAMESPACE)
            for name in os.listdir(root):
                mod, _ = os.path.splitext(name)
                if mod in seen or mod.startswith("."):
                    continue
                add_files(files, seen, root, name, args, exclude_list)
        else:
            supported_versions = parse_versions(os.path.join("stdlib", "VERSIONS"))
            root = "stdlib"
            for name in os.listdir(root):
                if name == PY2_NAMESPACE or name == "VERSIONS":
                    continue
                mod, _ = os.path.splitext(name)
                if supported_versions[mod][0] <= (major, minor) <= supported_versions[mod][1]:
                    add_files(files, seen, root, name, args, exclude_list)

        # Next add files for all third party distributions.
        for distribution in os.listdir(THIRD_PARTY_NAMESPACE):
            if not is_supported(distribution, major):
                continue
            if major == 2 and os.path.isdir(os.path.join(THIRD_PARTY_NAMESPACE, distribution, PY2_NAMESPACE)):
                root = os.path.join(THIRD_PARTY_NAMESPACE, distribution, PY2_NAMESPACE)
            else:
                root = os.path.join(THIRD_PARTY_NAMESPACE, distribution)
            for name in os.listdir(root):
                if name == PY2_NAMESPACE:
                    continue
                mod, _ = os.path.splitext(name)
                if mod in seen or mod.startswith("."):
                    continue
                add_files(files, seen, root, name, args, exclude_list)
                add_configuration(configurations, seen_dist_configs, distribution)

        if files:
            with tempfile.NamedTemporaryFile("w+", delete=False) as temp:
                temp.write("[mypy]\n")

                for dist_conf in configurations:
                    temp.write("[mypy-%s]\n" % dist_conf.module_name)
                    for k, v in dist_conf.values.items():
                        temp.write("{} = {}\n".format(k, v))

                config_file_name = temp.name
            runs += 1
            flags = [
                "--python-version", "%d.%d" % (major, minor),
                "--config-file", config_file_name,
                "--strict-optional",
                "--no-site-packages",
                "--show-traceback",
                "--no-implicit-optional",
                "--disallow-any-generics",
                "--disallow-subclassing-any",
                "--warn-incomplete-stub",
                # Setting custom typeshed dir prevents mypy from falling back to its bundled
                # typeshed in case of stub deletions
                "--custom-typeshed-dir", os.path.dirname(os.path.dirname(__file__)),
            ]
            if args.warn_unused_ignores:
                flags.append("--warn-unused-ignores")
            if args.platform:
                flags.extend(["--platform", args.platform])
            sys.argv = ["mypy"] + flags + files
            if args.verbose:
                print("running", " ".join(sys.argv))
            else:
                print("running mypy", " ".join(flags), "# with", len(files), "files")
            try:
                if not args.dry_run:
                    mypy_main("", sys.stdout, sys.stderr)
            except SystemExit as err:
                code = max(code, err.code)
            finally:
                os.remove(config_file_name)
    if code:
        print("--- exit status", code, "---")
        sys.exit(code)
    if not runs:
        print("--- nothing to do; exit 1 ---")
        sys.exit(1)


if __name__ == "__main__":
    main()
