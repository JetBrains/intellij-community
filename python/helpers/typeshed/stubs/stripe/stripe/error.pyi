from typing import Any

class StripeError(Exception):
    http_body: Any
    http_status: Any
    json_body: Any
    headers: Any
    code: Any
    request_id: Any
    error: Any
    def __init__(
        self,
        message: Any | None = ...,
        http_body: Any | None = ...,
        http_status: Any | None = ...,
        json_body: Any | None = ...,
        headers: Any | None = ...,
        code: Any | None = ...,
    ) -> None: ...
    @property
    def user_message(self): ...
    def construct_error_object(self): ...

class APIError(StripeError): ...

class APIConnectionError(StripeError):
    should_retry: Any
    def __init__(
        self,
        message,
        http_body: Any | None = ...,
        http_status: Any | None = ...,
        json_body: Any | None = ...,
        headers: Any | None = ...,
        code: Any | None = ...,
        should_retry: bool = ...,
    ) -> None: ...

class StripeErrorWithParamCode(StripeError): ...

class CardError(StripeErrorWithParamCode):
    param: Any
    def __init__(
        self,
        message,
        param,
        code,
        http_body: Any | None = ...,
        http_status: Any | None = ...,
        json_body: Any | None = ...,
        headers: Any | None = ...,
    ) -> None: ...

class IdempotencyError(StripeError): ...

class InvalidRequestError(StripeErrorWithParamCode):
    param: Any
    def __init__(
        self,
        message,
        param,
        code: Any | None = ...,
        http_body: Any | None = ...,
        http_status: Any | None = ...,
        json_body: Any | None = ...,
        headers: Any | None = ...,
    ) -> None: ...

class AuthenticationError(StripeError): ...
class PermissionError(StripeError): ...
class RateLimitError(StripeError): ...

class SignatureVerificationError(StripeError):
    sig_header: Any
    def __init__(self, message, sig_header, http_body: Any | None = ...) -> None: ...
