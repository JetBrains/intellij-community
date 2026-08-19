from collections.abc import Callable

from authlib.jose import JWTClaims

# Inherits from joserfc.jwt.JWTClaimsRegistry
class JWTAccessTokenClaimsValidator:
    def validate_auth_time(self, auth_time) -> None: ...
    def validate_amr(self, amr) -> None: ...

class JWTAccessTokenClaims(JWTClaims):
    registry_cls = JWTAccessTokenClaimsValidator
    def validate(self, *, now: int | Callable[[], int] | None = None, leeway: int = 0) -> None: ...  # type: ignore[override]
