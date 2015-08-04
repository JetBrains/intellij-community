"""Command-line support for Coverage."""

import optparse, os, sys, time, traceback

from coverage.backward import sorted                # pylint: disable=W0622
from coverage.execfile import run_python_file, run_python_module
from coverage.misc import CoverageException, ExceptionDuringRun, NoSource
from coverage.debug import info_formatter


class Opts(object):
    """A namespace class for individual options we'll build parsers from."""

    append = optparse.make_option(
        '-a', '--append', action='store_false', dest="erase_first",
        help="Append coverage data to .coverage, otherwise it is started "
                "clean with each run."
        )
    branch = optparse.make_option(
        '', '--branch', action='store_true',
        help="Measure branch coverage in addition to statement coverage."
        )
    debug = optparse.make_option(
        '', '--debug', action='store', metavar="OPTS",
        help="Debug options, separated by commas"
        )
    directory = optparse.make_option(
        '-d', '--directory', action='store', metavar="DIR",
        help="Write the output files to DIR."
        )
    fail_under = optparse.make_option(
        '', '--fail-under', action='store', metavar="MIN", type="int",
        help="Exit with a status of 2 if the total coverage is less than MIN."
        )
    help = optparse.make_option(
        '-h', '--help', action='store_true',
        help="Get help on this command."
        )
    ignore_errors = optparse.make_option(
        '-i', '--ignore-errors', action='store_true',
        help="Ignore errors while reading source files."
        )
    include = optparse.make_option(
        '', '--include', action='store',
        metavar="PAT1,PAT2,...",
        help="Include files only when their filename path matches one of "
                "these patterns.  Usually needs quoting on the command line."
        )
    pylib = optparse.make_option(
        '-L', '--pylib', action='store_true',
        help="Measure coverage even inside the Python installed library, "
                "which isn't done by default."
        )
    show_missing = optparse.make_option(
        '-m', '--show-missing', action='store_true',
        help="Show line numbers of statements in each module that weren't "
                "executed."
        )
    old_omit = optparse.make_option(
        '-o', '--omit', action='store',
        metavar="PAT1,PAT2,...",
        help="Omit files when their filename matches one of these patterns. "
                "Usually needs quoting on the command line."
        )
    omit = optparse.make_option(
        '', '--omit', action='store',
        metavar="PAT1,PAT2,...",
        help="Omit files when their filename matches one of these patterns. "
                "Usually needs quoting on the command line."
        )
    output_xml = optparse.make_option(
        '-o', '', action='store', dest="outfile",
        metavar="OUTFILE",
        help="Write the XML report to this file. Defaults to 'coverage.xml'"
        )
    parallel_mode = optparse.make_option(
        '-p', '--parallel-mode', action='store_true',
        help="Append the machine name, process id and random number to the "
                ".coverage data file name to simplify collecting data from "
                "many processes."
        )
    module = optparse.make_option(
        '-m', '--module', action='store_true',
        help="<pyfile> is an importable Python module, not a script path, "
                "to be run as 'python -m' would run it."
        )
    rcfile = optparse.make_option(
        '', '--rcfile', action='store',
        help="Specify configuration file.  Defaults to '.coveragerc'"
        )
    source = optparse.make_option(
        '', '--source', action='store', metavar="SRC1,SRC2,...",
        help="A list of packages or directories of code to be measured."
        )
    timid = optparse.make_option(
        '', '--timid', action='store_true',
        help="Use a simpler but slower trace method.  Try this if you get "
                "seemingly impossible results!"
        )
    title = optparse.make_option(
        '', '--title', action='store', metavar="TITLE",
        help="A text string to use as the title on the HTML."
        )
    version = optparse.make_option(
        '', '--version', action='store_true',
        help="Display version information and exit."
        )


