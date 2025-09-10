from typing import Final

URL: Final[str]
METHOD: Final[str]
USER_AGENT: Final[str]
CLIENT_IP: Final[str]
X_FORWARDED_FOR: Final[str]
STATUS: Final[str]
CONTENT_LENGTH: Final[str]
XRAY_HEADER: Final[str]
ALT_XRAY_HEADER: Final[str]
request_keys: tuple[str, ...]
response_keys: tuple[str, ...]
