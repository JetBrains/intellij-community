from typing import Any

from stripe import api_requestor as api_requestor
from stripe.api_resources.abstract import (
    CreateableAPIResource as CreateableAPIResource,
    DeletableAPIResource as DeletableAPIResource,
    ListableAPIResource as ListableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
    custom_method as custom_method,
)

class Invoice(CreateableAPIResource, DeletableAPIResource, ListableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def finalize_invoice(self, idempotency_key: Any | None = ..., **params): ...
    def mark_uncollectible(self, idempotency_key: Any | None = ..., **params): ...
    def pay(self, idempotency_key: Any | None = ..., **params): ...
    def send_invoice(self, idempotency_key: Any | None = ..., **params): ...
    def void_invoice(self, idempotency_key: Any | None = ..., **params): ...
    @classmethod
    def upcoming(
        cls, api_key: Any | None = ..., stripe_version: Any | None = ..., stripe_account: Any | None = ..., **params
    ): ...