class CoverageOptionParser(optparse.OptionParser, object):
    """Base OptionParser for coverage.

    Problems don't exit the program.
    Defaults are initialized for all options.

    """

    def __init__(self, *args, **kwargs):
        super(CoverageOptionParser, self).__init__(
            add_help_option=False, *args, **kwargs
            )
        self.set_defaults(
            actions=[],
            branch=None,
            debug=None,
            directory=None,
            fail_under=None,
            help=None,
            ignore_errors=None,
            include=None,
            omit=None,
            parallel_mode=None,
            module=None,
            pylib=None,
            rcfile=True,
            show_missing=None,
            source=None,
            timid=None,
            title=None,
            erase_first=None,
            version=None,
            )

        self.disable_interspersed_args()
        self.help_fn = self.help_noop

    def help_noop(self, error=None, topic=None, parser=None):
        """No-op help function."""
        pass

    class OptionParserError(Exception):
        """Used to stop the optparse error handler ending the process."""
        pass

    def parse_args(self, args=None, options=None):
        """Call optparse.parse_args, but return a triple:

        (ok, options, args)

        """
        try:
            options, args = \
                super(CoverageOptionParser, self).parse_args(args, options)
        except self.OptionParserError:
            return False, None, None
        return True, options, args

    def error(self, msg):
        """Override optparse.error so sys.exit doesn't get called."""
        self.help_fn(msg)
        raise self.OptionParserError


class ClassicOptionParser(CoverageOptionParser):
    """Command-line parser for coverage.py classic arguments."""

    def __init__(self):
        super(ClassicOptionParser, self).__init__()

        self.add_action('-a', '--annotate', 'annotate')
        self.add_action('-b', '--html', 'html')
        self.add_action('-c', '--combine', 'combine')
        self.add_action('-e', '--erase', 'erase')
        self.add_action('-r', '--report', 'report')
        self.add_action('-x', '--execute', 'execute')

        self.add_options([
            Opts.directory,
            Opts.help,
            Opts.ignore_errors,
            Opts.pylib,
            Opts.show_missing,
            Opts.old_omit,
            Opts.parallel_mode,
            Opts.timid,
            Opts.version,
        ])

    def add_action(self, dash, dashdash, action_code):
        """Add a specialized option that is the action to execute."""
        option = self.add_option(dash, dashdash, action='callback',
            callback=self._append_action
            )
        option.action_code = action_code

    def _append_action(self, option, opt_unused, value_unused, parser):
        """Callback for an option that adds to the `actions` list."""
        parser.values.actions.append(option.action_code)


class CmdOptionParser(CoverageOptionParser):
    """Parse one of the new-style commands for coverage.py."""

    def __init__(self, action, options=None, defaults=None, usage=None,
                cmd=None, description=None
                ):
        """Create an OptionParser for a coverage command.

        `action` is the slug to put into `options.actions`.
        `options` is a list of Option's for the command.
        `defaults` is a dict of default value for options.
        `usage` is the usage string to display in help.
        `cmd` is the command name, if different than `action`.
        `description` is the description of the command, for the help text.

        """
        if usage:
            usage = "%prog " + usage
        super(CmdOptionParser, self).__init__(
            prog="coverage %s" % (cmd or action),
            usage=usage,
            description=description,
        )
        self.set_defaults(actions=[action], **(defaults or {}))
        if options:
            self.add_options(options)
        self.cmd = cmd or action

    def __eq__(self, other):
        # A convenience equality, so that I can put strings in unit test
        # results, and they will compare equal to objects.
        return (other == "<CmdOptionParser:%s>" % self.cmd)

GLOBAL_ARGS = [
    Opts.rcfile,
    Opts.help,
    ]

