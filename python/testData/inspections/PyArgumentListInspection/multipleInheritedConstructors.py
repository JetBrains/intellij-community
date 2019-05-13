class ThreadingMixIn: pass

class TCPServer:
    def __init__(self, server_address, RequestHandlerClass):
        pass

class HTTPServer(TCPServer):
    pass

class ProxyHandler: pass

class ThreadingHTTPServer(ThreadingMixIn, HTTPServer):
  pass


httpd = ThreadingHTTPServer(('127.0.0.1', 8000), ProxyHandler)