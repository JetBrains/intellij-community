from typing import Any

from stripe.api_resources.abstract import (
    CreateableAPIResource as CreateableAPIResource,
    ListableAPIResource as ListableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
    custom_method as custom_method,
)

class Order(CreateableAPIResource, ListableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def pay(self, idempotency_key: Any | None = ..., **params): ...
    def return_order(self, idempotency_key: Any | None = ..., **params): ...
