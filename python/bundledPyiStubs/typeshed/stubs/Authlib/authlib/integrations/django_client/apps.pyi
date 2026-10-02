from _typeshed import Incomplete
from typing import TypeAlias

from ..base_client import BaseApp, OAuth1Mixin, OAuth2Mixin, OpenIDMixin
from ..requests_client import OAuth1Session, OAuth2Session

_HttpResponseRedirect: TypeAlias = Incomplete  # actual type is django.http.response.HttpResponseRedirect

class DjangoAppMixin:
    def save_authorize_data(self, request, **kwargs) -> None: ...
    def authorize_redirect(self, request, redirect_uri=None, **kwargs): ...

class DjangoOAuth1App(DjangoAppMixin, OAuth1Mixin, BaseApp):
    client_cls = OAuth1Session
    def authorize_access_token(self, request, **kwargs): ...

class DjangoOAuth2App(DjangoAppMixin, OAuth2Mixin, OpenIDMixin, BaseApp):
    client_cls = OAuth2Session
    def logout_redirect(
        self,
        request,
        post_logout_redirect_uri=None,
        id_token_hint=None,
        *,
        state=None,
        client_id=None,
        logout_hint=None,
        ui_locales=None,
    ) -> _HttpResponseRedirect: ...
    def validate_logout_response(self, request): ...
    def authorize_access_token(self, request, **kwargs): ...
