# epydoc -- Command line interface
#
# Copyright (C) 2005 Edward Loper
# Author: Edward Loper <edloper@loper.org>
# URL: <http://epydoc.sf.net>
#
# $Id: cli.py 1678 2008-01-29 17:21:29Z edloper $

"""
Command-line interface for epydoc.  Abbreviated Usage::

 epydoc [options] NAMES...
 
     NAMES...                  The Python modules to document.
     --html                    Generate HTML output (default).
     --latex                   Generate LaTeX output.
     --pdf                     Generate pdf output, via LaTeX.
     -o DIR, --output DIR      The output directory.
     --inheritance STYLE       The format for showing inherited objects.
     -V, --version             Print the version of epydoc.
     -h, --help                Display a usage message.

Run \"epydoc --help\" for a complete option list.  See the epydoc(1)
man page for more information.

Config Files
============
Configuration files can be specified with the C{--config} option.
These files are read using U{ConfigParser
<http://docs.python.org/lib/module-ConfigParser.html>}.  Configuration
files may set options or add names of modules to document.  Option
names are (usually) identical to the long names of command line
options.  To specify names to document, use any of the following
option names::

  module modules value values object objects

A simple example of a config file is::

  [epydoc]
  modules: sys, os, os.path, re, %(MYSANDBOXPATH)/utilities.py
  name: Example
  graph: classtree
  introspect: no

All ConfigParser interpolations are done using local values and the
environment variables.


Verbosity Levels
================
The C{-v} and C{-q} options increase and decrease verbosity,
respectively.  The default verbosity level is zero.  The verbosity
levels are currently defined as follows::

                Progress    Markup warnings   Warnings   Errors
 -3               none            no             no        no
 -2               none            no             no        yes
 -1               none            no             yes       yes
  0 (default)     bar             no             yes       yes
  1               bar             yes            yes       yes
  2               list            yes            yes       yes
"""
__docformat__ = 'epytext en'

import sys, os, time, re, pickle, textwrap
from glob import glob
from optparse import OptionParser, OptionGroup, SUPPRESS_HELP
import optparse
import epydoc
from epydoc import log
from epydoc.util import wordwrap, run_subprocess, RunSubprocessError
from epydoc.util import plaintext_to_html
from epydoc.apidoc import UNKNOWN
from epydoc.compat import *
import ConfigParser
from epydoc.docwriter.html_css import STYLESHEETS as CSS_STYLESHEETS

# This module is only available if Docutils are in the system
try:
    from epydoc.docwriter import xlink
except:
    xlink = None

INHERITANCE_STYLES = ('grouped', 'listed', 'included')
GRAPH_TYPES = ('classtree', 'callgraph', 'umlclasstree')
ACTIONS = ('html', 'text', 'latex', 'dvi', 'ps', 'pdf', 'check')
DEFAULT_DOCFORMAT = 'epytext'
PROFILER = 'profile' #: Which profiler to use: 'hotshot' or 'profile'

######################################################################
#{ Help Topics
######################################################################

DOCFORMATS = ('epytext', 'plaintext', 'restructuredtext', 'javadoc')
HELP_TOPICS = {
    'docformat': textwrap.dedent('''\
        __docformat__ is a module variable that specifies the markup
        language for the docstrings in a module.  Its value is a 
        string, consisting the name of a markup language, optionally 
        followed by a language code (such as "en" for English).  Epydoc
        currently recognizes the following markup language names:
        ''' + ', '.join(DOCFORMATS)),
    'inheritance': textwrap.dedent('''\
        The following inheritance formats are currently supported:
            - grouped: inherited objects are gathered into groups,
              based on what class they were inherited from.
            - listed: inherited objects are listed in a short list
              at the end of their section.
            - included: inherited objects are mixed in with 
              non-inherited objects.'''),
    'css': textwrap.dedent(
        'The following built-in CSS stylesheets are available:\n' +
        '\n'.join(['  %10s: %s' % (key, descr)
                   for (key, (sheet, descr))
                   in CSS_STYLESHEETS.items()])),
    #'checks': textwrap.dedent('''\
    #
    #    '''),
    }
        

HELP_TOPICS['topics'] = wordwrap(
    'Epydoc can provide additional help for the following topics: ' +
    ', '.join(['%r' % topic for topic in HELP_TOPICS.keys()]))
    
######################################################################
#{ Argument & Config File Parsing
######################################################################

OPTION_DEFAULTS = dict(
    action="html", show_frames=True, docformat=DEFAULT_DOCFORMAT, 
    show_private=True, show_imports=False, inheritance="listed",
    verbose=0, quiet=0, load_pickle=False, parse=True, introspect=True,
    debug=epydoc.DEBUG, profile=False, graphs=[],
    list_classes_separately=False, graph_font=None, graph_font_size=None,
    include_source_code=True, pstat_files=[], simple_term=False, fail_on=None,
    exclude=[], exclude_parse=[], exclude_introspect=[],
    external_api=[], external_api_file=[], external_api_root=[],
    redundant_details=False, src_code_tab_width=8)

