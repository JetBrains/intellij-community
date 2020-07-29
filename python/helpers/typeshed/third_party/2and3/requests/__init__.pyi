# Stubs for requests (based on version 2.6.0, Python 3)

from typing import Any
from . import models
from . import api
from . import sessions
from . import status_codes
from . import exceptions
from . import packages
import logging

from .models import Request as Request
from .models import Response as Response
from .models import PreparedRequest as PreparedRequest

from .api import request as request
from .api import get as get
from .api import head as head
from .api import post as post
from .api import patch as patch
from .api import put as put
from .api import delete as delete
from .api import options as options

from .sessions import session as session
from .sessions import Session as Session

from .status_codes import codes as codes

from .exceptions import RequestException as RequestException
from .exceptions import Timeout as Timeout
from .exceptions import URLRequired as URLRequired
from .exceptions import TooManyRedirects as TooManyRedirects
from .exceptions import HTTPError as HTTPError
from .exceptions import ConnectionError as ConnectionError

__title__: Any
__build__: Any
__license__: Any
__copyright__: Any
__version__: Any

class NullHandler(logging.Handler):
    def emit(self, record): ...
