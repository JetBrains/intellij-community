import pickle, zlib, base64, os
import py
from py._code import code  # @UnresolvedImport
from _pydev_runfiles import pydev_runfiles_xml_rpc
from pydevd_file_utils import _NormFile
import pytest
import sys
import time


#===================================================================================================
# Load filters with tests we should skip
#===================================================================================================
py_test_accept_filter = None

def _load_filters():
    global py_test_accept_filter
    if py_test_accept_filter is None:
        py_test_accept_filter = os.environ.get('PYDEV_PYTEST_SKIP')
        if py_test_accept_filter:
            py_test_accept_filter = pickle.loads(zlib.decompress(base64.b64decode(py_test_accept_filter)))
        else:
            py_test_accept_filter = {}


def connect_to_server_for_communication_to_xml_rpc_on_xdist():
    main_pid = os.environ.get('PYDEV_MAIN_PID')
    if main_pid and main_pid != str(os.getpid()):
        port = os.environ.get('PYDEV_PYTEST_SERVER')
        if not port:
            sys.stderr.write('Error: no PYDEV_PYTEST_SERVER environment variable defined.\n')
        else:
            pydev_runfiles_xml_rpc.initialize_server(int(port), daemon=True)

PY2 = sys.version_info[0] <= 2
PY3 = not PY2

#===================================================================================================
# Mocking to get clickable file representations
#===================================================================================================
def _MockFileRepresentation():
    code.ReprFileLocation._original_toterminal = code.ReprFileLocation.toterminal

    def toterminal(self, tw):
        # filename and lineno output for each entry,
        # using an output format that most editors understand
        msg = self.message
        i = msg.find("\n")
        if i != -1:
            msg = msg[:i]

        path = os.path.abspath(self.path)

        if PY2:
            if not isinstance(path, unicode):  # Note: it usually is NOT unicode...
                path = path.decode(sys.getfilesystemencoding(), 'replace')

            if not isinstance(msg, unicode):  # Note: it usually is unicode...
                msg = msg.decode('utf-8', 'replace')
            unicode_line = unicode('File "%s", line %s\n%s') % (path, self.lineno, msg)
            tw.line(unicode_line)
        else:
            tw.line('File "%s", line %s\n%s' % (path, self.lineno, msg))

    code.ReprFileLocation.toterminal = toterminal


def _UninstallMockFileRepresentation():
    code.ReprFileLocation.toterminal = code.ReprFileLocation._original_toterminal #@UndefinedVariable


class State:
    numcollected = 0
    start_time = time.time()


def pytest_configure(*args, **kwargs):
    _MockFileRepresentation()


def pytest_collectreport(report):

    i = 0
    for x in report.result:
        if isinstance(x, pytest.Item):
            try:
                # Call our setup (which may do a skip, in which
                # case we won't count it).
                pytest_runtest_setup(x)
                i += 1
            except:
                continue
    State.numcollected += i


def pytest_collection_modifyitems():
    connect_to_server_for_communication_to_xml_rpc_on_xdist()
    pydev_runfiles_xml_rpc.notifyTestsCollected(State.numcollected)
    State.numcollected = 0


def pytest_unconfigure(*args, **kwargs):
    _UninstallMockFileRepresentation()
    pydev_runfiles_xml_rpc.notifyTestRunFinished('Finished in: %.2f secs.' % (time.time() - State.start_time,))


def pytest_runtest_setup(item):
    filename = item.fspath.strpath
    test = item.location[2]
    State.start_test_time = time.time()

    pydev_runfiles_xml_rpc.notifyStartTest(filename, test)


def report_test(cond, filename, test, captured_output, error_contents, delta):
    '''
    @param filename: 'D:\\src\\mod1\\hello.py'
    @param test: 'TestCase.testMet1'
    @param cond: fail, error, ok
    '''
    time_str = '%.2f' % (delta,)
    pydev_runfiles_xml_rpc.notifyTest(cond, captured_output, error_contents, filename, test, time_str)


