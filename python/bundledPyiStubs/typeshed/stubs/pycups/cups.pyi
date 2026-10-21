from _typeshed import Unused
from collections.abc import Callable, Sequence
from io import IOBase
from typing import Final, Literal, TypeAlias, TypedDict, TypeVar, final, overload, type_check_only
from typing_extensions import NotRequired

_T = TypeVar("_T")

_FileOrFd: TypeAlias = IOBase | int

_CupsDevice = TypedDict(
    "_CupsDevice",
    {"device-class": str, "device-info": str, "device-make-and-model": str, "device-id": str, "device-location": str},
)

_CupsDocument = TypedDict("_CupsDocument", {"file": str, "document-format": NotRequired[str], "document-name": NotRequired[str]})

_CupsPPD = TypedDict(
    "_CupsPPD",
    {
        "ppd-natural-language": str,
        "ppd-make": str,
        "ppd-make-and-model": str,
        "ppd-device-id": str,
        "ppd-product": str,
        "ppd-psversion": str,
        "ppd-type": str,
        "ppd-model-number": int,
    },
)

_CupsPPD2 = TypedDict(
    "_CupsPPD2",
    {
        "ppd-natural-language": list[str],
        "ppd-make": list[str],
        "ppd-make-and-model": list[str],
        "ppd-device-id": list[str],
        "ppd-product": list[str],
        "ppd-psversion": list[str],
        "ppd-type": list[str],
        "ppd-model-number": list[int],
    },
)

_CupsJob = TypedDict(
    "_CupsJob",
    {
        "number-of-documents": int,
        "job-media-progress": int,
        "job-more-info": str,
        "job-preserved": bool,
        "job-printer-up-time": int,
        "job-printer-uri": str,
        "job-uri": str,
        "printer-uri": str,
        "document-format-detected": str,
        "document-format": str,
        "job-priority": int,
        "job-uuid": str,
        "date-time-at-completed": str,
        "date-time-at-creation": str,
        "date-time-at-processing": str,
        "time-at-completed": int,
        "time-at-creation": int,
        "time-at-processing": int,
        "job-state": int,
        "job-state-reasons": str,
        "job-impressions-completed": int,
        "job-media-sheets-completed": int,
        "job-k-octets": int,
        "job-hold-until": str,
        "job-sheets": list[str],
        "job-printer-state-message": str,
        "job-printer-state-reasons": str,
        "job-name": str,
        "job-originating-user-name": str,
    },
)

_CupsAttributeInfo = TypedDict(
    "_CupsAttributeInfo", {"attributes-charset": str, "attributes-natural-language": str, "job-id": int}
)

@type_check_only
class _CupsOptionChoice(TypedDict):
    choice: str
    text: str
    marked: bool

@type_check_only
class _CupsJobWithAttributeInfo(_CupsJob, _CupsAttributeInfo): ...

_CupsEvent = TypedDict(  # noqa: Y049
    "_CupsEvent",
    {
        "notify-charset": str,
        "notify-natural-language": str,
        "notify-subscription-id": int,
        "notify-sequence-number": int,
        "notify-subscribed-event": str,
        "printer-up-time": int,
        "notify-text": str,
        "notify-printer-uri": str,
        "printer-name": str,
        "printer-state": int,
        "printer-state-reasons": list[str],
        "printer-is-accepting-jobs": bool,
        "notify-job-id": int,
        "job-state": int,
        "job-name": str,
        "job-state-reasons": str,
        "job-impressions-completed": int,
    },
)

_CupsNotifications = TypedDict(
    "_CupsNotifications", {"notify-get-interval": int, "printer-up-time": int, "events": list[_CupsEvent]}
)

