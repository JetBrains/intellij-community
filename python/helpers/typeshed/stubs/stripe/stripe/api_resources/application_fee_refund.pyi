from _typeshed import Self
from typing import Any, NoReturn

from stripe.api_resources import ApplicationFee as ApplicationFee
from stripe.api_resources.abstract import UpdateableAPIResource as UpdateableAPIResource

class ApplicationFeeRefund(UpdateableAPIResource):
    OBJECT_NAME: str
    @classmethod
    def modify(cls: type[Self], fee, sid: str, **params) -> Self: ...  # type: ignore[override]
    def instance_url(self) -> str: ...
    @classmethod
    def retrieve(cls, id, api_key: Any | None = ..., **params) -> NoReturn: ...