def pytest_runtest_makereport(item, call):
    report_when = call.when
    report_duration = call.stop-call.start
    excinfo = call.excinfo

    if not call.excinfo:
        evalxfail = getattr(item, '_evalxfail', None)
        if evalxfail and report_when == 'call' and (not hasattr(evalxfail, 'expr') or evalxfail.expr):
            # I.e.: a method marked with xfail passed... let the user know.
            report_outcome = "failed"
            report_longrepr = "XFAIL: Unexpected pass"

        else:
            report_outcome = "passed"
            report_longrepr = None
    else:
        excinfo = call.excinfo

        handled = False

        if not (call.excinfo and
            call.excinfo.errisinstance(pytest.xfail.Exception)):
            evalxfail = getattr(item, '_evalxfail', None)
            # Something which had an xfail failed: this is expected.
            if evalxfail and (not hasattr(evalxfail, 'expr') or evalxfail.expr):
                report_outcome = "passed"
                report_longrepr = None
                handled = True

        if handled:
            pass

        elif not isinstance(excinfo, py.code.ExceptionInfo):  # @UndefinedVariable
            report_outcome = "failed"
            report_longrepr = excinfo

        elif excinfo.errisinstance(pytest.xfail.Exception):
            # Case where an explicit xfail is raised (i.e.: pytest.xfail("reason") is called
            # programatically).
            report_outcome = "passed"
            report_longrepr = None

        elif excinfo.errisinstance(py.test.skip.Exception):  # @UndefinedVariable
            report_outcome = "skipped"
            r = excinfo._getreprcrash()
            report_longrepr = None #(str(r.path), r.lineno, r.message)

        else:
            report_outcome = "failed"
            if call.when == "call":
                report_longrepr = item.repr_failure(excinfo)

            else: # exception in setup or teardown
                report_longrepr = item._repr_failure_py(excinfo, style=item.config.option.tbstyle)

    filename = item.fspath.strpath
    test = item.location[2]

    status = 'ok'
    captured_output = ''
    error_contents = ''

    if report_outcome in ('passed', 'skipped'):
        #passed or skipped: no need to report if in setup or teardown (only on the actual test if it passed).
        if report_when in ('setup', 'teardown'):
            return

    else:
        #It has only passed, skipped and failed (no error), so, let's consider error if not on call.
        if report_when == 'setup':
            if status == 'ok':
                status = 'error'

        elif report_when == 'teardown':
            if status == 'ok':
                status = 'error'

        else:
            #any error in the call (not in setup or teardown) is considered a regular failure.
            status = 'fail'


    if call.excinfo:
        rep = report_longrepr
        if hasattr(rep, 'reprcrash'):
            reprcrash = rep.reprcrash
            error_contents += str(reprcrash)
            error_contents += '\n'

        if hasattr(rep, 'reprtraceback'):
            error_contents += str(rep.reprtraceback)

        if hasattr(rep, 'sections'):
            for name, content, sep in rep.sections:
                error_contents += sep * 40
                error_contents += name
                error_contents += sep * 40
                error_contents += '\n'
                error_contents += content
                error_contents += '\n'
    else:
        if report_longrepr:
            error_contents += str(report_longrepr)

    if status != 'skip': #I.e.: don't event report skips...
        report_test(status, filename, test, captured_output, error_contents, report_duration)



@pytest.mark.tryfirst
def pytest_runtest_setup(item):  # @DuplicatedSignature
    '''
    Skips tests. With xdist will be on a secondary process.
    '''
    _load_filters()
    if not py_test_accept_filter:
        return #Keep on going (nothing to filter)

    f = _NormFile(str(item.parent.fspath))
    name = item.name

    if f not in py_test_accept_filter:
        pytest.skip() # Skip the file

    accept_tests = py_test_accept_filter[f]

    if item.cls is not None:
        class_name = item.cls.__name__
    else:
        class_name = None
    for test in accept_tests:
        # This happens when parameterizing pytest tests.
        i = name.find('[')
        if i > 0:
            name = name[:i]
        if test == name:
            #Direct match of the test (just go on with the default loading)
            return

        if class_name is not None:
            if test == class_name + '.' + name:
                return

            if class_name == test:
                return

    # If we had a match it'd have returned already.
    pytest.skip() # Skip the test


