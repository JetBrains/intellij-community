from typing import Any

from stripe.api_resources.abstract import (
    ListableAPIResource as ListableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
    custom_method as custom_method,
)

class Authorization(ListableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def approve(self, idempotency_key: Any | None = ..., **params): ...
    def decline(self, idempotency_key: Any | None = ..., **params): ...