_CupsPrinter = TypedDict(
    "_CupsPrinter",
    {
        "marker-change-time": int,
        "printer-config-change-date-time": str,
        "printer-config-change-time": int,
        "printer-current-time": str,
        "printer-dns-sd-name": str | None,
        "printer-error-policy": str,
        "printer-error-policy-supported": list[str],
        "printer-icons": str,
        "printer-is-accepting-jobs": bool,
        "printer-is-shared": bool,
        "printer-is-temporary": bool,
        "printer-more-info": str,
        "printer-op-policy": str,
        "printer-state": int,
        "printer-state-change-date-time": str,
        "printer-state-change-time": int,
        "printer-state-message": str,
        "printer-state-reasons": list[str],
        "printer-strings-uri": str,
        "printer-type": int,
        "printer-up-time": int,
        "printer-uri-supported": list[str],
        "queued-job-count": int,
        "uri-security-supported": list[str],
        "uri-authentication-supported": list[str],
        "printer-id": int,
        "printer-name": str,
        "printer-location": str,
        "printer-geo-location": str,
        "printer-info": str,
        "printer-organization": str,
        "printer-organizational-unit": str,
        "printer-uuid": str,
        "job-quota-period": int,
        "job-k-limit": int,
        "job-page-limit": int,
        "job-sheets-default": tuple[str, str],
        "device-uri": str,
        "document-format-supported": list[str],
        "copies-default": int,
        "document-format-default": str,
        "job-cancel-after-default": int,
        "job-hold-until-default": str,
        "job-priority-default": int,
        "number-up-default": int,
        "notify-lease-duration-default": int,
        "notify-events-default": list[str],
        "orientation-requested-default": int | None,
        "print-color-mode-default": str,
        "print-quality-default": int,
        "copies-supported": tuple[int, int],
        "ipp-features-supported": list[str],
        "job-creation-attributes-supported": list[str],
        "printer-make-and-model": str,
        "finishings-supported": list[int],
        "finishings-default": int,
        "charset-configured": str,
        "charset-supported": list[str],
        "compression-supported": list[str],
        "cups-version": str,
        "generated-natural-language-supported": list[str],
        "ipp-versions-supported": list[str],
        "ippget-event-life": int,
        "job-cancel-after-supported": tuple[int, int],
        "job-hold-until-supported": list[str],
        "job-ids-supported": bool,
        "job-k-octets-supported": tuple[int, int],
        "job-priority-supported": list[int],
        "job-settable-attributes-supported": list[str],
        "job-sheets-supported": list[str],
        "jpeg-k-octets-supported": tuple[int, int],
        "jpeg-x-dimension-supported": tuple[int, int],
        "jpeg-y-dimension-supported": tuple[int, int],
        "media-col-supported": list[str],
        "multiple-document-handling-supported": list[str],
        "multiple-document-jobs-supported": bool,
        "multiple-operation-time-out": int,
        "multiple-operation-time-out-action": str,
        "natural-language-configured": str,
        "notify-attributes-supported": list[str],
        "notify-lease-duration-supported": tuple[int, int],
        "notify-max-events-supported": list[int],
        "notify-events-supported": list[str],
        "notify-pull-method-supported": list[str],
        "notify-schemes-supported": list[str],
        "number-up-supported": list[int],
        "number-up-layout-supported": list[str],
        "operations-supported": list[int],
        "orientation-requested-supported": list[int],
        "page-delivery-supported": list[str],
        "page-ranges-supported": bool,
        "pdf-k-octets-supported": tuple[int, int],
        "pdf-versions-supported": list[str],
        "pdl-override-supported": list[str],
        "print-scaling-supported": list[str],
        "printer-get-attributes-supported": list[str],
        "printer-op-policy-supported": list[str],
        "printer-settable-attributes-supported": list[str],
        "server-is-sharing-printers": bool,
        "which-jobs-supported": list[str],
    },
)

_CupsPrinterSimple = TypedDict(
    "_CupsPrinterSimple",
    {
        "printer-is-shared": bool,
        "printer-state": int,
        "printer-state-message": str,
        "printer-state-reasons": list[str],
        "printer-type": int,
        "printer-uri-supported": str,
        "printer-location": str,
        "printer-info": str,
        "device-uri": str,
        "printer-make-and-model": str,
    },
)

_CupsSubscription = TypedDict(
    "_CupsSubscription",
    {
        "notify-events": list[str],
        "notify-lease-duration": int,
        "notify-pull-method": NotRequired[str],
        "notify-recipient-uri": NotRequired[str],
        "notify-subscriber-user-name": str,
        "notify-time-interval": int,
        "notify-subscription-id": int,
    },
)

