from typing import Any

from stripe.api_resources.abstract import (
    CreateableAPIResource as CreateableAPIResource,
    ListableAPIResource as ListableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
    custom_method as custom_method,
)

class PaymentIntent(CreateableAPIResource, ListableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def cancel(self, idempotency_key: Any | None = ..., **params): ...
    def capture(self, idempotency_key: Any | None = ..., **params): ...
    def confirm(self, idempotency_key: Any | None = ..., **params): ...
