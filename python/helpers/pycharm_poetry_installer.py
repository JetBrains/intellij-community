#!/usr/bin/env python3
r"""
This script will install Poetry and its dependencies in an isolated fashion.

It will perform the following steps:
    * Create a new virtual environment using the built-in venv module, or the virtualenv zipapp if venv is unavailable.
      This will be created at a platform-specific path (or `$POETRY_HOME` if `$POETRY_HOME` is set:
        - `~/Library/Application Support/pypoetry` on macOS
        - `$XDG_DATA_HOME/pypoetry` on Linux/Unix (`$XDG_DATA_HOME` is `~/.local/share` if unset)
        - `%APPDATA%\pypoetry` on Windows
    * Update pip inside the virtual environment to avoid bugs in older versions.
    * Install the latest (or a given) version of Poetry inside this virtual environment using pip.
    * Install a `poetry` script into a platform-specific path (or `$POETRY_HOME/bin` if `$POETRY_HOME` is set):
        - `~/.local/bin` on Unix
        - `%APPDATA%\Python\Scripts` on Windows
    * Attempt to inform the user if they need to add this bin directory to their `$PATH`, as well as how to do so.
    * Upon failure, write an error log to `poetry-installer-error-<hash>.log and restore any previous environment.

This script performs minimal magic, and should be relatively stable. However, it is optimized for interactive developer
use and trivial pipelines. If you are considering using this script in production, you should consider manually-managed
installs, or use of pipx as alternatives to executing arbitrary, unversioned code from the internet. If you prefer this
script to alternatives, consider maintaining a local copy as part of your infrastructure.

For full documentation, visit https://python-poetry.org/docs/#installation.
"""  # noqa: E501
import sys


# Eager version check so we fail nicely before possible syntax errors
if sys.version_info < (3, 6):  # noqa: UP036
    sys.stdout.write("Poetry installer requires Python 3.6 or newer to run!\n")
    sys.exit(1)


import argparse
import json
import os
import re
import shutil
import subprocess
import sysconfig
import tempfile

from contextlib import closing
from contextlib import contextmanager
from functools import cmp_to_key
from io import UnsupportedOperation
from pathlib import Path
from typing import Optional
from urllib.request import Request
from urllib.request import urlopen


SHELL = os.getenv("SHELL", "")
WINDOWS = sys.platform.startswith("win") or (sys.platform == "cli" and os.name == "nt")
MINGW = sysconfig.get_platform().startswith("mingw")
MACOS = sys.platform == "darwin"

FOREGROUND_COLORS = {
    "black": 30,
    "red": 31,
    "green": 32,
    "yellow": 33,
    "blue": 34,
    "magenta": 35,
    "cyan": 36,
    "white": 37,
}

BACKGROUND_COLORS = {
    "black": 40,
    "red": 41,
    "green": 42,
    "yellow": 43,
    "blue": 44,
    "magenta": 45,
    "cyan": 46,
    "white": 47,
}

OPTIONS = {"bold": 1, "underscore": 4, "blink": 5, "reverse": 7, "conceal": 8}


def style(fg, bg, options):
    codes = []

    if fg:
        codes.append(FOREGROUND_COLORS[fg])

    if bg:
        codes.append(BACKGROUND_COLORS[bg])

    if options:
        if not isinstance(options, (list, tuple)):
            options = [options]

        for option in options:
            codes.append(OPTIONS[option])

    return "\033[{}m".format(";".join(map(str, codes)))


STYLES = {
    "info": style("cyan", None, None),
    "comment": style("yellow", None, None),
    "success": style("green", None, None),
    "error": style("red", None, None),
    "warning": style("yellow", None, None),
    "b": style(None, None, ("bold",)),
}


def is_decorated():
    if WINDOWS:
        return (
                os.getenv("ANSICON") is not None
                or os.getenv("ConEmuANSI") == "ON"  # noqa: SIM112
                or os.getenv("Term") == "xterm"  # noqa: SIM112
        )

    if not hasattr(sys.stdout, "fileno"):
        return False

    try:
        return os.isatty(sys.stdout.fileno())
    except UnsupportedOperation:
        return False


def is_interactive():
    if not hasattr(sys.stdin, "fileno"):
        return False

    try:
        return os.isatty(sys.stdin.fileno())
    except UnsupportedOperation:
        return False


def colorize(style, text):
    if not is_decorated():
        return text

    return f"{STYLES[style]}{text}\033[0m"


