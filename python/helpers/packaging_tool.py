import getopt
import os
import sys
import traceback

ERROR_WRONG_USAGE = 1
ERROR_NO_PIP = 2
ERROR_NO_SETUPTOOLS = 3
ERROR_EXCEPTION = 4

os.putenv("PIP_REQUIRE_VIRTUALENV", "false")

def exit(retcode):
    major, minor, micro, release, serial = sys.version_info
    version = major * 10 + minor
    if version < 25:
        import os
        os._exit(retcode)
    else:
        sys.exit(retcode)


def usage():
    sys.stderr.write('Usage: packaging_tool.py <list|list_outdated|install|install_bundled_wheels|uninstall|pyvenv>\n')
    sys.stderr.flush()
    exit(ERROR_WRONG_USAGE)


def error(message, retcode):
    sys.stderr.write('Error: %s\n' % message)
    sys.stderr.flush()
    exit(retcode)


def error_no_pip():
    type, value, tb = sys.exc_info()
    if tb is not None and tb.tb_next is None:
        error("Python packaging tool 'pip' not found", ERROR_NO_PIP)
    else:
        error(traceback.format_exc(), ERROR_EXCEPTION)


def do_list():
    args = ['list', "--format=json", "--disable-pip-version-check"]
    bundled_pip = bundled_pip_if_missing()
    if bundled_pip is not None:
        # pip comes from its wheel. The wheel holds the metadata of pip, so pip lists only the paths of the environment.
        env_paths = [path for path in sys.path if path and os.path.isdir(path)]
        sys.path.insert(0, bundled_pip)
        for path in env_paths:
            args += ['--path', path]
    run_pip(args)


def bundled_pip_if_missing():
    """The path of the bundled pip wheel when the environment has no pip, else None."""
    try:
        import pip
        return None
    except ImportError:
        pass
    helpers_dir = os.path.dirname(os.path.abspath(__file__))
    wheels = [name for name in os.listdir(helpers_dir) if name.startswith('pip-') and name.endswith('.whl')]
    return os.path.join(helpers_dir, wheels[0]) if wheels else None


def do_list_outdated():
    run_pip(['list', "--outdated", "--format=json", "--disable-pip-version-check"])

def do_install(pkgs):
    run_pip(['install'] + pkgs)


def do_install_bundled_wheels(pip_wheel, wheels):
    # The wheels are in the helpers directory, next to this script. It is also true on a remote machine,
    # where the helpers directory is a copy. pip is imported from its wheel, so it works when pip is missing.
    helpers_dir = os.path.dirname(os.path.abspath(__file__))
    sys.path.insert(0, os.path.join(helpers_dir, pip_wheel))
    run_pip(['install', '--no-index'] + [os.path.join(helpers_dir, wheel) for wheel in wheels])


def do_uninstall(pkgs):
    run_pip(['uninstall', '-y', "--disable-pip-version-check"] + pkgs)


def run_pip(args):
    import runpy
    sys.argv[1:] = args
    # pip.__main__ has been around since 2010 but support for executing it automatically
    # was added in runpy.run_module only in Python 2.7/3.1
    module_name = 'pip.__main__' if sys.version_info < (2, 7) else 'pip'
    try:
        runpy.run_module(module_name, run_name='__main__', alter_sys=True)
    except ImportError:
        error_no_pip()


def do_pyvenv(args):
    import runpy
    try:
        import ensurepip
        sys.argv[1:] = args
    except ImportError:
        sys.argv[1:] = ['--without-pip'] + args

    try:
        runpy.run_module('venv', run_name='__main__', alter_sys=True)
    except ImportError:
        error("Standard Python 'venv' module not found", ERROR_EXCEPTION)


def main():
    try:
        # As a workaround for #885 in setuptools, don't expose other helpers
        # in sys.path so as not no confuse it with possible combination of
        # namespace/ordinary packages
        sys.path.remove(os.path.dirname(__file__))
    except ValueError:
        pass

    try:
        if len(sys.argv) < 2:
            usage()
        cmd = sys.argv[1]
        if cmd == 'list':
            if len(sys.argv) != 2:
                usage()
            do_list()
        elif cmd == 'list_outdated':
            if len(sys.argv) != 2:
                usage()
            do_list_outdated()
        elif cmd == 'install':
            if len(sys.argv) < 2:
                usage()

            pkgs = sys.argv[2:]
            do_install(pkgs)

        elif cmd == 'install_bundled_wheels':
            if len(sys.argv) < 4:
                usage()
            do_install_bundled_wheels(sys.argv[2], sys.argv[3:])

        elif cmd == 'uninstall':
            if len(sys.argv) < 2:
                usage()
            pkgs = sys.argv[2:]
            do_uninstall(pkgs)
        elif cmd == 'pyvenv':
            opts, args = getopt.getopt(sys.argv[2:], '', ['system-site-packages'])
            if len(args) != 1:
                usage()
            do_pyvenv(sys.argv[2:])
        else:
            usage()
    except Exception:
        traceback.print_exc()
        exit(ERROR_EXCEPTION)


if __name__ == '__main__':
    main()
