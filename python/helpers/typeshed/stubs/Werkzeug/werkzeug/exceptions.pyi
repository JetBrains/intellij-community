import datetime
from _typeshed.wsgi import StartResponse, WSGIEnvironment
from typing import Any, Iterable, NoReturn, Protocol, Text, Tuple, Type

from werkzeug.wrappers import Response

class _EnvironContainer(Protocol):
    @property
    def environ(self) -> WSGIEnvironment: ...

class HTTPException(Exception):
    code: int | None
    description: Text | None
    response: Response | None
    def __init__(self, description: Text | None = ..., response: Response | None = ...) -> None: ...
    @classmethod
    def wrap(cls, exception: Type[Exception], name: str | None = ...) -> Any: ...
    @property
    def name(self) -> str: ...
    def get_description(self, environ: WSGIEnvironment | None = ...) -> Text: ...
    def get_body(self, environ: WSGIEnvironment | None = ...) -> Text: ...
    def get_headers(self, environ: WSGIEnvironment | None = ...) -> list[Tuple[str, str]]: ...
    def get_response(self, environ: WSGIEnvironment | _EnvironContainer | None = ...) -> Response: ...
    def __call__(self, environ: WSGIEnvironment, start_response: StartResponse) -> Iterable[bytes]: ...

default_exceptions: dict[int, Type[HTTPException]]

class BadRequest(HTTPException):
    code: int
    description: Text

class ClientDisconnected(BadRequest): ...
class SecurityError(BadRequest): ...
class BadHost(BadRequest): ...

class Unauthorized(HTTPException):
    code: int
    description: Text
    www_authenticate: Iterable[object] | None
    def __init__(
        self,
        description: Text | None = ...,
        response: Response | None = ...,
        www_authenticate: None | Tuple[object, ...] | list[object] | object = ...,
    ) -> None: ...

class Forbidden(HTTPException):
    code: int
    description: Text

class NotFound(HTTPException):
    code: int
    description: Text

class MethodNotAllowed(HTTPException):
    code: int
    description: Text
    valid_methods: Any
    def __init__(self, valid_methods: Any | None = ..., description: Any | None = ...): ...

class NotAcceptable(HTTPException):
    code: int
    description: Text

class RequestTimeout(HTTPException):
    code: int
    description: Text

class Conflict(HTTPException):
    code: int
    description: Text

class Gone(HTTPException):
    code: int
    description: Text

class LengthRequired(HTTPException):
    code: int
    description: Text

class PreconditionFailed(HTTPException):
    code: int
    description: Text

class RequestEntityTooLarge(HTTPException):
    code: int
    description: Text

class RequestURITooLarge(HTTPException):
    code: int
    description: Text

class UnsupportedMediaType(HTTPException):
    code: int
    description: Text

class RequestedRangeNotSatisfiable(HTTPException):
    code: int
    description: Text
    length: Any
    units: str
    def __init__(self, length: Any | None = ..., units: str = ..., description: Any | None = ...): ...

class ExpectationFailed(HTTPException):
    code: int
    description: Text

class ImATeapot(HTTPException):
    code: int
    description: Text

class UnprocessableEntity(HTTPException):
    code: int
    description: Text

class Locked(HTTPException):
    code: int
    description: Text

class FailedDependency(HTTPException):
    code: int
    description: Text

class PreconditionRequired(HTTPException):
    code: int
    description: Text

class _RetryAfter(HTTPException):
    retry_after: None | int | datetime.datetime
    def __init__(
        self, description: Text | None = ..., response: Response | None = ..., retry_after: None | int | datetime.datetime = ...
    ) -> None: ...

class TooManyRequests(_RetryAfter):
    code: int
    description: Text

class RequestHeaderFieldsTooLarge(HTTPException):
    code: int
    description: Text

class UnavailableForLegalReasons(HTTPException):
    code: int
    description: Text

class InternalServerError(HTTPException):
    def __init__(
        self, description: Text | None = ..., response: Response | None = ..., original_exception: Exception | None = ...
    ) -> None: ...
    code: int
    description: Text

class NotImplemented(HTTPException):
    code: int
    description: Text

class BadGateway(HTTPException):
    code: int
    description: Text

class ServiceUnavailable(_RetryAfter):
    code: int
    description: Text

class GatewayTimeout(HTTPException):
    code: int
    description: Text

class HTTPVersionNotSupported(HTTPException):
    code: int
    description: Text

class Aborter:
    mapping: Any
    def __init__(self, mapping: Any | None = ..., extra: Any | None = ...) -> None: ...
    def __call__(self, code: int | Response, *args: Any, **kwargs: Any) -> NoReturn: ...

def abort(status: int | Response, *args: Any, **kwargs: Any) -> NoReturn: ...

class BadRequestKeyError(BadRequest, KeyError): ...
