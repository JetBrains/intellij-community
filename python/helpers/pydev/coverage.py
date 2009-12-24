#!/usr/bin/python
#
#             Perforce Defect Tracking Integration Project
#              <http://www.ravenbrook.com/project/p4dti/>
#
#                   COVERAGE.PY -- COVERAGE TESTING
#
#             Gareth Rees, Ravenbrook Limited, 2001-12-04
#                     Ned Batchelder, 2004-12-12
#         http://nedbatchelder.com/code/modules/coverage.html
#
#
# 1. INTRODUCTION
#
# This module provides coverage testing for Python code.
#
# The intended readership is all Python developers.
#
# This document is not confidential.
#
# See [GDR 2001-12-04a] for the command-line interface, programmatic
# interface and limitations.  See [GDR 2001-12-04b] for requirements and
# design.

r"""Usage:

coverage.py -x [-p] MODULE.py [ARG1 ARG2 ...]
    Execute module, passing the given command-line arguments, collecting
    coverage data. With the -p option, write to a temporary file containing
    the machine name and process ID.

coverage.py -e
    Erase collected coverage data.

coverage.py -waitfor
    it's the same as -r -m, but...
    goes to a raw_input() and waits for the files that should be executed...

coverage.py -c
    Collect data from multiple coverage files (as created by -p option above)
    and store it into a single file representing the union of the coverage.

coverage.py -r [-m] [-o dir1,dir2,...] FILE1 FILE2 ...
    Report on the statement coverage for the given files.  With the -m
    option, show line numbers of the statements that weren't executed.

coverage.py -a [-d dir] [-o dir1,dir2,...] FILE1 FILE2 ...
    Make annotated copies of the given files, marking statements that
    are executed with > and statements that are missed with !.  With
    the -d option, make the copies in that directory.  Without the -d
    option, make each copy in the same directory as the original.

-o dir,dir2,...
  Omit reporting or annotating files when their filename path starts with
  a directory listed in the omit list.
  e.g. python coverage.py -i -r -o c:\python23,lib\enthought\traits

Coverage data is saved in the file .coverage by default.  Set the
COVERAGE_FILE environment variable to save it somewhere else."""

__version__ = "2.78.20070930"    # see detailed history at the end of this file.

import compiler
import compiler.visitor
import glob
import os
import re
import string
import symbol
import sys
import threading
import token
import types
import email
from socket import gethostname

# Python version compatibility
try:
    strclass = basestring   # new to 2.3
except:
    strclass = str

# 2. IMPLEMENTATION
#
# This uses the "singleton" pattern.
#
# The word "morf" means a module object (from which the source file can
# be deduced by suitable manipulation of the __file__ attribute) or a
# filename.
#
# When we generate a coverage report we have to canonicalize every
# filename in the coverage dictionary just in case it refers to the
# module we are reporting on.  It seems a shame to throw away this
# information so the data in the coverage dictionary is transferred to
# the 'cexecuted' dictionary under the canonical filenames.
#
# The coverage dictionary is called "c" and the trace function "t".  The
# reason for these short names is that Python looks up variables by name
# at runtime and so execution time depends on the length of variables!
# In the bottleneck of this application it's appropriate to abbreviate
# names to increase speed.

