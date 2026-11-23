"""Writes the native stdlib classes that define no `__str__`, `__repr__` or `__format__`.

`PyStringDunderUtil` reads the result from `python-psi-impl/resources/tools/native_types_without_string_conversion.txt`.
A class is in the result when no observed Python version defines one of these methods for it, and the bundled typeshed
declares it under the same name. The script observes each version of `PYTHON_VERSIONS` on the current platform.

The typeshed update jobs in the TeamCity configuration run the script on Linux, then on Windows, then on macOS, after
typeshed_docstrings.py. Each run adds its observations to the `--observations` file of the run before and writes the
list again, so the last run writes the list of all three platforms. A run without `--observations` writes the list of
the current platform only.

Usage: uv run --no-project python list_types_without_string_conversion.py
         [--stdlib <typeshed stdlib dir>] [--observations <json file>] [--output <list file>]
"""

import argparse
import importlib
import inspect
import json
import os
import re
import subprocess
import sys
import tempfile
import warnings

from python_versions import PYTHON_VERSIONS

METHODS = ("__str__", "__repr__", "__format__")
SKIPPED_MODULES = {"__main__", "antigravity", "this", "idlelib", "tkinter", "turtle", "turtledemo", "ensurepip", "venv"}
TOOL_DIR = os.path.dirname(os.path.abspath(__file__))
Py_TPFLAGS_IMMUTABLETYPE = 1 << 8


def is_native(cls):
    """Whether native code defines `cls`.

    The source file of `__module__` does not tell. A native class can name a Python module there, such as
    `_zstd.ZstdCompressor` in Python 3.14, which names `compression.zstd`.
    """
    # Python sets the flag on each static type and on most native heap types. A Python class never has it.
    if cls.__flags__ & Py_TPFLAGS_IMMUTABLETYPE:
        return True
    # A mutable native type, such as `_json.make_encoder`. A Python class without a source has a Python metaclass or
    # Python functions. An example is the class that `typing` makes with `type.__new__` for `NamedTuple`.
    if type(cls) is not cls and not is_native(type(cls)):
        return False
    if any(inspect.isfunction(value) for value in vars(cls).values()):
        return False
    try:
        inspect.getsource(cls)
        return False
    except (TypeError, OSError):
        return True


def collect(output_file):
    """Writes the methods of `METHODS` that each native class of this interpreter defines to `output_file`."""
    warnings.simplefilter("ignore")
    result = {}
    for module_name in sorted(sys.stdlib_module_names):
        if module_name in SKIPPED_MODULES:
            continue
        try:
            module = importlib.import_module(module_name)
        except BaseException:
            continue
        for attr, obj in list(vars(module).items()):
            if not inspect.isclass(obj):
                continue
            mro = [cls for cls in obj.__mro__ if cls is not object]
            if not all(is_native(cls) for cls in mro):
                continue
            methods = sorted({m for cls in mro for m in METHODS if m in vars(cls)})
            result["{}.{}".format(obj.__module__, obj.__qualname__)] = methods
            result["{}.{}".format(module_name, attr)] = methods
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(result, f)


def observe(version):
    """The methods of `METHODS` that each native class defines in Python `version` on this platform."""
    fd, output_file = tempfile.mkstemp(suffix=".json")
    os.close(fd)
    try:
        # An imported module can print to the standard output, so the result goes to a file. The standard error stays,
        # so that the build log shows why a run failed.
        subprocess.run(
            ["uv", "run", "--no-project", "--python", version, "python", __file__, "--collect", output_file],
            check=True, stdout=subprocess.DEVNULL,
        )
        with open(output_file, encoding="utf-8") as f:
            return json.load(f)
    finally:
        os.remove(output_file)


def declared_in_typeshed(stdlib_dir, qname):
    module_name, _, class_name = qname.rpartition(".")
    path = os.path.join(stdlib_dir, *module_name.split("."))
    for stub in (path + ".pyi", os.path.join(path, "__init__.pyi")):
        if os.path.exists(stub):
            with open(stub, encoding="utf-8") as f:
                return re.search(r"^\s*class {}\b".format(re.escape(class_name)), f.read(), re.M) is not None
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--stdlib", default=os.path.join(TOOL_DIR, "..", "..", "bundledPyiStubs", "typeshed", "stdlib"),
                        help="the typeshed stdlib dir that must declare each class of the list")
    parser.add_argument("--observations",
                        help="a JSON file with the methods of each class that earlier runs observed; the run adds its own")
    parser.add_argument("--output", default=os.path.join(TOOL_DIR, "..", "..", "python-psi-impl", "resources", "tools",
                                                         "native_types_without_string_conversion.txt"))
    args = parser.parse_args()

    observed = {}
    if args.observations and os.path.exists(args.observations):
        with open(args.observations, encoding="utf-8") as f:
            observed = {qname: set(methods) for qname, methods in json.load(f).items()}
    for version in PYTHON_VERSIONS:
        for qname, methods in observe(version).items():
            observed.setdefault(qname, set()).update(methods)
    if args.observations:
        with open(args.observations, "w", encoding="utf-8", newline="\n") as f:
            json.dump({qname: sorted(methods) for qname, methods in sorted(observed.items())}, f, indent=1)

    result = [qname for qname, methods in sorted(observed.items())
              if qname != "builtins.object" and not methods and declared_in_typeshed(args.stdlib, qname)]
    with open(args.output, "w", encoding="utf-8", newline="\n") as f:
        f.writelines(qname + "\n" for qname in result)


if __name__ == "__main__":
    if sys.argv[1:2] == ["--collect"]:
        collect(sys.argv[2])
    else:
        main()
