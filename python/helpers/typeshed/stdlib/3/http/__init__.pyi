import sys

from enum import IntEnum

if sys.version_info >= (3, 5):
    class HTTPStatus(IntEnum):

        def __init__(self, *a) -> None: ...

        phrase = ...  # type: str
        description = ...  # type: str

        CONTINUE = ...  # type: HTTPStatus
        SWITCHING_PROTOCOLS = ...  # type: HTTPStatus
        PROCESSING = ...  # type: HTTPStatus
        OK = ...  # type: HTTPStatus
        CREATED = ...  # type: HTTPStatus
        ACCEPTED = ...  # type: HTTPStatus
        NON_AUTHORITATIVE_INFORMATION = ...  # type: HTTPStatus
        NO_CONTENT = ...  # type: HTTPStatus
        RESET_CONTENT = ...  # type: HTTPStatus
        PARTIAL_CONTENT = ...  # type: HTTPStatus
        MULTI_STATUS = ...  # type: HTTPStatus
        ALREADY_REPORTED = ...  # type: HTTPStatus
        IM_USED = ...  # type: HTTPStatus
        MULTIPLE_CHOICES = ...  # type: HTTPStatus
        MOVED_PERMANENTLY = ...  # type: HTTPStatus
        FOUND = ...  # type: HTTPStatus
        SEE_OTHER = ...  # type: HTTPStatus
        NOT_MODIFIED = ...  # type: HTTPStatus
        USE_PROXY = ...  # type: HTTPStatus
        TEMPORARY_REDIRECT = ...  # type: HTTPStatus
        PERMANENT_REDIRECT = ...  # type: HTTPStatus
        BAD_REQUEST = ...  # type: HTTPStatus
        UNAUTHORIZED = ...  # type: HTTPStatus
        PAYMENT_REQUIRED = ...  # type: HTTPStatus
        FORBIDDEN = ...  # type: HTTPStatus
        NOT_FOUND = ...  # type: HTTPStatus
        METHOD_NOT_ALLOWED = ...  # type: HTTPStatus
        NOT_ACCEPTABLE = ...  # type: HTTPStatus
        PROXY_AUTHENTICATION_REQUIRED = ...  # type: HTTPStatus
        REQUEST_TIMEOUT = ...  # type: HTTPStatus
        CONFLICT = ...  # type: HTTPStatus
        GONE = ...  # type: HTTPStatus
        LENGTH_REQUIRED = ...  # type: HTTPStatus
        PRECONDITION_FAILED = ...  # type: HTTPStatus
        REQUEST_ENTITY_TOO_LARGE = ...  # type: HTTPStatus
        REQUEST_URI_TOO_LONG = ...  # type: HTTPStatus
        UNSUPPORTED_MEDIA_TYPE = ...  # type: HTTPStatus
        REQUESTED_RANGE_NOT_SATISFIABLE = ...  # type: HTTPStatus
        EXPECTATION_FAILED = ...  # type: HTTPStatus
        UNPROCESSABLE_ENTITY = ...  # type: HTTPStatus
        LOCKED = ...  # type: HTTPStatus
        FAILED_DEPENDENCY = ...  # type: HTTPStatus
        UPGRADE_REQUIRED = ...  # type: HTTPStatus
        PRECONDITION_REQUIRED = ...  # type: HTTPStatus
        TOO_MANY_REQUESTS = ...  # type: HTTPStatus
        REQUEST_HEADER_FIELDS_TOO_LARGE = ...  # type: HTTPStatus
        INTERNAL_SERVER_ERROR = ...  # type: HTTPStatus
        NOT_IMPLEMENTED = ...  # type: HTTPStatus
        BAD_GATEWAY = ...  # type: HTTPStatus
        SERVICE_UNAVAILABLE = ...  # type: HTTPStatus
        GATEWAY_TIMEOUT = ...  # type: HTTPStatus
        HTTP_VERSION_NOT_SUPPORTED = ...  # type: HTTPStatus
        VARIANT_ALSO_NEGOTIATES = ...  # type: HTTPStatus
        INSUFFICIENT_STORAGE = ...  # type: HTTPStatus
        LOOP_DETECTED = ...  # type: HTTPStatus
        NOT_EXTENDED = ...  # type: HTTPStatus
        NETWORK_AUTHENTICATION_REQUIRED = ...  # type: HTTPStatus
