"""Skeleton for 'pytest'.

Project: pytest 3.0.0 <http://doc.pytest.org>
Skeleton by: Bruno Oliveira <nicoddemus@gmail.com>

Exposing everything that can be extracted from `pytest_namespace` hook
in standard pytest modules, using original docstrings.
"""


def freeze_includes():
    """
    Returns a list of module names used by py.test that should be
    included by cx_freeze.
    """


# _pytest.main
class collect:

    class Item:
        """ a basic test invocation item. Note that for a single function
        there might be multiple test invocation items.
        """

    class Collector:
        """ Collector instances create children through collect()
            and thus iteratively build a tree.
        """

    class File:
        """ base class for collecting tests from a file. """

    class Session:
        """
        """

    # _pytest.python
    class Module:
        """ Collector for test classes and functions. """

    class Class:
        """ Collector for test methods. """

    class Instance:
        """
        """

    class Function:
        """ a Function Item is responsible for setting up and executing a
        Python test function.
        """

    class Generator:
        """
        """

    @staticmethod
    def _fillfuncargs(function):
        """ fill missing funcargs for a test function. """




# _pytest.pdb
def set_trace():
    """ invoke PDB set_trace debugging, dropping any IO capturing. """

# _pytest.mark
class mark:

    def __getattr__(self, item):
        """
        This class may have any attribute, so this method should exist
        """
        pass

    @staticmethod
    def skipif(condition, reason=None):
        """skip the given test function if eval(condition) results in a True
        value.

        Optionally specify a reason for better reporting.

        Evaluation happens within the module global context.
        Example: ``skipif('sys.platform == "win32"')`` skips the test if
        we are on the win32 platform.

        see http://doc.pytest.org/en/latest/skipping.html
        """

    @staticmethod
    def skip(reason=None):
        """skip the given test function, optionally specify a reason for better reporting.

        see http://doc.pytest.org/en/latest/skipping.html
        """

    @staticmethod
    def xfail(condition=None, reason=None, raises=None, run=True, strict=False):
        """mark the the test function as an expected failure if eval(condition)
        has a True value.

        Optionally specify a reason for better reporting and run=False if
        you don't even want to execute the test function.

        See http://doc.pytest.org/en/latest/skipping.html
        """

    @staticmethod
    def parametrize(argnames, argvalues, indirect=False, ids=None, scope=None):
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
            names (subset of argnames). If True the list contains all names from
            the argnames. Each argvalue corresponding to an argname in this list will
            be passed as request.param to its respective argname fixture
            function so that it can perform more expensive setups during the
            setup phase of a test rather than at collection time.

        :arg ids: list of string ids, or a callable.
            If strings, each is corresponding to the argvalues so that they are
            part of the test id. If None is given as id of specific test, the
            automatically generated id for that argument will be used.
            If callable, it should take one argument (a single argvalue) and return
            a string or return None. If None, the automatically generated id for that
            argument will be used.
            If no ids are provided they will be generated automatically from
            the argvalues.

        :arg scope: if specified it denotes the scope of the parameters.
            The scope is used for grouping tests by parameter instances.
            It will also override any fixture-function defined scope, allowing
            to set a dynamic scope using test context or configuration.
        """

    @staticmethod
    def usefixtures(*fixturenames):
        """mark tests as needing all of the specified fixtures.

        see http://doc.pytest.org/en/latest/fixture.html#usefixtures
        """

    @staticmethod
    def tryfirst(f):
        """mark a hook implementation function such that the plugin machinery
        will try to call it first/as early as possible.
        """

    @staticmethod
    def trylast(f):
        """mark a hook implementation function such that the plugin machinery
        will try to call it last/as late as possible.
        """

    @staticmethod
    def hookwrapper(f):
        """A hook wrapper is a generator function which yields exactly once.
        When pytest invokes hooks it first executes hook wrappers and passes
        the same arguments as to the regular hooks.
        """


# _pytest.python
def raises(ExpectedException, *args, **kwargs):
    """ assert that a code block/function call raises @ExpectedException and
    raise a failure exception otherwise.

    :type ExpectedException: T

    This helper produces a ``py.code.ExceptionInfo()`` object.

    If using Python 2.5 or above, you may use this function as a
    context manager::

        >>> with raises(ZeroDivisionError):
        ...    1/0

    Or you can specify a callable by passing a to-be-called lambda::

        >>> raises(ZeroDivisionError, lambda: 1/0)
        <ExceptionInfo ...>

    or you can specify an arbitrary callable with arguments::

        >>> def f(x): return 1/x
        ...
        >>> raises(ZeroDivisionError, f, 0)
        <ExceptionInfo ...>
        >>> raises(ZeroDivisionError, f, x=0)
        <ExceptionInfo ...>

    A third possibility is to use a string to be executed::

        >>> raises(ZeroDivisionError, "f(0)")
        <ExceptionInfo ...>

    Performance note:
    -----------------

    Similar to caught exception objects in Python, explicitly clearing
    local references to returned ``py.code.ExceptionInfo`` objects can
    help the Python interpreter speed up its garbage collection.

    Clearing those references breaks a reference cycle
    (``ExceptionInfo`` --> caught exception --> frame stack raising
    the exception --> current frame stack --> local variables -->
    ``ExceptionInfo``) which makes Python keep all objects referenced
    from that cycle (including all local variables in the current
    frame) alive until the next cyclic garbage collection run. See the
    official Python ``try`` statement documentation for more detailed
    information.

    """


def fixture(scope="function", params=None, autouse=False, ids=None, name=None):
    """ (return a) decorator to mark a fixture factory function.

    This decorator can be used (with or or without parameters) to define
    a fixture function.  The name of the fixture function can later be
    referenced to cause its invocation ahead of running tests: test
    modules or classes can use the pytest.mark.usefixtures(fixturename)
    marker.  Test functions can directly use fixture names as input
    arguments in which case the fixture instance returned from the fixture
    function will be injected.

    :arg scope: the scope for which this fixture is shared, one of
                "function" (default), "class", "module", "session" or "invocation".

    :arg params: an optional list of parameters which will cause multiple
                invocations of the fixture function and all of the tests
                using it.

    :arg autouse: if True, the fixture func is activated for all tests that
                can see it.  If False (the default) then an explicit
                reference is needed to activate the fixture.

    :arg ids: list of string ids each corresponding to the params
       so that they are part of the test id. If no ids are provided
       they will be generated automatically from the params.

    :arg name: the name of the fixture. This defaults to the name of the
               decorated function. If a fixture is used in the same module in
               which it is defined, the function name of the fixture will be
               shadowed by the function arg that requests the fixture; one way
               to resolve this is to name the decorated function
               ``fixture_<fixturename>`` and then use
               ``@pytest.fixture(name='<fixturename>')``.
    """


def yield_fixture(scope="function", params=None, autouse=False, ids=None, name=None):
    """ (return a) decorator to mark a yield-fixture factory function.

    .. deprecated:: 3.0
        Use :py:func:`pytest.fixture` directly instead.
    """
    import warnings
    warnings.warn(DeprecationWarning('Use @pytest.fixture directly instead.'))


# _pytest.recwarn
def deprecated_call(func, *args, **kwargs):
    """ assert that calling ``func(*args, **kwargs)``
    triggers a DeprecationWarning.
    """


# _pytest.runner
def exit(msg):
    """ exit testing process as if KeyboardInterrupt was triggered. """

exit.Exception = Exception


def skip(msg=""):
    """ skip an executing test with the given message.  Note: it's usually
    better to use the pytest.mark.skipif marker to declare a test to be
    skipped under certain conditions like mismatching platforms or
    dependencies.  See the pytest_skipping plugin for details.
    """
skip.Exception = Exception


def fail(msg="", pytrace=True):
    """ explicitely fail an currently-executing test with the given Message.

    :arg pytrace: if false the msg represents the full failure information
                  and no python traceback will be reported.
    """
fail.Exception = Exception


def importorskip(modname, minversion=None):
    """ return imported module if it has at least "minversion" as its
    __version__ attribute.  If no minversion is specified the a skip
    is only triggered if the module can not be imported.
    Note that version comparison only works with simple version strings
    like "1.2.3" but not "1.2.3.dev1" or others.
    """

# _pytest.skipping
def xfail(reason=""):
    """ xfail an executing test or setup functions with the given reason.
    """


def approx(expected, rel=None, abs=None):
    """
    Assert that two numbers (or two sets of numbers) are equal to each other
    within some tolerance.

    The ``approx`` class performs floating-point comparisons using a syntax
    that's as intuitive as possible::

        >>> from pytest import approx
        >>> 0.1 + 0.2 == approx(0.3)
        True

    http://doc.pytest.org/en/latest/builtin.html#comparing-floating-point-numbers
    """


def register_assert_rewrite(*names):
    """Register a module name to be rewritten on import.

    This function will make sure that this module or all modules inside
    the package will get their assert statements rewritten.
    Thus you should make sure to call this before the module is
    actually imported, usually in your __init__.py if you are a plugin
    using a package.

    :raise TypeError: if the given module names are not strings.
    """    
