import sys
import os

#Put pydevconsole in the path.
sys.argv[0] = os.path.dirname(sys.argv[0]) 
sys.path.insert(1, os.path.join(os.path.dirname(sys.argv[0])))

print('Running tests with:', sys.executable)
print('PYTHONPATH:')
print('\n'.join(sorted(sys.path)))

import threading
import unittest

import pydevconsole
from pydev_imports import xmlrpclib, SimpleXMLRPCServer

try:
    raw_input
    raw_input_name = 'raw_input'
except NameError:
    raw_input_name = 'input'

#=======================================================================================================================
# Test
#=======================================================================================================================
class Test(unittest.TestCase):

    
    def startClientThread(self, client_port):
        class ClientThread(threading.Thread):
            def __init__(self, client_port):
                threading.Thread.__init__(self)
                self.client_port = client_port
                
            def run(self):
                class HandleRequestInput:
                    def RequestInput(self):
                        return 'RequestInput: OK'
                
                handle_request_input = HandleRequestInput()
                
                import pydev_localhost
                print('Starting client with:', pydev_localhost.get_localhost(), self.client_port)
                client_server = SimpleXMLRPCServer((pydev_localhost.get_localhost(), self.client_port), logRequests=False)
                client_server.register_function(handle_request_input.RequestInput)
                client_server.serve_forever()
                
        client_thread = ClientThread(client_port)
        client_thread.setDaemon(True)
        client_thread.start()
        return client_thread

        
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
                print('Starting server with:', pydev_localhost.get_localhost(), self.server_port, self.client_port)
                pydevconsole.StartServer(pydev_localhost.get_localhost(), self.server_port, self.client_port)
        server_thread = ServerThread(client_port, server_port)
        server_thread.setDaemon(True)
        server_thread.start()

        client_thread = self.startClientThread(client_port) #@UnusedVariable
        
        import time
        time.sleep(.3) #let's give it some time to start the threads
        
        import pydev_localhost
        server = xmlrpclib.Server('http://%s:%s' % (pydev_localhost.get_localhost(), server_port))
        server.addExec("import sys; print('Running with: %s %s' % (sys.executable or sys.platform, sys.version))")
        server.addExec('class Foo:')
        server.addExec('    pass')
        server.addExec('')
        server.addExec('foo = Foo()')
        server.addExec('a = %s()' % raw_input_name)
        server.addExec('print (a)')
        
#=======================================================================================================================
# main        
#=======================================================================================================================
if __name__ == '__main__':
    unittest.main()

