import sys
import traceback

ERROR_WRONG_USAGE = 1
ERROR_EXCEPTION = 4

def usage():
    sys.stderr.write('Usage: conda_packaging_tool.py <listall>\n')
    sys.stderr.flush()
    exit(ERROR_WRONG_USAGE)

def do_list_available_packages():
    try:
        from conda.cli.main_search import common
        index = common.get_index_trap()
    except ImportError:
        from conda.cli.main_search import get_index
        index = get_index()

    for pkg in index.values():
        sys.stdout.write("\t".join([pkg["name"], pkg["version"], ":".join(pkg["depends"])])+chr(10))
        sys.stdout.flush()


def do_list_channels():
    import conda.config as config
    if hasattr(config, "get_channel_urls"):
        channels = config.get_channel_urls()
    else:
        channels = config.context.channels
    for channel in channels:
        if channel != 'defaults':
            sys.stdout.write(channel+chr(10))
            sys.stdout.flush()


def main():
    retcode = 0
    try:
        if len(sys.argv) < 2:
            usage()
        cmd = sys.argv[1]
        if cmd == 'listall':
            if len(sys.argv) != 2:
                usage()
                return
            do_list_available_packages()
        elif cmd == 'channels':
            if len(sys.argv) != 2:
                usage()
                return
            do_list_channels()
        else:
            usage()
    except Exception:
        traceback.print_exc()
        exit(ERROR_EXCEPTION)
    exit(retcode)

if __name__ == '__main__':
    main()
