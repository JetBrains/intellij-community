from _typeshed import Incomplete
from collections.abc import Callable
from logging import Logger
from typing import Any, Final

log: Logger
SIGNATURE_HMAC_SHA1: Final[str]
SIGNATURE_HMAC_SHA256: Final[str]
SIGNATURE_HMAC_SHA512: Final[str]
SIGNATURE_HMAC: Final[str]
SIGNATURE_RSA_SHA1: Final[str]
SIGNATURE_RSA_SHA256: Final[str]
SIGNATURE_RSA_SHA512: Final[str]
SIGNATURE_RSA: Final[str]
SIGNATURE_PLAINTEXT: Final[str]
SIGNATURE_METHODS: Final[tuple[str, str, str, str, str, str, str]]
SIGNATURE_TYPE_AUTH_HEADER: Final[str]
SIGNATURE_TYPE_QUERY: Final[str]
SIGNATURE_TYPE_BODY: Final[str]
CONTENT_TYPE_FORM_URLENCODED: Final[str]

class Client:
    SIGNATURE_METHODS: dict[str, Callable[[str, Incomplete], str]]
    @classmethod
    def register_signature_method(cls, method_name, method_callback) -> None: ...
    client_key: Any
    client_secret: Any
    resource_owner_key: Any
    resource_owner_secret: Any
    signature_method: Any
    signature_type: Any
    callback_uri: Any
    rsa_key: Any
    verifier: Any
    realm: Any
    encoding: Any
    decoding: Any
    nonce: Any
    timestamp: Any
    def __init__(
        self,
        client_key: str,
        client_secret: str | None = None,
        resource_owner_key=None,
        resource_owner_secret=None,
        callback_uri=None,
        signature_method="HMAC-SHA1",
        signature_type="AUTH_HEADER",
        rsa_key=None,
        verifier=None,
        realm=None,
        encoding: str = "utf-8",
        decoding=None,
        nonce=None,
        timestamp=None,
    ): ...
    def get_oauth_signature(self, request): ...
    def get_oauth_params(self, request): ...
    def sign(self, uri, http_method: str = "GET", body: str | None = None, headers: dict[str, str] | None = None, realm=None): ...
