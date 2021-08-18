from typing import Any

from stripe import api_requestor as api_requestor
from stripe.api_resources.abstract import (
    CreateableAPIResource as CreateableAPIResource,
    ListableAPIResource as ListableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
    custom_method as custom_method,
)

class Quote(CreateableAPIResource, ListableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def accept(self, idempotency_key: Any | None = ..., **params): ...
    def cancel(self, idempotency_key: Any | None = ..., **params): ...
    def finalize_quote(self, idempotency_key: Any | None = ..., **params): ...
    def list_line_items(self, idempotency_key: Any | None = ..., **params): ...
    def pdf(
        self,
        api_key: Any | None = ...,
        api_version: Any | None = ...,
        stripe_version: Any | None = ...,
        stripe_account: Any | None = ...,
        **params,
    ): ...