def parse_arguments():
    # Construct the option parser.
    usage = '%prog [ACTION] [options] NAMES...'
    version = "Epydoc, version %s" % epydoc.__version__
    optparser = OptionParser(usage=usage, add_help_option=False)

    optparser.add_option('--config',
        action='append', dest="configfiles", metavar='FILE',
        help=("A configuration file, specifying additional OPTIONS "
              "and/or NAMES.  This option may be repeated."))

    optparser.add_option("--output", "-o",
        dest="target", metavar="PATH",
        help="The output directory.  If PATH does not exist, then "
        "it will be created.")

    optparser.add_option("--quiet", "-q",
        action="count", dest="quiet",
        help="Decrease the verbosity.")

    optparser.add_option("--verbose", "-v",
        action="count", dest="verbose",
        help="Increase the verbosity.")

    optparser.add_option("--debug",
        action="store_true", dest="debug",
        help="Show full tracebacks for internal errors.")

    optparser.add_option("--simple-term",
        action="store_true", dest="simple_term",
        help="Do not try to use color or cursor control when displaying "
        "the progress bar, warnings, or errors.")


    action_group = OptionGroup(optparser, 'Actions')
    optparser.add_option_group(action_group)

    action_group.add_option("--html",
        action="store_const", dest="action", const="html",
        help="Write HTML output.")

    action_group.add_option("--text",
        action="store_const", dest="action", const="text",
        help="Write plaintext output. (not implemented yet)")

    action_group.add_option("--latex",
        action="store_const", dest="action", const="latex",
        help="Write LaTeX output.")

    action_group.add_option("--dvi",
        action="store_const", dest="action", const="dvi",
        help="Write DVI output.")

    action_group.add_option("--ps",
        action="store_const", dest="action", const="ps",
        help="Write Postscript output.")

    action_group.add_option("--pdf",
        action="store_const", dest="action", const="pdf",
        help="Write PDF output.")

    action_group.add_option("--check",
        action="store_const", dest="action", const="check",
        help="Check completeness of docs.")

    action_group.add_option("--pickle",
        action="store_const", dest="action", const="pickle",
        help="Write the documentation to a pickle file.")

    # Provide our own --help and --version options.
    action_group.add_option("--version",
        action="store_const", dest="action", const="version",
        help="Show epydoc's version number and exit.")

    action_group.add_option("-h", "--help",
        action="store_const", dest="action", const="help",
        help="Show this message and exit.  For help on specific "
        "topics, use \"--help TOPIC\".  Use \"--help topics\" for a "
        "list of available help topics")


    generation_group = OptionGroup(optparser, 'Generation Options')
    optparser.add_option_group(generation_group)

    generation_group.add_option("--docformat",
        dest="docformat", metavar="NAME",
        help="The default markup language for docstrings.  Defaults "
        "to \"%s\"." % DEFAULT_DOCFORMAT)

    generation_group.add_option("--parse-only",
        action="store_false", dest="introspect",
        help="Get all information from parsing (don't introspect)")

    generation_group.add_option("--introspect-only",
        action="store_false", dest="parse",
        help="Get all information from introspecting (don't parse)")

    generation_group.add_option("--exclude",
        dest="exclude", metavar="PATTERN", action="append",
        help="Exclude modules whose dotted name matches "
             "the regular expression PATTERN")

    generation_group.add_option("--exclude-introspect",
        dest="exclude_introspect", metavar="PATTERN", action="append",
        help="Exclude introspection of modules whose dotted name matches "
             "the regular expression PATTERN")

    generation_group.add_option("--exclude-parse",
        dest="exclude_parse", metavar="PATTERN", action="append",
        help="Exclude parsing of modules whose dotted name matches "
             "the regular expression PATTERN")

    generation_group.add_option("--inheritance",
        dest="inheritance", metavar="STYLE",
        help="The format for showing inheritance objects.  STYLE "
        "should be one of: %s." % ', '.join(INHERITANCE_STYLES))

    generation_group.add_option("--show-private",
        action="store_true", dest="show_private",
        help="Include private variables in the output. (default)")

    generation_group.add_option("--no-private",
        action="store_false", dest="show_private",
        help="Do not include private variables in the output.")

    generation_group.add_option("--show-imports",
        action="store_true", dest="show_imports",
        help="List each module's imports.")

    generation_group.add_option("--no-imports",
        action="store_false", dest="show_imports",
        help="Do not list each module's imports. (default)")

    generation_group.add_option('--show-sourcecode',
        action='store_true', dest='include_source_code',
        help=("Include source code with syntax highlighting in the "
              "HTML output. (default)"))

    generation_group.add_option('--no-sourcecode',
        action='store_false', dest='include_source_code',
        help=("Do not include source code with syntax highlighting in the "
              "HTML output."))

    generation_group.add_option('--include-log',
        action='store_true', dest='include_log',
        help=("Include a page with the process log (epydoc-log.html)"))

    generation_group.add_option(
        '--redundant-details',
        action='store_true', dest='redundant_details',
        help=("Include values in the details lists even if all info "
              "about them is already provided by the summary table."))

    output_group = OptionGroup(optparser, 'Output Options')
    optparser.add_option_group(output_group)

    output_group.add_option("--name", "-n",
        dest="prj_name", metavar="NAME",
        help="The documented project's name (for the navigation bar).")

    output_group.add_option("--css", "-c",
        dest="css", metavar="STYLESHEET",
        help="The CSS stylesheet.  STYLESHEET can be either a "
        "builtin stylesheet or the name of a CSS file.")

    output_group.add_option("--url", "-u",
        dest="prj_url", metavar="URL",
        help="The documented project's URL (for the navigation bar).")

    output_group.add_option("--navlink",
        dest="prj_link", metavar="HTML",
        help="HTML code for a navigation link to place in the "
        "navigation bar.")

    output_group.add_option("--top",
        dest="top_page", metavar="PAGE",
        help="The \"top\" page for the HTML documentation.  PAGE can "
        "be a URL, the name of a module or class, or one of the "
        "special names \"trees.html\", \"indices.html\", or \"help.html\"")

    output_group.add_option("--help-file",
        dest="help_file", metavar="FILE",
        help="An alternate help file.  FILE should contain the body "
        "of an HTML file -- navigation bars will be added to it.")

    output_group.add_option("--show-frames",
        action="store_true", dest="show_frames",
        help="Include frames in the HTML output. (default)")

    output_group.add_option("--no-frames",
        action="store_false", dest="show_frames",
        help="Do not include frames in the HTML output.")

    output_group.add_option('--separate-classes',
        action='store_true', dest='list_classes_separately',
        help=("When generating LaTeX or PDF output, list each class in "
              "its own section, instead of listing them under their "
              "containing module."))

    output_group.add_option('--src-code-tab-width',
        action='store', type='int', dest='src_code_tab_width',
        help=("When generating HTML output, sets the number of spaces "
              "each tab in source code listings is replaced with."))
    
    # The group of external API options.
    # Skip if the module couldn't be imported (usually missing docutils)
    if xlink is not None:
        link_group = OptionGroup(optparser,
                                 xlink.ApiLinkReader.settings_spec[0])
        optparser.add_option_group(link_group)

        for help, names, opts in xlink.ApiLinkReader.settings_spec[2]:
            opts = opts.copy()
            opts['help'] = help
            link_group.add_option(*names, **opts)

    graph_group = OptionGroup(optparser, 'Graph Options')
    optparser.add_option_group(graph_group)

    graph_group.add_option('--graph',
        action='append', dest='graphs', metavar='GRAPHTYPE',
        help=("Include graphs of type GRAPHTYPE in the generated output.  "
              "Graphs are generated using the Graphviz dot executable.  "
              "If this executable is not on the path, then use --dotpath "
              "to specify its location.  This option may be repeated to "
              "include multiple graph types in the output.  GRAPHTYPE "
              "should be one of: all, %s." % ', '.join(GRAPH_TYPES)))

    graph_group.add_option("--dotpath",
        dest="dotpath", metavar='PATH',
        help="The path to the Graphviz 'dot' executable.")

    graph_group.add_option('--graph-font',
        dest='graph_font', metavar='FONT',
        help=("Specify the font used to generate Graphviz graphs.  (e.g., "
              "helvetica or times)."))

    graph_group.add_option('--graph-font-size',
        dest='graph_font_size', metavar='SIZE',
        help=("Specify the font size used to generate Graphviz graphs, "
              "in points."))

    graph_group.add_option('--pstat',
        action='append', dest='pstat_files', metavar='FILE',
        help="A pstat output file, to be used in generating call graphs.")

    # this option is for developers, not users.
    graph_group.add_option("--profile-epydoc",
        action="store_true", dest="profile",
        help=SUPPRESS_HELP or
             ("Run the hotshot profiler on epydoc itself.  Output "
              "will be written to profile.out."))


    return_group = OptionGroup(optparser, 'Return Value Options')
    optparser.add_option_group(return_group)

    return_group.add_option("--fail-on-error",
        action="store_const", dest="fail_on", const=log.ERROR,
        help="Return a non-zero exit status, indicating failure, if any "
        "errors are encountered.")

    return_group.add_option("--fail-on-warning",
        action="store_const", dest="fail_on", const=log.WARNING,
        help="Return a non-zero exit status, indicating failure, if any "
        "errors or warnings are encountered (not including docstring "
        "warnings).")

    return_group.add_option("--fail-on-docstring-warning",
        action="store_const", dest="fail_on", const=log.DOCSTRING_WARNING,
        help="Return a non-zero exit status, indicating failure, if any "
        "errors or warnings are encountered (including docstring "
        "warnings).")

    # Set the option parser's defaults.
    optparser.set_defaults(**OPTION_DEFAULTS)

    # Parse the arguments.
    options, names = optparser.parse_args()

    # Print help message, if requested.  We also provide support for
    # --help [topic]
    if options.action == 'help':
        names = set([n.lower() for n in names])
        for (topic, msg) in HELP_TOPICS.items():
            if topic.lower() in names:
                print '\n' + msg.rstrip() + '\n'
                sys.exit(0)
        optparser.print_help()
        sys.exit(0)

    # Print version message, if requested.
    if options.action == 'version':
        print version
        sys.exit(0)
    
    # Process any config files.
    if options.configfiles:
        try:
            parse_configfiles(options.configfiles, options, names)
        except (KeyboardInterrupt,SystemExit): raise
        except Exception, e:
            if len(options.configfiles) == 1:
                cf_name = 'config file %s' % options.configfiles[0]
            else:
                cf_name = 'config files %s' % ', '.join(options.configfiles)
            optparser.error('Error reading %s:\n    %s' % (cf_name, e))

    # Check if the input file is a pickle file.
    for name in names:
        if name.endswith('.pickle'):
            if len(names) != 1:
                optparser.error("When a pickle file is specified, no other "
                               "input files may be specified.")
            options.load_pickle = True
    
    # Check to make sure all options are valid.
    if len(names) == 0:
        optparser.error("No names specified.")
        
    # perform shell expansion.
    for i, name in reversed(list(enumerate(names[:]))):
        if '?' in name or '*' in name:
            names[i:i+1] = glob(name)
        
    if options.inheritance not in INHERITANCE_STYLES:
        optparser.error("Bad inheritance style.  Valid options are " +
                        ",".join(INHERITANCE_STYLES))
    if not options.parse and not options.introspect:
        optparser.error("Invalid option combination: --parse-only "
                        "and --introspect-only.")
    if options.action == 'text' and len(names) > 1:
        optparser.error("--text option takes only one name.")

    # Check the list of requested graph types to make sure they're
    # acceptable.
    options.graphs = [graph_type.lower() for graph_type in options.graphs]
    for graph_type in options.graphs:
        if graph_type == 'callgraph' and not options.pstat_files:
            optparser.error('"callgraph" graph type may only be used if '
                            'one or more pstat files are specified.')
        # If it's 'all', then add everything (but don't add callgraph if
        # we don't have any profiling info to base them on).
        if graph_type == 'all':
            if options.pstat_files:
                options.graphs = GRAPH_TYPES
            else:
                options.graphs = [g for g in GRAPH_TYPES if g != 'callgraph']
            break
        elif graph_type not in GRAPH_TYPES:
            optparser.error("Invalid graph type %s." % graph_type)

    # Calculate verbosity.
    verbosity = getattr(options, 'verbosity', 0)
    options.verbosity = verbosity + options.verbose - options.quiet

    # The target default depends on the action.
    if options.target is None:
        options.target = options.action
    
    # Return parsed args.
    options.names = names
    return options, names

