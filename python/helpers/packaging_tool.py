import sys
import traceback

ERROR_WRONG_USAGE = 1
ERROR_NO_PIP = 2
ERROR_NO_DISTRIBUTE = 3
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
    sys.stderr.write('Usage: packaging_tool.py <list|install|uninstall>\n')
    sys.stderr.flush()
    exit(ERROR_WRONG_USAGE)


def error(message, retcode):
    sys.stderr.write('Error: %s\n' % message)
    sys.stderr.flush()
    exit(retcode)


def error_no_pip():
    error("Python package management tool 'pip' not found", ERROR_NO_PIP)


def do_list():
    try:
        import pkg_resources
    except ImportError:
        error("Python package management tool 'setuptools' or 'distribute' not found", ERROR_NO_DISTRIBUTE)
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


def untarDirectory(name):
    import os
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
        else:
            usage()
    except Exception:
        traceback.print_exc()
        exit(ERROR_EXCEPTION)
    exit(retcode)

if __name__ == '__main__':
    main()