def string_to_bool(value):
    value = value.lower()

    return value in {"true", "1", "y", "yes"}


def data_dir() -> Path:
    if os.getenv("POETRY_HOME"):
        return Path(os.getenv("POETRY_HOME")).expanduser()

    if WINDOWS:
        base_dir = Path(_get_win_folder("CSIDL_APPDATA"))
    elif MACOS:
        base_dir = Path("~/Library/Application Support").expanduser()
    else:
        base_dir = Path(os.getenv("XDG_DATA_HOME", "~/.local/share")).expanduser()

    base_dir = base_dir.resolve()
    return base_dir / "pypoetry"


def bin_dir() -> Path:
    if os.getenv("POETRY_HOME"):
        return Path(os.getenv("POETRY_HOME")).expanduser() / "bin"

    if WINDOWS and not MINGW:
        return Path(_get_win_folder("CSIDL_APPDATA")) / "Python/Scripts"
    else:
        return Path("~/.local/bin").expanduser()


def _get_win_folder_from_registry(csidl_name):
    import winreg as _winreg

    shell_folder_name = {
        "CSIDL_APPDATA": "AppData",
        "CSIDL_COMMON_APPDATA": "Common AppData",
        "CSIDL_LOCAL_APPDATA": "Local AppData",
    }[csidl_name]

    key = _winreg.OpenKey(
        _winreg.HKEY_CURRENT_USER,
        r"Software\Microsoft\Windows\CurrentVersion\Explorer\Shell Folders",
    )
    path, _ = _winreg.QueryValueEx(key, shell_folder_name)

    return path


def _get_win_folder_with_ctypes(csidl_name):
    import ctypes

    csidl_const = {
        "CSIDL_APPDATA": 26,
        "CSIDL_COMMON_APPDATA": 35,
        "CSIDL_LOCAL_APPDATA": 28,
    }[csidl_name]

    buf = ctypes.create_unicode_buffer(1024)
    ctypes.windll.shell32.SHGetFolderPathW(None, csidl_const, None, 0, buf)

    # Downgrade to short path name if have highbit chars. See
    # <http://bugs.activestate.com/show_bug.cgi?id=85099>.
    has_high_char = False
    for c in buf:
        if ord(c) > 255:
            has_high_char = True
            break
    if has_high_char:
        buf2 = ctypes.create_unicode_buffer(1024)
        if ctypes.windll.kernel32.GetShortPathNameW(buf.value, buf2, 1024):
            buf = buf2

    return buf.value


if WINDOWS:
    try:
        from ctypes import windll  # noqa: F401

        _get_win_folder = _get_win_folder_with_ctypes
    except ImportError:
        _get_win_folder = _get_win_folder_from_registry


PRE_MESSAGE = """# Welcome to {poetry}!

This will download and install the latest version of {poetry},
a dependency and package manager for Python.

It will add the `poetry` command to {poetry}'s bin directory, located at:

{poetry_home_bin}

You can uninstall at any time by executing this script with the --uninstall option,
and these changes will be reverted.
"""

POST_MESSAGE = """{poetry} ({version}) is installed now. Great!

You can test that everything is set up by executing:

`{test_command}`
"""

POST_MESSAGE_NOT_IN_PATH = """{poetry} ({version}) is installed now. Great!

To get started you need {poetry}'s bin directory ({poetry_home_bin}) in your `PATH`
environment variable.
{configure_message}
Alternatively, you can call {poetry} explicitly with `{poetry_executable}`.

You can test that everything is set up by executing:

`{test_command}`
"""

POST_MESSAGE_CONFIGURE_UNIX = """
Add `export PATH="{poetry_home_bin}:$PATH"` to your shell configuration file.
"""

POST_MESSAGE_CONFIGURE_FISH = """
You can execute `set -U fish_user_paths {poetry_home_bin} $fish_user_paths`
"""

POST_MESSAGE_CONFIGURE_WINDOWS = """"""


class PoetryInstallationError(RuntimeError):
    def __init__(self, return_code: int = 0, log: Optional[str] = None):
        super().__init__()
        self.return_code = return_code
        self.log = log


