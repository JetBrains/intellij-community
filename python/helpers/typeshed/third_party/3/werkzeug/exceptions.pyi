from typing import Any

class HTTPException(Exception):
    code = ...  # type: Any
    description = ...  # type: Any
    response = ...  # type: Any
    def __init__(self, description=None, response=None): ...
    @classmethod
    def wrap(cls, exception, name=None): ...
    @property
    def name(self): ...
    def get_description(self, environ=None): ...
    def get_body(self, environ=None): ...
    def get_headers(self, environ=None): ...
    def get_response(self, environ=None): ...
    def __call__(self, environ, start_response): ...

class BadRequest(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class ClientDisconnected(BadRequest): ...
class SecurityError(BadRequest): ...
class BadHost(BadRequest): ...

class Unauthorized(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class Forbidden(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class NotFound(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class MethodNotAllowed(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any
    valid_methods = ...  # type: Any
    def __init__(self, valid_methods=None, description=None): ...
    def get_headers(self, environ): ...

class NotAcceptable(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class RequestTimeout(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class Conflict(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class Gone(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class LengthRequired(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class PreconditionFailed(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class RequestEntityTooLarge(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class RequestURITooLarge(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class UnsupportedMediaType(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class RequestedRangeNotSatisfiable(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any
    length = ...  # type: Any
    units = ...  # type: Any
    def __init__(self, length=None, units='', description=None): ...
    def get_headers(self, environ): ...

class ExpectationFailed(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class ImATeapot(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class UnprocessableEntity(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class Locked(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class PreconditionRequired(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class TooManyRequests(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class RequestHeaderFieldsTooLarge(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class UnavailableForLegalReasons(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class InternalServerError(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class NotImplemented(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class BadGateway(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class ServiceUnavailable(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class GatewayTimeout(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class HTTPVersionNotSupported(HTTPException):
    code = ...  # type: Any
    description = ...  # type: Any

class Aborter:
    mapping = ...  # type: Any
    def __init__(self, mapping=None, extra=None): ...
    def __call__(self, code, *args, **kwargs): ...
