import fnmatch
import os.path
import re
import sys
import unittest




try:
    __setFalse = False
except:
    import __builtin__
    setattr(__builtin__, 'True', 1)
    setattr(__builtin__, 'False', 0)




#=======================================================================================================================
# Jython?
#=======================================================================================================================
try:
    import org.python.core.PyDictionary #@UnresolvedImport @UnusedImport -- just to check if it could be valid
    def DictContains(d, key):
        return d.has_key(key)
except:
    try:
        #Py3k does not have has_key anymore, and older versions don't have __contains__
        DictContains = dict.__contains__
    except:
        DictContains = dict.has_key

try:
    xrange
except:
    #Python 3k does not have it
    xrange = range

try:
    enumerate
except:
    def enumerate(lst):
        ret = []
        i=0
        for element in lst:
            ret.append((i, element))
            i+=1
        return ret
    


#=======================================================================================================================
# getopt code copied since gnu_getopt is not available on jython 2.1
#=======================================================================================================================
class GetoptError(Exception):
    opt = ''
    msg = ''
    def __init__(self, msg, opt=''):
        self.msg = msg
        self.opt = opt
        Exception.__init__(self, msg, opt)

    def __str__(self):
        return self.msg


def gnu_getopt(args, shortopts, longopts=[]):
    """getopt(args, options[, long_options]) -> opts, args

    This function works like getopt(), except that GNU style scanning
    mode is used by default. This means that option and non-option
    arguments may be intermixed. The getopt() function stops
    processing options as soon as a non-option argument is
    encountered.

    If the first character of the option string is `+', or if the
    environment variable POSIXLY_CORRECT is set, then option
    processing stops as soon as a non-option argument is encountered.
    """

    opts = []
    prog_args = []
    if isinstance(longopts, ''.__class__):
        longopts = [longopts]
    else:
        longopts = list(longopts)

    # Allow options after non-option arguments?
    if shortopts.startswith('+'):
        shortopts = shortopts[1:]
        all_options_first = True
    elif os.environ.get("POSIXLY_CORRECT"):
        all_options_first = True
    else:
        all_options_first = False

    while args:
        if args[0] == '--':
            prog_args += args[1:]
            break

        if args[0][:2] == '--':
            opts, args = do_longs(opts, args[0][2:], longopts, args[1:])
        elif args[0][:1] == '-':
            opts, args = do_shorts(opts, args[0][1:], shortopts, args[1:])
        else:
            if all_options_first:
                prog_args += args
                break
            else:
                prog_args.append(args[0])
                args = args[1:]

    return opts, prog_args

def do_longs(opts, opt, longopts, args):
    try:
        i = opt.index('=')
    except ValueError:
        optarg = None
    else:
        opt, optarg = opt[:i], opt[i + 1:]

    has_arg, opt = long_has_args(opt, longopts)
    if has_arg:
        if optarg is None:
            if not args:
                raise GetoptError('option --%s requires argument' % opt, opt)
            optarg, args = args[0], args[1:]
    elif optarg:
        raise GetoptError('option --%s must not have an argument' % opt, opt)
    opts.append(('--' + opt, optarg or ''))
    return opts, args

# Return:
#   has_arg?
#   full option name
def long_has_args(opt, longopts):
    possibilities = [o for o in longopts if o.startswith(opt)]
    if not possibilities:
        raise GetoptError('option --%s not recognized' % opt, opt)
    # Is there an exact match?
    if opt in possibilities:
        return False, opt
    elif opt + '=' in possibilities:
        return True, opt
    # No exact match, so better be unique.
    if len(possibilities) > 1:
        # XXX since possibilities contains all valid continuations, might be
        # nice to work them into the error msg
        raise GetoptError('option --%s not a unique prefix' % opt, opt)
    assert len(possibilities) == 1
    unique_match = possibilities[0]
    has_arg = unique_match.endswith('=')
    if has_arg:
        unique_match = unique_match[:-1]
    return has_arg, unique_match