def parse_configfiles(configfiles, options, names):
    configparser = ConfigParser.ConfigParser()
    # ConfigParser.read() silently ignores errors, so open the files
    # manually (since we want to notify the user of any errors).
    for configfile in configfiles:
        fp = open(configfile, 'r') # may raise IOError.
        configparser.readfp(fp, configfile)
        fp.close()
    for optname in configparser.options('epydoc'):
        val = configparser.get('epydoc', optname, vars=os.environ).strip()
        optname = optname.lower().strip()

        if optname in ('modules', 'objects', 'values',
                       'module', 'object', 'value'):
            names.extend(_str_to_list(val))
        elif optname == 'target':
            options.target = val
        elif optname == 'output':
            if val.lower() not in ACTIONS:
                raise ValueError('"%s" expected one of: %s' %
                                 (optname, ', '.join(ACTIONS)))
            options.action = val.lower()
        elif optname == 'verbosity':
            options.verbosity = _str_to_int(val, optname)
        elif optname == 'debug':
            options.debug = _str_to_bool(val, optname)
        elif optname in ('simple-term', 'simple_term'):
            options.simple_term = _str_to_bool(val, optname)

        # Generation options
        elif optname == 'docformat':
            options.docformat = val
        elif optname == 'parse':
            options.parse = _str_to_bool(val, optname)
        elif optname == 'introspect':
            options.introspect = _str_to_bool(val, optname)
        elif optname == 'exclude':
            options.exclude.extend(_str_to_list(val))
        elif optname in ('exclude-parse', 'exclude_parse'):
            options.exclude_parse.extend(_str_to_list(val))
        elif optname in ('exclude-introspect', 'exclude_introspect'):
            options.exclude_introspect.extend(_str_to_list(val))
        elif optname == 'inheritance':
            if val.lower() not in INHERITANCE_STYLES:
                raise ValueError('"%s" expected one of: %s.' %
                                 (optname, ', '.join(INHERITANCE_STYLES)))
            options.inheritance = val.lower()
        elif optname =='private':
            options.show_private = _str_to_bool(val, optname)
        elif optname =='imports':
            options.show_imports = _str_to_bool(val, optname)
        elif optname == 'sourcecode':
            options.include_source_code = _str_to_bool(val, optname)
        elif optname in ('include-log', 'include_log'):
            options.include_log = _str_to_bool(val, optname)
        elif optname in ('redundant-details', 'redundant_details'):
            options.redundant_details = _str_to_bool(val, optname)

        # Output options
        elif optname == 'name':
            options.prj_name = val
        elif optname == 'css':
            options.css = val
        elif optname == 'url':
            options.prj_url = val
        elif optname == 'link':
            options.prj_link = val
        elif optname == 'top':
            options.top_page = val
        elif optname == 'help':
            options.help_file = val
        elif optname =='frames':
            options.show_frames = _str_to_bool(val, optname)
        elif optname in ('separate-classes', 'separate_classes'):
            options.list_classes_separately = _str_to_bool(val, optname)
        elif optname in ('src-code-tab-width', 'src_code_tab_width'):
            options.src_code_tab_width = _str_to_int(val, optname)

        # External API
        elif optname in ('external-api', 'external_api'):
            options.external_api.extend(_str_to_list(val))
        elif optname in ('external-api-file', 'external_api_file'):
            options.external_api_file.extend(_str_to_list(val))
        elif optname in ('external-api-root', 'external_api_root'):
            options.external_api_root.extend(_str_to_list(val))

        # Graph options
        elif optname == 'graph':
            graphtypes = _str_to_list(val)
            for graphtype in graphtypes:
                if graphtype not in GRAPH_TYPES + ('all',):
                    raise ValueError('"%s" expected one of: all, %s.' %
                                     (optname, ', '.join(GRAPH_TYPES)))
            options.graphs.extend(graphtypes)
        elif optname == 'dotpath':
            options.dotpath = val
        elif optname in ('graph-font', 'graph_font'):
            options.graph_font = val
        elif optname in ('graph-font-size', 'graph_font_size'):
            options.graph_font_size = _str_to_int(val, optname)
        elif optname == 'pstat':
            options.pstat_files.extend(_str_to_list(val))

        # Return value options
        elif optname in ('failon', 'fail-on', 'fail_on'):
            if val.lower().strip() in ('error', 'errors'):
                options.fail_on = log.ERROR
            elif val.lower().strip() in ('warning', 'warnings'):
                options.fail_on = log.WARNING
            elif val.lower().strip() in ('docstring_warning',
                                         'docstring_warnings'):
                options.fail_on = log.DOCSTRING_WARNING
            else:
                raise ValueError("%r expected one of: error, warning, "
                                 "docstring_warning" % optname)
        else:
            raise ValueError('Unknown option %s' % optname)

