# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Command-line support for coverage.py."""

from __future__ import print_function

import glob
import optparse
import os.path
import shlex
import sys
import textwrap
import traceback

import coverage
from coverage import Coverage
from coverage import env
from coverage.collector import CTracer
from coverage.data import line_counts
from coverage.debug import info_formatter, info_header, short_stack
from coverage.execfile import PyRunner
from coverage.misc import BaseCoverageException, ExceptionDuringRun, NoSource, output_encoding
from coverage.results import should_fail_under


class Opts(object):
    """A namespace class for individual options we'll build parsers from."""

    append = optparse.make_option(
        '-a', '--append', action='store_true',
        help="Append coverage data to .coverage, otherwise it starts clean each time.",
    )
    keep = optparse.make_option(
        '', '--keep', action='store_true',
        help="Keep original coverage files, otherwise they are deleted.",
    )
    branch = optparse.make_option(
        '', '--branch', action='store_true',
        help="Measure branch coverage in addition to statement coverage.",
    )
    CONCURRENCY_CHOICES = [
        "thread", "gevent", "greenlet", "eventlet", "multiprocessing",
    ]
    concurrency = optparse.make_option(
        '', '--concurrency', action='store', metavar="LIB",
        choices=CONCURRENCY_CHOICES,
        help=(
            "Properly measure code using a concurrency library. "
            "Valid values are: %s."
        ) % ", ".join(CONCURRENCY_CHOICES),
    )
    context = optparse.make_option(
        '', '--context', action='store', metavar="LABEL",
        help="The context label to record for this coverage run.",
    )
    debug = optparse.make_option(
        '', '--debug', action='store', metavar="OPTS",
        help="Debug options, separated by commas. [env: COVERAGE_DEBUG]",
    )
    directory = optparse.make_option(
        '-d', '--directory', action='store', metavar="DIR",
        help="Write the output files to DIR.",
    )
    fail_under = optparse.make_option(
        '', '--fail-under', action='store', metavar="MIN", type="float",
        help="Exit with a status of 2 if the total coverage is less than MIN.",
    )
    help = optparse.make_option(
        '-h', '--help', action='store_true',
        help="Get help on this command.",
    )
    ignore_errors = optparse.make_option(
        '-i', '--ignore-errors', action='store_true',
        help="Ignore errors while reading source files.",
    )
    include = optparse.make_option(
        '', '--include', action='store',
        metavar="PAT1,PAT2,...",
        help=(
            "Include only files whose paths match one of these patterns. "
            "Accepts shell-style wildcards, which must be quoted."
        ),
    )
    pylib = optparse.make_option(
        '-L', '--pylib', action='store_true',
        help=(
            "Measure coverage even inside the Python installed library, "
            "which isn't done by default."
        ),
    )
    sort = optparse.make_option(
        '--sort', action='store', metavar='COLUMN',
        help="Sort the report by the named column: name, stmts, miss, branch, brpart, or cover. "
             "Default is name."
    )
    show_missing = optparse.make_option(
        '-m', '--show-missing', action='store_true',
        help="Show line numbers of statements in each module that weren't executed.",
    )
    skip_covered = optparse.make_option(
        '--skip-covered', action='store_true',
        help="Skip files with 100% coverage.",
    )
    no_skip_covered = optparse.make_option(
        '--no-skip-covered', action='store_false', dest='skip_covered',
        help="Disable --skip-covered.",
    )
    skip_empty = optparse.make_option(
        '--skip-empty', action='store_true',
        help="Skip files with no code.",
    )
    show_contexts = optparse.make_option(
        '--show-contexts', action='store_true',
        help="Show contexts for covered lines.",
    )
    omit = optparse.make_option(
        '', '--omit', action='store',
        metavar="PAT1,PAT2,...",
        help=(
            "Omit files whose paths match one of these patterns. "
            "Accepts shell-style wildcards, which must be quoted."
        ),
    )
    contexts = optparse.make_option(
        '', '--contexts', action='store',
        metavar="REGEX1,REGEX2,...",
        help=(
            "Only display data from lines covered in the given contexts. "
            "Accepts Python regexes, which must be quoted."
        ),
    )
    output_xml = optparse.make_option(
        '-o', '', action='store', dest="outfile",
        metavar="OUTFILE",
        help="Write the XML report to this file. Defaults to 'coverage.xml'",
    )
    output_json = optparse.make_option(
        '-o', '', action='store', dest="outfile",
        metavar="OUTFILE",
        help="Write the JSON report to this file. Defaults to 'coverage.json'",
    )
    json_pretty_print = optparse.make_option(
        '', '--pretty-print', action='store_true',
        help="Format the JSON for human readers.",
    )
    parallel_mode = optparse.make_option(
        '-p', '--parallel-mode', action='store_true',
        help=(
            "Append the machine name, process id and random number to the "
            ".coverage data file name to simplify collecting data from "
            "many processes."
        ),
    )
    module = optparse.make_option(
        '-m', '--module', action='store_true',
        help=(
            "<pyfile> is an importable Python module, not a script path, "
            "to be run as 'python -m' would run it."
        ),
    )
    precision = optparse.make_option(
        '', '--precision', action='store', metavar='N', type=int,
        help=(
            "Number of digits after the decimal point to display for "
            "reported coverage percentages."
        ),
    )
    rcfile = optparse.make_option(
        '', '--rcfile', action='store',
        help=(
            "Specify configuration file. "
            "By default '.coveragerc', 'setup.cfg', 'tox.ini', and "
            "'pyproject.toml' are tried. [env: COVERAGE_RCFILE]"
        ),
    )
    source = optparse.make_option(
        '', '--source', action='store', metavar="SRC1,SRC2,...",
        help="A list of packages or directories of code to be measured.",
    )
    timid = optparse.make_option(
        '', '--timid', action='store_true',
        help=(
            "Use a simpler but slower trace method. Try this if you get "
            "seemingly impossible results!"
        ),
    )
    title = optparse.make_option(
        '', '--title', action='store', metavar="TITLE",
        help="A text string to use as the title on the HTML.",
    )
    version = optparse.make_option(
        '', '--version', action='store_true',
        help="Display version information and exit.",
    )


