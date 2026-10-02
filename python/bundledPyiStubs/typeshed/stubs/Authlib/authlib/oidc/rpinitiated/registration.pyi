from collections.abc import Callable

from authlib.oauth2.claims import BaseClaims

class ClientMetadataClaims(BaseClaims):
    REGISTERED_CLAIMS: list[str]
    def validate(self, now: int | Callable[[], int] | None = None, leeway: int = 0) -> None: ...