class StatementFindingAstVisitor(compiler.visitor.ASTVisitor):
    """ A visitor for a parsed Abstract Syntax Tree which finds executable
        statements.
    """
    def __init__(self, statements, excluded, suite_spots):
        compiler.visitor.ASTVisitor.__init__(self)
        self.statements = statements
        self.excluded = excluded
        self.suite_spots = suite_spots
        self.excluding_suite = 0
        
    def doRecursive(self, node):
        for n in node.getChildNodes():
            self.dispatch(n)

    visitStmt = visitModule = doRecursive
    
    def doCode(self, node):
        if hasattr(node, 'decorators') and node.decorators:
            self.dispatch(node.decorators)
            self.recordAndDispatch(node.code)
        else:
            self.doSuite(node, node.code)
            
    visitFunction = visitClass = doCode

    def getFirstLine(self, node):
        # Find the first line in the tree node.
        lineno = node.lineno
        for n in node.getChildNodes():
            f = self.getFirstLine(n)
            if lineno and f:
                lineno = min(lineno, f)
            else:
                lineno = lineno or f
        return lineno

    def getLastLine(self, node):
        # Find the first line in the tree node.
        lineno = node.lineno
        for n in node.getChildNodes():
            lineno = max(lineno, self.getLastLine(n))
        return lineno
    
    def doStatement(self, node):
        self.recordLine(self.getFirstLine(node))

    visitAssert = visitAssign = visitAssTuple = visitPrint = \
        visitPrintnl = visitRaise = visitSubscript = visitDecorators = \
        doStatement
    
    def visitPass(self, node):
        # Pass statements have weird interactions with docstrings.  If this
        # pass statement is part of one of those pairs, claim that the statement
        # is on the later of the two lines.
        l = node.lineno
        if l:
            lines = self.suite_spots.get(l, [l, l])
            self.statements[lines[1]] = 1
        
    def visitDiscard(self, node):
        # Discard nodes are statements that execute an expression, but then
        # discard the results.  This includes function calls, so we can't 
        # ignore them all.  But if the expression is a constant, the statement
        # won't be "executed", so don't count it now.
        if node.expr.__class__.__name__ != 'Const':
            self.doStatement(node)

    def recordNodeLine(self, node):
        # Stmt nodes often have None, but shouldn't claim the first line of
        # their children (because the first child might be an ignorable line
        # like "global a").
        if node.__class__.__name__ != 'Stmt':
            return self.recordLine(self.getFirstLine(node))
        else:
            return 0
    
    def recordLine(self, lineno):
        # Returns a bool, whether the line is included or excluded.
        if lineno:
            # Multi-line tests introducing suites have to get charged to their
            # keyword.
            if lineno in self.suite_spots:
                lineno = self.suite_spots[lineno][0]
            # If we're inside an excluded suite, record that this line was
            # excluded.
            if self.excluding_suite:
                self.excluded[lineno] = 1
                return 0
            # If this line is excluded, or suite_spots maps this line to
            # another line that is exlcuded, then we're excluded.
            elif self.excluded.has_key(lineno) or \
                 self.suite_spots.has_key(lineno) and \
                 self.excluded.has_key(self.suite_spots[lineno][1]):
                return 0
            # Otherwise, this is an executable line.
            else:
                self.statements[lineno] = 1
                return 1
        return 0
    
    default = recordNodeLine
    
    def recordAndDispatch(self, node):
        self.recordNodeLine(node)
        self.dispatch(node)

    def doSuite(self, intro, body, exclude=0):
        exsuite = self.excluding_suite
        if exclude or (intro and not self.recordNodeLine(intro)):
            self.excluding_suite = 1
        self.recordAndDispatch(body)
        self.excluding_suite = exsuite
        
    def doPlainWordSuite(self, prevsuite, suite):
        # Finding the exclude lines for else's is tricky, because they aren't
        # present in the compiler parse tree.  Look at the previous suite,
        # and find its last line.  If any line between there and the else's
        # first line are excluded, then we exclude the else.
        lastprev = self.getLastLine(prevsuite)
        firstelse = self.getFirstLine(suite)
        for l in range(lastprev + 1, firstelse):
            if self.suite_spots.has_key(l):
                self.doSuite(None, suite, exclude=self.excluded.has_key(l))
                break
        else:
            self.doSuite(None, suite)
        
    def doElse(self, prevsuite, node):
        if node.else_:
            self.doPlainWordSuite(prevsuite, node.else_)
    
    def visitFor(self, node):
        self.doSuite(node, node.body)
        self.doElse(node.body, node)

    visitWhile = visitFor

    def visitIf(self, node):
        # The first test has to be handled separately from the rest.
        # The first test is credited to the line with the "if", but the others
        # are credited to the line with the test for the elif.
        self.doSuite(node, node.tests[0][1])
        for t, n in node.tests[1:]:
            self.doSuite(t, n)
        self.doElse(node.tests[-1][1], node)

    def visitTryExcept(self, node):
        self.doSuite(node, node.body)
        for i in range(len(node.handlers)):
            a, b, h = node.handlers[i]
            if not a:
                # It's a plain "except:".  Find the previous suite.
                if i > 0:
                    prev = node.handlers[i - 1][2]
                else:
                    prev = node.body
                self.doPlainWordSuite(prev, h)
            else:
                self.doSuite(a, h)
        self.doElse(node.handlers[-1][2], node)
    
    def visitTryFinally(self, node):
        self.doSuite(node, node.body)
        self.doPlainWordSuite(node.body, node.final)
        
    def visitWith(self, node):
        self.doSuite(node, node.body)
        
    def visitGlobal(self, node):
        # "global" statements don't execute like others (they don't call the
        # trace function), so don't record their line numbers.
        pass


def getCoverageLoc():
    global cache_location
    return cache_location


class CoverageException(Exception): pass