class CoverageOptionParser(optparse.OptionParser, object):
    """Base OptionParser for coverage.py.

    Problems don't exit the program.
    Defaults are initialized for all options.

    """

    def __init__(self, *args, **kwargs):
        super(CoverageOptionParser, self).__init__(
            add_help_option=False, *args, **kwargs
            )
        self.set_defaults(
            action=None,
            append=None,
            branch=None,
            concurrency=None,
            context=None,
            debug=None,
            directory=None,
            fail_under=None,
            help=None,
            ignore_errors=None,
            include=None,
            keep=None,
            module=None,
            omit=None,
            contexts=None,
            parallel_mode=None,
            precision=None,
            pylib=None,
            rcfile=True,
            show_missing=None,
            skip_covered=None,
            skip_empty=None,
            show_contexts=None,
            sort=None,
            source=None,
            timid=None,
            title=None,
            version=None,
            )

        self.disable_interspersed_args()

    class OptionParserError(Exception):
        """Used to stop the optparse error handler ending the process."""
        pass

    def parse_args_ok(self, args=None, options=None):
        """Call optparse.parse_args, but return a triple:

        (ok, options, args)

        """
        try:
            options, args = super(CoverageOptionParser, self).parse_args(args, options)
        except self.OptionParserError:
            return False, None, None
        return True, options, args

    def error(self, msg):
        """Override optparse.error so sys.exit doesn't get called."""
        show_help(msg)
        raise self.OptionParserError


class GlobalOptionParser(CoverageOptionParser):
    """Command-line parser for coverage.py global option arguments."""

    def __init__(self):
        super(GlobalOptionParser, self).__init__()

        self.add_options([
            Opts.help,
            Opts.version,
        ])


