from typing import Any, NoReturn

from stripe.api_resources.abstract import UpdateableAPIResource as UpdateableAPIResource
from stripe.api_resources.account import Account as Account

class Person(UpdateableAPIResource):
    OBJECT_NAME: str
    def instance_url(self) -> str: ...
    @classmethod
    def modify(cls, sid, **params) -> NoReturn: ...
    @classmethod
    def retrieve(cls, id, api_key: Any | None = ..., **params) -> NoReturn: ...