class coverage:
    # Name of the cache file (unless environment variable is set).
    cache_default = ".coverage"

    # Environment variable naming the cache file.
    cache_env = "COVERAGE_FILE"

    # A dictionary with an entry for (Python source file name, line number
    # in that file) if that line has been executed.
    c = {}
    
    # A map from canonical Python source file name to a dictionary in
    # which there's an entry for each line number that has been
    # executed.
    cexecuted = {}

    # Cache of results of calling the analysis2() method, so that you can
    # specify both -r and -a without doing double work.
    analysis_cache = {}

    # Cache of results of calling the canonical_filename() method, to
    # avoid duplicating work.
    canonical_filename_cache = {}

    def __init__(self):
        self.usecache = 1
        self.cache = None
        self.parallel_mode = False
        self.exclude_re = ''
        self.nesting = 0
        self.cstack = []
        self.xstack = []
        self.relative_dir = os.path.normcase(os.path.abspath(os.curdir) + os.sep)
        self.exclude('# *pragma[: ]*[nN][oO] *[cC][oO][vV][eE][rR]')

    # t(f, x, y).  This method is passed to sys.settrace as a trace function.  
    # See [van Rossum 2001-07-20b, 9.2] for an explanation of sys.settrace and 
    # the arguments and return value of the trace function.
    # See [van Rossum 2001-07-20a, 3.2] for a description of frame and code
    # objects.
    
    def t(self, f, w, unused):                                 #pragma: no cover
        if w == 'line':
            #print "Executing %s @ %d" % (f.f_code.co_filename, f.f_lineno)
            self.c[(f.f_code.co_filename, f.f_lineno)] = 1
            for c in self.cstack:
                c[(f.f_code.co_filename, f.f_lineno)] = 1
        return self.t
    
    def help(self, error=None):     #pragma: no cover
        if error:
            print error
            print
        print __doc__
        sys.exit(1)

    def command_line(self, argv, help_fn=None):
        import getopt
        help_fn = help_fn or self.help
        settings = {}
        optmap = {
            '-a': 'annotate',
            '-c': 'collect',
            '-d:': 'directory=',
            '-e': 'erase',
            '-h': 'help',
            '-i': 'ignore-errors',
            '-m': 'show-missing',
            '-p': 'parallel-mode',
            '-r': 'report',
            '-x': 'execute',
            '-o:': 'omit=',
            }
        short_opts = string.join(map(lambda o: o[1:], optmap.keys()), '')
        long_opts = optmap.values()
        options, args = getopt.getopt(argv, short_opts, long_opts)
        for o, a in options:
            if optmap.has_key(o):
                settings[optmap[o]] = 1
            elif optmap.has_key(o + ':'):
                settings[optmap[o + ':']] = a
            elif o[2:] in long_opts:
                settings[o[2:]] = 1
            elif o[2:] + '=' in long_opts:
                settings[o[2:] + '='] = a
            else:       #pragma: no cover
                pass    # Can't get here, because getopt won't return anything unknown.

        if settings.get('help'):
            help_fn()

        for i in ['erase', 'execute']:
            for j in ['annotate', 'report', 'collect']:
                if settings.get(i) and settings.get(j):
                    help_fn("You can't specify the '%s' and '%s' "
                              "options at the same time." % (i, j))

        args_needed = (settings.get('execute')
                       or settings.get('annotate')
                       or settings.get('report'))
        action = (settings.get('erase') 
                  or settings.get('collect')
                  or args_needed)
        if not action:
            help_fn("You must specify at least one of -e, -x, -c, -r, or -a.")
        if not args_needed and args:
            help_fn("Unexpected arguments: %s" % " ".join(args))
        
        self.parallel_mode = settings.get('parallel-mode')
        self.get_ready()

        if settings.get('erase'):
            self.erase()
        if settings.get('execute'):
            if not args:
                help_fn("Nothing to do.")
            sys.argv = args
            self.start()
            import __main__
            sys.path[0] = os.path.dirname(sys.argv[0])
            execfile(sys.argv[0], __main__.__dict__)
        if settings.get('collect'):
            self.collect()
        if not args:
            args = self.cexecuted.keys()
        
        ignore_errors = settings.get('ignore-errors')
        show_missing = settings.get('show-missing')
        directory = settings.get('directory=')

        omit = settings.get('omit=')
        if omit is not None:
            omit = omit.split(',')
        else:
            omit = []

        if settings.get('report'):
            self.report(args, show_missing, ignore_errors, omit_prefixes=omit)
        if settings.get('annotate'):
            self.annotate(args, directory, ignore_errors, omit_prefixes=omit)

    def use_cache(self, usecache, cache_file=None):
        self.usecache = usecache
        if cache_file and not self.cache:
            self.cache_default = cache_file
        
    def get_ready(self, parallel_mode=False):
        if self.usecache and not self.cache:
            self.cache = getCoverageLoc()
            if self.parallel_mode:
                self.cache += "." + gethostname() + "." + str(os.getpid())
            self.restore()
        self.analysis_cache = {}
        
    def start(self, parallel_mode=False):
        self.get_ready()
        if self.nesting == 0:                               #pragma: no cover
            sys.settrace(self.t)
            if hasattr(threading, 'settrace'):
                threading.settrace(self.t)
        self.nesting += 1
        
    def stop(self):
        self.nesting -= 1
        if self.nesting == 0:                               #pragma: no cover
            sys.settrace(None)
            if hasattr(threading, 'settrace'):
                threading.settrace(None)

    def erase(self):
        self.get_ready()
        self.c = {}
        self.analysis_cache = {}
        self.cexecuted = {}
        if self.cache and os.path.exists(self.cache):
            os.remove(self.cache)

    def exclude(self, re):
        if self.exclude_re:
            self.exclude_re += "|"
        self.exclude_re += "(" + re + ")"

    def begin_recursive(self):
        self.cstack.append(self.c)
        self.xstack.append(self.exclude_re)
        
    def end_recursive(self):
        self.c = self.cstack.pop()
        self.exclude_re = self.xstack.pop()

    # save().  Save coverage data to the coverage cache.

    def save(self):
        if self.usecache and self.cache:
            self.canonicalize_filenames()
            cache = open(self.cache, 'wb')
            import marshal
            marshal.dump(self.cexecuted, cache)
            cache.close()

    # restore().  Restore coverage data from the coverage cache (if it exists).

    def restore(self):
        self.c = {}
        self.cexecuted = {}
        assert self.usecache
        if os.path.exists(self.cache):
            self.cexecuted = self.restore_file(self.cache)

    def restore_file(self, file_name):
        try:
            cache = open(file_name, 'rb')
            import marshal
            cexecuted = marshal.load(cache)
            cache.close()
            if isinstance(cexecuted, types.DictType):
                return cexecuted
            else:
                return {}
        except:
            return {}

    # collect(). Collect data in multiple files produced by parallel mode

    def collect(self):
        cache_dir, local = os.path.split(self.cache)
        for f in os.listdir(cache_dir or '.'):
            if not f.startswith(local):
                continue

            full_path = os.path.join(cache_dir, f)
            cexecuted = self.restore_file(full_path)
            self.merge_data(cexecuted)

    def merge_data(self, new_data):
        for file_name, file_data in new_data.items():
            if self.cexecuted.has_key(file_name):
                self.merge_file_data(self.cexecuted[file_name], file_data)
            else:
                self.cexecuted[file_name] = file_data

    def merge_file_data(self, cache_data, new_data):
        for line_number in new_data.keys():
            if not cache_data.has_key(line_number):
                cache_data[line_number] = new_data[line_number]

    # canonical_filename(filename).  Return a canonical filename for the
    # file (that is, an absolute path with no redundant components and
    # normalized case).  See [GDR 2001-12-04b, 3.3].

    def canonical_filename(self, filename):
        if not self.canonical_filename_cache.has_key(filename):
            f = filename
            if os.path.isabs(f) and not os.path.exists(f):
                f = os.path.basename(f)
            if not os.path.isabs(f):
                for path in [os.curdir] + sys.path:
                    g = os.path.join(path, f)
                    if os.path.exists(g):
                        f = g
                        break
            cf = os.path.normcase(os.path.abspath(f))
            self.canonical_filename_cache[filename] = cf
        return self.canonical_filename_cache[filename]

    # canonicalize_filenames().  Copy results from "c" to "cexecuted", 
    # canonicalizing filenames on the way.  Clear the "c" map.

    def canonicalize_filenames(self):
        for filename, lineno in self.c.keys():
            if filename == '<string>':
                # Can't do anything useful with exec'd strings, so skip them.
                continue
            f = self.canonical_filename(filename)
            if not self.cexecuted.has_key(f):
                self.cexecuted[f] = {}
            self.cexecuted[f][lineno] = 1
        self.c = {}

    # morf_filename(morf).  Return the filename for a module or file.

    def morf_filename(self, morf):
        if isinstance(morf, types.ModuleType) or (hasattr(email, 'LazyImporter') and isinstance(morf, email.LazyImporter)):
            if not hasattr(morf, '__file__'):
                raise CoverageException("Module has no __file__ attribute.")
            f = morf.__file__
        else:
            f = morf
        return self.canonical_filename(f)

    # analyze_morf(morf).  Analyze the module or filename passed as
    # the argument.  If the source code can't be found, raise an error.
    # Otherwise, return a tuple of (1) the canonical filename of the
    # source code for the module, (2) a list of lines of statements
    # in the source code, (3) a list of lines of excluded statements,
    # and (4), a map of line numbers to multi-line line number ranges, for
    # statements that cross lines.
    
    def analyze_morf(self, morf):
        if self.analysis_cache.has_key(morf):
            return self.analysis_cache[morf]
        filename = self.morf_filename(morf)
        ext = os.path.splitext(filename)[1]
        if ext == '.pyc':
            if not os.path.exists(filename[:-1]):
                raise CoverageException(
                    "No source for compiled code '%s'." % filename
                    )
            filename = filename[:-1]
        source = open(filename, 'r')
        try:
            lines, excluded_lines, line_map = self.find_executable_statements(
                source.read(), exclude=self.exclude_re
                )
        except SyntaxError, synerr:
            raise CoverageException(
                "Couldn't parse '%s' as Python source: '%s' at line %d" % 
                    (filename, synerr.msg, synerr.lineno)
                )            
        source.close()
        result = filename, lines, excluded_lines, line_map
        self.analysis_cache[morf] = result
        return result

    def first_line_of_tree(self, tree):
        while True:
            if len(tree) == 3 and type(tree[2]) == type(1):
                return tree[2]
            tree = tree[1]
    
    def last_line_of_tree(self, tree):
        while True:
            if len(tree) == 3 and type(tree[2]) == type(1):
                return tree[2]
            tree = tree[-1]
    
    def find_docstring_pass_pair(self, tree, spots):
        for i in range(1, len(tree)):
            if self.is_string_constant(tree[i]) and self.is_pass_stmt(tree[i + 1]):
                first_line = self.first_line_of_tree(tree[i])
                last_line = self.last_line_of_tree(tree[i + 1])
                self.record_multiline(spots, first_line, last_line)
        
    def is_string_constant(self, tree):
        try:
            return tree[0] == symbol.stmt and tree[1][1][1][0] == symbol.expr_stmt
        except:
            return False
        
    def is_pass_stmt(self, tree):
        try:
            return tree[0] == symbol.stmt and tree[1][1][1][0] == symbol.pass_stmt
        except:
            return False

    def record_multiline(self, spots, i, j):
        for l in range(i, j + 1):
            spots[l] = (i, j)
            
    def get_suite_spots(self, tree, spots):
        """ Analyze a parse tree to find suite introducers which span a number
            of lines.
        """
        for i in range(1, len(tree)):
            if type(tree[i]) == type(()):
                if tree[i][0] == symbol.suite:
                    # Found a suite, look back for the colon and keyword.
                    lineno_colon = lineno_word = None
                    for j in range(i - 1, 0, -1):
                        if tree[j][0] == token.COLON:
                            # Colons are never executed themselves: we want the
                            # line number of the last token before the colon.
                            lineno_colon = self.last_line_of_tree(tree[j - 1])
                        elif tree[j][0] == token.NAME:
                            if tree[j][1] == 'elif':
                                # Find the line number of the first non-terminal
                                # after the keyword.
                                t = tree[j + 1]
                                while t and token.ISNONTERMINAL(t[0]):
                                    t = t[1]
                                if t:
                                    lineno_word = t[2]
                            else:
                                lineno_word = tree[j][2]
                            break
                        elif tree[j][0] == symbol.except_clause:
                            # "except" clauses look like:
                            # ('except_clause', ('NAME', 'except', lineno), ...)
                            if tree[j][1][0] == token.NAME:
                                lineno_word = tree[j][1][2]
                                break
                    if lineno_colon and lineno_word:
                        # Found colon and keyword, mark all the lines
                        # between the two with the two line numbers.
                        self.record_multiline(spots, lineno_word, lineno_colon)

                    # "pass" statements are tricky: different versions of Python
                    # treat them differently, especially in the common case of a
                    # function with a doc string and a single pass statement.
                    self.find_docstring_pass_pair(tree[i], spots)
                    
                elif tree[i][0] == symbol.simple_stmt:
                    first_line = self.first_line_of_tree(tree[i])
                    last_line = self.last_line_of_tree(tree[i])
                    if first_line != last_line:
                        self.record_multiline(spots, first_line, last_line)
                self.get_suite_spots(tree[i], spots)

    def find_executable_statements(self, text, exclude=None):
        # Find lines which match an exclusion pattern.
        excluded = {}
        suite_spots = {}
        if exclude:
            reExclude = re.compile(exclude)
            lines = text.split('\n')
            for i in range(len(lines)):
                if reExclude.search(lines[i]):
                    excluded[i + 1] = 1

        # Parse the code and analyze the parse tree to find out which statements
        # are multiline, and where suites begin and end.
        import parser
        tree = parser.suite(text + '\n\n').totuple(1)
        self.get_suite_spots(tree, suite_spots)
        #print "Suite spots:", suite_spots
        
        # Use the compiler module to parse the text and find the executable
        # statements.  We add newlines to be impervious to final partial lines.
        statements = {}
        ast = compiler.parse(text + '\n\n')
        visitor = StatementFindingAstVisitor(statements, excluded, suite_spots)
        compiler.walk(ast, visitor, walker=visitor)

        lines = statements.keys()
        lines.sort()
        excluded_lines = excluded.keys()
        excluded_lines.sort()
        return lines, excluded_lines, suite_spots

    # format_lines(statements, lines).  Format a list of line numbers
    # for printing by coalescing groups of lines as long as the lines
    # represent consecutive statements.  This will coalesce even if
    # there are gaps between statements, so if statements =
    # [1,2,3,4,5,10,11,12,13,14] and lines = [1,2,5,10,11,13,14] then
    # format_lines will return "1-2, 5-11, 13-14".

    def format_lines(self, statements, lines):
        pairs = []
        i = 0
        j = 0
        start = None
        pairs = []
        while i < len(statements) and j < len(lines):
            if statements[i] == lines[j]:
                if start == None:
                    start = lines[j]
                end = lines[j]
                j = j + 1
            elif start:
                pairs.append((start, end))
                start = None
            i = i + 1
        if start:
            pairs.append((start, end))
        def stringify(pair):
            start, end = pair
            if start == end:
                return "%d" % start
            else:
                return "%d-%d" % (start, end)
        ret = string.join(map(stringify, pairs), ",")
        return ret

    # Backward compatibility with version 1.
    def analysis(self, morf):
        f, s, _, m, mf = self.analysis2(morf)
        return f, s, m, mf

    def analysis2(self, morf):
        filename, statements, excluded, line_map = self.analyze_morf(morf)
        self.canonicalize_filenames()
        if not self.cexecuted.has_key(filename):
            self.cexecuted[filename] = {}
        missing = []
        for line in statements:
            lines = line_map.get(line, [line, line])
            for l in range(lines[0], lines[1] + 1):
                if self.cexecuted[filename].has_key(l):
                    break
            else:
                missing.append(line)
        return (filename, statements, excluded, missing,
                self.format_lines(statements, missing))

    def relative_filename(self, filename):
        """ Convert filename to relative filename from self.relative_dir.
        """
        return filename.replace(self.relative_dir, "")

    def morf_name(self, morf):
        """ Return the name of morf as used in report.
        """
        if isinstance(morf, types.ModuleType) or (hasattr(email, 'LazyImporter') and isinstance(morf, email.LazyImporter)):
            return morf.__name__
        else:
            return self.relative_filename(os.path.splitext(morf)[0])

    def filter_by_prefix(self, morfs, omit_prefixes):
        """ Return list of morfs where the morf name does not begin
            with any one of the omit_prefixes.
        """
        filtered_morfs = []
        for morf in morfs:
            for prefix in omit_prefixes:
                if self.morf_name(morf).startswith(prefix):
                    break
            else:
                filtered_morfs.append(morf)

        return filtered_morfs

    def morf_name_compare(self, x, y):
        return cmp(self.morf_name(x), self.morf_name(y))

    def report(self, morfs, show_missing=1, ignore_errors=0, file=None, omit_prefixes=[]):
        '''
        @param morfs: list of files that we want to get information from
        
        The report is created in the following format:
        Name            Stmts   Exec  Cover   Missing
        ---------------------------------------------
        file_to_test    @    7   @   6  @  85%  @ 8
        file_to_test2   @   13   @   9  @  69%  @ 12-14, 17
        ---------------------------------------------
        TOTAL              20     15    75%   
        
        @returns a list of tuples in the format ('file_to_test2', 13, 9, 69.230769230769226, '12-14, 17')
        @note: 'file' param was 'out'
        '''
        
        
        if not isinstance(morfs, types.ListType):
            morfs = [morfs]
        # On windows, the shell doesn't expand wildcards.  Do it here.
        globbed = []
        for morf in morfs:
            if isinstance(morf, strclass):
                globbed.extend(glob.glob(morf))
            else:
                globbed.append(morf)
        morfs = globbed
        
        morfs = self.filter_by_prefix(morfs, omit_prefixes)
        morfs.sort(self.morf_name_compare)

        max_name = max([5, ] + map(len, map(self.morf_name, morfs)))
        fmt_name = "%%- %ds  " % max_name
        fmt_err = fmt_name + "%s: %s"
        header = fmt_name % "Name" + " Stmts   Exec  Cover"
        fmt_coverage = fmt_name + "@% 6d @% 6d @% 5d%%"
        if show_missing:
            header = header + "   Missing"
            fmt_coverage = fmt_coverage + "@   %s"
        if not file:
            file = sys.stdout
        print >> file, header
        print >> file, "-" * len(header)
        total_statements = 0
        total_executed = 0
        for morf in morfs:
            name = self.morf_name(morf)
            try:
                _, statements, _, missing, readable = self.analysis2(morf)
                n = len(statements)
                m = n - len(missing)
                if n > 0:
                    pc = 100.0 * m / n
                else:
                    pc = 100.0
                args = (morf, n, m, pc)
                if show_missing:
                    args = args + (readable,)
                print >> file, fmt_coverage % args
                total_statements = total_statements + n
                total_executed = total_executed + m
            except KeyboardInterrupt:                       #pragma: no cover
                raise
            except:
                if not ignore_errors:
                    typ, msg = sys.exc_info()[:2]
                    print >> file, fmt_err % (morf, typ, msg)
        if len(morfs) > 1:
            print >> file, "-" * len(header)
            if total_statements > 0:
                pc = 100.0 * total_executed / total_statements
            else:
                pc = 100.0
            args = ("TOTAL", total_statements, total_executed, pc)
            if show_missing:
                args = args + ("",)
            print >> file, fmt_coverage % args

    # annotate(morfs, ignore_errors).

    blank_re = re.compile(r"\s*(#|$)")
    else_re = re.compile(r"\s*else\s*:\s*(#|$)")

    def annotate(self, morfs, directory=None, ignore_errors=0, omit_prefixes=[]):
        morfs = self.filter_by_prefix(morfs, omit_prefixes)
        for morf in morfs:
            try:
                filename, statements, excluded, missing, _ = self.analysis2(morf)
                self.annotate_file(filename, statements, excluded, missing, directory)
            except KeyboardInterrupt:
                raise
            except:
                if not ignore_errors:
                    raise
                
    def annotate_file(self, filename, statements, excluded, missing, directory=None):
        source = open(filename, 'r')
        if directory:
            dest_file = os.path.join(directory,
                                     os.path.basename(filename)
                                     + ',cover')
        else:
            dest_file = filename + ',cover'
        dest = open(dest_file, 'w')
        lineno = 0
        i = 0
        j = 0
        covered = 1
        while 1:
            line = source.readline()
            if line == '':
                break
            lineno = lineno + 1
            while i < len(statements) and statements[i] < lineno:
                i = i + 1
            while j < len(missing) and missing[j] < lineno:
                j = j + 1
            if i < len(statements) and statements[i] == lineno:
                covered = j >= len(missing) or missing[j] > lineno
            if self.blank_re.match(line):
                dest.write('  ')
            elif self.else_re.match(line):
                # Special logic for lines containing only 'else:'.  
                # See [GDR 2001-12-04b, 3.2].
                if i >= len(statements) and j >= len(missing):
                    dest.write('! ')
                elif i >= len(statements) or j >= len(missing):
                    dest.write('> ')
                elif statements[i] == missing[j]:
                    dest.write('! ')
                else:
                    dest.write('> ')
            elif lineno in excluded:
                dest.write('- ')
            elif covered:
                dest.write('> ')
            else:
                dest.write('! ')
            dest.write(line)
        source.close()
        dest.close()