CUPS_DEST_FLAGS_CANCELED: Final[int]
CUPS_DEST_FLAGS_CONNECTING: Final[int]
CUPS_DEST_FLAGS_ERROR: Final[int]
CUPS_DEST_FLAGS_MORE: Final[int]
CUPS_DEST_FLAGS_NONE: Final[int]
CUPS_DEST_FLAGS_REMOVED: Final[int]
CUPS_DEST_FLAGS_RESOLVING: Final[int]
CUPS_DEST_FLAGS_UNCONNECTED: Final[int]
CUPS_FORMAT_AUTO: Final[str]
CUPS_FORMAT_COMMAND: Final[str]
CUPS_FORMAT_PDF: Final[str]
CUPS_FORMAT_POSTSCRIPT: Final[str]
CUPS_FORMAT_RAW: Final[str]
CUPS_FORMAT_TEXT: Final[str]
CUPS_PRINTER_AUTHENTICATED: Final[int]
CUPS_PRINTER_BIND: Final[int]
CUPS_PRINTER_BW: Final[int]
CUPS_PRINTER_CLASS: Final[int]
CUPS_PRINTER_COLLATE: Final[int]
CUPS_PRINTER_COLOR: Final[int]
CUPS_PRINTER_COMMANDS: Final[int]
CUPS_PRINTER_COPIES: Final[int]
CUPS_PRINTER_COVER: Final[int]
CUPS_PRINTER_DEFAULT: Final[int]
CUPS_PRINTER_DELETE: Final[int]
CUPS_PRINTER_DISCOVERED: Final[int]
CUPS_PRINTER_DUPLEX: Final[int]
CUPS_PRINTER_FAX: Final[int]
CUPS_PRINTER_IMPLICIT: Final[int]
CUPS_PRINTER_LARGE: Final[int]
CUPS_PRINTER_LOCAL: Final[int]
CUPS_PRINTER_MEDIUM: Final[int]
CUPS_PRINTER_NOT_SHARED: Final[int]
CUPS_PRINTER_OPTIONS: Final[int]
CUPS_PRINTER_PUNCH: Final[int]
CUPS_PRINTER_REJECTING: Final[int]
CUPS_PRINTER_REMOTE: Final[int]
CUPS_PRINTER_SMALL: Final[int]
CUPS_PRINTER_SORT: Final[int]
CUPS_PRINTER_STAPLE: Final[int]
CUPS_PRINTER_VARIABLE: Final[int]
CUPS_SERVER_DEBUG_LOGGING: Final[str]
CUPS_SERVER_REMOTE_ADMIN: Final[str]
CUPS_SERVER_REMOTE_ANY: Final[str]
CUPS_SERVER_REMOTE_PRINTERS: Final[str]
CUPS_SERVER_SHARE_PRINTERS: Final[str]
CUPS_SERVER_USER_CANCEL_ANY: Final[str]
HTTP_AUTHORIZATION_CANCELED: Final[int]
HTTP_BAD_GATEWAY: Final[int]
HTTP_BAD_REQUEST: Final[int]
HTTP_ENCRYPT_ALWAYS: Final[int]
HTTP_ENCRYPT_IF_REQUESTED: Final[int]
HTTP_ENCRYPT_NEVER: Final[int]
HTTP_ENCRYPT_REQUIRED: Final[int]
HTTP_ERROR: Final[int]
HTTP_FORBIDDEN: Final[int]
HTTP_GATEWAY_TIMEOUT: Final[int]
HTTP_NOT_FOUND: Final[int]
HTTP_NOT_IMPLEMENTED: Final[int]
HTTP_NOT_MODIFIED: Final[int]
HTTP_NOT_SUPPORTED: Final[int]
HTTP_OK: Final[int]
HTTP_PKI_ERROR: Final[int]
HTTP_REQUEST_TIMEOUT: Final[int]
HTTP_SERVER_ERROR: Final[int]
HTTP_SERVICE_UNAVAILABLE: Final[int]
HTTP_STATUS_BAD_GATEWAY: Final[int]
HTTP_STATUS_BAD_REQUEST: Final[int]
HTTP_STATUS_CUPS_AUTHORIZATION_CANCELED: Final[int]
HTTP_STATUS_CUPS_PKI_ERROR: Final[int]
HTTP_STATUS_ERROR: Final[int]
HTTP_STATUS_FORBIDDEN: Final[int]
HTTP_STATUS_GATEWAY_TIMEOUT: Final[int]
HTTP_STATUS_NOT_FOUND: Final[int]
HTTP_STATUS_NOT_IMPLEMENTED: Final[int]
HTTP_STATUS_NOT_MODIFIED: Final[int]
HTTP_STATUS_NOT_SUPPORTED: Final[int]
HTTP_STATUS_OK: Final[int]
HTTP_STATUS_REQUEST_TIMEOUT: Final[int]
HTTP_STATUS_SERVER_ERROR: Final[int]
HTTP_STATUS_SERVICE_UNAVAILABLE: Final[int]
HTTP_STATUS_UNAUTHORIZED: Final[int]
HTTP_STATUS_UPGRADE_REQUIRED: Final[int]
HTTP_UNAUTHORIZED: Final[int]
HTTP_UPGRADE_REQUIRED: Final[int]
IPP_ATTRIBUTE: Final[int]
IPP_ATTRIBUTES: Final[int]
IPP_ATTRIBUTES_NOT_SETTABLE: Final[int]
IPP_AUTHENTICATION_CANCELED: Final[int]
IPP_BAD_REQUEST: Final[int]
IPP_CHARSET: Final[int]
IPP_COMPRESSION_ERROR: Final[int]
IPP_COMPRESSION_NOT_SUPPORTED: Final[int]
IPP_CONFLICT: Final[int]
IPP_CREATE_JOB_SUBSCRIPTION: Final[int]
IPP_CREATE_PRINTER_SUBSCRIPTION: Final[int]
IPP_DATA: Final[int]
IPP_DEVICE_ERROR: Final[int]
IPP_DOCUMENT_ACCESS_ERROR: Final[int]
IPP_DOCUMENT_FORMAT: Final[int]
IPP_DOCUMENT_FORMAT_ERROR: Final[int]
IPP_ERROR: Final[int]
IPP_ERROR_JOB_CANCELED: Final[int]
IPP_FINISHINGS_BALE: Final[int]
IPP_FINISHINGS_BIND: Final[int]
IPP_FINISHINGS_BIND_BOTTOM: Final[int]
IPP_FINISHINGS_BIND_LEFT: Final[int]
IPP_FINISHINGS_BIND_RIGHT: Final[int]
IPP_FINISHINGS_BIND_TOP: Final[int]
IPP_FINISHINGS_BOOKLET_MAKER: Final[int]
IPP_FINISHINGS_COVER: Final[int]
IPP_FINISHINGS_EDGE_STITCH: Final[int]
IPP_FINISHINGS_EDGE_STITCH_BOTTOM: Final[int]
IPP_FINISHINGS_EDGE_STITCH_LEFT: Final[int]
IPP_FINISHINGS_EDGE_STITCH_RIGHT: Final[int]
IPP_FINISHINGS_EDGE_STITCH_TOP: Final[int]
IPP_FINISHINGS_FOLD: Final[int]
IPP_FINISHINGS_JOB_OFFSET: Final[int]
IPP_FINISHINGS_NONE: Final[int]
IPP_FINISHINGS_PUNCH: Final[int]
IPP_FINISHINGS_SADDLE_STITCH: Final[int]
IPP_FINISHINGS_STAPLE: Final[int]
IPP_FINISHINGS_STAPLE_BOTTOM_LEFT: Final[int]
IPP_FINISHINGS_STAPLE_BOTTOM_RIGHT: Final[int]
IPP_FINISHINGS_STAPLE_DUAL_BOTTOM: Final[int]
IPP_FINISHINGS_STAPLE_DUAL_LEFT: Final[int]
IPP_FINISHINGS_STAPLE_DUAL_RIGHT: Final[int]
IPP_FINISHINGS_STAPLE_DUAL_TOP: Final[int]
IPP_FINISHINGS_STAPLE_TOP_LEFT: Final[int]
IPP_FINISHINGS_STAPLE_TOP_RIGHT: Final[int]
IPP_FINISHINGS_TRIM: Final[int]
IPP_FORBIDDEN: Final[int]
IPP_GONE: Final[int]
IPP_HEADER: Final[int]
IPP_IDLE: Final[int]
IPP_IGNORED_ALL_NOTIFICATIONS: Final[int]
IPP_IGNORED_ALL_SUBSCRIPTIONS: Final[int]
IPP_INTERNAL_ERROR: Final[int]
IPP_JOB_ABORTED: Final[int]
IPP_JOB_CANCELED: Final[int]
IPP_JOB_COMPLETED: Final[int]
IPP_JOB_HELD: Final[int]
IPP_JOB_PENDING: Final[int]
IPP_JOB_PROCESSING: Final[int]
IPP_JOB_STOPPED: Final[int]
IPP_LANDSCAPE: Final[int]
IPP_MAX_NAME: Final[int]
IPP_MULTIPLE_JOBS_NOT_SUPPORTED: Final[int]
IPP_NOT_ACCEPTING: Final[int]
IPP_NOT_AUTHENTICATED: Final[int]
IPP_NOT_AUTHORIZED: Final[int]
IPP_NOT_FOUND: Final[int]
IPP_NOT_POSSIBLE: Final[int]
IPP_OK: Final[int]
IPP_OK_BUT_CANCEL_SUBSCRIPTION: Final[int]
IPP_OK_CONFLICT: Final[int]
IPP_OK_EVENTS_COMPLETE: Final[int]
IPP_OK_IGNORED_NOTIFICATIONS: Final[int]
IPP_OK_IGNORED_SUBSCRIPTIONS: Final[int]
IPP_OK_SUBST: Final[int]
IPP_OK_TOO_MANY_EVENTS: Final[int]
IPP_OPERATION_NOT_SUPPORTED: Final[int]
IPP_OP_ACTIVATE_PRINTER: Final[int]
IPP_OP_CANCEL_CURRENT_JOB: Final[int]
IPP_OP_CANCEL_JOB: Final[int]
IPP_OP_CANCEL_JOBS: Final[int]
IPP_OP_CANCEL_MY_JOBS: Final[int]
IPP_OP_CANCEL_SUBSCRIPTION: Final[int]
IPP_OP_CLOSE_JOB: Final[int]
IPP_OP_CREATE_JOB: Final[int]
IPP_OP_CREATE_JOB_SUBSCRIPTIONS: Final[int]
IPP_OP_CREATE_PRINTER_SUBSCRIPTIONS: Final[int]
IPP_OP_CUPS_ACCEPT_JOBS: Final[int]
IPP_OP_CUPS_ADD_MODIFY_CLASS: Final[int]
IPP_OP_CUPS_ADD_MODIFY_PRINTER: Final[int]
IPP_OP_CUPS_AUTHENTICATE_JOB: Final[int]
IPP_OP_CUPS_DELETE_CLASS: Final[int]
IPP_OP_CUPS_DELETE_PRINTER: Final[int]
IPP_OP_CUPS_GET_CLASSES: Final[int]
IPP_OP_CUPS_GET_DEFAULT: Final[int]
IPP_OP_CUPS_GET_DOCUMENT: Final[int]
IPP_OP_CUPS_GET_PPD: Final[int]
IPP_OP_CUPS_GET_PPDS: Final[int]
IPP_OP_CUPS_GET_PRINTERS: Final[int]
IPP_OP_CUPS_MOVE_JOB: Final[int]
IPP_OP_CUPS_REJECT_JOBS: Final[int]
IPP_OP_CUPS_SET_DEFAULT: Final[int]
IPP_OP_DEACTIVATE_PRINTER: Final[int]
IPP_OP_DISABLE_PRINTER: Final[int]
IPP_OP_ENABLE_PRINTER: Final[int]
IPP_OP_GET_JOBS: Final[int]
IPP_OP_GET_JOB_ATTRIBUTES: Final[int]
IPP_OP_GET_NOTIFICATIONS: Final[int]
IPP_OP_GET_PRINTER_ATTRIBUTES: Final[int]
IPP_OP_GET_PRINTER_SUPPORTED_VALUES: Final[int]
IPP_OP_GET_PRINT_SUPPORT_FILES: Final[int]
IPP_OP_GET_RESOURCES: Final[int]
IPP_OP_GET_RESOURCE_ATTRIBUTES: Final[int]
IPP_OP_GET_RESOURCE_DATA: Final[int]
IPP_OP_GET_SUBSCRIPTIONS: Final[int]
IPP_OP_HOLD_JOB: Final[int]
IPP_OP_HOLD_NEW_JOBS: Final[int]
IPP_OP_IDENTIFY_PRINTER: Final[int]
IPP_OP_PAUSE_PRINTER: Final[int]
IPP_OP_PAUSE_PRINTER_AFTER_CURRENT_JOB: Final[int]
IPP_OP_PRINT_JOB: Final[int]
IPP_OP_PRINT_URI: Final[int]
IPP_OP_PROMOTE_JOB: Final[int]
IPP_OP_PURGE_JOBS: Final[int]
IPP_OP_RELEASE_HELD_NEW_JOBS: Final[int]
IPP_OP_RELEASE_JOB: Final[int]
IPP_OP_RENEW_SUBSCRIPTION: Final[int]
IPP_OP_REPROCESS_JOB: Final[int]
IPP_OP_RESTART_JOB: Final[int]
IPP_OP_RESTART_PRINTER: Final[int]
IPP_OP_RESUBMIT_JOB: Final[int]
IPP_OP_RESUME_JOB: Final[int]
IPP_OP_RESUME_PRINTER: Final[int]
IPP_OP_SCHEDULE_JOB_AFTER: Final[int]
IPP_OP_SEND_DOCUMENT: Final[int]
IPP_OP_SEND_HARDCOPY_DOCUMENT: Final[int]
IPP_OP_SEND_NOTIFICATIONS: Final[int]
IPP_OP_SEND_URI: Final[int]
IPP_OP_SET_JOB_ATTRIBUTES: Final[int]
IPP_OP_SET_PRINTER_ATTRIBUTES: Final[int]
IPP_OP_SHUTDOWN_PRINTER: Final[int]
IPP_OP_STARTUP_PRINTER: Final[int]
IPP_OP_SUSPEND_CURRENT_JOB: Final[int]
IPP_OP_VALIDATE_DOCUMENT: Final[int]
IPP_OP_VALIDATE_JOB: Final[int]
IPP_ORIENT_LANDSCAPE: Final[int]
IPP_ORIENT_PORTRAIT: Final[int]
IPP_ORIENT_REVERSE_LANDSCAPE: Final[int]
IPP_ORIENT_REVERSE_PORTRAIT: Final[int]
IPP_PKI_ERROR: Final[int]
IPP_PORTRAIT: Final[int]
IPP_PRINTER_BUSY: Final[int]
IPP_PRINTER_IDLE: Final[int]
IPP_PRINTER_IS_DEACTIVATED: Final[int]
IPP_PRINTER_PROCESSING: Final[int]
IPP_PRINTER_STOPPED: Final[int]
IPP_PRINT_SUPPORT_FILE_NOT_FOUND: Final[int]
IPP_QUALITY_DRAFT: Final[int]
IPP_QUALITY_HIGH: Final[int]
IPP_QUALITY_NORMAL: Final[int]
IPP_REDIRECTION_OTHER_SITE: Final[int]
IPP_REQUEST_ENTITY: Final[int]
IPP_REQUEST_VALUE: Final[int]
IPP_RES_PER_CM: Final[int]
IPP_RES_PER_INCH: Final[int]
IPP_REVERSE_LANDSCAPE: Final[int]
IPP_REVERSE_PORTRAIT: Final[int]
IPP_SERVICE_UNAVAILABLE: Final[int]
IPP_STATE_ATTRIBUTE: Final[int]
IPP_STATE_DATA: Final[int]
IPP_STATE_ERROR: Final[int]
IPP_STATE_HEADER: Final[int]
IPP_STATE_IDLE: Final[int]
IPP_STATUS_ERROR_ATTRIBUTES_NOT_SETTABLE: Final[int]
IPP_STATUS_ERROR_ATTRIBUTES_OR_VALUES: Final[int]
IPP_STATUS_ERROR_BAD_REQUEST: Final[int]
IPP_STATUS_ERROR_BUSY: Final[int]
IPP_STATUS_ERROR_CHARSET: Final[int]
IPP_STATUS_ERROR_COMPRESSION_ERROR: Final[int]
IPP_STATUS_ERROR_COMPRESSION_NOT_SUPPORTED: Final[int]
IPP_STATUS_ERROR_CONFLICTING: Final[int]
IPP_STATUS_ERROR_CUPS_AUTHENTICATION_CANCELED: Final[int]
IPP_STATUS_ERROR_CUPS_PKI: Final[int]
IPP_STATUS_ERROR_CUPS_UPGRADE_REQUIRED: Final[int]
IPP_STATUS_ERROR_DEVICE: Final[int]
IPP_STATUS_ERROR_DOCUMENT_ACCESS: Final[int]
IPP_STATUS_ERROR_DOCUMENT_FORMAT_ERROR: Final[int]
IPP_STATUS_ERROR_DOCUMENT_FORMAT_NOT_SUPPORTED: Final[int]
IPP_STATUS_ERROR_FORBIDDEN: Final[int]
IPP_STATUS_ERROR_GONE: Final[int]
IPP_STATUS_ERROR_IGNORED_ALL_NOTIFICATIONS: Final[int]
IPP_STATUS_ERROR_IGNORED_ALL_SUBSCRIPTIONS: Final[int]
IPP_STATUS_ERROR_INTERNAL: Final[int]
IPP_STATUS_ERROR_JOB_CANCELED: Final[int]
IPP_STATUS_ERROR_MULTIPLE_JOBS_NOT_SUPPORTED: Final[int]
IPP_STATUS_ERROR_NOT_ACCEPTING_JOBS: Final[int]
IPP_STATUS_ERROR_NOT_AUTHENTICATED: Final[int]
IPP_STATUS_ERROR_NOT_AUTHORIZED: Final[int]
IPP_STATUS_ERROR_NOT_FOUND: Final[int]
IPP_STATUS_ERROR_NOT_POSSIBLE: Final[int]
IPP_STATUS_ERROR_OPERATION_NOT_SUPPORTED: Final[int]
IPP_STATUS_ERROR_PRINTER_IS_DEACTIVATED: Final[int]
IPP_STATUS_ERROR_PRINT_SUPPORT_FILE_NOT_FOUND: Final[int]
IPP_STATUS_ERROR_REQUEST_ENTITY: Final[int]
IPP_STATUS_ERROR_REQUEST_VALUE: Final[int]
IPP_STATUS_ERROR_SERVICE_UNAVAILABLE: Final[int]
IPP_STATUS_ERROR_TEMPORARY: Final[int]
IPP_STATUS_ERROR_TIMEOUT: Final[int]
IPP_STATUS_ERROR_TOO_MANY_SUBSCRIPTIONS: Final[int]
IPP_STATUS_ERROR_URI_SCHEME: Final[int]
IPP_STATUS_ERROR_VERSION_NOT_SUPPORTED: Final[int]
IPP_STATUS_OK: Final[int]
IPP_STATUS_OK_BUT_CANCEL_SUBSCRIPTION: Final[int]
IPP_STATUS_OK_CONFLICTING: Final[int]
IPP_STATUS_OK_EVENTS_COMPLETE: Final[int]
IPP_STATUS_OK_IGNORED_NOTIFICATIONS: Final[int]
IPP_STATUS_OK_IGNORED_OR_SUBSTITUTED: Final[int]
IPP_STATUS_OK_IGNORED_SUBSCRIPTIONS: Final[int]
IPP_STATUS_OK_TOO_MANY_EVENTS: Final[int]
IPP_STATUS_REDIRECTION_OTHER_SITE: Final[int]
IPP_TAG_BOOLEAN: Final[int]
IPP_TAG_CHARSET: Final[int]
IPP_TAG_ENUM: Final[int]
IPP_TAG_INTEGER: Final[int]
IPP_TAG_JOB: Final[int]
IPP_TAG_KEYWORD: Final[int]
IPP_TAG_LANGUAGE: Final[int]
IPP_TAG_MIMETYPE: Final[int]
IPP_TAG_NAME: Final[int]
IPP_TAG_OPERATION: Final[int]
IPP_TAG_PRINTER: Final[int]
IPP_TAG_RANGE: Final[int]
IPP_TAG_STRING: Final[int]
IPP_TAG_TEXT: Final[int]
IPP_TAG_URI: Final[int]
IPP_TAG_ZERO: Final[int]
IPP_TEMPORARY_ERROR: Final[int]
IPP_TIMEOUT: Final[int]
IPP_TOO_MANY_SUBSCRIPTIONS: Final[int]
IPP_UPGRADE_REQUIRED: Final[int]
IPP_URI_SCHEME: Final[int]
IPP_VERSION_NOT_SUPPORTED: Final[int]
PPD_CONFORM_RELAXED: Final[int]
PPD_CONFORM_STRICT: Final[int]
PPD_ORDER_ANY: Final[int]
PPD_ORDER_DOCUMENT: Final[int]
PPD_ORDER_EXIT: Final[int]
PPD_ORDER_JCL: Final[int]
PPD_ORDER_PAGE: Final[int]
PPD_ORDER_PROLOG: Final[int]
PPD_UI_BOOLEAN: Final[int]
PPD_UI_PICKMANY: Final[int]
PPD_UI_PICKONE: Final[int]

