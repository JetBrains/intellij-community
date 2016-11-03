import sys

from enum import IntEnum

if sys.version_info >= (3, 5):
    class HTTPStatus(IntEnum):

        def __init__(self, *a) -> None:
            self.phrase = ...  # type: str
            self.description = ...  # type: str

        CONTINUE = ...  # type: object
        SWITCHING_PROTOCOLS = ...  # type: object
        PROCESSING = ...  # type: object
        OK = ...  # type: object
        CREATED = ...  # type: object
        ACCEPTED = ...  # type: object
        NON_AUTHORITATIVE_INFORMATION = ...  # type: object
        NO_CONTENT = ...  # type: object
        RESET_CONTENT = ...  # type: object
        PARTIAL_CONTENT = ...  # type: object
        MULTI_STATUS = ...  # type: object
        ALREADY_REPORTED = ...  # type: object
        IM_USED = ...  # type: object
        MULTIPLE_CHOICES = ...  # type: object
        MOVED_PERMANENTLY = ...  # type: object
        FOUND = ...  # type: object
        SEE_OTHER = ...  # type: object
        NOT_MODIFIED = ...  # type: object
        USE_PROXY = ...  # type: object
        TEMPORARY_REDIRECT = ...  # type: object
        PERMANENT_REDIRECT = ...  # type: object
        BAD_REQUEST = ...  # type: object
        UNAUTHORIZED = ...  # type: object
        PAYMENT_REQUIRED = ...  # type: object
        FORBIDDEN = ...  # type: object
        NOT_FOUND = ...  # type: object
        METHOD_NOT_ALLOWED = ...  # type: object
        NOT_ACCEPTABLE = ...  # type: object
        PROXY_AUTHENTICATION_REQUIRED = ...  # type: object
        REQUEST_TIMEOUT = ...  # type: object
        CONFLICT = ...  # type: object
        GONE = ...  # type: object
        LENGTH_REQUIRED = ...  # type: object
        PRECONDITION_FAILED = ...  # type: object
        REQUEST_ENTITY_TOO_LARGE = ...  # type: object
        REQUEST_URI_TOO_LONG = ...  # type: object
        UNSUPPORTED_MEDIA_TYPE = ...  # type: object
        REQUESTED_RANGE_NOT_SATISFIABLE = ...  # type: object
        EXPECTATION_FAILED = ...  # type: object
        UNPROCESSABLE_ENTITY = ...  # type: object
        LOCKED = ...  # type: object
        FAILED_DEPENDENCY = ...  # type: object
        UPGRADE_REQUIRED = ...  # type: object
        PRECONDITION_REQUIRED = ...  # type: object
        TOO_MANY_REQUESTS = ...  # type: object
        REQUEST_HEADER_FIELDS_TOO_LARGE = ...  # type: object
        INTERNAL_SERVER_ERROR = ...  # type: object
        NOT_IMPLEMENTED = ...  # type: object
        BAD_GATEWAY = ...  # type: object
        SERVICE_UNAVAILABLE = ...  # type: object
        GATEWAY_TIMEOUT = ...  # type: object
        HTTP_VERSION_NOT_SUPPORTED = ...  # type: object
        VARIANT_ALSO_NEGOTIATES = ...  # type: object
        INSUFFICIENT_STORAGE = ...  # type: object
        LOOP_DETECTED = ...  # type: object
        NOT_EXTENDED = ...  # type: object
        NETWORK_AUTHENTICATION_REQUIRED = ...  # type: object