def _str_to_bool(val, optname):
    if val.lower() in ('0', 'no', 'false', 'n', 'f', 'hide'):
        return False
    elif val.lower() in ('1', 'yes', 'true', 'y', 't', 'show'):
        return True
    else:
        raise ValueError('"%s" option expected a boolean' % optname)
        
def _str_to_int(val, optname):
    try:
        return int(val)
    except ValueError:
        raise ValueError('"%s" option expected an int' % optname)

def _str_to_list(val):
    return val.replace(',', ' ').split()

######################################################################
#{ Interface
######################################################################

def main(options, names):
    # Set the debug flag, if '--debug' was specified.
    if options.debug:
        epydoc.DEBUG = True

    ## [XX] Did this serve a purpose?  Commenting out for now:
    #if options.action == 'text':
    #    if options.parse and options.introspect:
    #        options.parse = False

    # Set up the logger
    if options.simple_term:
        TerminalController.FORCE_SIMPLE_TERM = True
    if options.action == 'text':
        logger = None # no logger for text output.
    elif options.verbosity > 1:
        logger = ConsoleLogger(options.verbosity)
        log.register_logger(logger)
    else:
        # Each number is a rough approximation of how long we spend on
        # that task, used to divide up the unified progress bar.
        stages = [40,  # Building documentation
                  7,   # Merging parsed & introspected information
                  1,   # Linking imported variables
                  3,   # Indexing documentation
                  1,   # Checking for overridden methods
                  30,  # Parsing Docstrings
                  1,   # Inheriting documentation
                  2]   # Sorting & Grouping
        if options.load_pickle:
            stages = [30] # Loading pickled documentation
        if options.action == 'html': stages += [100]
        elif options.action == 'text': stages += [30]
        elif options.action == 'latex': stages += [60]
        elif options.action == 'dvi': stages += [60,30]
        elif options.action == 'ps': stages += [60,40]
        elif options.action == 'pdf': stages += [60,50]
        elif options.action == 'check': stages += [10]
        elif options.action == 'pickle': stages += [10]
        else: raise ValueError, '%r not supported' % options.action
        if options.parse and not options.introspect:
            del stages[1] # no merging
        if options.introspect and not options.parse:
            del stages[1:3] # no merging or linking
        logger = UnifiedProgressConsoleLogger(options.verbosity, stages)
        log.register_logger(logger)

    # check the output directory.
    if options.action not in ('text', 'check', 'pickle'):
        if os.path.exists(options.target):
            if not os.path.isdir(options.target):
                log.error("%s is not a directory" % options.target)
                sys.exit(1)

    if options.include_log:
        if options.action == 'html':
            if not os.path.exists(options.target):
                os.mkdir(options.target)
            log.register_logger(HTMLLogger(options.target, options))
        else:
            log.warning("--include-log requires --html")

    # Set the default docformat
    from epydoc import docstringparser
    docstringparser.DEFAULT_DOCFORMAT = options.docformat

    # Configure the external API linking
    if xlink is not None:
        try:
            xlink.ApiLinkReader.read_configuration(options, problematic=False)
        except Exception, exc:
            log.error("Error while configuring external API linking: %s: %s"
                % (exc.__class__.__name__, exc))

    # Set the dot path
    if options.dotpath:
        from epydoc.docwriter import dotgraph
        dotgraph.DOT_COMMAND = options.dotpath

    # Set the default graph font & size
    if options.graph_font:
        from epydoc.docwriter import dotgraph
        fontname = options.graph_font
        dotgraph.DotGraph.DEFAULT_NODE_DEFAULTS['fontname'] = fontname
        dotgraph.DotGraph.DEFAULT_EDGE_DEFAULTS['fontname'] = fontname
    if options.graph_font_size:
        from epydoc.docwriter import dotgraph
        fontsize = options.graph_font_size
        dotgraph.DotGraph.DEFAULT_NODE_DEFAULTS['fontsize'] = fontsize
        dotgraph.DotGraph.DEFAULT_EDGE_DEFAULTS['fontsize'] = fontsize

    # If the input name is a pickle file, then read the docindex that
    # it contains.  Otherwise, build the docs for the input names.
    if options.load_pickle:
        assert len(names) == 1
        log.start_progress('Deserializing')
        log.progress(0.1, 'Loading %r' % names[0])
        t0 = time.time()
        unpickler = pickle.Unpickler(open(names[0], 'rb'))
        unpickler.persistent_load = pickle_persistent_load
        docindex = unpickler.load()
        log.debug('deserialization time: %.1f sec' % (time.time()-t0))
        log.end_progress()
    else:
        # Build docs for the named values.
        from epydoc.docbuilder import build_doc_index
        exclude_parse = '|'.join(options.exclude_parse+options.exclude)
        exclude_introspect = '|'.join(options.exclude_introspect+
                                      options.exclude)
        docindex = build_doc_index(names, options.introspect, options.parse,
                                   add_submodules=(options.action!='text'),
                                   exclude_introspect=exclude_introspect,
                                   exclude_parse=exclude_parse)

    if docindex is None:
        if log.ERROR in logger.reported_message_levels:
            sys.exit(1)
        else:
            return # docbuilder already logged an error.

    # Load profile information, if it was given.
    if options.pstat_files:
        try: import pstats
        except ImportError:
            log.error("Could not import pstats -- ignoring pstat files.")
        try:
            profile_stats = pstats.Stats(options.pstat_files[0])
            for filename in options.pstat_files[1:]:
                profile_stats.add(filename)
        except KeyboardInterrupt: raise
        except Exception, e:
            log.error("Error reading pstat file: %s" % e)
            profile_stats = None
        if profile_stats is not None:
            docindex.read_profiling_info(profile_stats)

    # Perform the specified action.
    if options.action == 'html':
        write_html(docindex, options)
    elif options.action in ('latex', 'dvi', 'ps', 'pdf'):
        write_latex(docindex, options, options.action)
    elif options.action == 'text':
        write_text(docindex, options)
    elif options.action == 'check':
        check_docs(docindex, options)
    elif options.action == 'pickle':
        write_pickle(docindex, options)
    else:
        print >>sys.stderr, '\nUnsupported action %s!' % options.action

    # If we suppressed docstring warnings, then let the user know.
    if logger is not None and logger.suppressed_docstring_warning:
        if logger.suppressed_docstring_warning == 1:
            prefix = '1 markup error was found'
        else:
            prefix = ('%d markup errors were found' %
                      logger.suppressed_docstring_warning)
        log.warning("%s while processing docstrings.  Use the verbose "
                    "switch (-v) to display markup errors." % prefix)

    # Basic timing breakdown:
    if options.verbosity >= 2 and logger is not None:
        logger.print_times()

    # If we encountered any message types that we were requested to
    # fail on, then exit with status 2.
    if options.fail_on is not None:
        max_reported_message_level = max(logger.reported_message_levels)
        if max_reported_message_level >= options.fail_on:
            sys.exit(2)

