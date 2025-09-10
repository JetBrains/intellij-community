import sys

from passlib.context import CryptContext

linux_context: CryptContext
linux2_context: CryptContext
freebsd_context: CryptContext
openbsd_context: CryptContext
netbsd_context: CryptContext
# Only exists if crypt is present
if sys.version_info < (3, 13) and sys.platform != "win32":
    host_context: CryptContext
    __all__ = ["linux_context", "linux2_context", "openbsd_context", "netbsd_context", "freebsd_context", "host_context"]
else:
    __all__ = ["linux_context", "linux2_context", "openbsd_context", "netbsd_context", "freebsd_context"]
