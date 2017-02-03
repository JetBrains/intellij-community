# Stubs for requests (based on version 2.6.0, Python 3)

from typing import Any
from . import models
from . import api
from . import sessions
from . import status_codes
from . import exceptions
import logging

__title__ = ...  # type: Any
__build__ = ...  # type: Any
__license__ = ...  # type: Any
__copyright__ = ...  # type: Any
__version__ = ...  # type: Any

Request = models.Request
Response = models.Response
PreparedRequest = models.PreparedRequest
request = api.request
get = api.get
head = api.head
post = api.post
patch = api.patch
put = api.put
delete = api.delete
options = api.options
session = sessions.session
Session = sessions.Session
codes = status_codes.codes
RequestException = exceptions.RequestException
Timeout = exceptions.Timeout
URLRequired = exceptions.URLRequired
TooManyRedirects = exceptions.TooManyRedirects
HTTPError = exceptions.HTTPError
ConnectionError = exceptions.ConnectionError

class NullHandler(logging.Handler):
    def emit(self, record): ...
