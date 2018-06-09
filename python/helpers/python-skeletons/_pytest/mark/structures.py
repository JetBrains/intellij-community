# _pytest.mark
class MarkGenerator(object):

    def __getattr__(self, item):
        """
        This class may have any attribute, so this method should exist
        """
        pass

    def skipif(self,condition, reason=None):
        """skip the given test function if eval(self,condition) results in a True
        value.

        Optionally specify a reason for better reporting.

        Evaluation happens within the module global context.
        Example: ``skipif(self,'sys.platform == "win32"')`` skips the test if
        we are on the win32 platform.

        see http://doc.pytest.org/en/latest/skipping.html
        """

    def skip(self,reason=None):
        """skip the given test function, optionally specify a reason for better reporting.

        see http://doc.pytest.org/en/latest/skipping.html
        """

    def xfail(self,condition=None, reason=None, raises=None, run=True, strict=False):
        """mark the the test function as an expected failure if eval(self,condition)
        has a True value.

        Optionally specify a reason for better reporting and run=False if
        you don't even want to execute the test function.

        See http://doc.pytest.org/en/latest/skipping.html
        """

    def parametrize(self,argnames, argvalues, indirect=False, ids=None, scope=None):
        """ Add new invocations to the underlying test function using the list
        of argvalues for the given argnames.  Parametrization is performed
        during the collection phase.  If you need to setup expensive resources
        see about setting indirect to do it rather at test setup time.

        :arg argnames: a comma-separated string denoting one or more argument
                       names, or a list/tuple of argument strings.

        :arg argvalues: The list of argvalues determines how often a
            test is invoked with different argument values.  If only one
            argname was specified argvalues is a list of values.  If N
            argnames were specified, argvalues must be a list of N-tuples,
            where each tuple-element specifies a value for its respective
            argname.

        :arg indirect: The list of argnames or boolean. A list of arguments'
            names (self,subset of argnames). If True the list contains all names from
            the argnames. Each argvalue corresponding to an argname in this list will
            be passed as request.param to its respective argname fixture
            function so that it can perform more expensive setups during the
            setup phase of a test rather than at collection time.

        :arg ids: list of string ids, or a callable.
            If strings, each is corresponding to the argvalues so that they are
            part of the test id. If None is given as id of specific test, the
            automatically generated id for that argument will be used.
            If callable, it should take one argument (self,a single argvalue) and return
            a string or return None. If None, the automatically generated id for that
            argument will be used.
            If no ids are provided they will be generated automatically from
            the argvalues.

        :arg scope: if specified it denotes the scope of the parameters.
            The scope is used for grouping tests by parameter instances.
            It will also override any fixture-function defined scope, allowing
            to set a dynamic scope using test context or configuration.
        """

    def usefixtures(self,*fixturenames):
        """mark tests as needing all of the specified fixtures.

        see http://doc.pytest.org/en/latest/fixture.html#usefixtures
        """

    def tryfirst(self,f):
        """mark a hook implementation function such that the plugin machinery
        will try to call it first/as early as possible.
        """

    def trylast(self,f):
        """mark a hook implementation function such that the plugin machinery
        will try to call it last/as late as possible.
        """

    def hookwrapper(self,f):
        """A hook wrapper is a generator function which yields exactly once.
        When pytest invokes hooks it first executes hook wrappers and passes
        the same arguments as to the regular hooks.
        """
