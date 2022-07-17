from typing import Any

from stripe.api_resources.abstract import APIResource as APIResource
from stripe.api_resources.customer import Customer as Customer

class TaxId(APIResource):
    OBJECT_NAME: str
    def instance_url(self): ...
    @classmethod
    def retrieve(cls, id, api_key: Any | None = ..., **params) -> None: ...
