import os
import sys
import time
import unittest

import pytest

from _pydev_bundle.pydev_stdin import StdIn
from _pydev_bundle.pydev_localhost import get_localhost
from _pydev_comm.pydev_rpc import make_rpc_client
from _pydevd_bundle import pydevd_io
from pydev_console.pydev_protocol import PythonConsoleFrontendService, PythonConsoleBackendService
from pydevconsole import enable_thrift_logging, create_server_handler_factory

try:
    xrange
except:
    xrange = range

def eq_(a, b):
    if a != b:
        raise AssertionError('%s != %s' % (a, b))
    
try:
    from IPython import core
    has_ipython = True
except:
    has_ipython = False

@pytest.mark.skipif(not has_ipython, reason='IPython not available')
class TestBase(unittest.TestCase):


    def setUp(self):
        from _pydev_bundle.pydev_ipython_console_011 import get_pydev_ipython_frontend

        # PyDevFrontEnd depends on singleton in IPython, so you
        # can't make multiple versions. So we reuse self.front_end for
        # all the tests
        self.front_end = get_pydev_ipython_frontend(None)

        from pydev_ipython.inputhook import set_return_control_callback
        set_return_control_callback(lambda:True)
        self.front_end.clear_buffer()

    def tearDown(self):
        pass

    def add_exec(self, code, expected_more=False):
        more, exception_occurred = self.front_end.add_exec(code)
        eq_(expected_more, more)

    def redirect_stdout(self):
        from IPython.utils import io

        self.original_stdout = sys.stdout
        sys.stdout = io.stdout = pydevd_io.IOBuf()

    def restore_stdout(self):
        from IPython.utils import io
        io.stdout = sys.stdout = self.original_stdout


@pytest.mark.skipif(not has_ipython, reason='IPython not available')
class TestPyDevFrontEnd(TestBase):

    def testAddExec_1(self):
        self.add_exec('if True:', True)

    
    def testAddExec_2(self):
        #Change: 'more' must now be controlled in the client side after the initial 'True' returned.
        self.add_exec('if True:\n    testAddExec_a = 10\n', False)
        assert 'testAddExec_a' in self.front_end.get_namespace()

    
    def testAddExec_3(self):
        assert 'testAddExec_x' not in self.front_end.get_namespace()
        self.add_exec('if True:\n    testAddExec_x = 10\n\n')
        assert 'testAddExec_x' in self.front_end.get_namespace()
        eq_(self.front_end.get_namespace()['testAddExec_x'], 10)

    
    def test_get_namespace(self):
        assert 'testGetNamespace_a' not in self.front_end.get_namespace()
        self.add_exec('testGetNamespace_a = 10')
        assert 'testGetNamespace_a' in self.front_end.get_namespace()
        eq_(self.front_end.get_namespace()['testGetNamespace_a'], 10)

    
    def test_complete(self):
        unused_text, matches = self.front_end.complete('%')
        assert len(matches) > 1, 'at least one magic should appear in completions'

    
    def test_complete_does_not_do_python_matches(self):
        # Test that IPython's completions do not do the things that
        # PyDev's completions will handle
        self.add_exec('testComplete_a = 5')
        self.add_exec('testComplete_b = 10')
        self.add_exec('testComplete_c = 15')
        unused_text, matches = self.front_end.complete('testComplete_')
        assert len(matches) == 0

    
    def testGetCompletions_1(self):
        # Test the merged completions include the standard completions
        self.add_exec('testComplete_a = 5')
        self.add_exec('testComplete_b = 10')
        self.add_exec('testComplete_c = 15')
        res = self.front_end.getCompletions('testComplete_', 'testComplete_')
        matches = [f[0] for f in res]
        assert len(matches) == 3
        eq_(set(['testComplete_a', 'testComplete_b', 'testComplete_c']), set(matches))

    
    def testGetCompletions_2(self):
        # Test that we get IPython completions in results
        # we do this by checking kw completion which PyDev does
        # not do by default
        self.add_exec('def ccc(ABC=123): pass')
        res = self.front_end.getCompletions('ccc(', '')
        matches = [f[0] for f in res]
        assert 'ABC=' in matches

    
    def testGetCompletions_3(self):
        # Test that magics return IPYTHON magic as type
        res = self.front_end.getCompletions('%cd', '%cd')
        assert len(res) == 1
        eq_(res[0][3], '12')  # '12' == IToken.TYPE_IPYTHON_MAGIC
        assert len(res[0][1]) > 100, 'docstring for %cd should be a reasonably long string'

