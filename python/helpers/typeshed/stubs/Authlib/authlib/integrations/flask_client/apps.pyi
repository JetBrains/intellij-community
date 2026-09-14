from _typeshed import Incomplete

from werkzeug.wrappers import Response

from ..base_client import BaseApp, OAuth1Mixin, OAuth2Mixin, OpenIDMixin
from ..requests_client import OAuth1Session, OAuth2Session

class FlaskAppMixin:
    @property
    def token(self): ...
    @token.setter
    def token(self, token): ...

    def save_authorize_data(self, **kwargs) -> None: ...
    def authorize_redirect(self, redirect_uri=None, **kwargs) -> Response: ...

class FlaskOAuth1App(FlaskAppMixin, OAuth1Mixin, BaseApp):
    client_cls = OAuth1Session
    def authorize_access_token(self, **kwargs) -> dict[Incomplete, Incomplete]: ...

class FlaskOAuth2App(FlaskAppMixin, OAuth2Mixin, OpenIDMixin, BaseApp):
    client_cls = OAuth2Session
    def logout_redirect(
        self, post_logout_redirect_uri=None, id_token_hint=None, *, state=None, client_id=None, logout_hint=None, ui_locales=None
    ) -> Response: ...
    def validate_logout_response(self): ...
    def authorize_access_token(self, **kwargs) -> dict[Incomplete, Incomplete]: ...
