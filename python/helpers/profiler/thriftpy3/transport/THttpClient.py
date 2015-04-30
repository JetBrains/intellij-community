#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements. See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership. The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License. You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the
# specific language governing permissions and limitations
# under the License.
#

import httplib
import os
import socket
import sys
import urllib
import urlparse
import warnings

from cStringIO import StringIO

from TTransport import *


class THttpClient(TTransportBase):
  """Http implementation of TTransport base."""

  def __init__(self, uri_or_host, port=None, path=None):
    """THttpClient supports two different types constructor parameters.

    THttpClient(host, port, path) - deprecated
    THttpClient(uri)

    Only the second supports https.
    """
    if port is not None:
      warnings.warn(
        "Please use the THttpClient('http://host:port/path') syntax",
        DeprecationWarning,
        stacklevel=2)
      self.host = uri_or_host
      self.port = port
      assert path
      self.path = path
      self.scheme = 'http'
    else:
      parsed = urlparse.urlparse(uri_or_host)
      self.scheme = parsed.scheme
      assert self.scheme in ('http', 'https')
      if self.scheme == 'http':
        self.port = parsed.port or httplib.HTTP_PORT
      elif self.scheme == 'https':
        self.port = parsed.port or httplib.HTTPS_PORT
      self.host = parsed.hostname
      self.path = parsed.path
      if parsed.query:
        self.path += '?%s' % parsed.query
    self.__wbuf = StringIO()
    self.__http = None
    self.__timeout = None
    self.__custom_headers = None

  def open(self):
    if self.scheme == 'http':
      self.__http = httplib.HTTP(self.host, self.port)
    else:
      self.__http = httplib.HTTPS(self.host, self.port)

  def close(self):
    self.__http.close()
    self.__http = None

  def isOpen(self):
    return self.__http is not None

  def setTimeout(self, ms):
    if not hasattr(socket, 'getdefaulttimeout'):
      raise NotImplementedError

    if ms is None:
      self.__timeout = None
    else:
      self.__timeout = ms / 1000.0

  def setCustomHeaders(self, headers):
    self.__custom_headers = headers

  def read(self, sz):
    return self.__http.file.read(sz)

  def write(self, buf):
    self.__wbuf.write(buf)

  def __withTimeout(f):
    def _f(*args, **kwargs):
      orig_timeout = socket.getdefaulttimeout()
      socket.setdefaulttimeout(args[0].__timeout)
      try:
        result = f(*args, **kwargs)
      finally:
        socket.setdefaulttimeout(orig_timeout)
      return result
    return _f

  def flush(self):
    if self.isOpen():
      self.close()
    self.open()

    # Pull data out of buffer
    data = self.__wbuf.getvalue()
    self.__wbuf = StringIO()

    # HTTP request
    self.__http.putrequest('POST', self.path)

    # Write headers
    self.__http.putheader('Host', self.host)
    self.__http.putheader('Content-Type', 'application/x-thrift')
    self.__http.putheader('Content-Length', str(len(data)))

    if not self.__custom_headers or 'User-Agent' not in self.__custom_headers:
      user_agent = 'Python/THttpClient'
      script = os.path.basename(sys.argv[0])
      if script:
        user_agent = '%s (%s)' % (user_agent, urllib.quote(script))
      self.__http.putheader('User-Agent', user_agent)

    if self.__custom_headers:
        for key, val in self.__custom_headers.iteritems():
            self.__http.putheader(key, val)

    self.__http.endheaders()

    # Write payload
    self.__http.send(data)

    # Get reply to flush the request
    self.code, self.message, self.headers = self.__http.getreply()

  # Decorate if we know how to timeout
  if hasattr(socket, 'getdefaulttimeout'):
    flush = __withTimeout(flush)