class VirtualEnvironment:
    def __init__(self, path: Path) -> None:
        self._path = path
        self._bin_path = self._path.joinpath(
            "Scripts" if WINDOWS and not MINGW else "bin"
        )
        # str is for compatibility with subprocess.run on CPython <= 3.7 on Windows
        self._python = str(
            self._path.joinpath(self._bin_path, "python.exe" if WINDOWS else "python")
        )

    @property
    def path(self):
        return self._path

    @property
    def bin_path(self):
        return self._bin_path

    @classmethod
    def make(cls, target: Path) -> "VirtualEnvironment":
        if not sys.executable:
            raise ValueError(
                "Unable to determine sys.executable. Set PATH to a sane value or set it"
                " explicitly with PYTHONEXECUTABLE."
            )

        try:
            # on some linux distributions (eg: debian), the distribution provided python
            # installation might not include ensurepip, causing the venv module to
            # fail when attempting to create a virtual environment
            # we import ensurepip but do not use it explicitly here
            import ensurepip  # noqa: F401
            import venv

            builder = venv.EnvBuilder(clear=True, with_pip=True, symlinks=False)
            context = builder.ensure_directories(target)

            if (
                    WINDOWS
                    and hasattr(context, "env_exec_cmd")
                    and context.env_exe != context.env_exec_cmd
            ):
                target = target.resolve()

            builder.create(target)
        except ImportError:
            # fallback to using virtualenv package if venv is not available, eg: ubuntu
            python_version = f"{sys.version_info.major}.{sys.version_info.minor}"
            virtualenv_bootstrap_url = (
                f"https://bootstrap.pypa.io/virtualenv/{python_version}/virtualenv.pyz"
            )

            with tempfile.TemporaryDirectory(prefix="poetry-installer") as temp_dir:
                virtualenv_pyz = Path(temp_dir) / "virtualenv.pyz"
                request = Request(
                    virtualenv_bootstrap_url, headers={"User-Agent": "Python Poetry"}
                )
                virtualenv_pyz.write_bytes(urlopen(request).read())
                cls.run(
                    sys.executable, virtualenv_pyz, "--clear", "--always-copy", target
                )

        # We add a special file so that Poetry can detect
        # its own virtual environment
        target.joinpath("poetry_env").touch()

        env = cls(target)

        # this ensures that outdated system default pip does not trigger older bugs
        env.pip("install", "--disable-pip-version-check", "--upgrade", "pip")

        return env

    @staticmethod
    def run(*args, **kwargs) -> subprocess.CompletedProcess:
        completed_process = subprocess.run(
            args,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            **kwargs,
        )
        if completed_process.returncode != 0:
            raise PoetryInstallationError(
                return_code=completed_process.returncode,
                log=completed_process.stdout.decode(),
            )
        return completed_process

    def python(self, *args, **kwargs) -> subprocess.CompletedProcess:
        return self.run(self._python, *args, **kwargs)

    def pip(self, *args, **kwargs) -> subprocess.CompletedProcess:
        return self.python("-m", "pip", *args, **kwargs)


class Cursor:
    def __init__(self) -> None:
        self._output = sys.stdout

    def move_up(self, lines: int = 1) -> "Cursor":
        self._output.write(f"\x1b[{lines}A")

        return self

    def move_down(self, lines: int = 1) -> "Cursor":
        self._output.write(f"\x1b[{lines}B")

        return self

    def move_right(self, columns: int = 1) -> "Cursor":
        self._output.write(f"\x1b[{columns}C")

        return self

    def move_left(self, columns: int = 1) -> "Cursor":
        self._output.write(f"\x1b[{columns}D")

        return self

    def move_to_column(self, column: int) -> "Cursor":
        self._output.write(f"\x1b[{column}G")

        return self

    def move_to_position(self, column: int, row: int) -> "Cursor":
        self._output.write(f"\x1b[{row + 1};{column}H")

        return self

    def save_position(self) -> "Cursor":
        self._output.write("\x1b7")

        return self

    def restore_position(self) -> "Cursor":
        self._output.write("\x1b8")

        return self

    def hide(self) -> "Cursor":
        self._output.write("\x1b[?25l")

        return self

    def show(self) -> "Cursor":
        self._output.write("\x1b[?25h\x1b[?0c")

        return self

    def clear_line(self) -> "Cursor":
        """
        Clears all the output from the current line.
        """
        self._output.write("\x1b[2K")

        return self

    def clear_line_after(self) -> "Cursor":
        """
        Clears all the output from the current line after the current position.
        """
        self._output.write("\x1b[K")

        return self

    def clear_output(self) -> "Cursor":
        """
        Clears all the output from the cursors' current position
        to the end of the screen.
        """
        self._output.write("\x1b[0J")

        return self

    def clear_screen(self) -> "Cursor":
        """
        Clears the entire screen.
        """
        self._output.write("\x1b[2J")

        return self


