import threading
import unittest
import sys
import os

sys.argv[0] = os.path.dirname(sys.argv[0])
sys.path.insert(1, os.path.join(os.path.dirname(sys.argv[0])))
import pydevconsole
from pydev_imports import xmlrpclib, SimpleXMLRPCServer, StringIO

try:
    raw_input
    raw_input_name = 'raw_input'
except NameError:
    raw_input_name = 'input'

#=======================================================================================================================
# Test
#=======================================================================================================================
class Test(unittest.TestCase):

    def setUp(self):
        self.original_stdout = sys.stdout
        sys.stdout = StringIO()


    def tearDown(self):
        ret = sys.stdout  #@UnusedVariable
        sys.stdout = self.original_stdout
        #print_ ret.getvalue() -- use to see test output

    def testConsoleHello(self):
        client_port, _server_port = self.getFreeAddresses()
        client_thread = self.startClientThread(client_port)  #@UnusedVariable
        import time
        time.sleep(.3)  #let's give it some time to start the threads

        import pydev_localhost
        interpreter = pydevconsole.InterpreterInterface(pydev_localhost.get_localhost(), client_port, server=None)

        (result,) = interpreter.hello("Hello pydevconsole")
        self.assertEqual(result, "Hello eclipse")


    def testConsoleRequests(self):
        client_port, _server_port = self.getFreeAddresses()
        client_thread = self.startClientThread(client_port)  #@UnusedVariable
        import time
        time.sleep(.3)  #let's give it some time to start the threads

        import pydev_localhost
        interpreter = pydevconsole.InterpreterInterface(pydev_localhost.get_localhost(), client_port, server=None)
        interpreter.addExec('class Foo:')
        interpreter.addExec('   CONSTANT=1')
        interpreter.addExec('')
        interpreter.addExec('foo=Foo()')
        interpreter.addExec('foo.__doc__=None')
        interpreter.addExec('val = %s()' % (raw_input_name,))
        interpreter.addExec('50')
        interpreter.addExec('print (val)')
        found = sys.stdout.getvalue().split()
        try:
            self.assertEqual(['50', 'input_request'], found)
        except:
            self.assertEqual(['input_request'], found)  #IPython

        comps = interpreter.getCompletions('foo.', 'foo.')
        self.assert_(
            ('CONSTANT', '', '', '3') in comps or ('CONSTANT', '', '', '4') in comps, \
            'Found: %s' % comps
        )

        comps = interpreter.getCompletions('"".', '"".')
        self.assert_(
            ('__add__', 'x.__add__(y) <==> x+y', '', '3') in comps or
            ('__add__', '', '', '4') in comps or
            ('__add__', 'x.__add__(y) <==> x+y\r\nx.__add__(y) <==> x+y', '()', '2') in comps or
            ('__add__', 'x.\n__add__(y) <==> x+yx.\n__add__(y) <==> x+y', '()', '2'),
            'Did not find __add__ in : %s' % (comps,)
        )


        completions = interpreter.getCompletions('', '')
        for c in completions:
            if c[0] == 'AssertionError':
                break
        else:
            self.fail('Could not find AssertionError')

        completions = interpreter.getCompletions('Assert', 'Assert')
        for c in completions:
            if c[0] == 'RuntimeError':
                self.fail('Did not expect to find RuntimeError there')

        self.assert_(('__doc__', None, '', '3') not in interpreter.getCompletions('foo.CO', 'foo.'))

        comps = interpreter.getCompletions('va', 'va')
        self.assert_(('val', '', '', '3') in comps or ('val', '', '', '4') in comps)

        interpreter.addExec('s = "mystring"')

        desc = interpreter.getDescription('val')
        self.assert_(desc.find('str(object) -> string') >= 0 or
                     desc == "'input_request'" or
                     desc.find('str(string[, encoding[, errors]]) -> str') >= 0 or
                     desc.find('str(Char* value)') >= 0 or
                     desc.find('str(value: Char*)') >= 0,
                     'Could not find what was needed in %s' % desc)

        desc = interpreter.getDescription('val.join')
        self.assert_(desc.find('S.join(sequence) -> string') >= 0 or
                     desc.find('S.join(sequence) -> str') >= 0 or
                     desc.find('S.join(iterable) -> string') >= 0 or
                     desc == "<builtin method 'join'>"  or
                     desc == "<built-in method join of str object>" or
                     desc.find('str join(str self, list sequence)') >= 0 or
                     desc.find('S.join(iterable) -> str') >= 0 or
                     desc.find('join(self: str, sequence: list) -> str') >= 0,
                     "Could not recognize: %s" % (desc,))


    def startClientThread(self, client_port):
        class ClientThread(threading.Thread):
            def __init__(self, client_port):
                threading.Thread.__init__(self)
                self.client_port = client_port
            def run(self):
                class HandleRequestInput:
                    def RequestInput(self):
                        return 'input_request'

                handle_request_input = HandleRequestInput()

                import pydev_localhost
                client_server = SimpleXMLRPCServer((pydev_localhost.get_localhost(), self.client_port), logRequests=False)
                client_server.register_function(handle_request_input.RequestInput)
                client_server.serve_forever()

        client_thread = ClientThread(client_port)
        client_thread.setDaemon(True)
        client_thread.start()
        return client_thread


    def startDebuggerServerThread(self, debugger_port, socket_code):
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


    def getFreeAddresses(self):
        import socket
        s = socket.socket()
        s.bind(('', 0))
        port0 = s.getsockname()[1]

        s1 = socket.socket()
        s1.bind(('', 0))
        port1 = s1.getsockname()[1]
        s.close()
        s1.close()

        if port0 <= 0 or port1 <= 0:
            #This happens in Jython...
            from java.net import ServerSocket
            s0 = ServerSocket(0)
            port0 = s0.getLocalPort()

            s1 = ServerSocket(0)
            port1 = s1.getLocalPort()

            s0.close()
            s1.close()

        assert port0 != port1
        assert port0 > 0
        assert port1 > 0

        return port0, port1


    def testServer(self):
        client_port, server_port = self.getFreeAddresses()
        class ServerThread(threading.Thread):
            def __init__(self, client_port, server_port):
                threading.Thread.__init__(self)
                self.client_port = client_port
                self.server_port = server_port

            def run(self):
                import pydev_localhost
                pydevconsole.StartServer(pydev_localhost.get_localhost(), self.server_port, self.client_port)
        server_thread = ServerThread(client_port, server_port)
        server_thread.setDaemon(True)
        server_thread.start()

        client_thread = self.startClientThread(client_port)  #@UnusedVariable

        import time
        time.sleep(.3)  #let's give it some time to start the threads

        import pydev_localhost
        server = xmlrpclib.Server('http://%s:%s' % (pydev_localhost.get_localhost(), server_port))
        server.addExec('class Foo:')
        server.addExec('    pass')
        server.addExec('')
        server.addExec('foo = Foo()')
        server.addExec('a = %s()' % (raw_input_name,))
        server.addExec('print (a)')
        self.assertEqual(['input_request'], sys.stdout.getvalue().split())

#=======================================================================================================================
# main
#=======================================================================================================================
if __name__ == '__main__':
    unittest.main()

