import argparse
import atexit
import json
import logging
import os
import sys

_containing_dir = os.path.dirname(os.path.abspath(__file__))
_helpers_dir = os.path.dirname(_containing_dir)


def _cleanup_sys_path():
    return [root for root in sys.path if os.path.normpath(root) not in (_containing_dir, _helpers_dir)]


def _bootstrap_sys_path():
    sys.path.insert(0, _helpers_dir)


def _setup_logging():
    logging.addLevelName(logging.DEBUG - 1, 'TRACE')

    class JsonFormatter(logging.Formatter):
        def format(self, record):
            s = super(JsonFormatter, self).format(record)
            return json.dumps({
                'type': 'log',
                'level': record.levelname.lower(),
                'message': s
            })

    root = logging.getLogger()
    root.setLevel(logging.DEBUG)
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(JsonFormatter())
    root.addHandler(handler)


def _enable_segfault_tracebacks():
    try:
        import faulthandler

        faulthandler.enable()
    except ImportError:
        pass


def parse_args(gen_version):
    parser = argparse.ArgumentParser(prog='generator3',
                                     description='Generates interface skeletons (binary stubs) for binary and '
                                                 'built-in Python modules.')
    parser.add_argument('-d', metavar='PATH', dest='output_dir',
                        help='Output dir, must be writable. If not given, current dir is used.')
    # TODO using os.pathsep might cause problems with remote interpreters when host and target OS don't match
    parser.add_argument('-s', metavar='PATH_LIST', dest='roots', type=(lambda s: s.split(os.pathsep)), default=[],
                        help='List of root directories to scan for binaries separated with `os.pathsep` character. '
                             'These directories will be added in `sys.path`.')
    parser.add_argument('--name-pattern', metavar='PATTERN',
                        help='Shell-like glob pattern restricting generation only to modules with matching qualified '
                             'names, e.g, "_ast" or "numpy.*".')
    parser.add_argument('--builtins-only', action='store_true',
                        help='Limit generation only to the modules in `sys.builtin_module_names`.')
    parser.add_argument('--state-file-policy', metavar='TYPE', choices=('readwrite', 'write'),
                        help='Controls the behavior regarding ".state.json" files: '
                             '"readwrite" means that the initial state will be read from stdin '
                             'and the resulting one will written in the output directory '
                             'together with skeletons, with "write" it will only be written.')

    # Common flags
    # TODO evaluate these flags, some of them seem redundant now with proper logging
    parser.add_argument('-q', dest='quiet', action='store_true',
                        help='Be quiet, do not print anything on stdout. Errors still go to stderr.')
    parser.add_argument('-v', dest='verbose', action='store_true',
                        help='Be verbose, print lots of debug output to stderr.')

    parser.add_argument('-V', action='version', version=gen_version)

    extra_modes = parser.add_argument_group('extra modes')
    extra_modes.add_argument('-S', dest='list_sources_mode', action='store_true',
                             help='Lists all python sources found in `sys.path` and directories specified with -s.')
    extra_modes.add_argument('-z', dest='zip_sources_archive', metavar='ARCHIVE',
                             help='Zip files to specified archive. Accepts files to be archived from stdin in '
                                  'format: <filepath> <name in archive>.')
    extra_modes.add_argument('-u', dest='zip_roots_archive', metavar='ARCHIVE',
                             help='Zip all source files from `sys.path` and provided roots in the specified archive.')

    clr_specific = parser.add_argument_group('CLR specific options')
    clr_specific.add_argument('-c', dest='clr_assemblies', metavar='MODULES', type=(lambda s: s.split(';')), default=[],
                              help='Semicolon separated list of CLR assemblies to be imported.')
    clr_specific.add_argument('-p', dest='run_clr_profiler', action='store_true',
                              help='Run CLR profiler.')

    parser.add_argument("mod_name", nargs='?', help='Qualified name of a single module to analyze.', default=None)
    parser.add_argument("mod_path", nargs='?', help='Path to the specified module if it\'s not builtin.', default=None)
    return parser.parse_args()


def main():
    import generator3.core
    import generator3.extra
    from generator3.clr_tools import get_namespace_by_name
    from generator3.constants import Timer
    from generator3.core import version, GenerationStatus, SkeletonGenerator
    from generator3.util_methods import set_verbose, say, note, print_profile

    args = parse_args(version())

    generator3.core.quiet = args.quiet
    set_verbose(args.verbose)

    if args.roots:
        for p in args.roots:
            if p and p not in sys.path:
                sys.path.append(p)  # we need this to make things in additional dirs importable
        note("Altered sys.path: %r", sys.path)

    if args.state_file_policy == 'readwrite':
        # We can't completely shut off stdin in case Docker-based interpreter to use json.load()
        # and have to retreat to reading the content line-wise
        state_json = json.loads(sys.stdin.readline(), encoding='utf-8')
    else:
        state_json = None

    target_roots = _cleanup_sys_path()

    if args.list_sources_mode:
        say(version())
        generator3.extra.list_sources(target_roots)
        sys.exit(0)

    if args.zip_sources_archive:
        generator3.extra.zip_sources(args.zip_sources_archive)
        sys.exit(0)

    if args.zip_roots_archive:
        generator3.extra.zip_stdlib(target_roots, args.zip_roots_archive)
        sys.exit(0)

    generator = SkeletonGenerator(output_dir=args.output_dir,
                                  roots=target_roots,
                                  state_json=state_json,
                                  write_state_json=args.state_file_policy is not None)

    timer = Timer()
    if not args.mod_name:
        generator.discover_and_process_all_modules(name_pattern=args.name_pattern, builtins_only=args.builtins_only)
        sys.exit(0)

    if sys.platform == 'cli':
        # noinspection PyUnresolvedReferences
        import clr

        for ref in args.clr_assemblies:
            clr.AddReferenceByPartialName(ref)

        if args.run_clr_profiler:
            atexit.register(print_profile)

        # We take module name from import statement
        args.mod_name = get_namespace_by_name(args.mod_name)

    if generator.process_module(args.mod_name, args.mod_path) == GenerationStatus.FAILED:
        sys.exit(1)

    say("Generation completed in %d ms", timer.elapsed())


if __name__ == "__main__":
    _bootstrap_sys_path()
    _setup_logging()
    _enable_segfault_tracebacks()
    main()
