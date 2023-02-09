from _typeshed import Self

from stripe.api_resources.abstract.api_resource import APIResource as APIResource

class UpdateableAPIResource(APIResource):
    @classmethod
    def modify(cls: type[Self], sid: str, **params) -> Self: ...
    def save(self: Self, idempotency_key: str | None = ...) -> Self: ...
