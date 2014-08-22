import sys
import unittest
import threading
import os
from nose.tools import eq_
from pydev_imports import StringIO, SimpleXMLRPCServer
from pydev_localhost import get_localhost
from pydev_console_utils import StdIn
import socket
from pydev_ipython_console_011 import get_pydev_frontend
import time

try:
    xrange
except:
    xrange = range


class TestBase(unittest.TestCase):
    
    
    def setUp(self):
        # PyDevFrontEnd depends on singleton in IPython, so you
        # can't make multiple versions. So we reuse self.front_end for
        # all the tests
        self.front_end = get_pydev_frontend(get_localhost(), 0)
        
        from pydev_ipython.inputhook import set_return_control_callback
        set_return_control_callback(lambda:True)
        self.front_end.clearBuffer()

    def tearDown(self):
        pass
    
    def addExec(self, code, expected_more=False):
        more = self.front_end.addExec(code)
        eq_(expected_more, more)
    
    def redirectStdout(self):
        from IPython.utils import io
        
        self.original_stdout = sys.stdout
        sys.stdout = io.stdout = StringIO()
    
    def restoreStdout(self):
        from IPython.utils import io
        io.stdout = sys.stdout = self.original_stdout


class TestPyDevFrontEnd(TestBase):
    
    def testAddExec_1(self):
        self.addExec('if True:', True)
        
    def testAddExec_2(self):
        #Change: 'more' must now be controlled in the client side after the initial 'True' returned.
        self.addExec('if True:\n    testAddExec_a = 10\n', False) 
        assert 'testAddExec_a' in self.front_end.getNamespace()
        
    def testAddExec_3(self):
        assert 'testAddExec_x' not in self.front_end.getNamespace()
        self.addExec('if True:\n    testAddExec_x = 10\n\n')
        assert 'testAddExec_x' in self.front_end.getNamespace()
        eq_(self.front_end.getNamespace()['testAddExec_x'], 10)

    def testGetNamespace(self):
        assert 'testGetNamespace_a' not in self.front_end.getNamespace()
        self.addExec('testGetNamespace_a = 10')
        assert 'testGetNamespace_a' in self.front_end.getNamespace()
        eq_(self.front_end.getNamespace()['testGetNamespace_a'], 10)

    def testComplete(self):
        unused_text, matches = self.front_end.complete('%')
        assert len(matches) > 1, 'at least one magic should appear in completions'

    def testCompleteDoesNotDoPythonMatches(self):
        # Test that IPython's completions do not do the things that
        # PyDev's completions will handle
        self.addExec('testComplete_a = 5')
        self.addExec('testComplete_b = 10')
        self.addExec('testComplete_c = 15')
        unused_text, matches = self.front_end.complete('testComplete_')
        assert len(matches) == 0

    def testGetCompletions_1(self):
        # Test the merged completions include the standard completions
        self.addExec('testComplete_a = 5')
        self.addExec('testComplete_b = 10')
        self.addExec('testComplete_c = 15')
        res = self.front_end.getCompletions('testComplete_', 'testComplete_')
        matches = [f[0] for f in res]
        assert len(matches) == 3
        eq_(set(['testComplete_a', 'testComplete_b', 'testComplete_c']), set(matches))

    def testGetCompletions_2(self):
        # Test that we get IPython completions in results
        # we do this by checking kw completion which PyDev does
        # not do by default
        self.addExec('def ccc(ABC=123): pass')
        res = self.front_end.getCompletions('ccc(', '')
        matches = [f[0] for f in res]
        assert 'ABC=' in matches

    def testGetCompletions_3(self):
        # Test that magics return IPYTHON magic as type
        res = self.front_end.getCompletions('%cd', '%cd')
        assert len(res) == 1
        eq_(res[0][3], '12')  # '12' == IToken.TYPE_IPYTHON_MAGIC
        assert len(res[0][1]) > 100, 'docstring for %cd should be a reasonably long string'

