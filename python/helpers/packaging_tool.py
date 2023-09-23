import sys
import traceback
import getopt
import os

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
    sys.stderr.write('Usage: packaging_tool.py <list|install|uninstall|pyvenv>\n')
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
    if sys.version_info < (3, 12):
        try:
            import pkg_resources
        except ImportError:
            error("Python packaging tool 'setuptools' not found", ERROR_NO_SETUPTOOLS)
        for pkg in pkg_resources.working_set:
            try:
                requirements = pkg.requires()
            except Exception:
                requirements = []
            requires = ':'.join([str(x) for x in requirements])
            sys.stdout.write('\t'.join([pkg.project_name, pkg.version, pkg.location, requires])+chr(10))
    else:
        import importlib.metadata
        for pkg in importlib.metadata.distributions():
            try:
                requirements = [] if (pkg.requires is None) else pkg.requires
            except Exception:
                requirements = []
            requires = ':'.join([str(x) for x in requirements])
            sys.stdout.write('\t'.join([pkg.name, pkg.version, str(pkg._path.parent), requires])+chr(10))
    sys.stdout.flush()


def do_install(pkgs):
    run_pip(['install'] + pkgs)


def do_uninstall(pkgs):
    run_pip(['uninstall', '-y'] + pkgs)


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
        elif cmd == 'install':
            if len(sys.argv) < 2:
                usage()

            pkgs = sys.argv[2:]
            do_install(pkgs)

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
