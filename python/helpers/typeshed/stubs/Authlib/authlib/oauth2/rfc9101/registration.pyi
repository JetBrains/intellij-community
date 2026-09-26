from collections.abc import Callable

from authlib.jose import BaseClaims

class ClientMetadataClaims(BaseClaims):
    def validate(self, now: int | Callable[[], int] | None = None, leeway: int = 0) -> None: ...
    def validate_require_signed_request_object(self) -> None: ...
