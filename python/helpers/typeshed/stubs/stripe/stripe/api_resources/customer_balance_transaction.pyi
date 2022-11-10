from typing import Any, NoReturn

from stripe.api_resources.abstract import APIResource as APIResource
from stripe.api_resources.customer import Customer as Customer

class CustomerBalanceTransaction(APIResource):
    OBJECT_NAME: str
    def instance_url(self) -> str: ...
    @classmethod
    def retrieve(cls, id, api_key: Any | None = ..., **params) -> NoReturn: ...