@pytest.mark.skipif(not has_ipython, reason='IPython not available')
class TestRunningCode(TestBase):
    
    def test_print(self):
        self.redirect_stdout()
        try:
            self.add_exec('print("output")')
            eq_(sys.stdout.getvalue(), 'output\n')
        finally:
            self.restore_stdout()

    
    def testQuestionMark_1(self):
        self.redirect_stdout()
        try:
            self.add_exec('?')
            found = sys.stdout.getvalue()
            if len(found) < 1000:
                raise AssertionError('Expected IPython help to be big. Found: %s' % (found,))
        finally:
            self.restore_stdout()

    
    def testQuestionMark_2(self):
        self.redirect_stdout()
        try:
            self.add_exec('int?')
            found = sys.stdout.getvalue()
            if 'Convert' not in found:
                raise AssertionError('Expected to find "Convert" in %s' % (found,))
        finally:
            self.restore_stdout()


    
    def test_gui(self):
        try:
            import Tkinter
        except:
            return
        else:
            from pydev_ipython.inputhook import get_inputhook
            assert get_inputhook() is None
            self.add_exec('%gui tk')
            # we can't test the GUI works here because we aren't connected to XML-RPC so
            # nowhere for hook to run
            assert get_inputhook() is not None
            self.add_exec('%gui none')
            assert get_inputhook() is None

    
    def test_history(self):
        ''' Make sure commands are added to IPython's history '''
        self.redirect_stdout()
        try:
            self.add_exec('a=1')
            self.add_exec('b=2')
            _ih = self.front_end.get_namespace()['_ih']
            eq_(_ih[-1], 'b=2')
            eq_(_ih[-2], 'a=1')

            self.add_exec('history')
            hist = sys.stdout.getvalue().split('\n')
            eq_(hist[-1], '')
            eq_(hist[-2], 'history')
            eq_(hist[-3], 'b=2')
            eq_(hist[-4], 'a=1')
        finally:
            self.restore_stdout()

    
    def test_edit(self):
        ''' Make sure we can issue an edit command'''
        if os.environ.get('TRAVIS') == 'true':
            # This test is too flaky on travis.
            return

        from _pydev_bundle.pydev_ipython_console_011 import get_pydev_ipython_frontend
        from _pydev_comm.pydev_rpc import start_rpc_server_and_make_client

        called_RequestInput = [False]
        called_IPythonEditor = [False]

        class RequestInputHandler:
            def __init__(self):
                self.rpc_client = None

            def requestInput(self, path):
                called_RequestInput[0] = True
                return '\n'

            def IPythonEditor(self, name, line):
                called_IPythonEditor[0] = (name, line)
                return True

        enable_thrift_logging()

        # here we start the test server
        server_socket = start_rpc_server_and_make_client(get_localhost(), 0,
                                                         PythonConsoleFrontendService,
                                                         PythonConsoleBackendService,
                                                         create_server_handler_factory(RequestInputHandler()))

        host, port = server_socket.getsockname()

        rpc_client, _ = make_rpc_client(PythonConsoleFrontendService, host, port)

        # PyDevFrontEnd depends on singleton in IPython, so you
        # can't make multiple versions. So we reuse self.front_end for
        # all the tests
        self.front_end = get_pydev_ipython_frontend(rpc_client)

        orig_stdin = sys.stdin
        sys.stdin = StdIn(self, rpc_client)
        try:
            filename = 'made_up_file.py'
            self.add_exec('%edit ' + filename)

            for i in xrange(10):
                if called_IPythonEditor[0] == (os.path.abspath(filename), '0'):
                    break
                time.sleep(.1)

            if not called_IPythonEditor[0]:
                #   File "/home/travis/miniconda/lib/python3.3/site-packages/IPython/core/interactiveshell.py", line 2883, in run_code
                #     exec(code_obj, self.user_global_ns, self.user_ns)
                #   File "<ipython-input-15-09583ca3bce1>", line 1, in <module>
                #     get_ipython().magic('edit made_up_file.py')
                #   File "/home/travis/miniconda/lib/python3.3/site-packages/IPython/core/interactiveshell.py", line 2205, in magic
                #     return self.run_line_magic(magic_name, magic_arg_s)
                #   File "/home/travis/miniconda/lib/python3.3/site-packages/IPython/core/interactiveshell.py", line 2126, in run_line_magic
                #     result = fn(*args,**kwargs)
                #   File "<string>", line 2, in edit
                #   File "/home/travis/miniconda/lib/python3.3/site-packages/IPython/core/magic.py", line 193, in <lambda>
                #     call = lambda f, *a, **k: f(*a, **k)
                #   File "/home/travis/miniconda/lib/python3.3/site-packages/IPython/core/magics/code.py", line 662, in edit
                #     self.shell.hooks.editor(filename,lineno)
                #   File "/home/travis/build/fabioz/PyDev.Debugger/pydev_ipython_console_011.py", line 70, in call_editor
                #     server.IPythonEditor(filename, str(line))
                #   File "/home/travis/miniconda/lib/python3.3/xmlrpc/client.py", line 1090, in __call__
                #     return self.__send(self.__name, args)
                #   File "/home/travis/miniconda/lib/python3.3/xmlrpc/client.py", line 1419, in __request
                #     verbose=self.__verbose
                #   File "/home/travis/miniconda/lib/python3.3/xmlrpc/client.py", line 1132, in request
                #     return self.single_request(host, handler, request_body, verbose)
                #   File "/home/travis/miniconda/lib/python3.3/xmlrpc/client.py", line 1143, in single_request
                #     http_conn = self.send_request(host, handler, request_body, verbose)
                #   File "/home/travis/miniconda/lib/python3.3/xmlrpc/client.py", line 1255, in send_request
                #     self.send_content(connection, request_body)
                #   File "/home/travis/miniconda/lib/python3.3/xmlrpc/client.py", line 1285, in send_content
                #     connection.endheaders(request_body)
                #   File "/home/travis/miniconda/lib/python3.3/http/client.py", line 1061, in endheaders
                #     self._send_output(message_body)
                #   File "/home/travis/miniconda/lib/python3.3/http/client.py", line 906, in _send_output
                #     self.send(msg)
                #   File "/home/travis/miniconda/lib/python3.3/http/client.py", line 844, in send
                #     self.connect()
                #   File "/home/travis/miniconda/lib/python3.3/http/client.py", line 822, in connect
                #     self.timeout, self.source_address)
                #   File "/home/travis/miniconda/lib/python3.3/socket.py", line 435, in create_connection
                #     raise err
                #   File "/home/travis/miniconda/lib/python3.3/socket.py", line 426, in create_connection
                #     sock.connect(sa)
                # ConnectionRefusedError: [Errno 111] Connection refused

                # I.e.: just warn that the test failing, don't actually fail.
                sys.stderr.write('Test failed: this test is brittle in travis because sometimes the connection is refused (as above) and we do not have a callback.\n')
                return

            eq_(called_IPythonEditor[0], (os.path.abspath(filename), '0'))
            assert called_RequestInput[0], "Make sure the 'wait' parameter has been respected"
        finally:
            sys.stdin = orig_stdin