# Module functions call methods in the singleton object.
def use_cache(*args, **kw): 
    return the_coverage.use_cache(*args, **kw)

def start(*args, **kw): 
    return the_coverage.start(*args, **kw)

def stop(*args, **kw): 
    return the_coverage.stop(*args, **kw)

def erase(*args, **kw): 
    return the_coverage.erase(*args, **kw)

def begin_recursive(*args, **kw): 
    return the_coverage.begin_recursive(*args, **kw)

def end_recursive(*args, **kw): 
    return the_coverage.end_recursive(*args, **kw)

def exclude(*args, **kw): 
    return the_coverage.exclude(*args, **kw)

def analysis(*args, **kw): 
    return the_coverage.analysis(*args, **kw)

def analysis2(*args, **kw): 
    return the_coverage.analysis2(*args, **kw)

def report(*args, **kw): 
    return the_coverage.report(*args, **kw)

def annotate(*args, **kw): 
    return the_coverage.annotate(*args, **kw)

def annotate_file(*args, **kw): 
    return the_coverage.annotate_file(*args, **kw)


# Command-line interface.
if __name__ == '__main__':
#    it's the same as -r -m, but...
#    goes to a raw_input() and waits for the files that should be executed...

    global cache_location #let's set the cache location now...
    cache_location = sys.argv[1] #first parameter is the cache location.
    sys.argv.remove(cache_location)
    print cache_location
    
    global the_coverage
    # Singleton object.
    the_coverage = coverage()

    if len(sys.argv) == 2:
        
        if '-waitfor' == sys.argv[1]:
            sys.argv.remove('-waitfor')
            sys.argv.append('-r')
            sys.argv.append('-m')
            
            #second gets the files to be executed
            s = raw_input()
            s = s.replace('\r', '')
            s = s.replace('\n', '')
            files = s.split('|')
            files = [v for v in files if len(v) > 0]
            sys.argv += files
    
    if '-x' in sys.argv:
        # Save coverage data when Python exits.  (The atexit module wasn't
        # introduced until Python 2.0, so use sys.exitfunc when it's not
        # available.)
        try:
            import atexit
            atexit.register(the_coverage.save)
        except ImportError:
            sys.exitfunc = the_coverage.save
    
    the_coverage.command_line(sys.argv[1:])