class CmdOptionParser(CoverageOptionParser):
    """Parse one of the new-style commands for coverage.py."""

    def __init__(self, action, options, defaults=None, usage=None, description=None):
        """Create an OptionParser for a coverage.py command.

        `action` is the slug to put into `options.action`.
        `options` is a list of Option's for the command.
        `defaults` is a dict of default value for options.
        `usage` is the usage string to display in help.
        `description` is the description of the command, for the help text.

        """
        if usage:
            usage = "%prog " + usage
        super(CmdOptionParser, self).__init__(
            usage=usage,
            description=description,
        )
        self.set_defaults(action=action, **(defaults or {}))
        self.add_options(options)
        self.cmd = action

    def __eq__(self, other):
        # A convenience equality, so that I can put strings in unit test
        # results, and they will compare equal to objects.
        return (other == "<CmdOptionParser:%s>" % self.cmd)

    __hash__ = None     # This object doesn't need to be hashed.

    def get_prog_name(self):
        """Override of an undocumented function in optparse.OptionParser."""
        program_name = super(CmdOptionParser, self).get_prog_name()

        # Include the sub-command for this parser as part of the command.
        return "{command} {subcommand}".format(command=program_name, subcommand=self.cmd)


GLOBAL_ARGS = [
    Opts.debug,
    Opts.help,
    Opts.rcfile,
    ]

CMDS = {
    'annotate': CmdOptionParser(
        "annotate",
        [
            Opts.directory,
            Opts.ignore_errors,
            Opts.include,
            Opts.omit,
            ] + GLOBAL_ARGS,
        usage="[options] [modules]",
        description=(
            "Make annotated copies of the given files, marking statements that are executed "
            "with > and statements that are missed with !."
        ),
    ),

    'combine': CmdOptionParser(
        "combine",
        [
            Opts.append,
            Opts.keep,
            ] + GLOBAL_ARGS,
        usage="[options] <path1> <path2> ... <pathN>",
        description=(
            "Combine data from multiple coverage files collected "
            "with 'run -p'.  The combined results are written to a single "
            "file representing the union of the data. The positional "
            "arguments are data files or directories containing data files. "
            "If no paths are provided, data files in the default data file's "
            "directory are combined."
        ),
    ),

    'debug': CmdOptionParser(
        "debug", GLOBAL_ARGS,
        usage="<topic>",
        description=(
            "Display information about the internals of coverage.py, "
            "for diagnosing problems. "
            "Topics are: "
                "'data' to show a summary of the collected data; "
                "'sys' to show installation information; "
                "'config' to show the configuration; "
                "'premain' to show what is calling coverage."
        ),
    ),

    'erase': CmdOptionParser(
        "erase", GLOBAL_ARGS,
        description="Erase previously collected coverage data.",
    ),

    'help': CmdOptionParser(
        "help", GLOBAL_ARGS,
        usage="[command]",
        description="Describe how to use coverage.py",
    ),

    'html': CmdOptionParser(
        "html",
        [
            Opts.contexts,
            Opts.directory,
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.include,
            Opts.omit,
            Opts.precision,
            Opts.show_contexts,
            Opts.skip_covered,
            Opts.no_skip_covered,
            Opts.skip_empty,
            Opts.title,
            ] + GLOBAL_ARGS,
        usage="[options] [modules]",
        description=(
            "Create an HTML report of the coverage of the files.  "
            "Each file gets its own page, with the source decorated to show "
            "executed, excluded, and missed lines."
        ),
    ),

    'json': CmdOptionParser(
        "json",
        [
            Opts.contexts,
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.include,
            Opts.omit,
            Opts.output_json,
            Opts.json_pretty_print,
            Opts.show_contexts,
            ] + GLOBAL_ARGS,
        usage="[options] [modules]",
        description="Generate a JSON report of coverage results."
    ),

    'report': CmdOptionParser(
        "report",
        [
            Opts.contexts,
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.include,
            Opts.omit,
            Opts.precision,
            Opts.sort,
            Opts.show_missing,
            Opts.skip_covered,
            Opts.no_skip_covered,
            Opts.skip_empty,
            ] + GLOBAL_ARGS,
        usage="[options] [modules]",
        description="Report coverage statistics on modules."
    ),

    'run': CmdOptionParser(
        "run",
        [
            Opts.append,
            Opts.branch,
            Opts.concurrency,
            Opts.context,
            Opts.include,
            Opts.module,
            Opts.omit,
            Opts.pylib,
            Opts.parallel_mode,
            Opts.source,
            Opts.timid,
            ] + GLOBAL_ARGS,
        usage="[options] <pyfile> [program options]",
        description="Run a Python program, measuring code execution."
    ),

    'xml': CmdOptionParser(
        "xml",
        [
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.include,
            Opts.omit,
            Opts.output_xml,
            Opts.skip_empty,
            ] + GLOBAL_ARGS,
        usage="[options] [modules]",
        description="Generate an XML report of coverage results."
    ),
}


