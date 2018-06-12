import sys

from typing import Any
from base64 import encodestring as encodebytes

from six.moves import http_client

expanduser = ...  # type: Any

if sys.version_info >= (3, 0):
    StandardError = Exception
else:
    StandardError = __builtins__.StandardError

long_type = ...  # type: Any
unquote_str = ...  # type: Any
parse_qs_safe = ...  # type: Any