def write_html(docindex, options):
    from epydoc.docwriter.html import HTMLWriter
    html_writer = HTMLWriter(docindex, **options.__dict__)
    if options.verbose > 0:
        log.start_progress('Writing HTML docs to %r' % options.target)
    else:
        log.start_progress('Writing HTML docs')
    html_writer.write(options.target)
    log.end_progress()

def write_pickle(docindex, options):
    """Helper for writing output to a pickle file, which can then be
    read in at a later time.  But loading the pickle is only marginally
    faster than building the docs from scratch, so this has pretty
    limited application."""
    if options.target == 'pickle':
        options.target = 'api.pickle'
    elif not options.target.endswith('.pickle'):
        options.target += '.pickle'

    log.start_progress('Serializing output')
    log.progress(0.2, 'Writing %r' % options.target)
    outfile = open(options.target, 'wb')
    pickler = pickle.Pickler(outfile, protocol=0)
    pickler.persistent_id = pickle_persistent_id
    pickler.dump(docindex)
    outfile.close()
    log.end_progress()

def pickle_persistent_id(obj):
    """Helper for pickling, which allows us to save and restore UNKNOWN,
    which is required to be identical to apidoc.UNKNOWN."""
    if obj is UNKNOWN: return 'UNKNOWN'
    else: return None

def pickle_persistent_load(identifier):
    """Helper for pickling, which allows us to save and restore UNKNOWN,
    which is required to be identical to apidoc.UNKNOWN."""
    if identifier == 'UNKNOWN': return UNKNOWN
    else: raise pickle.UnpicklingError, 'Invalid persistent id'

_RERUN_LATEX_RE = re.compile(r'(?im)^LaTeX\s+Warning:\s+Label\(s\)\s+may'
                             r'\s+have\s+changed.\s+Rerun')

def write_latex(docindex, options, format):
    from epydoc.docwriter.latex import LatexWriter
    latex_writer = LatexWriter(docindex, **options.__dict__)
    log.start_progress('Writing LaTeX docs')
    latex_writer.write(options.target)
    log.end_progress()
    # If we're just generating the latex, and not any output format,
    # then we're done.
    if format == 'latex': return
    
    if format == 'dvi': steps = 4
    elif format == 'ps': steps = 5
    elif format == 'pdf': steps = 6
    
    log.start_progress('Processing LaTeX docs')
    oldpath = os.path.abspath(os.curdir)
    running = None # keep track of what we're doing.
    try:
        try:
            os.chdir(options.target)

            # Clear any old files out of the way.
            for ext in 'tex aux log out idx ilg toc ind'.split():
                if os.path.exists('apidoc.%s' % ext):
                    os.remove('apidoc.%s' % ext)

            # The first pass generates index files.
            running = 'latex'
            log.progress(0./steps, 'LaTeX: First pass')
            run_subprocess('latex api.tex')

            # Build the index.
            running = 'makeindex'
            log.progress(1./steps, 'LaTeX: Build index')
            run_subprocess('makeindex api.idx')

            # The second pass generates our output.
            running = 'latex'
            log.progress(2./steps, 'LaTeX: Second pass')
            out, err = run_subprocess('latex api.tex')
            
            # The third pass is only necessary if the second pass
            # changed what page some things are on.
            running = 'latex'
            if _RERUN_LATEX_RE.match(out):
                log.progress(3./steps, 'LaTeX: Third pass')
                out, err = run_subprocess('latex api.tex')
 
            # A fourth path should (almost?) never be necessary.
            running = 'latex'
            if _RERUN_LATEX_RE.match(out):
                log.progress(3./steps, 'LaTeX: Fourth pass')
                run_subprocess('latex api.tex')

            # If requested, convert to postscript.
            if format in ('ps', 'pdf'):
                running = 'dvips'
                log.progress(4./steps, 'dvips')
                run_subprocess('dvips api.dvi -o api.ps -G0 -Ppdf')

            # If requested, convert to pdf.
            if format in ('pdf'):
                running = 'ps2pdf'
                log.progress(5./steps, 'ps2pdf')
                run_subprocess(
                    'ps2pdf -sPAPERSIZE#letter -dMaxSubsetPct#100 '
                    '-dSubsetFonts#true -dCompatibilityLevel#1.2 '
                    '-dEmbedAllFonts#true api.ps api.pdf')
        except RunSubprocessError, e:
            if running == 'latex':
                e.out = re.sub(r'(?sm)\A.*?!( LaTeX Error:)?', r'', e.out)
                e.out = re.sub(r'(?sm)\s*Type X to quit.*', '', e.out)
                e.out = re.sub(r'(?sm)^! Emergency stop.*', '', e.out)
            log.error("%s failed: %s" % (running, (e.out+e.err).lstrip()))
        except OSError, e:
            log.error("%s failed: %s" % (running, e))
    finally:
        os.chdir(oldpath)
        log.end_progress()