def show_help(error=None, topic=None, parser=None):
    """Display an error message, or the named topic."""
    assert error or topic or parser

    program_path = sys.argv[0]
    if program_path.endswith(os.path.sep + '__main__.py'):
        # The path is the main module of a package; get that path instead.
        program_path = os.path.dirname(program_path)
    program_name = os.path.basename(program_path)
    if env.WINDOWS:
        # entry_points={'console_scripts':...} on Windows makes files
        # called coverage.exe, coverage3.exe, and coverage-3.5.exe. These
        # invoke coverage-script.py, coverage3-script.py, and
        # coverage-3.5-script.py.  argv[0] is the .py file, but we want to
        # get back to the original form.
        auto_suffix = "-script.py"
        if program_name.endswith(auto_suffix):
            program_name = program_name[:-len(auto_suffix)]

    help_params = dict(coverage.__dict__)
    help_params['program_name'] = program_name
    if CTracer is not None:
        help_params['extension_modifier'] = 'with C extension'
    else:
        help_params['extension_modifier'] = 'without C extension'

    if error:
        print(error, file=sys.stderr)
        print("Use '%s help' for help." % (program_name,), file=sys.stderr)
    elif parser:
        print(parser.format_help().strip())
        print()
    else:
        help_msg = textwrap.dedent(HELP_TOPICS.get(topic, '')).strip()
        if help_msg:
            print(help_msg.format(**help_params))
        else:
            print("Don't know topic %r" % topic)
    print("Full documentation is at {__url__}".format(**help_params))


OK, ERR, FAIL_UNDER = 0, 1, 2


