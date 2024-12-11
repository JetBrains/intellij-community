#!/usr/bin/env python3
r"""
This script will install Package and its dependencies in an isolated fashion.

It will perform the following steps:
    * Create a new virtual environment using the built-in venv module, or the virtualenv zipapp if venv is unavailable.
      This will be created at a platform-specific path:
        - `~/Library/Application Support/package_name` on macOS
        - `$XDG_DATA_HOME/package_name` on Linux/Unix (`$XDG_DATA_HOME` is `~/.local/share` if unset)
        - `%APPDATA%\package_name` on Windows
    * Update pip inside the virtual environment to avoid bugs in older versions.
    * Install the latest (or a given) version of package inside this virtual environment using pip.
    * Install a script into a platform-specific path:
        - `~/.local/bin` on Unix
        - `%APPDATA%\Python\Scripts` on Windows
    * Upon failure, write an error log to `package-installer-error-<hash>.log and restore any previous environment.

This script performs minimal magic, and should be relatively stable. However, it is optimized for interactive developer
use and trivial pipelines. If you are considering using this script in production, you should consider manually-managed
installs, or use of pipx as alternatives to executing arbitrary, unversioned code from the internet. If you prefer this
script to alternatives, consider maintaining a local copy as part of your infrastructure.
"""  # noqa: E501
import sys

# Eager version check so we fail nicely before possible syntax errors
if sys.version_info < (3, 6):  # noqa: UP036
    sys.stdout.write("Installer requires Python 3.6 or newer to run!\n")
    sys.exit(1)

import argparse
import os
import shutil
import subprocess
import sysconfig
import tempfile

from contextlib import contextmanager
from pathlib import Path
from typing import Optional
from urllib.request import Request
from urllib.request import urlopen
from enum import Enum

SHELL = os.getenv("SHELL", "")
WINDOWS = sys.platform.startswith("win") or (sys.platform == "cli" and os.name == "nt")
MINGW = sysconfig.get_platform().startswith("mingw")
MACOS = sys.platform == "darwin"

class PipRunCommand(Enum):
    PIP = ("-m", "pip")
    WHL = (f"pip-24.3.1-py2.py3-none-any.whl{os.sep}pip", )

def data_dir(package_dir_name) -> Path:
    if WINDOWS:
        base_dir = Path(_get_win_folder("CSIDL_APPDATA"))
    elif MACOS:
        base_dir = Path("~/Library/Application Support").expanduser()
    else:
        base_dir = Path(os.getenv("XDG_DATA_HOME", "~/.local/share")).expanduser()

    base_dir = base_dir.resolve()
    return base_dir / package_dir_name

def bin_dir() -> Path:
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

PRE_MESSAGE = """# Welcome to {package}!

This will download and install the latest version of {package},
a dependency and package manager for Python.

It will add the `{package}` command to {package}'s bin directory, located at:

{package_home_bin}

You can uninstall at any time by executing this script with the --uninstall option,
and these changes will be reverted.
"""

POST_MESSAGE = """{package} ({version}) is installed now. Great!

You can test that everything is set up by executing:

`{test_command}`
"""

POST_MESSAGE_CONFIGURE_FISH = """set -U fish_user_paths {package_home_bin} $fish_user_paths"""

class PackageInstallationError(RuntimeError):
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

            with tempfile.TemporaryDirectory(prefix="package-installer") as temp_dir:
                virtualenv_pyz = Path(temp_dir) / "virtualenv.pyz"
                request = Request(
                    virtualenv_bootstrap_url, headers={"User-Agent": "Python Package Installer"}
                )
                virtualenv_pyz.write_bytes(urlopen(request).read())
                cls.run(
                    sys.executable, virtualenv_pyz, "--clear", "--always-copy", target
                )

        # We add a special file so that package can detect
        # its own virtual environment
        target.joinpath("package_env").touch()

        env = cls(target)

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
            raise PackageInstallationError(
                return_code=completed_process.returncode,
                log=completed_process.stdout,
            )
        return completed_process

    def python(self, *args, **kwargs) -> subprocess.CompletedProcess:
        return self.run(self._python, *args, **kwargs)

    def pip(self, pip_command, *args, **kwargs) -> subprocess.CompletedProcess:
        return self.python(*pip_command, *args, **kwargs)

    def find_pip_command(self):
        try:
            self.pip(PipRunCommand.PIP.value, "--version")
            return PipRunCommand.PIP
        except:
            return PipRunCommand.WHL