@final
class Attribute:
    @property
    def name(self) -> str: ...
    @property
    def spec(self) -> str: ...
    @property
    def text(self) -> str: ...
    @property
    def value(self) -> str: ...
    def __init__(self, *args: Unused) -> None: ...

@final
class Connection:
    def __init__(self, host: str = ..., port: int = ..., encryption: int = ...) -> None: ...
    def acceptJobs(self, name: str, /) -> None: ...

    @overload
    def addPrinter(self, name: str, filename: str = ..., *, info: str = ..., location: str = ..., device: str = ...) -> None: ...
    @overload
    def addPrinter(self, name: str, *, ppdname: str = ..., info: str = ..., location: str = ..., device: str = ...) -> None: ...
    @overload
    def addPrinter(self, name: str, *, info: str = ..., location: str = ..., device: str = ..., ppd: PPD = ...) -> None: ...

    def addPrinterOptionDefault(self, name: str, option: str, value: str | int | Sequence[str | int], /) -> None: ...
    def addPrinterToClass(self, name: str, _class: str, /) -> None: ...
    def adminExportSamba(self, name: str, server: str, user: str, password: str, /): ...
    def adminGetServerSettings(self) -> dict[str, str]: ...
    def adminSetServerSettings(self, settings: dict[str, str], /) -> None: ...
    def authenticateJob(self, jobid: int, auth_info: list[str] = ..., /) -> None: ...

    @overload
    def cancelAllJobs(self, name: str, *, my_jobs: bool = False, purge_jobs: bool = True) -> None: ...
    @overload
    def cancelAllJobs(self, *, uri: str, my_jobs: bool = False, purge_jobs: bool = True) -> None: ...

    def cancelJob(self, job_id: int, purge_job: bool = False) -> None: ...
    def cancelSubscription(self, id: int, /) -> None: ...
    def createJob(self, printer: str, title: str, options: dict[str, str]) -> int: ...
    def createSubscription(
        self,
        uri: str,
        events: list[str] = ...,
        job_id: int = ...,
        recipient_uri: str = ...,
        lease_duration: int = ...,
        time_interval: int = ...,
        user_data: str = ...,
    ) -> int: ...
    def deleteClass(self, _class: str, /) -> None: ...
    def deletePrinter(self, name: str, /) -> None: ...
    def deletePrinterFromClass(self, name: str, _class: str, /) -> None: ...
    def deletePrinterOptionDefault(self, name: str, option: str, /) -> None: ...
    def disablePrinter(self, name: str, reason: str = ...) -> None: ...
    def enablePrinter(self, name: str, /) -> None: ...
    def finishDocument(self, printer: str) -> int: ...
    def getClasses(self) -> dict[str, str | list[str]]: ...
    def getDefault(self) -> str | None: ...
    def getDests(self) -> dict[tuple[str, str] | tuple[None, None], Dest]: ...
    def getDevices(
        self, limit: int = 0, exclude_schemes: list[str] = ..., include_schemes: list[str] = ..., timeout: int = 0
    ) -> dict[str, _CupsDevice]: ...
    def getDocument(self, printer_uri: str, job_id: int, document_number: int, /) -> _CupsDocument: ...

    @overload
    def getFile(self, resource: str, filename: str = ...) -> None: ...
    @overload
    def getFile(self, resource: str, *, fd: int) -> None: ...
    @overload
    def getFile(self, resource: str, *, file: _FileOrFd) -> None: ...

    def getJobAttributes(self, job_id: int, requested_attributes: list[str] = ...) -> _CupsJobWithAttributeInfo: ...
    def getJobs(
        self,
        which_jobs: Literal["completed", "not-completed", "all"] = "not-completed",
        my_jobs: bool = False,
        limit: int = ...,
        first_job_id: int = ...,
        requested_attributes: list[str] = ["job-id", "job-uri"],
    ) -> dict[int, _CupsJob]: ...
    def getNotifications(self, subscription_ids: list[int], sequence_numbers: list[int] = ...) -> _CupsNotifications: ...
    def getPPD(self, name: str, /) -> str: ...
    def getPPD3(self, name: str, modtime: float = ..., filename: str = ...) -> tuple[int, float, str]: ...
    def getPPDs(
        self,
        limit: int = ...,
        exclude_schemes: list[str] = ...,
        include_schemes: list[str] = ...,
        ppd_natural_language: str = ...,
        ppd_device_id: str = ...,
        ppd_make: str = ...,
        ppd_make_and_model: str = ...,
        ppd_model_number: int = ...,
        ppd_product: str = ...,
        ppd_psversion: str = ...,
        ppd_type: str = ...,
    ) -> dict[str, _CupsPPD]: ...
    def getPPDs2(
        self,
        limit: int = ...,
        exclude_schemes: list[str] = ...,
        include_schemes: list[str] = ...,
        ppd_natural_language: str = ...,
        ppd_device_id: str = ...,
        ppd_make: str = ...,
        ppd_make_and_model: str = ...,
        ppd_model_number: int = ...,
        ppd_product: str = ...,
        ppd_psversion: str = ...,
        ppd_type: str = ...,
    ) -> dict[str, _CupsPPD2]: ...

    @overload
    def getPrinterAttributes(self, name: str, *, requested_attributes: list[str] = ...) -> _CupsPrinter: ...
    @overload
    def getPrinterAttributes(self, *, uri: str, requested_attributes: list[str] = ...) -> _CupsPrinter: ...

    def getPrinters(self) -> dict[str, _CupsPrinterSimple]: ...
    def getServerPPD(self, ppd_name: str, /) -> str: ...
    def getSubscriptions(self, uri: str, my_subscriptions: bool = False, job_id: int = ...) -> list[_CupsSubscription]: ...

    @overload
    def moveJob(self, printer_uri: str, job_id: int, job_printer_uri: str) -> None: ...
    @overload
    def moveJob(self, printer_uri: str, *, job_printer_uri: str) -> None: ...
    @overload
    def moveJob(self, *, job_id: int, job_printer_uri: str) -> None: ...

    def printFile(self, printer: str, filename: str, title: str, options: dict[str, str]) -> int: ...
    def printFiles(self, printer: str, filenames: list[str], title: str, options: dict[str, str]) -> int: ...
    def printTestPage(self, name: str) -> int: ...

    @overload
    def putFile(self, resource: str, filename: str) -> None: ...
    @overload
    def putFile(self, resource: str, *, fd: int) -> None: ...
    @overload
    def putFile(self, resource: str, *, file: _FileOrFd) -> None: ...

    def rejectJobs(self, name: str, reason: str = ...) -> None: ...
    def renewSubscription(self, id: int, lease_duration: int = ...) -> None: ...
    def restartJob(self, job_id: int, job_hold_until: str = ...) -> None: ...
    def setDefault(self, name: str, /) -> None: ...
    def setJobHoldUntil(self, job_id: int, job_hold_until: str, /) -> None: ...
    def setPrinterDevice(self, name: str, device_uri: str, /) -> None: ...
    def setPrinterErrorPolicy(self, name: str, policy: str, /) -> None: ...
    def setPrinterInfo(self, name: str, info: str, /) -> None: ...
    def setPrinterJobSheets(self, name: str, start: str, end: str, /) -> None: ...
    def setPrinterLocation(self, name: str, location: str, /) -> None: ...
    def setPrinterOpPolicy(self, name: str, policy: str, /) -> None: ...
    def setPrinterShared(self, name: str, shared: bool, /) -> None: ...
    def setPrinterUsersAllowed(self, name: str, allowed: list[str], /) -> None: ...
    def setPrinterUsersDenied(self, name: str, denied: list[str], /) -> None: ...
    def startDocument(self, printer: str, job_id: int, doc_name: str, format: str, last_document: bool) -> int: ...
    def writeRequestData(self, buffer: bytes, length: int) -> int: ...