CMDS = {
    'annotate': CmdOptionParser("annotate",
        [
            Opts.directory,
            Opts.ignore_errors,
            Opts.omit,
            Opts.include,
            ] + GLOBAL_ARGS,
        usage = "[options] [modules]",
        description = "Make annotated copies of the given files, marking "
            "statements that are executed with > and statements that are "
            "missed with !."
        ),

    'combine': CmdOptionParser("combine", GLOBAL_ARGS,
        usage = " ",
        description = "Combine data from multiple coverage files collected "
            "with 'run -p'.  The combined results are written to a single "
            "file representing the union of the data."
        ),

    'debug': CmdOptionParser("debug", GLOBAL_ARGS,
        usage = "<topic>",
        description = "Display information on the internals of coverage.py, "
            "for diagnosing problems. "
            "Topics are 'data' to show a summary of the collected data, "
            "or 'sys' to show installation information."
        ),

    'erase': CmdOptionParser("erase", GLOBAL_ARGS,
        usage = " ",
        description = "Erase previously collected coverage data."
        ),

    'help': CmdOptionParser("help", GLOBAL_ARGS,
        usage = "[command]",
        description = "Describe how to use coverage.py"
        ),

    'html': CmdOptionParser("html",
        [
            Opts.directory,
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.omit,
            Opts.include,
            Opts.title,
            ] + GLOBAL_ARGS,
        usage = "[options] [modules]",
        description = "Create an HTML report of the coverage of the files.  "
            "Each file gets its own page, with the source decorated to show "
            "executed, excluded, and missed lines."
        ),

    'report': CmdOptionParser("report",
        [
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.omit,
            Opts.include,
            Opts.show_missing,
            ] + GLOBAL_ARGS,
        usage = "[options] [modules]",
        description = "Report coverage statistics on modules."
        ),

    'run': CmdOptionParser("execute",
        [
            Opts.append,
            Opts.branch,
            Opts.debug,
            Opts.pylib,
            Opts.parallel_mode,
            Opts.module,
            Opts.timid,
            Opts.source,
            Opts.omit,
            Opts.include,
            ] + GLOBAL_ARGS,
        defaults = {'erase_first': True},
        cmd = "run",
        usage = "[options] <pyfile> [program options]",
        description = "Run a Python program, measuring code execution."
        ),

    'xml': CmdOptionParser("xml",
        [
            Opts.fail_under,
            Opts.ignore_errors,
            Opts.omit,
            Opts.include,
            Opts.output_xml,
            ] + GLOBAL_ARGS,
        cmd = "xml",
        usage = "[options] [modules]",
        description = "Generate an XML report of coverage results."
        ),
    }


OK, ERR, FAIL_UNDER = 0, 1, 2