class CoverageScript(object):
    """The command-line interface to coverage.py."""

    def __init__(self):
        self.global_option = False
        self.coverage = None

    def command_line(self, argv):
        """The bulk of the command line interface to coverage.py.

        `argv` is the argument list to process.

        Returns 0 if all is well, 1 if something went wrong.

        """
        # Collect the command-line options.
        if not argv:
            show_help(topic='minimum_help')
            return OK

        # The command syntax we parse depends on the first argument.  Global
        # switch syntax always starts with an option.
        self.global_option = argv[0].startswith('-')
        if self.global_option:
            parser = GlobalOptionParser()
        else:
            parser = CMDS.get(argv[0])
            if not parser:
                show_help("Unknown command: '%s'" % argv[0])
                return ERR
            argv = argv[1:]

        ok, options, args = parser.parse_args_ok(argv)
        if not ok:
            return ERR

        # Handle help and version.
        if self.do_help(options, args, parser):
            return OK

        # Listify the list options.
        source = unshell_list(options.source)
        omit = unshell_list(options.omit)
        include = unshell_list(options.include)
        debug = unshell_list(options.debug)
        contexts = unshell_list(options.contexts)

        # Do something.
        self.coverage = Coverage(
            data_suffix=options.parallel_mode,
            cover_pylib=options.pylib,
            timid=options.timid,
            branch=options.branch,
            config_file=options.rcfile,
            source=source,
            omit=omit,
            include=include,
            debug=debug,
            concurrency=options.concurrency,
            check_preimported=True,
            context=options.context,
            )

        if options.action == "debug":
            return self.do_debug(args)

        elif options.action == "erase":
            self.coverage.erase()
            return OK

        elif options.action == "run":
            return self.do_run(options, args)

        elif options.action == "combine":
            if options.append:
                self.coverage.load()
            data_dirs = args or None
            self.coverage.combine(data_dirs, strict=True, keep=bool(options.keep))
            self.coverage.save()
            return OK

        # Remaining actions are reporting, with some common options.
        report_args = dict(
            morfs=unglob_args(args),
            ignore_errors=options.ignore_errors,
            omit=omit,
            include=include,
            contexts=contexts,
            )

        # We need to be able to import from the current directory, because
        # plugins may try to, for example, to read Django settings.
        sys.path.insert(0, '')

        self.coverage.load()

        total = None
        if options.action == "report":
            total = self.coverage.report(
                show_missing=options.show_missing,
                skip_covered=options.skip_covered,
                skip_empty=options.skip_empty,
                precision=options.precision,
                sort=options.sort,
                **report_args
                )
        elif options.action == "annotate":
            self.coverage.annotate(directory=options.directory, **report_args)
        elif options.action == "html":
            total = self.coverage.html_report(
                directory=options.directory,
                title=options.title,
                skip_covered=options.skip_covered,
                skip_empty=options.skip_empty,
                show_contexts=options.show_contexts,
                precision=options.precision,
                **report_args
                )
        elif options.action == "xml":
            outfile = options.outfile
            total = self.coverage.xml_report(
                outfile=outfile, skip_empty=options.skip_empty,
                **report_args
                )
        elif options.action == "json":
            outfile = options.outfile
            total = self.coverage.json_report(
                outfile=outfile,
                pretty_print=options.pretty_print,
                show_contexts=options.show_contexts,
                **report_args
            )

        if total is not None:
            # Apply the command line fail-under options, and then use the config
            # value, so we can get fail_under from the config file.
            if options.fail_under is not None:
                self.coverage.set_option("report:fail_under", options.fail_under)

            fail_under = self.coverage.get_option("report:fail_under")
            precision = self.coverage.get_option("report:precision")
            if should_fail_under(total, fail_under, precision):
                msg = "total of {total:.{p}f} is less than fail-under={fail_under:.{p}f}".format(
                    total=total, fail_under=fail_under, p=precision,
                )
                print("Coverage failure:", msg)
                return FAIL_UNDER

        return OK

    def do_help(self, options, args, parser):
        """Deal with help requests.

        Return True if it handled the request, False if not.

        """
        # Handle help.
        if options.help:
            if self.global_option:
                show_help(topic='help')
            else:
                show_help(parser=parser)
            return True

        if options.action == "help":
            if args:
                for a in args:
                    parser = CMDS.get(a)
                    if parser:
                        show_help(parser=parser)
                    else:
                        show_help(topic=a)
            else:
                show_help(topic='help')
            return True

        # Handle version.
        if options.version:
            show_help(topic='version')
            return True

        return False

    def do_run(self, options, args):
        """Implementation of 'coverage run'."""

        if not args:
            if options.module:
                # Specified -m with nothing else.
                show_help("No module specified for -m")
                return ERR
            command_line = self.coverage.get_option("run:command_line")
            if command_line is not None:
                args = shlex.split(command_line)
                if args and args[0] == "-m":
                    options.module = True
                    args = args[1:]
        if not args:
            show_help("Nothing to do.")
            return ERR

        if options.append and self.coverage.get_option("run:parallel"):
            show_help("Can't append to data files in parallel mode.")
            return ERR

        if options.concurrency == "multiprocessing":
            # Can't set other run-affecting command line options with
            # multiprocessing.
            for opt_name in ['branch', 'include', 'omit', 'pylib', 'source', 'timid']:
                # As it happens, all of these options have no default, meaning
                # they will be None if they have not been specified.
                if getattr(options, opt_name) is not None:
                    show_help(
                        "Options affecting multiprocessing must only be specified "
                        "in a configuration file.\n"
                        "Remove --{} from the command line.".format(opt_name)
                    )
                    return ERR

        runner = PyRunner(args, as_module=bool(options.module))
        runner.prepare()

        if options.append:
            self.coverage.load()

        # Run the script.
        self.coverage.start()
        code_ran = True
        try:
            runner.run()
        except NoSource:
            code_ran = False
            raise
        finally:
            self.coverage.stop()
            if code_ran:
                self.coverage.save()

        return OK

    def do_debug(self, args):
        """Implementation of 'coverage debug'."""

        if not args:
            show_help("What information would you like: config, data, sys, premain?")
            return ERR

        for info in args:
            if info == 'sys':
                sys_info = self.coverage.sys_info()
                print(info_header("sys"))
                for line in info_formatter(sys_info):
                    print(" %s" % line)
            elif info == 'data':
                self.coverage.load()
                data = self.coverage.get_data()
                print(info_header("data"))
                print("path: %s" % data.data_filename())
                if data:
                    print("has_arcs: %r" % data.has_arcs())
                    summary = line_counts(data, fullpath=True)
                    filenames = sorted(summary.keys())
                    print("\n%d files:" % len(filenames))
                    for f in filenames:
                        line = "%s: %d lines" % (f, summary[f])
                        plugin = data.file_tracer(f)
                        if plugin:
                            line += " [%s]" % plugin
                        print(line)
                else:
                    print("No data collected")
            elif info == 'config':
                print(info_header("config"))
                config_info = self.coverage.config.__dict__.items()
                for line in info_formatter(config_info):
                    print(" %s" % line)
            elif info == "premain":
                print(info_header("premain"))
                print(short_stack())
            else:
                show_help("Don't know what you mean by %r" % info)
                return ERR

        return OK