def do_shorts(opts, optstring, shortopts, args):
    while optstring != '':
        opt, optstring = optstring[0], optstring[1:]
        if short_has_arg(opt, shortopts):
            if optstring == '':
                if not args:
                    raise GetoptError('option -%s requires argument' % opt,
                                      opt)
                optstring, args = args[0], args[1:]
            optarg, optstring = optstring, ''
        else:
            optarg = ''
        opts.append(('-' + opt, optarg))
    return opts, args

def short_has_arg(opt, shortopts):
    for i in range(len(shortopts)):
        if opt == shortopts[i] != ':':
            return shortopts.startswith(':', i + 1)
    raise GetoptError('option -%s not recognized' % opt, opt)


#=======================================================================================================================
# End getopt code
#=======================================================================================================================










#=======================================================================================================================
# parse_cmdline
#=======================================================================================================================
def parse_cmdline():
    """ parses command line and returns test directories, verbosity, test filter and test suites
        usage: 
            runfiles.py  -v|--verbosity <level>  -f|--filter <regex>  -t|--tests <Test.test1,Test2>  dirs|files
    """
    verbosity = 2
    test_filter = None
    tests = None

    optlist, dirs = gnu_getopt(sys.argv[1:], "v:f:t:", ["verbosity=", "filter=", "tests="])
    for opt, value in optlist:
        if opt in ("-v", "--verbosity"):
            verbosity = value

        elif opt in ("-f", "--filter"):
            test_filter = value.split(',')

        elif opt in ("-t", "--tests"):
            tests = value.split(',')

    if type([]) != type(dirs):
        dirs = [dirs]

    ret_dirs = []
    for d in dirs:
        if '|' in d:
            #paths may come from the ide separated by |
            ret_dirs.extend(d.split('|'))
        else:
            ret_dirs.append(d)

    return ret_dirs, int(verbosity), test_filter, tests


