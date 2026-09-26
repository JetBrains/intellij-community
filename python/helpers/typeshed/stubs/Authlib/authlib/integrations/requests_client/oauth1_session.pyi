from typing_extensions import Never

from authlib.oauth1 import ClientAuth
from authlib.oauth1.client import OAuth1Client
from requests import PreparedRequest, Response, Session
from requests.auth import AuthBase

class OAuth1Auth(AuthBase, ClientAuth):
    def __call__(self, req: PreparedRequest) -> PreparedRequest: ...

# Incompatible definitions of "auth" in the base classes
class OAuth1Session(OAuth1Client, Session):  # type: ignore[misc]  # pyrefly: ignore [inconsistent-inheritance]
    auth_class = OAuth1Auth
    def __init__(
        self,
        client_id,
        client_secret=None,
        token=None,
        token_secret=None,
        redirect_uri=None,
        rsa_key=None,
        verifier=None,
        signature_method="HMAC-SHA1",
        signature_type="HEADER",
        force_include_body=False,
        **kwargs,
    ) -> None: ...
    def rebuild_auth(self, prepared_request: PreparedRequest, response: Response) -> None: ...
    @staticmethod
    def handle_error(error_type: str | None, error_description: str | None) -> Never: ...
