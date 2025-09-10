from _typeshed import Incomplete
from logging import Logger

from authlib.oidc.core import OpenIDImplicitGrant

log: Logger

class OpenIDHybridGrant(OpenIDImplicitGrant):
    AUTHORIZATION_CODE_LENGTH: int
    RESPONSE_TYPES: Incomplete
    GRANT_TYPE: str
    DEFAULT_RESPONSE_MODE: str
    def generate_authorization_code(self): ...
    def save_authorization_code(self, code, request) -> None: ...
    def validate_authorization_request(self): ...
    def create_granted_params(self, grant_user): ...