class Installer:
    def __init__(
            self,
            name,
            target_version: Optional[str] = None,
            path: Optional[str] = None,
    ) -> None:
        self._name = name
        self._version = target_version
        self._path = path
        self._bin_dir = None
        self._data_dir = None

    @property
    def bin_dir(self) -> Path:
        if self._path is not None:
            return Path(self._path).resolve()
        if not self._bin_dir:
            self._bin_dir = bin_dir()
        return self._bin_dir

    @property
    def data_dir(self) -> Path:
        if not self._data_dir:
            self._data_dir = data_dir(self._name)
        return self._data_dir

    def run(self):
        install_version = self._version

        self.display_pre_message()
        self.ensure_directories()

        try:
            self.install(install_version)
        except subprocess.CalledProcessError as e:
            raise PackageInstallationError(
                return_code=e.returncode, log=e.output.decode()
            ) from e

        self._write("")
        self.display_post_message_and_add_to_path(install_version)

        execFile = "{package}.exe".format(package=self._name) if WINDOWS else "{package}".format(package=self._name)
        self._write(str(self.bin_dir.joinpath(execFile)))

        return 0

    def install(self, version):
        """
        Installs Package.
        """
        self._write(
            "Installing {} ({})".format(self._name,
                                        version if version is not None else "latest")
        )

        with self.make_env(version) as env:
            self.install_package(version, env)
            self.make_bin(version, env)
            self._install_comment(version, "Done")

            return 0

    def _install_comment(self, version: str, message: str):
        self._write(
            "Installing {} ({}): {}".format(
                self._name,
                version if version is not None else "latest",
                message
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

        script = "{package}.exe".format(package=self._name) if WINDOWS else "{package}".format(package=self._name)
        target_script = env.bin_path.joinpath(script)

        if self.bin_dir.joinpath(script).exists():
            self.bin_dir.joinpath(script).unlink()

        try:
            self.bin_dir.joinpath(script).symlink_to(target_script)
        except OSError:
            # This can happen if the user
            # does not have the correct permission on Windows
            shutil.copy(target_script, self.bin_dir.joinpath(script))

    def install_package(self, version: str, env: VirtualEnvironment) -> None:
        self._install_comment(version, "Installing {package}".format(package=self._name))

        if version is not None:
            specification = "{package}=={version}".format(package=self._name, version=version)
        else:
            specification = "{package}".format(package=self._name)

        env.pip(env.find_pip_command().value, "install", specification)

    def display_pre_message(self) -> None:
        kwargs = {
            "package": self._name,
            "package_home_bin": self.bin_dir,
        }
        self._write(PRE_MESSAGE.format(**kwargs))

    def display_post_message_and_add_to_path(self, version: str) -> None:
        if version is None:
            version = "latest"

        try:
            if WINDOWS:
                return self.display_post_message_windows_and_add_to_path(version)

            if SHELL == "fish":
                return self.display_post_message_fish_and_add_to_path(version)

            return self.display_post_message_unix(version)
        except:
            pass

    def display_post_message_windows_and_add_to_path(self, version: str) -> None:
        path = self.get_windows_path_var()

        if not path or str(self.bin_dir) not in path:
            current_path = os.environ.get('PATH', '')
            new_path = "{current_path};{new_path}".format(current_path=current_path, new_path=self.bin_dir)
            os.environ['PATH'] = new_path
            subprocess.run("setx PATH {new_path}".format(new_path=new_path), shell=True)

        self._write(
            POST_MESSAGE.format(
                package=self._name,
                version=version,
                test_command="{package} --version".format(package=self._name),
            )
        )

    def get_windows_path_var(self) -> Optional[str]:
        import winreg

        with winreg.ConnectRegistry(
                None, winreg.HKEY_CURRENT_USER
        ) as root, winreg.OpenKey(root, "Environment", 0, winreg.KEY_ALL_ACCESS) as key:
            path, _ = winreg.QueryValueEx(key, "PATH")

            return path

    def display_post_message_fish_and_add_to_path(self, version: str) -> None:
        fish_user_paths = subprocess.check_output(
            ["fish", "-c", "echo $fish_user_paths"]
        ).decode("utf-8")

        if not fish_user_paths or str(self.bin_dir) not in fish_user_paths:
            command = POST_MESSAGE_CONFIGURE_FISH.format(
                package_home_bin=self.bin_dir
            )
            os.system(command)

        self._write(
            POST_MESSAGE.format(
                package=self._name,
                version=version,
                test_command="{package} --version".format(package=self._name),
            )
        )

    def display_post_message_unix(self, version: str) -> None:
        paths = os.getenv("PATH", "").split(":")

        if not paths or str(self.bin_dir) not in paths:
            home = os.path.expanduser("~")
            if SHELL and "zsh" in SHELL:
                config_file = os.path.join(home, ".zshrc")
            else:
                config_file = os.path.join(home, ".bashrc")

            if not os.path.exists(config_file):
                with open(config_file, "w") as file:
                    file.write("#Created config file\n")

            with open(config_file, "a") as file:
                file.write("\nexport PATH=$PATH:{new_path}\n".format(new_path=self.bin_dir))

        self._write(
            POST_MESSAGE.format(
                package=self._name,
                version=version,
                test_command="{package} --version".format(package=self._name),
            )
        )

    def ensure_directories(self) -> None:
        self.data_dir.mkdir(parents=True, exist_ok=True)
        self.bin_dir.mkdir(parents=True, exist_ok=True)

    def _write(self, line) -> None:
        sys.stdout.write(line + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="Installs the latest (or given) version of package"
    )
    parser.add_argument("-n", "--name", required=True)
    parser.add_argument("-v", "--version", help="install named version", dest="version")
    parser.add_argument("-p", "--path")
    args = parser.parse_args()

    installer = Installer(
        name=args.name,
        target_version=args.version,
        path=args.path,
    )

    try:
        return installer.run()
    except PackageInstallationError as e:
        installer._write("{package} installation failed.".format(package=args.name))

        if e.log is not None:
            import traceback

            _, path = tempfile.mkstemp(
                suffix=".log",
                prefix="package-installer-error-",
                dir=str(Path.cwd()),
                text=True,
            )
            installer._write(f"See {path} for error logs.")
            tb = "".join(traceback.format_tb(e.__traceback__))
            text = f"{e.log}\nTraceback:\n\n{tb}"
            Path(path).write_text(text)

        return e.return_code


if __name__ == "__main__":
    sys.exit(main())
