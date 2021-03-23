# -*- coding: utf-8 -*-

"""
# Run server:
>>> import _shaded_thriftpy
>>> from _shaded_thriftpy.http import make_server
>>> pingpong = _shaded_thriftpy.load("pingpong.thrift")
>>>
>>> class Dispatcher(object):
>>>     def ping(self):
>>>         return "pong"

>>> server = make_server(pingpong.PingService, Dispatcher(),
                         host='127.0.0.1', port=6000)
>>> server.serve()

# Run client:
>>> import _shaded_thriftpy
>>> from _shaded_thriftpy.http import make_client
>>> pingpong = _shaded_thriftpy.load("pingpong.thrift")
>>> client = make_client(pingpong.PingService, host='127.0.0.1', port=6000)
>>> client.ping()

# Run HTTPS client with unverified SSL context for TESTING ONLY purpose:
>>> import ssl
>>> ssl_context_factory = ssl._create_unverified_context
>>> client = make_client(pingpong.PingService, host='example.com', port=443,
...                      scheme="https",
...                      ssl_context_factory=ssl_context_factory)
>>> client.ping()
"""

from __future__ import absolute_import

import os
import socket
import sys
from contextlib import contextmanager
from io import BytesIO

from _shaded_thriftpy._compat import PY3
if PY3:
    import http.client as http_client
    import http.server as http_server
    import urllib
else:
    import httplib as http_client
    import BaseHTTPServer as http_server
    import urllib2 as urllib
    import urlparse
    urllib.parse = urlparse
    urllib.parse.quote = urllib.quote


from _shaded_thriftpy.thrift import TProcessor, TClient
from _shaded_thriftpy.server import TServer
from _shaded_thriftpy.transport import TTransportBase, TMemoryBuffer

from _shaded_thriftpy.protocol import TBinaryProtocolFactory
from _shaded_thriftpy.transport import TBufferedTransportFactory


HTTP_URI = '{scheme}://{host}:{port}/{path}'
DEFAULT_HTTP_CLIENT_TIMEOUT_MS = 30000  # 30 seconds


class TFileObjectTransport(TTransportBase):
    """Wraps a file-like object to make it work as a Thrift transport."""

    def __init__(self, fileobj):
        self.fileobj = fileobj

    def isOpen(self):
        return True

    def close(self):
        self.fileobj.close()

    def read(self, sz):
        return self.fileobj.read(sz)

    def write(self, buf):
        self.fileobj.write(buf)

    def flush(self):
        self.fileobj.flush()


class ResponseException(Exception):
    """Allows handlers to override the HTTP response

    Normally, THttpServer always sends a 200 response.  If a handler wants
    to override this behavior (e.g., to simulate a misconfigured or
    overloaded web server during testing), it can raise a ResponseException.
    The function passed to the constructor will be called with the
    RequestHandler as its only argument.
    """
    def __init__(self, handler):
        self.handler = handler


class THttpHeaderFactory(object):
    """Default header factory return no custom headers
    """
    def __init__(self, headers=None):
        """Initialize a header factory
        @param headers(dict) a dictionary of static headers the factory generates
        """
        if headers:
            self.__headers = headers
        else:
            self.__headers = dict()

    def get_headers(self):
        return self.__headers


class THttpServer(TServer):
    """A simple HTTP-based Thrift server
    This class is not very performant, but it is useful (for example) for
    acting as a mock version of an Apache-based PHP Thrift endpoint.
    """
    def __init__(self,
                 processor,
                 server_address,
                 itrans_factory,
                 iprot_factory,
                 server_class=http_server.HTTPServer):
        """Set up protocol factories and HTTP server.
        See http.server for server_address.
        See TServer for protocol factories.
        """
        TServer.__init__(self, processor, trans=None,
                         itrans_factory=itrans_factory,
                         iprot_factory=iprot_factory,
                         otrans_factory=None, oprot_factory=None)

        thttpserver = self

        class RequestHander(http_server.BaseHTTPRequestHandler):
            # Don't care about the request path.

            def do_POST(self):
                # Don't care about the request path.
                # Pre-read all of the data into a BytesIO. Buffered transport
                # was previously configured to read everything on the first
                # consumption, but that was a hack relying on the internal
                # mechanism and prevents other transports from working, so
                # replicate that properly to prevent timeout issues
                content_len = int(self.headers['Content-Length'])
                buf = BytesIO(self.rfile.read(content_len))
                itrans = TFileObjectTransport(buf)
                itrans = thttpserver.itrans_factory.get_transport(itrans)
                iprot = thttpserver.iprot_factory.get_protocol(itrans)

                otrans = TMemoryBuffer()
                oprot = thttpserver.oprot_factory.get_protocol(otrans)
                try:
                    thttpserver.processor.process(iprot, oprot)
                except ResponseException as exn:
                    exn.handler(self)
                else:
                    self.send_response(200)
                    self.send_header("content-type", "application/x-thrift")
                    self.end_headers()
                    self.wfile.write(otrans.getvalue())

        self.httpd = server_class(server_address, RequestHander)

    def serve(self):
        self.httpd.serve_forever()


