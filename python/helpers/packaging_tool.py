import sys
import traceback
import getopt
import os

ERROR_WRONG_USAGE = 1
ERROR_NO_PIP = 2
ERROR_NO_SETUPTOOLS = 3
ERROR_EXCEPTION = 4

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
    tb = sys.exc_traceback
    if tb is not None and tb.tb_next is None:
        error("Python package management tool 'pip' not found", ERROR_NO_PIP)
    else:
        error(traceback.format_exc(), ERROR_EXCEPTION)


def do_list():
    try:
        import pkg_resources
    except ImportError:
        error("Python package management tool 'setuptools' or 'distribute' not found", ERROR_NO_SETUPTOOLS)
    for pkg in pkg_resources.working_set:
        requires = ':'.join([str(x) for x in pkg.requires()])
        sys.stdout.write('\t'.join([pkg.project_name, pkg.version, pkg.location, requires])+chr(10))
    sys.stdout.flush()


def do_install(pkgs):
    try:
        import pip
    except ImportError:
        error_no_pip()
    return pip.main(['install'] + pkgs)


def do_uninstall(pkgs):
    try:
        import pip
    except ImportError:
        error_no_pip()
    return pip.main(['uninstall', '-y'] + pkgs)


def do_pyvenv(path, system_site_packages):
    try:
        import venv
    except ImportError:
        error("Standard Python 'venv' module not found", ERROR_EXCEPTION)
    venv.create(path, system_site_packages=system_site_packages)


def untarDirectory(name):
    import tempfile

    directory_name = tempfile.mkdtemp("pycharm-management")

    import tarfile

    filename = name + ".tar.gz"
    tar = tarfile.open(filename)
    for item in tar:
        tar.extract(item, directory_name)

    sys.stdout.write(directory_name+chr(10))
    sys.stdout.flush()
    return 0

def mkdtemp_ifneeded():
    try:
        ind = sys.argv.index('--build-dir')
        if not os.path.exists(sys.argv[ind + 1]):
            import tempfile

            sys.argv[ind + 1] = tempfile.mkdtemp('pycharm-packaging')
            return sys.argv[ind + 1]
    except:
        pass

    return None


def main():
    retcode = 0
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

            rmdir = mkdtemp_ifneeded()

            pkgs = sys.argv[2:]
            retcode = do_install(pkgs)

            if rmdir is not None:
                import shutil
                shutil.rmtree(rmdir)


        elif cmd == 'untar':
            if len(sys.argv) < 2:
                usage()
            name = sys.argv[2]
            retcode = untarDirectory(name)
        elif cmd == 'uninstall':
            if len(sys.argv) < 2:
                usage()
            pkgs = sys.argv[2:]
            retcode = do_uninstall(pkgs)
        elif cmd == 'pyvenv':
            opts, args = getopt.getopt(sys.argv[2:], '', ['system-site-packages'])
            if len(args) != 1:
                usage()
            path = args[0]
            system_site_packages = False
            for opt, arg in opts:
                if opt == '--system-site-packages':
                    system_site_packages = True
            do_pyvenv(path, system_site_packages)
        else:
            usage()
    except Exception:
        traceback.print_exc()
        exit(ERROR_EXCEPTION)
    exit(retcode)

if __name__ == '__main__':
    main()
