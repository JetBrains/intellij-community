from typing import Any, NoReturn

from stripe.api_resources.abstract import (
    DeletableAPIResource as DeletableAPIResource,
    UpdateableAPIResource as UpdateableAPIResource,
)
from stripe.api_resources.customer import Customer as Customer

class AlipayAccount(DeletableAPIResource, UpdateableAPIResource):
    OBJECT_NAME: str
    def instance_url(self): ...
    @classmethod
    def modify(cls, customer, id, **params): ...
    @classmethod
    def retrieve(
        cls, id, api_key: Any | None = ..., stripe_version: Any | None = ..., stripe_account: Any | None = ..., **params
    ) -> NoReturn: ...