class THttpClient(object):
    """Http implementation of TTransport base.
    """

    def __init__(self, uri, timeout=None, ssl_context_factory=None, http_header_factory=None):
        """Initialize a HTTP Socket.

        @param uri(str)    The http_scheme:://host:port/path to connect to.
        @param timeout   timeout in ms
        """
        parsed = urllib.parse.urlparse(uri)
        self.scheme = parsed.scheme
        assert self.scheme in ('http', 'https')
        if self.scheme == 'http':
            self.port = parsed.port or http_client.HTTP_PORT
        elif self.scheme == 'https':
            self.port = parsed.port or http_client.HTTPS_PORT
        self.host = parsed.hostname
        self.path = parsed.path
        if parsed.query:
            self.path += '?%s' % parsed.query
        self.__wbuf = BytesIO()
        self.__http = None
        self._http_header_factory = http_header_factory or THttpHeaderFactory()
        self.__timeout = None
        if timeout:
            self.setTimeout(timeout)
        self._ssl_context_factory = ssl_context_factory

    def open(self):
        if self.scheme == "https":
            ssl_context = self._ssl_context_factory() \
                if self._ssl_context_factory else None
            self.__http = http_client.HTTPSConnection(self.host, self.port,
                                                      context=ssl_context)
        else:
            self.__http = http_client.HTTPConnection(self.host, self.port)

    def close(self):
        self.__http.close()
        self.__http = None

    def isOpen(self):
        return self.__http is not None

    def setTimeout(self, ms):
        if not hasattr(socket, 'getdefaulttimeout'):
            raise NotImplementedError

        self.__timeout = ms / 1000.0 if (ms and ms > 0) else None

    def setCustomHeaders(self, headers):
        self._http_header_factory = THttpHeaderFactory(headers)

    def read(self, sz):
        content = self.response.read(sz)
        return content

    def write(self, buf):
        self.__wbuf.write(buf)

    def flush(self):
        # Pull data out of buffer
        # Do this before opening a new connection in case there isn't data
        data = self.__wbuf.getvalue()
        self.__wbuf = BytesIO()
        if not data:  # No data to flush, ignore
            return

        if self.isOpen():
            self.close()
        self.open()

        # HTTP request
        self.__http.putrequest('POST', self.path, skip_host=True)

        # Write headers
        self.__http.putheader('Host', self.host)
        self.__http.putheader('Content-Type', 'application/x-thrift')
        self.__http.putheader('Content-Length', str(len(data)))
        custom_headers = self._http_header_factory.get_headers()
        if (not custom_headers or
                'User-Agent' not in custom_headers):
            user_agent = 'Python/THttpClient'
            script = os.path.basename(sys.argv[0])
            if script:
                user_agent = '%s (%s)' % (
                    user_agent, urllib.parse.quote(script))
                self.__http.putheader('User-Agent', user_agent)

        if custom_headers:
            for key, val in self._http_header_factory.get_headers().items():
                self.__http.putheader(key, val)

        self.__http.endheaders()

        # Write payload
        self.__http.send(data)

        # Get reply to flush the request
        response = self.__http.getresponse()
        self.code, self.message, self.headers = (
            response.status, response.msg, response.getheaders())
        self.response = response

    def __with_timeout(f):

        def _f(*args, **kwargs):
            orig_timeout = socket.getdefaulttimeout()
            socket.setdefaulttimeout(args[0].__timeout)
            result = None
            try:
                result = f(*args, **kwargs)
            finally:
                socket.setdefaulttimeout(orig_timeout)
            return result
        return _f

    # Decorate if we know how to timeout
    if hasattr(socket, 'getdefaulttimeout'):
        flush = __with_timeout(flush)


def make_client(service, host='localhost', port=9090, path='', scheme='http',
                proto_factory=TBinaryProtocolFactory(),
                trans_factory=TBufferedTransportFactory(),
                ssl_context_factory=None,
                http_header_factory=None,
                timeout=DEFAULT_HTTP_CLIENT_TIMEOUT_MS, url=''):
    if url:
        parsed_url = urllib.parse.urlparse(url)
        host = parsed_url.hostname or host
        port = parsed_url.port or port
        scheme = parsed_url.scheme or scheme
        path = parsed_url.path or path
    uri = HTTP_URI.format(scheme=scheme, host=host, port=port, path=path)
    http_socket = THttpClient(uri, timeout, ssl_context_factory, http_header_factory)
    transport = trans_factory.get_transport(http_socket)
    iprot = proto_factory.get_protocol(transport)
    transport.open()
    return TClient(service, iprot)


@contextmanager
def client_context(service, host='localhost', port=9090, path='', scheme='http',
                   proto_factory=TBinaryProtocolFactory(),
                   trans_factory=TBufferedTransportFactory(),
                   ssl_context_factory=None,
                   http_header_factory=None,
                   timeout=DEFAULT_HTTP_CLIENT_TIMEOUT_MS, url=''):
    if url:
        parsed_url = urllib.parse.urlparse(url)
        host = parsed_url.hostname or host
        port = parsed_url.port or port
        scheme = parsed_url.scheme or scheme
        path = parsed_url.path or path
    uri = HTTP_URI.format(scheme=scheme, host=host, port=port, path=path)
    http_socket = THttpClient(uri, timeout, ssl_context_factory, http_header_factory)
    transport = trans_factory.get_transport(http_socket)
    try:
        iprot = proto_factory.get_protocol(transport)
        transport.open()
        yield TClient(service, iprot)
    finally:
        transport.close()


def make_server(service, handler, host, port,
                proto_factory=TBinaryProtocolFactory(),
                trans_factory=TBufferedTransportFactory()):
    processor = TProcessor(service, handler)
    server = THttpServer(processor, (host, port),
                         itrans_factory=trans_factory,
                         iprot_factory=proto_factory)
    return server