@final
class Constraint:
    @property
    def choice1(self) -> str: ...
    @property
    def choice2(self) -> str: ...
    @property
    def option1(self) -> str: ...
    @property
    def option2(self) -> str: ...
    def __init__(self, *args: Unused) -> None: ...

@final
class Dest:
    @property
    def instance(self) -> str | None: ...
    @property
    def is_default(self) -> bool: ...
    @property
    def name(self) -> str: ...
    @property
    def options(self) -> dict[str, str]: ...
    def __init__(self, *args: Unused) -> None: ...

@final
class Group:
    @property
    def name(self) -> str: ...
    @property
    def options(self) -> list[Option]: ...
    @property
    def subgroups(self) -> list[Group]: ...
    @property
    def text(self) -> str: ...
    def __init__(self, *args: Unused) -> None: ...

class HTTPError(Exception): ...

@final
class IPPAttribute:
    @property
    def group_tag(self) -> int: ...
    @property
    def name(self) -> str: ...
    @property
    def value_tag(self) -> int: ...
    @property
    def values(self) -> list[int | str | bool]: ...
    def __init__(self, group_tag: int, value_tag: int, name: str, value: str = ..., /) -> None: ...

class IPPError(Exception): ...

@final
class IPPRequest:
    @property
    def attributes(self) -> list[IPPAttribute]: ...
    @property
    def operation(self) -> int: ...

    @property
    def state(self) -> int: ...
    @state.setter
    def state(self, value: int) -> None: ...

    @property
    def statuscode(self) -> int: ...
    @statuscode.setter
    def statuscode(self, value: int) -> None: ...

    def __init__(self, op: int = ..., /) -> None: ...
    def add(self, attr: IPPAttribute, /) -> IPPAttribute: ...
    def addSeparator(self) -> IPPAttribute: ...
    def readIO(self, read_fn: Callable[[int], int], blocking: bool = True) -> int: ...
    def writeIO(self, write_fn: Callable[[bytes], int], blocking: bool = True) -> int: ...

