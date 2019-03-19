# -*- coding: utf-8 -*-

"""
The codes in this ssl compat lib were inspired by urllib3.utils.ssl_ module.
"""

import ssl
import warnings

from .._compat import MODERN_SSL

try:
    from ssl import (
        OP_NO_SSLv2, OP_NO_SSLv3, OP_NO_COMPRESSION,
        OP_CIPHER_SERVER_PREFERENCE, OP_SINGLE_DH_USE, OP_SINGLE_ECDH_USE
    )
except ImportError:
    OP_NO_SSLv2 = 0x1000000
    OP_NO_SSLv3 = 0x2000000
    OP_NO_COMPRESSION = 0x20000
    OP_CIPHER_SERVER_PREFERENCE = 0x400000
    OP_SINGLE_DH_USE = 0x100000
    OP_SINGLE_ECDH_USE = 0x80000


# Disable weak or insecure ciphers by default
# (OpenSSL's default setting is 'DEFAULT:!aNULL:!eNULL')
# Enable a better set of ciphers by default
# This list has been explicitly chosen to:
#   * Prefer cipher suites that offer perfect forward secrecy (DHE/ECDHE)
#   * Prefer ECDHE over DHE for better performance
#   * Prefer any AES-GCM over any AES-CBC for better performance and security
#   * Then Use HIGH cipher suites as a fallback
#   * Then Use 3DES as fallback which is secure but slow
#   * Disable NULL authentication, NULL encryption, and MD5 MACs for security
#     reasons
DEFAULT_CIPHERS = (
    'ECDH+AESGCM:DH+AESGCM:ECDH+AES256:DH+AES256:ECDH+AES128:DH+AES:ECDH+HIGH:'
    'DH+HIGH:ECDH+3DES:DH+3DES:RSA+AESGCM:RSA+AES:RSA+HIGH:RSA+3DES:!aNULL:'
    '!eNULL:!MD5'
)

# Restricted and more secure ciphers for the server side
# This list has been explicitly chosen to:
#   * Prefer cipher suites that offer perfect forward secrecy (DHE/ECDHE)
#   * Prefer ECDHE over DHE for better performance
#   * Prefer any AES-GCM over any AES-CBC for better performance and security
#   * Then Use HIGH cipher suites as a fallback
#   * Then Use 3DES as fallback which is secure but slow
#   * Disable NULL authentication, NULL encryption, MD5 MACs, DSS, and RC4 for
#     security reasons
RESTRICTED_SERVER_CIPHERS = (
    'ECDH+AESGCM:DH+AESGCM:ECDH+AES256:DH+AES256:ECDH+AES128:DH+AES:ECDH+HIGH:'
    'DH+HIGH:ECDH+3DES:DH+3DES:RSA+AESGCM:RSA+AES:RSA+HIGH:RSA+3DES:!aNULL:'
    '!eNULL:!MD5:!DSS:!RC4'
)


class InsecurePlatformWarning(Warning):
    """Warned when certain SSL configuration is not available on a platform.
    """
    pass


try:
    from ssl import SSLContext
except ImportError:
    import sys

    class SSLContext(object):

        def __init__(self, protocol_version):
            self.protocol = protocol_version
            # Use default values from a real SSLContext
            self.check_hostname = False
            self.verify_mode = ssl.CERT_NONE
            self.ca_certs = None
            self.options = 0
            self.certfile = None
            self.keyfile = None
            self.ciphers = None

        def load_cert_chain(self, certfile=None, keyfile=None):
            self.certfile = certfile
            self.keyfile = keyfile

        def load_verify_locations(self, cafile=None, capath=None):
            if capath is not None:
                raise OSError("CA directories not supported in older Pythons")
            self.ca_certs = cafile

        def set_ciphers(self, cipher_suite):
            self.ciphers = cipher_suite

        def wrap_socket(self, socket, server_hostname=None, server_side=False):
            warnings.warn(
                "A true SSLContext object is not available. This prevents "
                "urllib3 from configuring SSL appropriately and may cause "
                "certain SSL connections to fail.",
                InsecurePlatformWarning
            )
            kwargs = {
                "keyfile": self.keyfile,
                "certfile": self.certfile,
                "ca_certs": self.ca_certs,
                "cert_reqs": self.verify_mode,
                "ssl_version": self.protocol,
                "server_side": server_side,
            }

            return ssl.wrap_socket(socket, ciphers=self.ciphers, **kwargs)


def create_thriftpy_context(server_side=False, ciphers=None):
    """Backport create_default_context for older python versions.

    The SSLContext has some default security options, you can disable them
    manually, for example::

        from thriftpy2.transport import _ssl
        context = _ssl.create_thriftpy_context()
        context.options &= ~_ssl.OP_NO_SSLv3

    You can do the same to enable compression.
    """
    if MODERN_SSL:
        if server_side:
            context = ssl.create_default_context(ssl.Purpose.CLIENT_AUTH)
        else:
            context = ssl.create_default_context(ssl.Purpose.SERVER_AUTH)

        if ciphers:
            context.set_ciphers(ciphers)

    else:
        context = SSLContext(ssl.PROTOCOL_SSLv23)
        context.options |= OP_NO_SSLv2
        context.options |= OP_NO_SSLv3
        context.options |= OP_NO_COMPRESSION

        # server/client default options
        if server_side:
            context.options |= OP_CIPHER_SERVER_PREFERENCE
            context.options |= OP_SINGLE_DH_USE
            context.options |= OP_SINGLE_ECDH_USE
        else:
            context.verify_mode = ssl.CERT_REQUIRED
            # context.check_hostname = True
            warnings.warn(
                "ssl check hostname support disabled, upgrade your python",
                InsecurePlatformWarning)

        if ciphers:
            context.set_ciphers(ciphers)

    return context
