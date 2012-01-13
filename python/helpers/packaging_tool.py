import sys
import logging
import traceback

ERROR_WRONG_USAGE = 1
ERROR_NO_PACKAGING_TOOLS = 2
ERROR_EXCEPTION = 3

def usage():
    logging.error('Usage: packaging_tool.py <list|install|uninstall>')
    sys.exit(ERROR_WRONG_USAGE)

def error(message, retcode):
    logging.error('Error: %s' % message)
    sys.exit(retcode)

def do_list():
    try:
        import pkg_resources
    except ImportError:
        error("Python package management tools not found. Please install 'setuptools' or 'distribute'", ERROR_NO_PACKAGING_TOOLS)
    for pkg in pkg_resources.working_set:
        print('\t'.join([pkg.project_name, pkg.version, pkg.location]))

def do_uninstall(pkg):
    try:
        import pip
    except ImportError:
        error("Python package management tool 'pip' not found. Please install 'pip'", ERROR_NO_PACKAGING_TOOLS)
    return pip.main(['uninstall', '-y', pkg])

def main():
    retcode = 0
    try:
        logging.basicConfig(format='%(message)s')
        if len(sys.argv) < 2:
            usage()
        cmd = sys.argv[1]
        if cmd == 'list':
            if len(sys.argv) != 2:
                usage()
            do_list()
        elif cmd == 'install':
            if len(sys.argv) != 3:
                usage()
            pkg = sys.argv[2]
            retcode = do_install(pkg)
        elif cmd == 'uninstall':
            if len(sys.argv) != 3:
                usage()
            pkg = sys.argv[2]
            retcode = do_uninstall(pkg)
        else:
            usage()
    except Exception:
        traceback.print_exc()
        sys.exit(ERROR_EXCEPTION)
    sys.exit(retcode)

if __name__ == '__main__':
    main()