@final
class Option:
    @property
    def choices(self) -> list[_CupsOptionChoice]: ...
    @property
    def conflicted(self) -> bool: ...
    @property
    def defchoice(self) -> str: ...
    @property
    def keyword(self) -> str: ...
    @property
    def text(self) -> str: ...
    @property
    def ui(self) -> int: ...
    def __init__(self, *args: Unused) -> None: ...

@final
class PPD:
    @property
    def attributes(self) -> list[Attribute]: ...
    @property
    def constraints(self) -> list[Constraint]: ...
    @property
    def optionGroups(self) -> list[Group]: ...
    def __init__(self, filename: str, /) -> None: ...
    def conflicts(self) -> int: ...
    def emit(self, file: _FileOrFd, section: int, /) -> None: ...
    def emitAfterOrder(self, file: _FileOrFd, section: int, limit: int, min_order: float, /) -> None: ...
    def emitFd(self, fd: int, section: int, /) -> None: ...
    def emitJCL(self, file: _FileOrFd, job_id: int, user: str, title: str, /) -> None: ...
    def emitJCLEnd(self, file: _FileOrFd, /) -> None: ...
    def emitString(self, section: int, min_order: float, /) -> str: ...
    def findAttr(self, name: str, spec: str = ...) -> Attribute | None: ...
    def findNextAttr(self, name: str, spec: str = ...) -> Attribute | None: ...
    def findOption(self, name: str, /) -> Option | None: ...
    def localize(self) -> None: ...
    def localizeIPPReason(self, reason: str, scheme: str = ...) -> str | None: ...
    def localizeMarkerName(self, name: str, /) -> str | None: ...
    def markDefaults(self) -> None: ...
    def markOption(self, option: str, choice: str, /) -> int: ...
    def nondefaultsMarked(self) -> bool: ...
    def writeFd(self, fd: int, /) -> None: ...

