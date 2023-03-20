from _typeshed import Self
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
    def finalize_invoice(self: Self, idempotency_key: str | None = ..., **params) -> Self: ...
    def mark_uncollectible(self: Self, idempotency_key: str | None = ..., **params) -> Self: ...
    def pay(self: Self, idempotency_key: str | None = ..., **params) -> Self: ...
    def send_invoice(self: Self, idempotency_key: str | None = ..., **params) -> Self: ...
    def void_invoice(self: Self, idempotency_key: str | None = ..., **params) -> Self: ...
    @classmethod
    def upcoming(
        cls, api_key: Any | None = ..., stripe_version: Any | None = ..., stripe_account: Any | None = ..., **params
    ) -> Invoice: ...
