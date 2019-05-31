import sys
import threading
import unittest

import pydevconsole
from _pydev_comm.rpc import make_rpc_client, start_rpc_server_and_make_client, start_rpc_server
from _pydevd_bundle import pydevd_io
from pydev_console.protocol import PythonConsoleFrontendService, PythonConsoleBackendService
from pydevconsole import enable_thrift_logging, create_server_handler_factory

try:
    raw_input
    raw_input_name = 'raw_input'
except NameError:
    raw_input_name = 'input'

#=======================================================================================================================
# Test
#=======================================================================================================================
class Test(unittest.TestCase):

    def test_console_hello(self):
        self.original_stdout = sys.stdout
        sys.stdout = pydevd_io.IOBuf()
        try:
            sys.stdout.encoding = sys.stdin.encoding
        except AttributeError:
            # In Python 3 encoding is not writable (whereas in Python 2 it doesn't exist).
            pass

        try:
            rpc_client = self.start_client_thread()  #@UnusedVariable
            import time
            time.sleep(.3)  #let's give it some time to start the threads

            from _pydev_bundle import pydev_localhost
            interpreter = pydevconsole.InterpreterInterface(threading.currentThread(), rpc_client=rpc_client)

            (result,) = interpreter.hello("Hello pydevconsole")
            self.assertEqual(result, "Hello eclipse")
        finally:
            sys.stdout = self.original_stdout


    def test_console_requests(self):
        self.original_stdout = sys.stdout
        sys.stdout = pydevd_io.IOBuf()

        try:
            rpc_client = self.start_client_thread()  #@UnusedVariable
            import time
            time.sleep(.3)  #let's give it some time to start the threads

            from _pydev_bundle import pydev_localhost
            from _pydev_bundle.pydev_console_types import CodeFragment

            interpreter = pydevconsole.InterpreterInterface(threading.currentThread(), rpc_client=rpc_client)
            sys.stdout = pydevd_io.IOBuf()
            interpreter.add_exec(CodeFragment('class Foo:\n    CONSTANT=1\n'))
            interpreter.add_exec(CodeFragment('foo=Foo()'))
            interpreter.add_exec(CodeFragment('foo.__doc__=None'))
            interpreter.add_exec(CodeFragment('val = %s()' % (raw_input_name,)))
            interpreter.add_exec(CodeFragment('50'))
            interpreter.add_exec(CodeFragment('print (val)'))
            found = sys.stdout.getvalue().split()
            try:
                self.assertEqual(['50', 'input_request'], found)
            except:
                try:
                    self.assertEqual(['input_request'], found)  #IPython
                except:
                    self.assertEqual([u'50', u'input_request'], found[1:]) # IPython 5.1
                    self.assertTrue(found[0].startswith(u'Out'))

            comps = interpreter.do_get_completions('foo.', 'foo.')
            self.assertTrue(
                ('CONSTANT', '', '', '3') in comps or ('CONSTANT', '', '', '4') in comps, \
                'Found: %s' % comps
            )

            comps = interpreter.do_get_completions('"".', '"".')
            self.assertTrue(
                ('__add__', 'x.__add__(y) <==> x+y', '', '3') in comps or
                ('__add__', '', '', '4') in comps or
                ('__add__', 'x.__add__(y) <==> x+y\r\nx.__add__(y) <==> x+y', '()', '2') in comps or
                ('__add__', 'x.\n__add__(y) <==> x+yx.\n__add__(y) <==> x+y', '()', '2'),
                'Did not find __add__ in : %s' % (comps,)
            )


            completions = interpreter.do_get_completions('', '')
            for c in completions:
                if c[0] == 'AssertionError':
                    break
            else:
                self.fail('Could not find AssertionError')

            completions = interpreter.do_get_completions('Assert', 'Assert')
            for c in completions:
                if c[0] == 'RuntimeError':
                    self.fail('Did not expect to find RuntimeError there')

            self.assertTrue(('__doc__', None, '', '3') not in interpreter.do_get_completions('foo.CO', 'foo.'))

            comps = interpreter.do_get_completions('va', 'va')
            self.assertTrue(('val', '', '', '3') in comps or ('vars', '', '', '4') in comps)

            interpreter.add_exec(CodeFragment('s = "mystring"'))

            desc = interpreter.getDescription('val')
            self.assertTrue(desc.find('str(object) -> string') >= 0 or
                         desc == "'input_request'" or
                         desc.find('str(string[, encoding[, errors]]) -> str') >= 0 or
                         desc.find('str(Char* value)') >= 0 or
                         desc.find('str(object=\'\') -> string') >= 0 or
                         desc.find('str(value: Char*)') >= 0 or
                         desc.find('str(object=\'\') -> str') >= 0 or
                         desc.find('unicode(object=\'\') -> unicode object') >= 0 or
                         desc.find('The most base type') >= 0 # Jython 2.7 is providing this :P
                         ,
                         'Could not find what was needed in %s' % desc)

            desc = interpreter.getDescription('val.join')
            self.assertTrue(desc.find('S.join(sequence) -> string') >= 0 or
                         desc.find('S.join(sequence) -> str') >= 0 or
                         desc.find('S.join(iterable) -> string') >= 0 or
                         desc == "<builtin method 'join'>"  or
                         desc == "<built-in method join of str object>" or
                         desc.find('str join(str self, list sequence)') >= 0 or
                         desc.find('S.join(iterable) -> str') >= 0 or
                         desc.find('S.join(iterable) -> unicode') >= 0 or
                         desc.find('join(self: str, sequence: list) -> str') >= 0,
                         "Could not recognize: %s" % (desc,))
        finally:
            sys.stdout = self.original_stdout


    def create_frontend_handler(self):
        class HandleRequestInput:
            def __init__(self):
                self.requested_input = False
                self.notified_finished = 0
                self.rpc_client = None

            def requestInput(self, path):
                self.requested_input = True
                return 'input_request'

            def notifyFinished(self, needs_more_input):
                self.notified_finished += 1

            def notifyAboutMagic(self, commands, is_auto_magic):
                pass

        return HandleRequestInput()

    def start_client_thread(self):
        from _pydev_bundle import pydev_localhost

        enable_thrift_logging()

        # here we start the test server
        server_socket = start_rpc_server_and_make_client(pydev_localhost.get_localhost(), 0,
                                                         PythonConsoleFrontendService,
                                                         PythonConsoleBackendService,
                                                         create_server_handler_factory(self.create_frontend_handler()))

        host, port = server_socket.getsockname()

        import time
        time.sleep(1)

        rpc_client, _ = make_rpc_client(PythonConsoleFrontendService, host, port)

        return rpc_client


    def start_debugger_server_thread(self, debugger_port, socket_code):
        class DebuggerServerThread(threading.Thread):
            def __init__(self, debugger_port, socket_code):
                threading.Thread.__init__(self)
                self.debugger_port = debugger_port
                self.socket_code = socket_code
            def run(self):
                import socket
                s = socket.socket()
                s.bind(('', debugger_port))
                s.listen(1)
                socket, unused_addr = s.accept()
                socket_code(socket)

        debugger_thread = DebuggerServerThread(debugger_port, socket_code)
        debugger_thread.setDaemon(True)
        debugger_thread.start()
        return debugger_thread


    def get_free_addresses(self):
        from _pydev_bundle.pydev_localhost import get_socket_names
        socket_names = get_socket_names(2, True)
        port0 = socket_names[0][1]
        port1 = socket_names[1][1]

        assert port0 != port1
        assert port0 > 0
        assert port1 > 0

        return port0, port1


    def test_server(self):
        self.original_stdout = sys.stdout
        sys.stdout = pydevd_io.IOBuf()
        try:
            from _pydev_bundle.pydev_localhost import get_socket_name
            host, port = get_socket_name(close=True)

            class ServerThread(threading.Thread):
                def __init__(self, backend_port):
                    threading.Thread.__init__(self)
                    self.backend_port = backend_port

                def run(self):
                    from _pydev_bundle import pydev_localhost
                    pydevconsole.start_server(self.backend_port)

            server_thread = ServerThread(port)
            server_thread.setDaemon(True)
            server_thread.start()

            import time
            time.sleep(1)  #let's give it some time to start the threads

            rpc_client, server_transport = make_rpc_client(PythonConsoleBackendService, host, port)

            server_service = PythonConsoleFrontendService

            server_handler = self.create_frontend_handler()

            start_rpc_server(server_transport, server_service, server_handler)

            rpc_client.execLine('class Foo:')
            rpc_client.execLine('    pass')
            rpc_client.execLine('')
            rpc_client.execLine('foo = Foo()')
            rpc_client.execLine('a = %s()' % (raw_input_name,))
            rpc_client.execLine('print (a)')
            initial = time.time()
            while not server_handler.requested_input:
                if time.time() - initial > 2:
                    raise AssertionError('Did not get the return asked before the timeout.')
                time.sleep(.1)

            found = sys.stdout.getvalue()
            while ['input_request'] != found.split():
                found += sys.stdout.getvalue()
                if time.time() - initial > 2:
                    break
                time.sleep(.1)
            self.assertIn('input_request', found.split())
        finally:
            sys.stdout = self.original_stdout

