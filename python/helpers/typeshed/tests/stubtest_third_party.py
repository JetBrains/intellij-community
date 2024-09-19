#!/usr/bin/env python3
"""Test typeshed's third party stubs using stubtest"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path
from textwrap import dedent
from typing import NoReturn

from _metadata import NoSuchStubError, get_recursive_requirements, read_metadata
from _utils import (
    PYTHON_VERSION,
    allowlist_stubtest_arguments,
    allowlists_path,
    colored,
    get_mypy_req,
    print_divider,
    print_error,
    print_success_msg,
    tests_path,
)


def run_stubtest(
    dist: Path, *, parser: argparse.ArgumentParser, verbose: bool = False, specified_platforms_only: bool = False
) -> bool:
    dist_name = dist.name
    try:
        metadata = read_metadata(dist_name)
    except NoSuchStubError as e:
        parser.error(str(e))
    print(f"{dist_name}... ", end="", flush=True)

    stubtest_settings = metadata.stubtest_settings
    if stubtest_settings.skip:
        print(colored("skipping", "yellow"))
        return True

    if sys.platform not in stubtest_settings.platforms:
        if specified_platforms_only:
            print(colored("skipping (platform not specified in METADATA.toml)", "yellow"))
            return True
        print(colored(f"Note: {dist_name} is not currently tested on {sys.platform} in typeshed's CI.", "yellow"))

    if not metadata.requires_python.contains(PYTHON_VERSION):
        print(colored(f"skipping (requires Python {metadata.requires_python})", "yellow"))
        return True

    with tempfile.TemporaryDirectory() as tmp:
        venv_dir = Path(tmp)
        try:
            subprocess.run(["uv", "venv", venv_dir, "--seed"], capture_output=True, check=True)
        except subprocess.CalledProcessError as e:
            print_command_failure("Failed to create a virtualenv (likely a bug in uv?)", e)
            return False
        if sys.platform == "win32":
            pip_exe = str(venv_dir / "Scripts" / "pip.exe")
            python_exe = str(venv_dir / "Scripts" / "python.exe")
        else:
            pip_exe = str(venv_dir / "bin" / "pip")
            python_exe = str(venv_dir / "bin" / "python")
        dist_extras = ", ".join(stubtest_settings.extras)
        dist_req = f"{dist_name}[{dist_extras}]=={metadata.version}"

        # If tool.stubtest.stubtest_requirements exists, run "pip install" on it.
        if stubtest_settings.stubtest_requirements:
            pip_cmd = [pip_exe, "install", *stubtest_settings.stubtest_requirements]
            try:
                subprocess.run(pip_cmd, check=True, capture_output=True)
            except subprocess.CalledProcessError as e:
                print_command_failure("Failed to install requirements", e)
                return False

        requirements = get_recursive_requirements(dist_name)

        # We need stubtest to be able to import the package, so install mypy into the venv
        # Hopefully mypy continues to not need too many dependencies
        # TODO: Maybe find a way to cache these in CI
        dists_to_install = [dist_req, get_mypy_req()]
        dists_to_install.extend(requirements.external_pkgs)  # Internal requirements are added to MYPYPATH

        # Since the "gdb" Python package is available only inside GDB, it is not
        # possible to install it through pip, so stub tests cannot install it.
        if dist_name == "gdb":
            dists_to_install[:] = dists_to_install[1:]

        pip_cmd = [pip_exe, "install", *dists_to_install]
        try:
            subprocess.run(pip_cmd, check=True, capture_output=True)
        except subprocess.CalledProcessError as e:
            print_command_failure("Failed to install", e)
            return False

        ignore_missing_stub = ["--ignore-missing-stub"] if stubtest_settings.ignore_missing_stub else []
        packages_to_check = [d.name for d in dist.iterdir() if d.is_dir() and d.name.isidentifier()]
        modules_to_check = [d.stem for d in dist.iterdir() if d.is_file() and d.suffix == ".pyi"]
        stubtest_cmd = [
            python_exe,
            "-m",
            "mypy.stubtest",
            # Use --custom-typeshed-dir in case we make linked changes to stdlib or _typeshed
            "--custom-typeshed-dir",
            str(dist.parent.parent),
            *ignore_missing_stub,
            *packages_to_check,
            *modules_to_check,
            *allowlist_stubtest_arguments(dist_name),
        ]

        stubs_dir = dist.parent
        mypypath_items = [str(dist)] + [str(stubs_dir / pkg) for pkg in requirements.typeshed_pkgs]
        mypypath = os.pathsep.join(mypypath_items)
        # For packages that need a display, we need to pass at least $DISPLAY
        # to stubtest. $DISPLAY is set by xvfb-run in CI.
        #
        # It seems that some other environment variables are needed too,
        # because the CI fails if we pass only os.environ["DISPLAY"]. I didn't
        # "bisect" to see which variables are actually needed.
        stubtest_env = os.environ | {"MYPYPATH": mypypath, "MYPY_FORCE_COLOR": "1"}

        # Perform some black magic in order to run stubtest inside uWSGI
        if dist_name == "uWSGI":
            if not setup_uwsgi_stubtest_command(dist, venv_dir, stubtest_cmd):
                return False

        if dist_name == "gdb":
            if not setup_gdb_stubtest_command(venv_dir, stubtest_cmd):
                return False

        try:
            subprocess.run(stubtest_cmd, env=stubtest_env, check=True, capture_output=True)
        except subprocess.CalledProcessError as e:
            print_error("fail")

            print_divider()
            print("Commands run:")
            print_commands(dist, pip_cmd, stubtest_cmd, mypypath)

            print_divider()
            print("Command output:\n")
            print_command_output(e)

            print_divider()
            print("Python version: ", end="", flush=True)
            ret = subprocess.run([sys.executable, "-VV"], capture_output=True)
            print_command_output(ret)
            print("\nRan with the following environment:")
            ret = subprocess.run([pip_exe, "freeze", "--all"], capture_output=True)
            print_command_output(ret)

            print_divider()
            main_allowlist_path = allowlists_path(dist_name) / "stubtest_allowlist.txt"
            if main_allowlist_path.exists():
                print(f'To fix "unused allowlist" errors, remove the corresponding entries from {main_allowlist_path}')
                print()
            else:
                print(f"Re-running stubtest with --generate-allowlist.\nAdd the following to {main_allowlist_path}:")
                ret = subprocess.run([*stubtest_cmd, "--generate-allowlist"], env=stubtest_env, capture_output=True)
                print_command_output(ret)

            print_divider()
            print(f"Upstream repository: {metadata.upstream_repository}")
            print(f"Typeshed source code: https://github.com/python/typeshed/tree/main/stubs/{dist.name}")

            print_divider()

            return False
        else:
            print_success_msg()

    if verbose:
        print_commands(dist, pip_cmd, stubtest_cmd, mypypath)

    return True


def setup_gdb_stubtest_command(venv_dir: Path, stubtest_cmd: list[str]) -> bool:
    """
    Use wrapper scripts to run stubtest inside gdb.
    The wrapper script is used to pass the arguments to the gdb script.
    """
    if sys.platform == "win32":
        print_error("gdb is not supported on Windows")
        return False

    try:
        gdb_version = subprocess.check_output(["gdb", "--version"], text=True, stderr=subprocess.STDOUT)
    except FileNotFoundError:
        print_error("gdb is not installed")
        return False
    if "Python scripting is not supported in this copy of GDB" in gdb_version:
        print_error("Python scripting is not supported in this copy of GDB")
        return False

    gdb_script = venv_dir / "gdb_stubtest.py"
    wrapper_script = venv_dir / "gdb_wrapper.py"
    gdb_script_contents = dedent(
        f"""
        import json
        import os
        import site
        import sys
        import traceback

        from glob import glob

        # Add the venv site-packages to sys.path. gdb doesn't use the virtual environment.
        # Taken from https://github.com/pwndbg/pwndbg/blob/83d8d95b576b749e888f533ce927ad5a77fb957b/gdbinit.py#L37
        site_pkgs_path = glob(os.path.join({str(venv_dir)!r}, "lib/*/site-packages"))[0]
        site.addsitedir(site_pkgs_path)

        exit_code = 1
        try:
            # gdb wraps stdout and stderr without a .fileno
            # colored output in mypy tries to access .fileno()
            sys.stdout.fileno = sys.__stdout__.fileno
            sys.stderr.fileno = sys.__stderr__.fileno

            from mypy.stubtest import main

            sys.argv = json.loads(os.environ.get("STUBTEST_ARGS"))
            exit_code = main()
        except Exception:
            traceback.print_exc()
        finally:
            gdb.execute(f"quit {{exit_code}}")
        """
    )
    gdb_script.write_text(gdb_script_contents)

    wrapper_script_contents = dedent(
        f"""
        import json
        import os
        import subprocess
        import sys

        stubtest_env = os.environ | {{"STUBTEST_ARGS": json.dumps(sys.argv)}}
        gdb_cmd = [
            "gdb",
            "--quiet",
            "--nx",
            "--batch",
            "--command",
            {str(gdb_script)!r},
        ]
        r = subprocess.run(gdb_cmd, env=stubtest_env)
        sys.exit(r.returncode)
        """
    )
    wrapper_script.write_text(wrapper_script_contents)

    # replace "-m mypy.stubtest" in stubtest_cmd with the path to our wrapper script
    assert stubtest_cmd[1:3] == ["-m", "mypy.stubtest"]
    stubtest_cmd[1:3] = [str(wrapper_script)]
    return True


def setup_uwsgi_stubtest_command(dist: Path, venv_dir: Path, stubtest_cmd: list[str]) -> bool:
    """Perform some black magic in order to run stubtest inside uWSGI.

    We have to write the exit code from stubtest to a surrogate file
    because uwsgi --pyrun does not exit with the exitcode from the
    python script. We have a second wrapper script that passed the
    arguments along to the uWSGI script and retrieves the exit code
    from the file, so it behaves like running stubtest normally would.

    Both generated wrapper scripts are created inside `venv_dir`,
    which itself is a subdirectory inside a temporary directory,
    so both scripts will be cleaned up after this function
    has been executed.
    """
    uwsgi_ini = tests_path(dist.name) / "uwsgi.ini"

    if sys.platform == "win32":
        print_error("uWSGI is not supported on Windows")
        return False

    uwsgi_script = venv_dir / "uwsgi_stubtest.py"
    wrapper_script = venv_dir / "uwsgi_wrapper.py"
    exit_code_surrogate = venv_dir / "exit_code"
    uwsgi_script_contents = dedent(
        f"""
        import json
        import os
        import sys
        from mypy.stubtest import main

        sys.argv = json.loads(os.environ.get("STUBTEST_ARGS"))
        exit_code = main()
        with open("{exit_code_surrogate}", mode="w") as fp:
            fp.write(str(exit_code))
        sys.exit(exit_code)
        """
    )
    uwsgi_script.write_text(uwsgi_script_contents)

    uwsgi_exe = venv_dir / "bin" / "uwsgi"

    # It would be nice to reliably separate uWSGI output from
    # the stubtest output, on linux it appears that stubtest
    # will always go to stdout and uWSGI to stderr, but on
    # MacOS they both go to stderr, for now we deal with the
    # bit of extra spam
    wrapper_script_contents = dedent(
        f"""
        import json
        import os
        import subprocess
        import sys

        stubtest_env = os.environ | {{"STUBTEST_ARGS": json.dumps(sys.argv)}}
        uwsgi_cmd = [
            "{uwsgi_exe}",
            "--ini",
            "{uwsgi_ini}",
            "--spooler",
            "{venv_dir}",
            "--pyrun",
            "{uwsgi_script}",
        ]
        subprocess.run(uwsgi_cmd, env=stubtest_env)
        with open("{exit_code_surrogate}", mode="r") as fp:
            sys.exit(int(fp.read()))
        """
    )
    wrapper_script.write_text(wrapper_script_contents)

    # replace "-m mypy.stubtest" in stubtest_cmd with the path to our wrapper script
    assert stubtest_cmd[1:3] == ["-m", "mypy.stubtest"]
    stubtest_cmd[1:3] = [str(wrapper_script)]
    return True


def print_commands(dist: Path, pip_cmd: list[str], stubtest_cmd: list[str], mypypath: str) -> None:
    print()
    print(" ".join(pip_cmd))
    print(f"MYPYPATH={mypypath}", " ".join(stubtest_cmd))


def print_command_failure(message: str, e: subprocess.CalledProcessError) -> None:
    print_error("fail")
    print()
    print(message)
    print_command_output(e)


def print_command_output(e: subprocess.CalledProcessError | subprocess.CompletedProcess[bytes]) -> None:
    print(e.stdout.decode(), end="")
    print(e.stderr.decode(), end="")


def main() -> NoReturn:
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", action="store_true", help="verbose output")
    parser.add_argument("--num-shards", type=int, default=1)
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument(
        "--specified-platforms-only",
        action="store_true",
        help="skip the test if the current platform is not specified in METADATA.toml/tool.stubtest.platforms",
    )
    parser.add_argument("dists", metavar="DISTRIBUTION", type=str, nargs=argparse.ZERO_OR_MORE)
    args = parser.parse_args()

    typeshed_dir = Path(".").resolve()
    if len(args.dists) == 0:
        dists = sorted((typeshed_dir / "stubs").iterdir())
    else:
        dists = [typeshed_dir / "stubs" / d for d in args.dists]

    result = 0
    for i, dist in enumerate(dists):
        if i % args.num_shards != args.shard_index:
            continue
        if not run_stubtest(dist, parser=parser, verbose=args.verbose, specified_platforms_only=args.specified_platforms_only):
            result = 1
    sys.exit(result)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
