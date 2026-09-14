from _typeshed import Incomplete
from collections.abc import Callable

from authlib.oauth1.rfc5849.authorization_server import AuthorizationServer

def register_temporary_credential_hooks(
    authorization_server: AuthorizationServer, cache, key_prefix: str = "temporary_credential:"
) -> None: ...
def create_exists_nonce_func(
    cache, key_prefix: str = "nonce:", expires=86400
) -> Callable[[Incomplete, Incomplete, Incomplete, Incomplete], Incomplete]: ...
def register_nonce_hooks(authorization_server: AuthorizationServer, cache, key_prefix: str = "nonce:", expires=86400) -> None: ...
