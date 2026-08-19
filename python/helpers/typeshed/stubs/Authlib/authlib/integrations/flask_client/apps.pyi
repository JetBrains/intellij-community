from _typeshed import Incomplete
from typing import TypeAlias

from ..base_client import BaseApp, OAuth1Mixin, OAuth2Mixin, OpenIDMixin
from ..requests_client import OAuth1Session, OAuth2Session

_Response: TypeAlias = Incomplete  # actual type is werkzeug.wrappers.Response

class FlaskAppMixin:
    @property
    def token(self): ...
    @token.setter
    def token(self, token): ...

    def save_authorize_data(self, **kwargs) -> None: ...
    def authorize_redirect(self, redirect_uri=None, **kwargs): ...

class FlaskOAuth1App(FlaskAppMixin, OAuth1Mixin, BaseApp):
    client_cls = OAuth1Session
    def authorize_access_token(self, **kwargs): ...

class FlaskOAuth2App(FlaskAppMixin, OAuth2Mixin, OpenIDMixin, BaseApp):
    client_cls = OAuth2Session
    def logout_redirect(
        self, post_logout_redirect_uri=None, id_token_hint=None, *, state=None, client_id=None, logout_hint=None, ui_locales=None
    ) -> _Response: ...
    def validate_logout_response(self): ...
    def authorize_access_token(self, **kwargs): ...