def write_text(docindex, options):
    log.start_progress('Writing output')
    from epydoc.docwriter.plaintext import PlaintextWriter
    plaintext_writer = PlaintextWriter()
    s = ''
    for apidoc in docindex.root:
        s += plaintext_writer.write(apidoc)
    log.end_progress()
    if isinstance(s, unicode):
        s = s.encode('ascii', 'backslashreplace')
    print s

def check_docs(docindex, options):
    from epydoc.checker import DocChecker
    DocChecker(docindex).check()
                
def cli():
    # Parse command-line arguments.
    options, names = parse_arguments()

    try:
        try:
            if options.profile:
                _profile()
            else:
                main(options, names)
        finally:
            log.close()
    except SystemExit:
        raise
    except KeyboardInterrupt:
        print '\n\n'
        print >>sys.stderr, 'Keyboard interrupt.'
    except:
        if options.debug: raise
        print '\n\n'
        exc_info = sys.exc_info()
        if isinstance(exc_info[0], basestring): e = exc_info[0]
        else: e = exc_info[1]
        print >>sys.stderr, ('\nUNEXPECTED ERROR:\n'
                             '%s\n' % (str(e) or e.__class__.__name__))
        print >>sys.stderr, 'Use --debug to see trace information.'
        sys.exit(3)
    
def _profile():
    # Hotshot profiler.
    if PROFILER == 'hotshot':
        try: import hotshot, hotshot.stats
        except ImportError:
            print >>sys.stderr, "Could not import profile module!"
            return
        try:
            prof = hotshot.Profile('hotshot.out')
            prof = prof.runctx('main(*parse_arguments())', globals(), {})
        except SystemExit:
            pass
        prof.close()
        # Convert profile.hotshot -> profile.out
        print 'Consolidating hotshot profiling info...'
        hotshot.stats.load('hotshot.out').dump_stats('profile.out')

    # Standard 'profile' profiler.
    elif PROFILER == 'profile':
        # cProfile module was added in Python 2.5 -- use it if its'
        # available, since it's faster.
        try: from cProfile import Profile
        except ImportError:
            try: from profile import Profile
            except ImportError:
                print >>sys.stderr, "Could not import profile module!"
                return

        # There was a bug in Python 2.4's profiler.  Check if it's
        # present, and if so, fix it.  (Bug was fixed in 2.4maint:
        # <http://mail.python.org/pipermail/python-checkins/
        #                         2005-September/047099.html>)
        if (hasattr(Profile, 'dispatch') and
            Profile.dispatch['c_exception'] is
            Profile.trace_dispatch_exception.im_func):
            trace_dispatch_return = Profile.trace_dispatch_return.im_func
            Profile.dispatch['c_exception'] = trace_dispatch_return
        try:
            prof = Profile()
            prof = prof.runctx('main(*parse_arguments())', globals(), {})
        except SystemExit:
            pass
        prof.dump_stats('profile.out')

    else:
        print >>sys.stderr, 'Unknown profiler %s' % PROFILER
        return
    
######################################################################
#{ Logging
######################################################################
    
class TerminalController:
    """
    A class that can be used to portably generate formatted output to
    a terminal.  See
    U{http://aspn.activestate.com/ASPN/Cookbook/Python/Recipe/475116}
    for documentation.  (This is a somewhat stripped-down version.)
    """
    BOL = ''             #: Move the cursor to the beginning of the line
    UP = ''              #: Move the cursor up one line
    DOWN = ''            #: Move the cursor down one line
    LEFT = ''            #: Move the cursor left one char
    RIGHT = ''           #: Move the cursor right one char
    CLEAR_EOL = ''       #: Clear to the end of the line.
    CLEAR_LINE = ''      #: Clear the current line; cursor to BOL.
    BOLD = ''            #: Turn on bold mode
    NORMAL = ''          #: Turn off all modes
    COLS = 75            #: Width of the terminal (default to 75)
    BLACK = BLUE = GREEN = CYAN = RED = MAGENTA = YELLOW = WHITE = ''
    
    _STRING_CAPABILITIES = """
    BOL=cr UP=cuu1 DOWN=cud1 LEFT=cub1 RIGHT=cuf1
    CLEAR_EOL=el BOLD=bold UNDERLINE=smul NORMAL=sgr0""".split()
    _COLORS = """BLACK BLUE GREEN CYAN RED MAGENTA YELLOW WHITE""".split()
    _ANSICOLORS = "BLACK RED GREEN YELLOW BLUE MAGENTA CYAN WHITE".split()

    #: If this is set to true, then new TerminalControllers will
    #: assume that the terminal is not capable of doing manipulation
    #: of any kind.
    FORCE_SIMPLE_TERM = False

    def __init__(self, term_stream=sys.stdout):
        # If the stream isn't a tty, then assume it has no capabilities.
        if not term_stream.isatty(): return
        if self.FORCE_SIMPLE_TERM: return

        # Curses isn't available on all platforms
        try: import curses
        except:
            # If it's not available, then try faking enough to get a
            # simple progress bar.
            self.BOL = '\r'
            self.CLEAR_LINE = '\r' + ' '*self.COLS + '\r'
            
        # Check the terminal type.  If we fail, then assume that the
        # terminal has no capabilities.
        try: curses.setupterm()
        except: return

        # Look up numeric capabilities.
        self.COLS = curses.tigetnum('cols')
        
        # Look up string capabilities.
        for capability in self._STRING_CAPABILITIES:
            (attrib, cap_name) = capability.split('=')
            setattr(self, attrib, self._tigetstr(cap_name) or '')
        if self.BOL and self.CLEAR_EOL:
            self.CLEAR_LINE = self.BOL+self.CLEAR_EOL

        # Colors
        set_fg = self._tigetstr('setf')
        if set_fg:
            for i,color in zip(range(len(self._COLORS)), self._COLORS):
                setattr(self, color, curses.tparm(set_fg, i) or '')
        set_fg_ansi = self._tigetstr('setaf')
        if set_fg_ansi:
            for i,color in zip(range(len(self._ANSICOLORS)), self._ANSICOLORS):
                setattr(self, color, curses.tparm(set_fg_ansi, i) or '')

    def _tigetstr(self, cap_name):
        # String capabilities can include "delays" of the form "$<2>".
        # For any modern terminal, we should be able to just ignore
        # these, so strip them out.
        import curses
        cap = curses.tigetstr(cap_name) or ''
        return re.sub(r'\$<\d+>[/*]?', '', cap)