# A. REFERENCES
#
# [GDR 2001-12-04a] "Statement coverage for Python"; Gareth Rees;
# Ravenbrook Limited; 2001-12-04;
# <http://www.nedbatchelder.com/code/modules/rees-coverage.html>.
#
# [GDR 2001-12-04b] "Statement coverage for Python: design and
# analysis"; Gareth Rees; Ravenbrook Limited; 2001-12-04;
# <http://www.nedbatchelder.com/code/modules/rees-design.html>.
#
# [van Rossum 2001-07-20a] "Python Reference Manual (releae 2.1.1)";
# Guide van Rossum; 2001-07-20;
# <http://www.python.org/doc/2.1.1/ref/ref.html>.
#
# [van Rossum 2001-07-20b] "Python Library Reference"; Guido van Rossum;
# 2001-07-20; <http://www.python.org/doc/2.1.1/lib/lib.html>.
#
#
# B. DOCUMENT HISTORY
#
# 2001-12-04 GDR Created.
#
# 2001-12-06 GDR Added command-line interface and source code
# annotation.
#
# 2001-12-09 GDR Moved design and interface to separate documents.
#
# 2001-12-10 GDR Open cache file as binary on Windows.  Allow
# simultaneous -e and -x, or -a and -r.
#
# 2001-12-12 GDR Added command-line help.  Cache analysis so that it
# only needs to be done once when you specify -a and -r.
#
# 2001-12-13 GDR Improved speed while recording.  Portable between
# Python 1.5.2 and 2.1.1.
#
# 2002-01-03 GDR Module-level functions work correctly.
#
# 2002-01-07 GDR Update sys.path when running a file with the -x option,
# so that it matches the value the program would get if it were run on
# its own.
#
# 2004-12-12 NMB Significant code changes.
# - Finding executable statements has been rewritten so that docstrings and
#   other quirks of Python execution aren't mistakenly identified as missing
#   lines.
# - Lines can be excluded from consideration, even entire suites of lines.
# - The filesystem cache of covered lines can be disabled programmatically.
# - Modernized the code.
#
# 2004-12-14 NMB Minor tweaks.  Return 'analysis' to its original behavior
# and add 'analysis2'.  Add a global for 'annotate', and factor it, adding
# 'annotate_file'.
#
# 2004-12-31 NMB Allow for keyword arguments in the module global functions.
# Thanks, Allen.
#
# 2005-12-02 NMB Call threading.settrace so that all threads are measured.
# Thanks Martin Fuzzey. Add a file argument to report so that reports can be 
# captured to a different destination.
#
# 2005-12-03 NMB coverage.py can now measure itself.
#
# 2005-12-04 NMB Adapted Greg Rogers' patch for using relative filenames,
# and sorting and omitting files to report on.
#
# 2006-07-23 NMB Applied Joseph Tate's patch for function decorators.
#
# 2006-08-21 NMB Applied Sigve Tjora and Mark van der Wal's fixes for argument
# handling.
#
# 2006-08-22 NMB Applied Geoff Bache's parallel mode patch.
#
# 2006-08-23 NMB Refactorings to improve testability.  Fixes to command-line
# logic for parallel mode and collect.
#
# 2006-08-25 NMB "#pragma: nocover" is excluded by default.
#
# 2006-09-10 NMB Properly ignore docstrings and other constant expressions that
# appear in the middle of a function, a problem reported by Tim Leslie.
# Minor changes to avoid lint warnings.
#
# 2006-09-17 NMB coverage.erase() shouldn't clobber the exclude regex.
# Change how parallel mode is invoked, and fix erase() so that it erases the
# cache when called programmatically.
#
# 2007-07-21 NMB In reports, ignore code executed from strings, since we can't
# do anything useful with it anyway.
# Better file handling on Linux, thanks Guillaume Chazarain.
# Better shell support on Windows, thanks Noel O'Boyle.
# Python 2.2 support maintained, thanks Catherine Proulx.
#
# 2007-07-22 NMB Python 2.5 now fully supported. The method of dealing with
# multi-line statements is now less sensitive to the exact line that Python
# reports during execution. Pass statements are handled specially so that their
# disappearance during execution won't throw off the measurement.
#
# 2007-07-23 NMB Now Python 2.5 is *really* fully supported: the body of the
# new with statement is counted as executable.
#
# 2007-07-29 NMB Better packaging.
#
# 2007-09-30 NMB Don't try to predict whether a file is Python source based on
# the extension. Extensionless files are often Pythons scripts. Instead, simply
# parse the file and catch the syntax errors.  Hat tip to Ben Finney.

# C. COPYRIGHT AND LICENCE
#
# Copyright 2001 Gareth Rees.  All rights reserved.
# Copyright 2004-2007 Ned Batchelder.  All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are
# met:
#
# 1. Redistributions of source code must retain the above copyright
#    notice, this list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright
#    notice, this list of conditions and the following disclaimer in the
#    documentation and/or other materials provided with the
#    distribution.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
# "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
# LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
# A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
# HOLDERS AND CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
# INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
# OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
# TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
# USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
# DAMAGE.
#