class CoverageScript(object):
    """The command-line interface to Coverage."""

    def __init__(self, _covpkg=None, _run_python_file=None,
                 _run_python_module=None, _help_fn=None):
        # _covpkg is for dependency injection, so we can test this code.
        if _covpkg:
            self.covpkg = _covpkg
        else:
            import coverage
            self.covpkg = coverage

        # For dependency injection:
        self.run_python_file = _run_python_file or run_python_file
        self.run_python_module = _run_python_module or run_python_module
        self.help_fn = _help_fn or self.help
        self.classic = False

        self.coverage = None

    def command_line(self, argv):
        """The bulk of the command line interface to Coverage.

        `argv` is the argument list to process.

        Returns 0 if all is well, 1 if something went wrong.

        """
        # Collect the command-line options.
        if not argv:
            self.help_fn(topic='minimum_help')
            return OK

        # The command syntax we parse depends on the first argument.  Classic
        # syntax always starts with an option.
        self.classic = argv[0].startswith('-')
        if self.classic:
            parser = ClassicOptionParser()
        else:
            parser = CMDS.get(argv[0])
            if not parser:
                self.help_fn("Unknown command: '%s'" % argv[0])
                return ERR
            argv = argv[1:]

        parser.help_fn = self.help_fn
        ok, options, args = parser.parse_args(argv)
        if not ok:
            return ERR

        # Handle help and version.
        if self.do_help(options, args, parser):
            return OK

        # Check for conflicts and problems in the options.
        if not self.args_ok(options, args):
            return ERR

        # Listify the list options.
        source = unshell_list(options.source)
        omit = unshell_list(options.omit)
        include = unshell_list(options.include)
        debug = unshell_list(options.debug)

        # Do something.
        self.coverage = self.covpkg.coverage(
            data_suffix = options.parallel_mode,
            cover_pylib = options.pylib,
            timid = options.timid,
            branch = options.branch,
            config_file = options.rcfile,
            source = source,
            omit = omit,
            include = include,
            debug = debug,
            )

        if 'debug' in options.actions:
            return self.do_debug(args)

        if 'erase' in options.actions or options.erase_first:
            self.coverage.erase()
        else:
            self.coverage.load()

        if 'execute' in options.actions:
            self.do_execute(options, args)

        if 'combine' in options.actions:
            self.coverage.combine()
            self.coverage.save()

        # Remaining actions are reporting, with some common options.
        report_args = dict(
            morfs = args,
            ignore_errors = options.ignore_errors,
            omit = omit,
            include = include,
            )

        if 'report' in options.actions:
            total = self.coverage.report(
                show_missing=options.show_missing, **report_args)
        if 'annotate' in options.actions:
            self.coverage.annotate(
                directory=options.directory, **report_args)
        if 'html' in options.actions:
            total = self.coverage.html_report(
                directory=options.directory, title=options.title,
                **report_args)
        if 'xml' in options.actions:
            outfile = options.outfile
            total = self.coverage.xml_report(outfile=outfile, **report_args)

        if options.fail_under is not None:
            if total >= options.fail_under:
                return OK
            else:
                return FAIL_UNDER
        else:
            return OK

    def help(self, error=None, topic=None, parser=None):
        """Display an error message, or the named topic."""
        assert error or topic or parser
        if error:
            print(error)
            print("Use 'coverage help' for help.")
        elif parser:
            print(parser.format_help().strip())
        else:
            help_msg = HELP_TOPICS.get(topic, '').strip()
            if help_msg:
                print(help_msg % self.covpkg.__dict__)
            else:
                print("Don't know topic %r" % topic)

    def do_help(self, options, args, parser):
        """Deal with help requests.

        Return True if it handled the request, False if not.

        """
        # Handle help.
        if options.help:
            if self.classic:
                self.help_fn(topic='help')
            else:
                self.help_fn(parser=parser)
            return True

        if "help" in options.actions:
            if args:
                for a in args:
                    parser = CMDS.get(a)
                    if parser:
                        self.help_fn(parser=parser)
                    else:
                        self.help_fn(topic=a)
            else:
                self.help_fn(topic='help')
            return True

        # Handle version.
        if options.version:
            self.help_fn(topic='version')
            return True

        return False

    def args_ok(self, options, args):
        """Check for conflicts and problems in the options.

        Returns True if everything is ok, or False if not.

        """
        for i in ['erase', 'execute']:
            for j in ['annotate', 'html', 'report', 'combine']:
                if (i in options.actions) and (j in options.actions):
                    self.help_fn("You can't specify the '%s' and '%s' "
                              "options at the same time." % (i, j))
                    return False

        if not options.actions:
            self.help_fn(
                "You must specify at least one of -e, -x, -c, -r, -a, or -b."
                )
            return False
        args_allowed = (
            'execute' in options.actions or
            'annotate' in options.actions or
            'html' in options.actions or
            'debug' in options.actions or
            'report' in options.actions or
            'xml' in options.actions
            )
        if not args_allowed and args:
            self.help_fn("Unexpected arguments: %s" % " ".join(args))
            return False

        if 'execute' in options.actions and not args:
            self.help_fn("Nothing to do.")
            return False

        return True

    def do_execute(self, options, args):
        """Implementation of 'coverage run'."""

        # Set the first path element properly.
        old_path0 = sys.path[0]

        # Run the script.
        self.coverage.start()
        code_ran = True
        try:
            try:
                if options.module:
                    sys.path[0] = ''
                    self.run_python_module(args[0], args)
                else:
                    filename = args[0]
                    sys.path[0] = os.path.abspath(os.path.dirname(filename))
                    self.run_python_file(filename, args)
            except NoSource:
                code_ran = False
                raise
        finally:
            self.coverage.stop()
            if code_ran:
                self.coverage.save()

            # Restore the old path
            sys.path[0] = old_path0

    def do_debug(self, args):
        """Implementation of 'coverage debug'."""

        if not args:
            self.help_fn("What information would you like: data, sys?")
            return ERR
        for info in args:
            if info == 'sys':
                print("-- sys ----------------------------------------")
                for line in info_formatter(self.coverage.sysinfo()):
                    print(" %s" % line)
            elif info == 'data':
                print("-- data ---------------------------------------")
                self.coverage.load()
                print("path: %s" % self.coverage.data.filename)
                print("has_arcs: %r" % self.coverage.data.has_arcs())
                summary = self.coverage.data.summary(fullpath=True)
                if summary:
                    filenames = sorted(summary.keys())
                    print("\n%d files:" % len(filenames))
                    for f in filenames:
                        print("%s: %d lines" % (f, summary[f]))
                else:
                    print("No data collected")
            else:
                self.help_fn("Don't know what you mean by %r" % info)
                return ERR
        return OK