class ConsoleLogger(log.Logger):
    def __init__(self, verbosity, progress_mode=None):
        self._verbosity = verbosity
        self._progress = None
        self._message_blocks = []
        # For ETA display:
        self._progress_start_time = None
        # For per-task times:
        self._task_times = []
        self._progress_header = None

        self.reported_message_levels = set()
        """This set contains all the message levels (WARNING, ERROR,
        etc) that have been reported.  It is used by the options
        --fail-on-warning etc to determine the return value."""
        
        self.suppressed_docstring_warning = 0
        """This variable will be incremented once every time a
        docstring warning is reported tothe logger, but the verbosity
        level is too low for it to be displayed."""

        self.term = TerminalController()

        # Set the progress bar mode.
        if verbosity >= 2: self._progress_mode = 'list'
        elif verbosity >= 0:
            if progress_mode is not None:
                self._progress_mode = progress_mode
            elif self.term.COLS < 15:
                self._progress_mode = 'simple-bar'
            elif self.term.BOL and self.term.CLEAR_EOL and self.term.UP:
                self._progress_mode = 'multiline-bar'
            elif self.term.BOL and self.term.CLEAR_LINE:
                self._progress_mode = 'bar'
            else:
                self._progress_mode = 'simple-bar'
        else: self._progress_mode = 'hide'

    def start_block(self, header):
        self._message_blocks.append( (header, []) )

    def end_block(self):
        header, messages = self._message_blocks.pop()
        if messages:
            width = self.term.COLS - 5 - 2*len(self._message_blocks)
            prefix = self.term.CYAN+self.term.BOLD+'| '+self.term.NORMAL
            divider = (self.term.CYAN+self.term.BOLD+'+'+'-'*(width-1)+
                       self.term.NORMAL)
            # Mark up the header:
            header = wordwrap(header, right=width-2, splitchars='\\/').rstrip()
            header = '\n'.join([prefix+self.term.CYAN+l+self.term.NORMAL
                                for l in header.split('\n')])
            # Construct the body:
            body = ''
            for message in messages:
                if message.endswith('\n'): body += message
                else: body += message+'\n'
            # Indent the body:
            body = '\n'.join([prefix+'  '+l for l in body.split('\n')])
            # Put it all together:
            message = divider + '\n' + header + '\n' + body + '\n'
            self._report(message)
            
    def _format(self, prefix, message, color):
        """
        Rewrap the message; but preserve newlines, and don't touch any
        lines that begin with spaces.
        """
        lines = message.split('\n')
        startindex = indent = len(prefix)
        for i in range(len(lines)):
            if lines[i].startswith(' '):
                lines[i] = ' '*(indent-startindex) + lines[i] + '\n'
            else:
                width = self.term.COLS - 5 - 4*len(self._message_blocks)
                lines[i] = wordwrap(lines[i], indent, width, startindex, '\\/')
            startindex = 0
        return color+prefix+self.term.NORMAL+''.join(lines)

    def log(self, level, message):
        self.reported_message_levels.add(level)
        if self._verbosity >= -2 and level >= log.ERROR:
            message = self._format('  Error: ', message, self.term.RED)
        elif self._verbosity >= -1 and level >= log.WARNING:
            message = self._format('Warning: ', message, self.term.YELLOW)
        elif self._verbosity >= 1 and level >= log.DOCSTRING_WARNING:
            message = self._format('Warning: ', message, self.term.YELLOW)
        elif self._verbosity >= 3 and level >= log.INFO:
            message = self._format('   Info: ', message, self.term.NORMAL)
        elif epydoc.DEBUG and level == log.DEBUG:
            message = self._format('  Debug: ', message, self.term.CYAN)
        else:
            if level >= log.DOCSTRING_WARNING:
                self.suppressed_docstring_warning += 1
            return
            
        self._report(message)

    def _report(self, message):
        if not message.endswith('\n'): message += '\n'
        
        if self._message_blocks:
            self._message_blocks[-1][-1].append(message)
        else:
            # If we're in the middle of displaying a progress bar,
            # then make room for the message.
            if self._progress_mode == 'simple-bar':
                if self._progress is not None:
                    print
                    self._progress = None
            if self._progress_mode == 'bar':
                sys.stdout.write(self.term.CLEAR_LINE)
            if self._progress_mode == 'multiline-bar':
                sys.stdout.write((self.term.CLEAR_EOL + '\n')*2 +
                                 self.term.CLEAR_EOL + self.term.UP*2)

            # Display the message message.
            sys.stdout.write(message)
            sys.stdout.flush()
                
    def progress(self, percent, message=''):
        percent = min(1.0, percent)
        message = '%s' % message
        
        if self._progress_mode == 'list':
            if message:
                print '[%3d%%] %s' % (100*percent, message)
                sys.stdout.flush()
                
        elif self._progress_mode == 'bar':
            dots = int((self.term.COLS/2-8)*percent)
            background = '-'*(self.term.COLS/2-8)
            if len(message) > self.term.COLS/2:
                message = message[:self.term.COLS/2-3]+'...'
            sys.stdout.write(self.term.CLEAR_LINE + '%3d%% '%(100*percent) +
                             self.term.GREEN + '[' + self.term.BOLD +
                             '='*dots + background[dots:] + self.term.NORMAL +
                             self.term.GREEN + '] ' + self.term.NORMAL +
                             message + self.term.BOL)
            sys.stdout.flush()
            self._progress = percent
        elif self._progress_mode == 'multiline-bar':
            dots = int((self.term.COLS-10)*percent)
            background = '-'*(self.term.COLS-10)
            
            if len(message) > self.term.COLS-10:
                message = message[:self.term.COLS-10-3]+'...'
            else:
                message = message.center(self.term.COLS-10)

            time_elapsed = time.time()-self._progress_start_time
            if percent > 0:
                time_remain = (time_elapsed / percent) * (1-percent)
            else:
                time_remain = 0

            sys.stdout.write(
                # Line 1:
                self.term.CLEAR_EOL + '      ' +
                '%-8s' % self._timestr(time_elapsed) +
                self.term.BOLD + 'Progress:'.center(self.term.COLS-26) +
                self.term.NORMAL + '%8s' % self._timestr(time_remain) + '\n' +
                # Line 2:
                self.term.CLEAR_EOL + ('%3d%% ' % (100*percent)) +
                self.term.GREEN + '[' +  self.term.BOLD + '='*dots +
                background[dots:] + self.term.NORMAL + self.term.GREEN +
                ']' + self.term.NORMAL + '\n' +
                # Line 3:
                self.term.CLEAR_EOL + '      ' + message + self.term.BOL +
                self.term.UP + self.term.UP)
            
            sys.stdout.flush()
            self._progress = percent
        elif self._progress_mode == 'simple-bar':
            if self._progress is None:
                sys.stdout.write('  [')
                self._progress = 0.0
            dots = int((self.term.COLS-2)*percent)
            progress_dots = int((self.term.COLS-2)*self._progress)
            if dots > progress_dots:
                sys.stdout.write('.'*(dots-progress_dots))
                sys.stdout.flush()
                self._progress = percent

    def _timestr(self, dt):
        dt = int(dt)
        if dt >= 3600:
            return '%d:%02d:%02d' % (dt/3600, dt%3600/60, dt%60)
        else:
            return '%02d:%02d' % (dt/60, dt%60)

    def start_progress(self, header=None):
        if self._progress is not None:
            raise ValueError
        self._progress = None
        self._progress_start_time = time.time()
        self._progress_header = header
        if self._progress_mode != 'hide' and header:
            print self.term.BOLD + header + self.term.NORMAL

    def end_progress(self):
        self.progress(1.)
        if self._progress_mode == 'bar':
            sys.stdout.write(self.term.CLEAR_LINE)
        if self._progress_mode == 'multiline-bar':
                sys.stdout.write((self.term.CLEAR_EOL + '\n')*2 +
                                 self.term.CLEAR_EOL + self.term.UP*2)
        if self._progress_mode == 'simple-bar':
            print ']'
        self._progress = None
        self._task_times.append( (time.time()-self._progress_start_time,
                                  self._progress_header) )

    def print_times(self):
        print
        print 'Timing summary:'
        total = sum([time for (time, task) in self._task_times])
        max_t = max([time for (time, task) in self._task_times])
        for (time, task) in self._task_times:
            task = task[:31]
            print '  %s%s %7.1fs' % (task, '.'*(35-len(task)), time),
            if self.term.COLS > 55:
                print '|'+'=' * int((self.term.COLS-53) * time / max_t)
            else:
                print
        print

