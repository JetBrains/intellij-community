import sys
import traceback

ERROR_WRONG_USAGE = 1
ERROR_NO_PACKAGING_TOOLS = 2
ERROR_EXCEPTION = 3

def exit(retcode):
    major, minor, micro, release, serial = sys.version_info
    version = major * 10 + minor
    if 'java' in sys.platform and version < 25:
        import os
        os._exit(retcode)
    else:
        sys.exit(retcode)

def usage():
    sys.stderr.write('Usage: packaging_tool.py <list|install|uninstall>\n')
    exit(ERROR_WRONG_USAGE)

def error(message, retcode):
    sys.stderr.write('Error: %s\n' % message)
    exit(retcode)

def error_no_pip():
    error("Python package management tool 'pip' not found. <a href=\"installPip\">Install 'pip'</a>.", ERROR_NO_PACKAGING_TOOLS)

def do_list():
    try:
        import pkg_resources
    except ImportError:
        error("Python package management tools not found. <a href=\"installDistribute\">Install 'distribute'</a>.", ERROR_NO_PACKAGING_TOOLS)
    for pkg in pkg_resources.working_set:
        requires = ':'.join([str(x) for x in pkg.requires()])
        print('\t'.join([pkg.project_name, pkg.version, pkg.location, requires]))

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
    directory_name = tempfile.mkdtemp("management")

    import tarfile
    filename = name + ".tar.gz"
    tar = tarfile.open(filename)
    for item in tar:
        tar.extract(item, directory_name)

    print (directory_name)

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
            pkgs = sys.argv[2:]
            retcode = do_install(pkgs)
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