class Installer:
    METADATA_URL = "https://pypi.org/pypi/poetry/json"
    VERSION_REGEX = re.compile(
        r"v?(\d+)(?:\.(\d+))?(?:\.(\d+))?(?:\.(\d+))?"
        "("
        "[._-]?"
        r"(?:(stable|beta|b|rc|RC|alpha|a|patch|pl|p)((?:[.-]?\d+)*)?)?"
        "([.-]?dev)?"
        ")?"
        r"(?:\+[^\s]+)?"
    )

    def __init__(
            self,
            version: Optional[str] = None,
            preview: bool = False,
            force: bool = False,
            accept_all: bool = False,
            git: Optional[str] = None,
            path: Optional[str] = None,
    ) -> None:
        self._version = version
        self._preview = preview
        self._force = force
        self._accept_all = accept_all
        self._git = git
        self._path = path

        self._cursor = Cursor()
        self._bin_dir = None
        self._data_dir = None

    @property
    def bin_dir(self) -> Path:
        if not self._bin_dir:
            self._bin_dir = bin_dir()
        return self._bin_dir

    @property
    def data_dir(self) -> Path:
        if not self._data_dir:
            self._data_dir = data_dir()
        return self._data_dir

    @property
    def version_file(self) -> Path:
        return self.data_dir.joinpath("VERSION")

    def allows_prereleases(self) -> bool:
        return self._preview

    def run(self) -> int:
        if self._git:
            version = self._git
        elif self._path:
            version = self._path
        else:
            try:
                version, current_version = self.get_version()
            except ValueError:
                return 1

        if version is None:
            return 0

        self.display_pre_message()
        self.ensure_directories()

        def _is_self_upgrade_supported(x):
            mx = self.VERSION_REGEX.match(x)

            if mx is None:
                # the version is not semver, perhaps scm or file
                # we assume upgrade is supported
                return True

            vx = (*tuple(int(p) for p in mx.groups()[:3]), mx.group(5))
            return vx >= (1, 1, 7)

        if version and not _is_self_upgrade_supported(version):
            self._write(
                colorize(
                    "warning",
                    f"You are installing {version}. When using the current installer, "
                    "this version does not support updating using the 'self update' "
                    "command. Please use 1.1.7 or later.",
                )
            )
            if not self._accept_all:
                continue_install = input("Do you want to continue? ([y]/n) ") or "y"
                if continue_install.lower() in {"n", "no"}:
                    return 0

        try:
            self.install(version)
        except subprocess.CalledProcessError as e:
            raise PoetryInstallationError(
                return_code=e.returncode, log=e.output.decode()
            ) from e

        self._write("")
        self.display_post_message(version)

        return 0

    def install(self, version):
        """
        Installs Poetry in $POETRY_HOME.
        """
        self._write(
            "Installing {} ({})".format(
                colorize("info", "Poetry"), colorize("info", version)
            )
        )

        with self.make_env(version) as env:
            self.install_poetry(version, env)
            self.make_bin(version, env)
            self.version_file.write_text(version)
            self._install_comment(version, "Done")

            return 0

    def uninstall(self) -> int:
        if not self.data_dir.exists():
            self._write(
                "{} is not currently installed.".format(colorize("info", "Poetry"))
            )

            return 1

        version = None
        if self.version_file.exists():
            version = self.version_file.read_text().strip()

        if version:
            self._write(
                "Removing {} ({})".format(
                    colorize("info", "Poetry"), colorize("b", version)
                )
            )
        else:
            self._write("Removing {}".format(colorize("info", "Poetry")))

        shutil.rmtree(str(self.data_dir))
        for script in ["poetry", "poetry.bat", "poetry.exe"]:
            if self.bin_dir.joinpath(script).exists():
                self.bin_dir.joinpath(script).unlink()

        return 0

    def _install_comment(self, version: str, message: str):
        self._overwrite(
            "Installing {} ({}): {}".format(
                colorize("info", "Poetry"),
                colorize("b", version),
                colorize("comment", message),
            )
        )

    @contextmanager
    def make_env(self, version: str) -> VirtualEnvironment:
        env_path = self.data_dir.joinpath("venv")
        env_path_saved = env_path.with_suffix(".save")

        if env_path.exists():
            self._install_comment(version, "Saving existing environment")
            if env_path_saved.exists():
                shutil.rmtree(env_path_saved)
            shutil.move(env_path, env_path_saved)

        try:
            self._install_comment(version, "Creating environment")
            yield VirtualEnvironment.make(env_path)
        except Exception as e:
            if env_path.exists():
                self._install_comment(
                    version, "An error occurred. Removing partial environment."
                )
                shutil.rmtree(env_path)

            if env_path_saved.exists():
                self._install_comment(
                    version, "Restoring previously saved environment."
                )
                shutil.move(env_path_saved, env_path)

            raise e
        else:
            if env_path_saved.exists():
                shutil.rmtree(env_path_saved, ignore_errors=True)

    def make_bin(self, version: str, env: VirtualEnvironment) -> None:
        self._install_comment(version, "Creating script")
        self.bin_dir.mkdir(parents=True, exist_ok=True)

        script = "poetry.exe" if WINDOWS else "poetry"
        target_script = env.bin_path.joinpath(script)

        if self.bin_dir.joinpath(script).exists():
            self.bin_dir.joinpath(script).unlink()

        try:
            self.bin_dir.joinpath(script).symlink_to(target_script)
        except OSError:
            # This can happen if the user
            # does not have the correct permission on Windows
            shutil.copy(target_script, self.bin_dir.joinpath(script))

    def install_poetry(self, version: str, env: VirtualEnvironment) -> None:
        self._install_comment(version, "Installing Poetry")

        if self._git:
            specification = "git+" + version
        elif self._path:
            specification = version
        else:
            specification = f"poetry=={version}"

        env.pip("install", specification)

    def display_pre_message(self) -> None:
        kwargs = {
            "poetry": colorize("info", "Poetry"),
            "poetry_home_bin": colorize("comment", self.bin_dir),
        }
        self._write(PRE_MESSAGE.format(**kwargs))

    def display_post_message(self, version: str) -> None:
        if WINDOWS:
            return self.display_post_message_windows(version)

        if SHELL == "fish":
            return self.display_post_message_fish(version)

        return self.display_post_message_unix(version)

    def display_post_message_windows(self, version: str) -> None:
        path = self.get_windows_path_var()

        message = POST_MESSAGE_NOT_IN_PATH
        if path and str(self.bin_dir) in path:
            message = POST_MESSAGE

        self._write(
            message.format(
                poetry=colorize("info", "Poetry"),
                version=colorize("b", version),
                poetry_home_bin=colorize("comment", self.bin_dir),
                poetry_executable=colorize("b", self.bin_dir.joinpath("poetry")),
                configure_message=POST_MESSAGE_CONFIGURE_WINDOWS.format(
                    poetry_home_bin=colorize("comment", self.bin_dir)
                ),
                test_command=colorize("b", "poetry --version"),
            )
        )

    def get_windows_path_var(self) -> Optional[str]:
        import winreg

        with winreg.ConnectRegistry(
                None, winreg.HKEY_CURRENT_USER
        ) as root, winreg.OpenKey(root, "Environment", 0, winreg.KEY_ALL_ACCESS) as key:
            path, _ = winreg.QueryValueEx(key, "PATH")

            return path

    def display_post_message_fish(self, version: str) -> None:
        fish_user_paths = subprocess.check_output(
            ["fish", "-c", "echo $fish_user_paths"]
        ).decode("utf-8")

        message = POST_MESSAGE_NOT_IN_PATH
        if fish_user_paths and str(self.bin_dir) in fish_user_paths:
            message = POST_MESSAGE

        self._write(
            message.format(
                poetry=colorize("info", "Poetry"),
                version=colorize("b", version),
                poetry_home_bin=colorize("comment", self.bin_dir),
                poetry_executable=colorize("b", self.bin_dir.joinpath("poetry")),
                configure_message=POST_MESSAGE_CONFIGURE_FISH.format(
                    poetry_home_bin=colorize("comment", self.bin_dir)
                ),
                test_command=colorize("b", "poetry --version"),
            )
        )

    def display_post_message_unix(self, version: str) -> None:
        paths = os.getenv("PATH", "").split(":")

        message = POST_MESSAGE_NOT_IN_PATH
        if paths and str(self.bin_dir) in paths:
            message = POST_MESSAGE

        self._write(
            message.format(
                poetry=colorize("info", "Poetry"),
                version=colorize("b", version),
                poetry_home_bin=colorize("comment", self.bin_dir),
                poetry_executable=colorize("b", self.bin_dir.joinpath("poetry")),
                configure_message=POST_MESSAGE_CONFIGURE_UNIX.format(
                    poetry_home_bin=colorize("comment", self.bin_dir)
                ),
                test_command=colorize("b", "poetry --version"),
            )
        )

    def ensure_directories(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.bin_dir.mkdir(parents=True, exist_ok=True)

    def get_version(self):
        current_version = None
        if self.version_file.exists():
            current_version = self.version_file.read_text().strip()

        self._write(colorize("info", "Retrieving Poetry metadata"))

        metadata = json.loads(self._get(self.METADATA_URL).decode())

        def _compare_versions(x, y):
            mx = self.VERSION_REGEX.match(x)
            my = self.VERSION_REGEX.match(y)

            vx = (*tuple(int(p) for p in mx.groups()[:3]), mx.group(5))
            vy = (*tuple(int(p) for p in my.groups()[:3]), my.group(5))

            if vx < vy:
                return -1
            elif vx > vy:
                return 1

            return 0

        self._write("")
        releases = sorted(
            metadata["releases"].keys(), key=cmp_to_key(_compare_versions)
        )

        if self._version and self._version not in releases:
            msg = f"Version {self._version} does not exist."
            self._write(colorize("error", msg))

            raise ValueError(msg)

        version = self._version
        if not version:
            for release in reversed(releases):
                m = self.VERSION_REGEX.match(release)
                if m.group(5) and not self.allows_prereleases():
                    continue

                version = release

                break

        if current_version == version and not self._force:
            self._write(
                f'The latest version ({colorize("b", version)}) is already installed.'
            )

            return None, current_version

        return version, current_version

    def _write(self, line) -> None:
        sys.stdout.write(line + "\n")

    def _overwrite(self, line) -> None:
        if not is_decorated():
            return self._write(line)

        self._cursor.move_up()
        self._cursor.clear_line()
        self._write(line)

    def _get(self, url):
        request = Request(url, headers={"User-Agent": "Python Poetry"})

        with closing(urlopen(request)) as r:
            return r.read()


def main():
    parser = argparse.ArgumentParser(
        description="Installs the latest (or given) version of poetry"
    )
    parser.add_argument(
        "-p",
        "--preview",
        help="install preview version",
        dest="preview",
        action="store_true",
        default=False,
    )
    parser.add_argument("--version", help="install named version", dest="version")
    parser.add_argument(
        "-f",
        "--force",
        help="install on top of existing version",
        dest="force",
        action="store_true",
        default=False,
    )
    parser.add_argument(
        "-y",
        "--yes",
        help="accept all prompts",
        dest="accept_all",
        action="store_true",
        default=False,
    )
    parser.add_argument(
        "--uninstall",
        help="uninstall poetry",
        dest="uninstall",
        action="store_true",
        default=False,
    )
    parser.add_argument(
        "--path",
        dest="path",
        action="store",
        help=(
            "Install from a given path (file or directory) instead of "
            "fetching the latest version of Poetry available online."
        ),
    )
    parser.add_argument(
        "--git",
        dest="git",
        action="store",
        help=(
            "Install from a git repository instead of fetching the latest version "
            "of Poetry available online."
        ),
    )

    args = parser.parse_args()

    installer = Installer(
        version=args.version or os.getenv("POETRY_VERSION"),
        preview=args.preview or string_to_bool(os.getenv("POETRY_PREVIEW", "0")),
        force=args.force,
        accept_all=args.accept_all
                   or string_to_bool(os.getenv("POETRY_ACCEPT", "0"))
                   or not is_interactive(),
        path=args.path,
        git=args.git,
    )

    if args.uninstall or string_to_bool(os.getenv("POETRY_UNINSTALL", "0")):
        return installer.uninstall()

    try:
        return installer.run()
    except PoetryInstallationError as e:
        installer._write(colorize("error", "Poetry installation failed."))

        if e.log is not None:
            import traceback

            _, path = tempfile.mkstemp(
                suffix=".log",
                prefix="poetry-installer-error-",
                dir=str(Path.cwd()),
                text=True,
            )
            installer._write(colorize("error", f"See {path} for error logs."))
            tb = "".join(traceback.format_tb(e.__traceback__))
            text = f"{e.log}\nTraceback:\n\n{tb}"
            Path(path).write_text(text)

        return e.return_code


if __name__ == "__main__":
    sys.exit(main())