def connectDest(
    dest: Dest, cb: Callable[[_T, int, Dest], Literal[0, 1]], flags: int = 0, msec: int = -1, user_data: _T = ...
) -> tuple[Connection, str]: ...
def enumDests(
    cb: Callable[[_T, int, Dest], Literal[0, 1]],
    flags: int = 0,
    msec: int = -1,
    type: int = 0,
    mask: int = 0,
    user_data: _T = ...,
) -> None: ...
def getEncryption() -> int: ...
def getPort() -> int: ...
def getServer() -> str: ...
def getUser() -> str: ...
def ippErrorString(status_code: int, /) -> str: ...
def ippOpString(op: int, /) -> str: ...
def modelSort(s1: str, s2: str, /) -> Literal[-1, 0, 1]: ...
def ppdSetConformance(level: int, /) -> None: ...
def require(version: str, /) -> None: ...
def setEncryption(policy: int, /) -> None: ...
def setPasswordCB(fn: Callable[[str], str | None], /) -> None: ...

@overload
def setPasswordCB2(fn: Callable[[str, Connection, str, str], str | None] | None, /) -> None: ...
@overload
def setPasswordCB2(fn: Callable[[str, Connection, str, str, _T], str | None], context: _T = ..., /) -> None: ...

def setPort(port: int, /) -> None: ...
def setServer(server: str, /) -> None: ...
def setUser(user: str, /) -> None: ...