def unshell_list(s):
    """Turn a command-line argument into a list."""
    if not s:
        return None
    if sys.platform == 'win32':
        # When running coverage as coverage.exe, some of the behavior
        # of the shell is emulated: wildcards are expanded into a list of
        # filenames.  So you have to single-quote patterns on the command
        # line, but (not) helpfully, the single quotes are included in the
        # argument, so we have to strip them off here.
        s = s.strip("'")
    return s.split(',')


HELP_TOPICS = {
# -------------------------
'classic':
r"""Coverage.py version %(__version__)s
Measure, collect, and report on code coverage in Python programs.

Usage:

coverage -x [-p] [-L] [--timid] MODULE.py [ARG1 ARG2 ...]
    Execute the module, passing the given command-line arguments, collecting
    coverage data.  With the -p option, include the machine name and process
    id in the .coverage file name.  With -L, measure coverage even inside the
    Python installed library, which isn't done by default.  With --timid, use a
    simpler but slower trace method.

coverage -e
    Erase collected coverage data.

coverage -c
    Combine data from multiple coverage files (as created by -p option above)
    and store it into a single file representing the union of the coverage.

coverage -r [-m] [-i] [-o DIR,...] [FILE1 FILE2 ...]
    Report on the statement coverage for the given files.  With the -m
    option, show line numbers of the statements that weren't executed.

coverage -b -d DIR [-i] [-o DIR,...] [FILE1 FILE2 ...]
    Create an HTML report of the coverage of the given files.  Each file gets
    its own page, with the file listing decorated to show executed, excluded,
    and missed lines.

coverage -a [-d DIR] [-i] [-o DIR,...] [FILE1 FILE2 ...]
    Make annotated copies of the given files, marking statements that
    are executed with > and statements that are missed with !.

-d DIR
    Write output files for -b or -a to this directory.

-i  Ignore errors while reporting or annotating.

-o DIR,...
    Omit reporting or annotating files when their filename path starts with
    a directory listed in the omit list.
    e.g. coverage -i -r -o c:\python25,lib\enthought\traits

Coverage data is saved in the file .coverage by default.  Set the
COVERAGE_FILE environment variable to save it somewhere else.
""",
# -------------------------
'help': """\
Coverage.py, version %(__version__)s
Measure, collect, and report on code coverage in Python programs.

usage: coverage <command> [options] [args]

Commands:
    annotate    Annotate source files with execution information.
    combine     Combine a number of data files.
    erase       Erase previously collected coverage data.
    help        Get help on using coverage.py.
    html        Create an HTML report.
    report      Report coverage stats on modules.
    run         Run a Python program and measure code execution.
    xml         Create an XML report of coverage results.

Use "coverage help <command>" for detailed help on any command.
Use "coverage help classic" for help on older command syntax.
For more information, see %(__url__)s
""",
# -------------------------
'minimum_help': """\
Code coverage for Python.  Use 'coverage help' for help.
""",
# -------------------------
'version': """\
Coverage.py, version %(__version__)s.  %(__url__)s
""",
}


def main(argv=None):
    """The main entry point to Coverage.

    This is installed as the script entry point.

    """
    if argv is None:
        argv = sys.argv[1:]
    try:
        start = time.clock()
        status = CoverageScript().command_line(argv)
        end = time.clock()
        if 0:
            print("time: %.3fs" % (end - start))
    except ExceptionDuringRun:
        # An exception was caught while running the product code.  The
        # sys.exc_info() return tuple is packed into an ExceptionDuringRun
        # exception.
        _, err, _ = sys.exc_info()
        traceback.print_exception(*err.args)
        status = ERR
    except CoverageException:
        # A controlled error inside coverage.py: print the message to the user.
        _, err, _ = sys.exc_info()
        print(err)
        status = ERR
    except SystemExit:
        # The user called `sys.exit()`.  Exit with their argument, if any.
        _, err, _ = sys.exc_info()
        if err.args:
            status = err.args[0]
        else:
            status = None
    return status