def unshell_list(s):
    """Turn a command-line argument into a list."""
    if not s:
        return None
    if env.WINDOWS:
        # When running coverage.py as coverage.exe, some of the behavior
        # of the shell is emulated: wildcards are expanded into a list of
        # file names.  So you have to single-quote patterns on the command
        # line, but (not) helpfully, the single quotes are included in the
        # argument, so we have to strip them off here.
        s = s.strip("'")
    return s.split(',')


def unglob_args(args):
    """Interpret shell wildcards for platforms that need it."""
    if env.WINDOWS:
        globbed = []
        for arg in args:
            if '?' in arg or '*' in arg:
                globbed.extend(glob.glob(arg))
            else:
                globbed.append(arg)
        args = globbed
    return args


HELP_TOPICS = {
    'help': """\
        Coverage.py, version {__version__} {extension_modifier}
        Measure, collect, and report on code coverage in Python programs.

        usage: {program_name} <command> [options] [args]

        Commands:
            annotate    Annotate source files with execution information.
            combine     Combine a number of data files.
            debug       Display information about the internals of coverage.py
            erase       Erase previously collected coverage data.
            help        Get help on using coverage.py.
            html        Create an HTML report.
            json        Create a JSON report of coverage results.
            report      Report coverage stats on modules.
            run         Run a Python program and measure code execution.
            xml         Create an XML report of coverage results.

        Use "{program_name} help <command>" for detailed help on any command.
    """,

    'minimum_help': """\
        Code coverage for Python, version {__version__} {extension_modifier}.  Use '{program_name} help' for help.
    """,

    'version': """\
        Coverage.py, version {__version__} {extension_modifier}
    """,
}


def main(argv=None):
    """The main entry point to coverage.py.

    This is installed as the script entry point.

    """
    if argv is None:
        argv = sys.argv[1:]
    try:
        status = CoverageScript().command_line(argv)
    except ExceptionDuringRun as err:
        # An exception was caught while running the product code.  The
        # sys.exc_info() return tuple is packed into an ExceptionDuringRun
        # exception.
        traceback.print_exception(*err.args)    # pylint: disable=no-value-for-parameter
        status = ERR
    except BaseCoverageException as err:
        # A controlled error inside coverage.py: print the message to the user.
        msg = err.args[0]
        if env.PY2:
            msg = msg.encode(output_encoding())
        print(msg)
        status = ERR
    except SystemExit as err:
        # The user called `sys.exit()`.  Exit with their argument, if any.
        if err.args:
            status = err.args[0]
        else:
            status = None
    return status

# Profiling using ox_profile.  Install it from GitHub:
#   pip install git+https://github.com/emin63/ox_profile.git
#
# $set_env.py: COVERAGE_PROFILE - Set to use ox_profile.
_profile = os.environ.get("COVERAGE_PROFILE", "")
if _profile:                                                # pragma: debugging
    from ox_profile.core.launchers import SimpleLauncher    # pylint: disable=import-error
    original_main = main

    def main(argv=None):                                    # pylint: disable=function-redefined
        """A wrapper around main that profiles."""
        profiler = SimpleLauncher.launch()
        try:
            return original_main(argv)
        finally:
            data, _ = profiler.query(re_filter='coverage', max_records=100)
            print(profiler.show(query=data, limit=100, sep='', col=''))
            profiler.cancel()
