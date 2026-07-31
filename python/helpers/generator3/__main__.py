import argparse
import json
import logging
import os
import sys

_containing_dir = os.path.dirname(os.path.abspath(__file__))
_helpers_dir = os.path.dirname(_containing_dir)


def _bootstrap_sys_path():
    if _helpers_dir not in sys.path:
        sys.path.insert(0, _helpers_dir)


# The `generator3` package below is only importable once the helpers root is on `sys.path`.
# It must be added here, before those top-level imports run: a wrapper interpreter (e.g. an
# OSGeo4W/QGIS `.bat`) can reset the PYTHONPATH the IDE relies on to make it importable. PY-90847
_bootstrap_sys_path()

from generator3.constants import (  # noqa: E402
    LOGGING_CATEGORIES,
    LOGGING_LEVEL_TRACE,
)
from generator3.util_methods import (  # noqa: E402
    delete,
    timed,
    _enable_segfault_tracebacks,
    _configure_logging,
)


def _cleanup_sys_path():
    return [root for root in sys.path
            if os.path.normpath(root) not in (_containing_dir, _helpers_dir)]


def _configure_multiprocessing():
    required_start_method = os.environ.get('GENERATOR3_MULTIPROCESSING_START_METHOD')
    if required_start_method:
        import multiprocessing
        # Available only since Python 3.4
        multiprocessing.set_start_method(required_start_method)


def parse_args(gen_version):
    parser = argparse.ArgumentParser(
        prog='generator3',
        description='Generates interface skeletons (binary stubs) for binary and '
                    'built-in Python modules.'
    )
    parser.add_argument(
        '-d', metavar='PATH', dest='output_dir', required=True,
        help='Output dir, must be writable. If not given, current dir is used.',
    )
    # TODO using os.pathsep might cause problems with remote interpreters when host and
    #  target OS don't match
    parser.add_argument(
        '-s', metavar='PATH_LIST', dest='roots',
        type=(lambda s: s.split(os.pathsep)), default=[],
        help='List of root directories to scan for binaries separated with `os.pathsep`'
             ' character. These directories will be added in `sys.path`.'
    )
    parser.add_argument(
        '--name-pattern', metavar='PATTERN',
        help='Shell-like glob pattern restricting generation only to modules with '
             'matching qualified names, e.g, "_ast" or "numpy.*".'
    )
    parser.add_argument(
        '--builtins-only', action='store_true',
        help='Limit generation only to the modules in `sys.builtin_module_names`.'
    )
    parser.add_argument(
        '--state-file', metavar='PATH',
        type=argparse.FileType('rb'),
        help='Path to the input ".state.json" file. If "-", the file is passed via '
             'stdin. The resulting ".state.json" will be generated automatically in '
             'the skeletons directory.'
    )
    parser.add_argument(
        '--init-state-file', action='store_true',
        help='Generate a new ".state.json" file in the skeletons directory.'
    )

    # Common flags
    parser.add_argument(
        '-v', dest='verbose', action='store_true',
        help='Be verbose, print lots of debug output to stderr.'
    )

    parser.add_argument('-V', action='version', version=gen_version)

    parser.add_argument("--clean", action='store_true',
                        help="Remove generated directories after run")
    parser.add_argument("--use-worker-process-pool", action='store_true',
                        help="If true use a multiprocessing pool for running generation tasks,"
                             " otherwise runs tasks sequentially.")
    parser.add_argument("--no-cache", action='store_true',
                        help="Disable using cache for generated files")
    parser.add_argument("--extra-tracing", nargs="+",
                        choices=LOGGING_CATEGORIES, default=[])

    parser.add_argument(
        "mod_name", nargs='?', default=None,
        help='Qualified name of a single module to analyze.'
    )
    parser.add_argument(
        "mod_path", nargs='?', default=None,
        help='Path to the specified module if it\'s not builtin.'
    )
    return parser.parse_args()


def main():
    from generator3.core import (
        version,
        GenerationStatus,
        SkeletonGenerator,
    )

    args = parse_args(version())

    if args.roots:
        for p in args.roots:
            if p and p not in sys.path:
                # we need this to make things in additional dirs importable
                sys.path.append(p)
        logging.debug("Altered sys.path: %r", sys.path)

    if args.state_file:
        # We can't completely shut off stdin in case Docker-based interpreter to use
        # json.load() and have to retreat to reading the content line-wise
        if args.state_file.name == '<stdin>':
            state_json = json.loads(sys.stdin.readline())  # utf-8 by default
        else:
            with args.state_file as f:
                state_json = json.loads(f.read().decode(encoding='utf-8'))
    else:
        state_json = None

    logging_config = {"": LOGGING_LEVEL_TRACE if args.verbose else logging.DEBUG}
    for category in args.extra_tracing:
        logging.getLogger("generator3." + category).setLevel(LOGGING_LEVEL_TRACE)

    _configure_logging(logging_config)

    exit_code = 0
    with SkeletonGenerator(
            output_dir=args.output_dir,
            roots=_cleanup_sys_path(),
            state_json=state_json,
            write_state_json=bool(args.init_state_file or args.state_file),
            use_worker_process_pool=args.use_worker_process_pool,
            no_cache=args.no_cache,
    ) as generator:
        try:
            with timed("Generation completed in {elapsed:.2f} ms"):
                if args.mod_name:
                    result = generator.process_module(args.mod_name, args.mod_path)
                    if result == GenerationStatus.FAILED:
                        exit_code = 1
                else:
                    generator.discover_and_process_all_modules(
                        name_pattern=args.name_pattern,
                        builtins_only=args.builtins_only
                    )

        finally:
            if args.clean:
                logging.debug("Removing output and cache directories")
                delete(generator.output_dir)
                if generator.cache_dir:
                    delete(generator.cache_dir)
    sys.exit(exit_code)


if __name__ == "__main__":
    _enable_segfault_tracebacks()
    _configure_multiprocessing()
    main()
