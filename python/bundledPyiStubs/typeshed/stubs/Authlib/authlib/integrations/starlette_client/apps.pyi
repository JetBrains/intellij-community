from _typeshed import Incomplete
from typing import TypeAlias

from ..base_client import BaseApp
from ..base_client.async_app import AsyncOAuth1Mixin, AsyncOAuth2Mixin
from ..base_client.async_openid import AsyncOpenIDMixin
from ..httpx_client import AsyncOAuth1Client, AsyncOAuth2Client

_RedirectResponse: TypeAlias = Incomplete  # actual type is starlette.responses.RedirectResponse

class StarletteAppMixin:
    async def save_authorize_data(self, request, **kwargs) -> None: ...
    async def authorize_redirect(self, request, redirect_uri=None, **kwargs) -> _RedirectResponse: ...

class StarletteOAuth1App(StarletteAppMixin, AsyncOAuth1Mixin, BaseApp):
    client_cls = AsyncOAuth1Client
    async def authorize_access_token(self, request, **kwargs): ...

class StarletteOAuth2App(StarletteAppMixin, AsyncOAuth2Mixin, AsyncOpenIDMixin, BaseApp):
    client_cls = AsyncOAuth2Client
    async def logout_redirect(
        self,
        request,
        post_logout_redirect_uri=None,
        id_token_hint=None,
        *,
        state=None,
        client_id=None,
        logout_hint=None,
        ui_locales=None,
    ) -> _RedirectResponse: ...
    async def validate_logout_response(self, request): ...
    async def authorize_access_token(self, request, **kwargs): ...