class UnifiedProgressConsoleLogger(ConsoleLogger):
    def __init__(self, verbosity, stages, progress_mode=None):
        self.stage = 0
        self.stages = stages
        self.task = None
        ConsoleLogger.__init__(self, verbosity, progress_mode)
        
    def progress(self, percent, message=''):
        #p = float(self.stage-1+percent)/self.stages
        i = self.stage-1
        p = ((sum(self.stages[:i]) + percent*self.stages[i]) /
             float(sum(self.stages)))

        if message is UNKNOWN: message = None
        if message: message = '%s: %s' % (self.task, message)
        ConsoleLogger.progress(self, p, message)

    def start_progress(self, header=None):
        self.task = header
        if self.stage == 0:
            ConsoleLogger.start_progress(self)
        self.stage += 1

    def end_progress(self):
        if self.stage == len(self.stages):
            ConsoleLogger.end_progress(self)

    def print_times(self):
        pass

class HTMLLogger(log.Logger):
    """
    A logger used to generate a log of all warnings and messages to an
    HTML file.
    """
    
    FILENAME = "epydoc-log.html"
    HEADER = textwrap.dedent('''\
        <?xml version="1.0" encoding="ascii"?>
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
                  "DTD/xhtml1-transitional.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
        <head>
          <title>Epydoc Log</title>
          <link rel="stylesheet" href="epydoc.css" type="text/css" />
        </head>
        
        <body bgcolor="white" text="black" link="blue" vlink="#204080"
              alink="#204080">
        <h1 class="epydoc">Epydoc Log</h1>
        <p class="log">Epydoc started at %s</p>''')
    START_BLOCK = '<div class="log-block"><h2 class="log-hdr">%s</h2>'
    MESSAGE = ('<div class="log-%s"><b>%s</b>: \n'
               '%s</div>\n')
    END_BLOCK = '</div>'
    FOOTER = "</body>\n</html>\n"
    
    def __init__(self, directory, options):
        self.start_time = time.time()
        self.out = open(os.path.join(directory, self.FILENAME), 'w')
        self.out.write(self.HEADER % time.ctime(self.start_time))
        self.is_empty = True
        self.options = options

    def write_options(self, options):
        self.out.write(self.START_BLOCK % 'Epydoc Options')
        msg = '<table border="0" cellpadding="0" cellspacing="0">\n'
        opts = [(key, getattr(options, key)) for key in dir(options)
                if key not in dir(optparse.Values)]
        opts = [(val==OPTION_DEFAULTS.get(key), key, val)
                for (key, val) in opts]
        for is_default, key, val in sorted(opts):
            css = is_default and 'opt-default' or 'opt-changed'
            msg += ('<tr valign="top" class="%s"><td valign="top">%s</td>'
                    '<td valign="top"><tt>&nbsp;=&nbsp;</tt></td>'
                    '<td valign="top"><tt>%s</tt></td></tr>' %
                    (css, key, plaintext_to_html(repr(val))))
        msg += '</table>\n'
        self.out.write('<div class="log-info">\n%s</div>\n' % msg)
        self.out.write(self.END_BLOCK)

    def start_block(self, header):
        self.out.write(self.START_BLOCK % header)

    def end_block(self):
        self.out.write(self.END_BLOCK)

    def log(self, level, message):
        if message.endswith("(-v) to display markup errors."): return
        if level >= log.ERROR:
            self.out.write(self._message('error', message))
        elif level >= log.WARNING:
            self.out.write(self._message('warning', message))
        elif level >= log.DOCSTRING_WARNING:
            self.out.write(self._message('docstring warning', message))

    def _message(self, level, message):
        self.is_empty = False
        message = plaintext_to_html(message)
        if '\n' in message:
            message = '<pre class="log">%s</pre>' % message
        hdr = ' '.join([w.capitalize() for w in level.split()])
        return self.MESSAGE % (level.split()[-1], hdr, message)

    def close(self):
        if self.is_empty:
            self.out.write('<div class="log-info">'
                           'No warnings or errors!</div>')
        self.write_options(self.options)
        self.out.write('<p class="log">Epydoc finished at %s</p>\n'
                       '<p class="log">(Elapsed time: %s)</p>' %
                       (time.ctime(), self._elapsed_time()))
        self.out.write(self.FOOTER)
        self.out.close()

    def _elapsed_time(self):
        secs = int(time.time()-self.start_time)
        if secs < 60:
            return '%d seconds' % secs
        if secs < 3600:
            return '%d minutes, %d seconds' % (secs/60, secs%60)
        else:
            return '%d hours, %d minutes' % (secs/3600, secs%3600)
            

######################################################################
## main
######################################################################

if __name__ == '__main__':
    cli()