#=======================================================================================================================
# PydevTestRunner
#=======================================================================================================================
class PydevTestRunner:
    """ finds and runs a file or directory of files as a unit test """

    __py_extensions = ["*.py", "*.pyw"]
    __exclude_files = ["__init__.*"]

    def __init__(self, test_dir, test_filter=None, verbosity=2, tests=None):
        self.test_dir = test_dir
        self.__adjust_path()
        self.test_filter = self.__setup_test_filter(test_filter)
        self.verbosity = verbosity
        self.tests = tests


    def __adjust_path(self):
        """ add the current file or directory to the python path """
        path_to_append = None
        for n in xrange(len(self.test_dir)):
            dir_name = self.__unixify(self.test_dir[n])
            if os.path.isdir(dir_name):
                if not dir_name.endswith("/"):
                    self.test_dir[n] = dir_name + "/"
                path_to_append = os.path.normpath(dir_name)
            elif os.path.isfile(dir_name):
                path_to_append = os.path.dirname(dir_name)
            else:
                msg = ("unknown type. \n%s\nshould be file or a directory.\n" % (dir_name))
                raise RuntimeError(msg)
        if path_to_append is not None:
            #Add it as the last one (so, first things are resolved against the default dirs and 
            #if none resolves, then we try a relative import).
            sys.path.append(path_to_append)
        return

    def __setup_test_filter(self, test_filter):
        """ turn a filter string into a list of filter regexes """
        if test_filter is None or len(test_filter) == 0:
            return None
        return [re.compile("test%s" % f) for f in test_filter]

    def __is_valid_py_file(self, fname):
        """ tests that a particular file contains the proper file extension 
            and is not in the list of files to exclude """
        is_valid_fname = 0
        for invalid_fname in self.__class__.__exclude_files:
            is_valid_fname += int(not fnmatch.fnmatch(fname, invalid_fname))
        if_valid_ext = 0
        for ext in self.__class__.__py_extensions:
            if_valid_ext += int(fnmatch.fnmatch(fname, ext))
        return is_valid_fname > 0 and if_valid_ext > 0

    def __unixify(self, s):
        """ stupid windows. converts the backslash to forwardslash for consistency """
        return os.path.normpath(s).replace(os.sep, "/")

    def __importify(self, s, dir=False):
        """ turns directory separators into dots and removes the ".py*" extension 
            so the string can be used as import statement """
        if not dir:
            dirname, fname = os.path.split(s)

            if fname.count('.') > 1:
                #if there's a file named xxx.xx.py, it is not a valid module, so, let's not load it...
                return

            imp_stmt_pieces = [dirname.replace("\\", "/").replace("/", "."), os.path.splitext(fname)[0]]

            if len(imp_stmt_pieces[0]) == 0:
                imp_stmt_pieces = imp_stmt_pieces[1:]

            return ".".join(imp_stmt_pieces)

        else: #handle dir
            return s.replace("\\", "/").replace("/", ".")

    def __add_files(self, pyfiles, root, files):
        """ if files match, appends them to pyfiles. used by os.path.walk fcn """
        for fname in files:
            if self.__is_valid_py_file(fname):
                name_without_base_dir = self.__unixify(os.path.join(root, fname))
                pyfiles.append(name_without_base_dir)
        return


    def find_import_files(self):
        """ return a list of files to import """
        pyfiles = []

        for base_dir in self.test_dir:
            if os.path.isdir(base_dir):
                if hasattr(os, 'walk'):
                    for root, dirs, files in os.walk(base_dir):
                        self.__add_files(pyfiles, root, files)
                else:
                    # jython2.1 is too old for os.walk!
                    os.path.walk(base_dir, self.__add_files, pyfiles)

            elif os.path.isfile(base_dir):
                pyfiles.append(base_dir)

        return pyfiles

    def __get_module_from_str(self, modname, print_exception):
        """ Import the module in the given import path.
            * Returns the "final" module, so importing "coilib40.subject.visu" 
            returns the "visu" module, not the "coilib40" as returned by __import__ """
        try:
            mod = __import__(modname)
            for part in modname.split('.')[1:]:
                mod = getattr(mod, part)
            return mod
        except:
            if print_exception:
                import traceback;traceback.print_exc()
                sys.stderr.write('ERROR: Module: %s could not be imported.\n' % (modname,))
            return None

    def find_modules_from_files(self, pyfiles):
        """ returns a lisst of modules given a list of files """
        #let's make sure that the paths we want are in the pythonpath...
        imports = [self.__importify(s) for s in pyfiles]

        system_paths = []
        for s in sys.path:
            system_paths.append(self.__importify(s, True))


        ret = []
        for imp in imports:
            if imp is None:
                continue #can happen if a file is not a valid module
            choices = []
            for s in system_paths:
                if imp.startswith(s):
                    add = imp[len(s) + 1:]
                    if add:
                        choices.append(add)
                    #sys.stdout.write(' ' + add + ' ')

            if not choices:
                sys.stdout.write('PYTHONPATH not found for file: %s\n' % imp)
            else:
                for i, import_str in enumerate(choices):
                    mod = self.__get_module_from_str(import_str, print_exception=i == len(choices) - 1)
                    if mod is not None:
                        ret.append(mod)
                        break


        return ret

    def find_tests_from_modules(self, modules):
        """ returns the unittests given a list of modules """
        loader = unittest.TestLoader()

        ret = []
        if self.tests:
            accepted_classes = {}
            accepted_methods = {}

            for t in self.tests:
                splitted = t.split('.')
                if len(splitted) == 1:
                    accepted_classes[t] = t

                elif len(splitted) == 2:
                    accepted_methods[t] = t

            #===========================================================================================================
            # GetTestCaseNames
            #===========================================================================================================
            class GetTestCaseNames:
                """Yes, we need a class for that (cannot use outer context on jython 2.1)"""

                def __init__(self, accepted_classes, accepted_methods):
                    self.accepted_classes = accepted_classes
                    self.accepted_methods = accepted_methods

                def __call__(self, testCaseClass):
                    """Return a sorted sequence of method names found within testCaseClass"""
                    testFnNames = []
                    className = testCaseClass.__name__

                    if DictContains(self.accepted_classes, className):
                        for attrname in dir(testCaseClass):
                            #If a class is chosen, we select all the 'test' methods'
                            if attrname.startswith('test') and hasattr(getattr(testCaseClass, attrname), '__call__'):
                                testFnNames.append(attrname)

                    else:
                        for attrname in dir(testCaseClass):
                            #If we have the class+method name, we must do a full check and have an exact match.
                            if DictContains(self.accepted_methods, className + '.' + attrname):
                                if hasattr(getattr(testCaseClass, attrname), '__call__'):
                                    testFnNames.append(attrname)

                    #sorted() is not available in jython 2.1
                    testFnNames.sort()
                    return testFnNames


            loader.getTestCaseNames = GetTestCaseNames(accepted_classes, accepted_methods)


        ret.extend([loader.loadTestsFromModule(m) for m in modules])

        return ret


    def filter_tests(self, test_objs):
        """ based on a filter name, only return those tests that have
            the test case names that match """
        test_suite = []
        for test_obj in test_objs:

            if isinstance(test_obj, unittest.TestSuite):
                if test_obj._tests:
                    test_obj._tests = self.filter_tests(test_obj._tests)
                    if test_obj._tests:
                        test_suite.append(test_obj)

            elif isinstance(test_obj, unittest.TestCase):
                test_cases = []
                for tc in test_objs:
                    try:
                        testMethodName = tc._TestCase__testMethodName
                    except AttributeError:
                        #changed in python 2.5
                        testMethodName = tc._testMethodName

                    if self.__match(self.test_filter, testMethodName) and self.__match_tests(self.tests, tc, testMethodName):
                        test_cases.append(tc)
                return test_cases
        return test_suite


    def __match_tests(self, tests, test_case, test_method_name):
        if not tests:
            return 1

        for t in tests:
            class_and_method = t.split('.')
            if len(class_and_method) == 1:
                #only class name
                if class_and_method[0] == test_case.__class__.__name__:
                    return 1

            elif len(class_and_method) == 2:
                if class_and_method[0] == test_case.__class__.__name__ and class_and_method[1] == test_method_name:
                    return 1

        return 0




    def __match(self, filter_list, name):
        """ returns whether a test name matches the test filter """
        if filter_list is None:
            return 1
        for f in filter_list:
            if re.match(f, name):
                return 1
        return 0


    def run_tests(self):
        """ runs all tests """
        sys.stdout.write("Finding files...\n")
        files = self.find_import_files()
        sys.stdout.write('%s %s\n' % (self.test_dir, '... done'))
        sys.stdout.write("Importing test modules ... ")
        modules = self.find_modules_from_files(files)
        sys.stdout.write("done.\n")
        all_tests = self.find_tests_from_modules(modules)
        if self.test_filter or self.tests:

            if self.test_filter:
                sys.stdout.write('Test Filter: %s' % ([p.pattern for p in self.test_filter],))

            if self.tests:
                sys.stdout.write('Tests to run: %s' % (self.tests,))

            all_tests = self.filter_tests(all_tests)

        sys.stdout.write('\n')
        runner = unittest.TextTestRunner(stream=sys.stdout, descriptions=1, verbosity=verbosity)
        runner.run(unittest.TestSuite(all_tests))
        return

#=======================================================================================================================
# main        
#=======================================================================================================================
if __name__ == '__main__':
    dirs, verbosity, test_filter, tests = parse_cmdline()
    PydevTestRunner(dirs, test_filter, verbosity, tests).run_tests()
