from _typeshed import Incomplete
from typing_extensions import Never

from authlib.oauth2.rfc7009 import RevocationEndpoint

class JWTRevocationEndpoint(RevocationEndpoint):
    issuer: Incomplete
    def __init__(self, issuer, server=None, *args, **kwargs) -> None: ...
    def authenticate_token(self, request, client) -> Never: ...
    def get_jwks(self): ...
