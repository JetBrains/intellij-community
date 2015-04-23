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

import os
import socket
import ssl

from thrift.transport import TSocket
from thrift.transport.TTransport import TTransportException


class TSSLSocket(TSocket.TSocket):
  """
  SSL implementation of client-side TSocket

  This class creates outbound sockets wrapped using the
  python standard ssl module for encrypted connections.

  The protocol used is set using the class variable
  SSL_VERSION, which must be one of ssl.PROTOCOL_* and
  defaults to  ssl.PROTOCOL_TLSv1 for greatest security.
  """
  SSL_VERSION = ssl.PROTOCOL_TLSv1

  def __init__(self,
               host='localhost',
               port=9090,
               validate=True,
               ca_certs=None,
               keyfile=None,
               certfile=None,
               unix_socket=None):
    """Create SSL TSocket

    @param validate: Set to False to disable SSL certificate validation
    @type validate: bool
    @param ca_certs: Filename to the Certificate Authority pem file, possibly a
    file downloaded from: http://curl.haxx.se/ca/cacert.pem  This is passed to
    the ssl_wrap function as the 'ca_certs' parameter.
    @type ca_certs: str
    @param keyfile: The private key
    @type keyfile: str
    @param certfile: The cert file
    @type certfile: str
    
    Raises an IOError exception if validate is True and the ca_certs file is
    None, not present or unreadable.
    """
    self.validate = validate
    self.is_valid = False
    self.peercert = None
    if not validate:
      self.cert_reqs = ssl.CERT_NONE
    else:
      self.cert_reqs = ssl.CERT_REQUIRED
    self.ca_certs = ca_certs
    self.keyfile = keyfile
    self.certfile = certfile
    if validate:
      if ca_certs is None or not os.access(ca_certs, os.R_OK):
        raise IOError('Certificate Authority ca_certs file "%s" '
                      'is not readable, cannot validate SSL '
                      'certificates.' % (ca_certs))
    TSocket.TSocket.__init__(self, host, port, unix_socket)

  def open(self):
    try:
      res0 = self._resolveAddr()
      for res in res0:
        sock_family, sock_type = res[0:2]
        ip_port = res[4]
        plain_sock = socket.socket(sock_family, sock_type)
        self.handle = ssl.wrap_socket(plain_sock,
                                      ssl_version=self.SSL_VERSION,
                                      do_handshake_on_connect=True,
                                      ca_certs=self.ca_certs,
                                      keyfile=self.keyfile,
                                      certfile=self.certfile,
                                      cert_reqs=self.cert_reqs)
        self.handle.settimeout(self._timeout)
        try:
          self.handle.connect(ip_port)
        except socket.error, e:
          if res is not res0[-1]:
            continue
          else:
            raise e
        break
    except socket.error, e:
      if self._unix_socket:
        message = 'Could not connect to secure socket %s: %s' \
                % (self._unix_socket, e)
      else:
        message = 'Could not connect to %s:%d: %s' % (self.host, self.port, e)
      raise TTransportException(type=TTransportException.NOT_OPEN,
                                message=message)
    if self.validate:
      self._validate_cert()

  def _validate_cert(self):
    """internal method to validate the peer's SSL certificate, and to check the
    commonName of the certificate to ensure it matches the hostname we
    used to make this connection.  Does not support subjectAltName records
    in certificates.

    raises TTransportException if the certificate fails validation.
    """
    cert = self.handle.getpeercert()
    self.peercert = cert
    if 'subject' not in cert:
      raise TTransportException(
        type=TTransportException.NOT_OPEN,
        message='No SSL certificate found from %s:%s' % (self.host, self.port))
    fields = cert['subject']
    for field in fields:
      # ensure structure we get back is what we expect
      if not isinstance(field, tuple):
        continue
      cert_pair = field[0]
      if len(cert_pair) < 2:
        continue
      cert_key, cert_value = cert_pair[0:2]
      if cert_key != 'commonName':
        continue
      certhost = cert_value
      # this check should be performed by some sort of Access Manager
      if certhost == self.host:
        # success, cert commonName matches desired hostname
        self.is_valid = True
        return
      else:
        raise TTransportException(
          type=TTransportException.UNKNOWN,
          message='Hostname we connected to "%s" doesn\'t match certificate '
                  'provided commonName "%s"' % (self.host, certhost))
    raise TTransportException(
      type=TTransportException.UNKNOWN,
      message='Could not validate SSL certificate from '
              'host "%s".  Cert=%s' % (self.host, cert))


class TSSLServerSocket(TSocket.TServerSocket):
  """SSL implementation of TServerSocket

  This uses the ssl module's wrap_socket() method to provide SSL
  negotiated encryption.
  """
  SSL_VERSION = ssl.PROTOCOL_TLSv1

  def __init__(self,
               host=None,
               port=9090,
               certfile='cert.pem',
               unix_socket=None):
    """Initialize a TSSLServerSocket

    @param certfile: filename of the server certificate, defaults to cert.pem
    @type certfile: str
    @param host: The hostname or IP to bind the listen socket to,
                 i.e. 'localhost' for only allowing local network connections.
                 Pass None to bind to all interfaces.
    @type host: str
    @param port: The port to listen on for inbound connections.
    @type port: int
    """
    self.setCertfile(certfile)
    TSocket.TServerSocket.__init__(self, host, port)

  def setCertfile(self, certfile):
    """Set or change the server certificate file used to wrap new connections.

    @param certfile: The filename of the server certificate,
                     i.e. '/etc/certs/server.pem'
    @type certfile: str

    Raises an IOError exception if the certfile is not present or unreadable.
    """
    if not os.access(certfile, os.R_OK):
      raise IOError('No such certfile found: %s' % (certfile))
    self.certfile = certfile

  def accept(self):
    plain_client, addr = self.handle.accept()
    try:
      client = ssl.wrap_socket(plain_client, certfile=self.certfile,
                      server_side=True, ssl_version=self.SSL_VERSION)
    except ssl.SSLError, ssl_exc:
      # failed handshake/ssl wrap, close socket to client
      plain_client.close()
      # raise ssl_exc
      # We can't raise the exception, because it kills most TServer derived
      # serve() methods.
      # Instead, return None, and let the TServer instance deal with it in
      # other exception handling.  (but TSimpleServer dies anyway)
      return None
    result = TSocket.TSocket()
    result.setHandle(client)
    return result