class TestRunningCode(TestBase):
    def testPrint(self):
        self.redirectStdout()
        try:
            self.addExec('print("output")')
            eq_(sys.stdout.getvalue(), 'output\n')
        finally:
            self.restoreStdout()

    def testQuestionMark_1(self):
        self.redirectStdout()
        try:
            self.addExec('?')
            assert len(sys.stdout.getvalue()) > 1000, 'IPython help should be pretty big'
        finally:
            self.restoreStdout()

    def testQuestionMark_2(self):
        self.redirectStdout()
        try:
            self.addExec('int?')
            assert sys.stdout.getvalue().find('Convert') != -1
        finally:
            self.restoreStdout()


    def testGui(self):
        try:
            import Tkinter
        except:
            return
        else:
            from pydev_ipython.inputhook import get_inputhook
            assert get_inputhook() is None
            self.addExec('%gui tk')
            # we can't test the GUI works here because we aren't connected to XML-RPC so
            # nowhere for hook to run
            assert get_inputhook() is not None
            self.addExec('%gui none')
            assert get_inputhook() is None

    def testHistory(self):
        ''' Make sure commands are added to IPython's history '''
        self.redirectStdout()
        try:
            self.addExec('a=1')
            self.addExec('b=2')
            _ih = self.front_end.getNamespace()['_ih']
            eq_(_ih[-1], 'b=2')
            eq_(_ih[-2], 'a=1')
    
            self.addExec('history')
            hist = sys.stdout.getvalue().split('\n')
            eq_(hist[-1], '')
            eq_(hist[-2], 'history')
            eq_(hist[-3], 'b=2')
            eq_(hist[-4], 'a=1')
        finally:
            self.restoreStdout()

    def testEdit(self):
        ''' Make sure we can issue an edit command'''
        called_RequestInput = [False]
        called_IPythonEditor = [False]
        def startClientThread(client_port):
            class ClientThread(threading.Thread):
                def __init__(self, client_port):
                    threading.Thread.__init__(self)
                    self.client_port = client_port
                def run(self):
                    class HandleRequestInput:
                        def RequestInput(self):
                            called_RequestInput[0] = True
                            return '\n'
                        def IPythonEditor(self, name, line):
                            called_IPythonEditor[0] = (name, line)
                            return True

                    handle_request_input = HandleRequestInput()

                    import pydev_localhost
                    self.client_server = client_server = SimpleXMLRPCServer(
                        (pydev_localhost.get_localhost(), self.client_port), logRequests=False)
                    client_server.register_function(handle_request_input.RequestInput)
                    client_server.register_function(handle_request_input.IPythonEditor)
                    client_server.serve_forever()
                    
                def shutdown(self):
                    return
                    self.client_server.shutdown()

            client_thread = ClientThread(client_port)
            client_thread.setDaemon(True)
            client_thread.start()
            return client_thread

        # PyDevFrontEnd depends on singleton in IPython, so you
        # can't make multiple versions. So we reuse self.front_end for
        # all the tests
        s = socket.socket()
        s.bind(('', 0))
        self.client_port = client_port = s.getsockname()[1]
        s.close()
        self.front_end = get_pydev_frontend(get_localhost(), client_port)

        client_thread = startClientThread(self.client_port)
        orig_stdin = sys.stdin
        sys.stdin = StdIn(self, get_localhost(), self.client_port)
        try:
            filename = 'made_up_file.py'
            self.addExec('%edit ' + filename)
            
            for i in xrange(10):
                if called_IPythonEditor[0] == (os.path.abspath(filename), '0'):
                    break
                time.sleep(.1)
                
            eq_(called_IPythonEditor[0], (os.path.abspath(filename), '0'))
            assert called_RequestInput[0], "Make sure the 'wait' parameter has been respected"
        finally:
            sys.stdin = orig_stdin
            client_thread.shutdown()

if __name__ == '__main__':

    #Just doing: unittest.main() was not working for me when run directly (not sure why)
    #And doing it the way below the test with the import: from pydev_ipython.inputhook import get_inputhook, set_stdin_file
    #is failing (but if I do a Ctrl+F9 in PyDev to run it, it works properly, so, I'm a bit puzzled here).
    unittest.TextTestRunner(verbosity=1).run(unittest.makeSuite(TestRunningCode))
    unittest.TextTestRunner(verbosity=1).run(unittest.makeSuite(TestPyDevFrontEnd))
