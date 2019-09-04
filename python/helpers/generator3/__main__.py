import atexit
import os
import sys

_containing_dir = os.path.dirname(os.path.abspath(__file__))
_helpers_dir = os.path.dirname(_containing_dir)


def _cleanup_sys_path():
    target_roots = []
    # filter out all roots that equal or under "helpers" directory itself
    for root in sys.path:
        try:
            if not os.path.relpath(root, _helpers_dir).startswith(os.path.pardir):
                continue
        except ValueError:
            pass
        target_roots.append(root)
    return target_roots


def _bootstrap_sys_path():
    sys.path.insert(0, _helpers_dir)


def get_help_text():
    return (
        # 01234567890123456789012345678901234567890123456789012345678901234567890123456789
        'Generates interface skeletons for python modules.' '\n'
        'Usage: ' '\n'
        '  generator [options] [module_name [file_name]]' '\n'
        '  generator [options] -L ' '\n'
        'module_name is fully qualified, and file_name is where the module is defined.' '\n'
        'E.g. foo.bar /usr/lib/python/foo_bar.so' '\n'
        'For built-in modules file_name is not provided.' '\n'
        'Output files will be named as modules plus ".py" suffix.' '\n'
        'Normally every name processed will be printed and stdout flushed.' '\n'
        'directory_list is one string separated by OS-specific path separtors.' '\n'
        '\n'
        'Options are:' '\n'
        ' -h -- prints this help message.' '\n'
        ' -d dir -- output dir, must be writable. If not given, current dir is used.' '\n'
        ' -q -- quiet, do not print anything on stdout. Errors still go to stderr.' '\n'
        ' -x -- die on exceptions with a stacktrace; only for debugging.' '\n'
        ' -v -- be verbose, print lots of debug output to stderr' '\n'
        ' -c modules -- import CLR assemblies with specified names' '\n'
        ' -p -- run CLR profiler ' '\n'
        ' -s path_list -- add paths to sys.path before run; path_list lists directories' '\n'
        '    separated by path separator char, e.g. "c:\\foo;d:\\bar;c:\\with space"' '\n'
        ' -i -- read module_name, file_name and list of imported CLR assemblies from stdin line-by-line' '\n'
        ' -S -- lists all python sources found in sys.path and in directories in directory_list\n'
        ' -z archive_name -- zip files to archive_name. Accepts files to be archived from stdin in format <filepath> <name in archive>\n'
        '--name-pattern pattern -- shell-like glob pattern restricting generation only to binaries with matching qualified names\n'
    )


def main():
    import generator3.core
    import generator3.extra
    from generator3.clr_tools import get_namespace_by_name
    from generator3.constants import Timer
    from generator3.core import version, process_one_with_results_reporting, GenerationStatus, process_all
    from generator3.util_methods import set_verbose, say, report, note, print_profile

    try:
        # Get traces after segmentation faults
        import faulthandler

        faulthandler.enable()
    except ImportError:
        pass

    from getopt import getopt

    helptext = get_help_text()
    opts, args = getopt(sys.argv[1:], "d:hbqxvc:ps:LiSzuV", longopts=['name-pattern='])
    opts = dict(opts)

    generator3.core.quiet = '-q' in opts
    set_verbose('-v' in opts)
    subdir = opts.get('-d', '')

    if not opts or '-h' in opts:
        say(helptext)
        sys.exit(0)

    if "-x" in opts:
        debug_mode = True

    # patch sys.path?
    extra_path = opts.get('-s', None)
    if extra_path:
        source_dirs = extra_path.split(os.path.pathsep)
        for p in source_dirs:
            if p and p not in sys.path:
                sys.path.append(p)  # we need this to make things in additional dirs importable
        note("Altered sys.path: %r", sys.path)

    target_roots = _cleanup_sys_path()

    if "-S" in opts:
        if len(args) > 0:
            report("Expected no args with -S, got %d args", len(args))
            sys.exit(1)
        say(version())
        generator3.extra.list_sources(target_roots)
        sys.exit(0)

    if "-z" in opts:
        if len(args) != 1:
            report("Expected 1 arg with -z, got %d args", len(args))
            sys.exit(1)
        generator3.extra.zip_sources(args[0])
        sys.exit(0)

    if "-u" in opts:
        if len(args) != 1:
            report("Expected 1 arg with -u, got %d args", len(args))
            sys.exit(1)
        generator3.extra.zip_stdlib(target_roots, args[0])
        sys.exit(0)

    if "-V" in opts:
        say(version())
        sys.exit(0)

    # build skeleton(s)

    timer = Timer()
    # determine names
    if '-i' in opts:
        if args:
            report("No names should be specified with -i")
            sys.exit(1)
        name = sys.stdin.readline().strip()

        mod_file_name = sys.stdin.readline().strip()
        if not mod_file_name:
            mod_file_name = None

        refs = sys.stdin.readline().strip()
    elif len(args) > 2:
        report("Only module_name or module_name and file_name should be specified; got %d args", len(args))
        sys.exit(1)
    elif not args:
        process_all(target_roots, subdir, name_pattern=opts.get('--name-pattern'))
        sys.exit()
    else:
        name = args[0]

        if len(args) == 2:
            mod_file_name = args[1]
        else:
            mod_file_name = None

        refs = opts.get('-c', '')

    if sys.platform == 'cli':
        # noinspection PyUnresolvedReferences
        import clr

        if refs:
            for ref in refs.split(';'): clr.AddReferenceByPartialName(ref)

        if '-p' in opts:
            atexit.register(print_profile)

        # We take module name from import statement
        name = get_namespace_by_name(name)

    if process_one_with_results_reporting(name, mod_file_name, False, subdir) == GenerationStatus.FAILED:
        sys.exit(1)

    say("Generation completed in %d ms", timer.elapsed())


if __name__ == "__main__":
    _bootstrap_sys_path()
    main()
