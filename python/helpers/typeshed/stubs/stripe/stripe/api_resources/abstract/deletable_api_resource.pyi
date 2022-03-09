from _typeshed import Self

from stripe.api_resources.abstract.api_resource import APIResource as APIResource

class DeletableAPIResource(APIResource):
    @classmethod
    def delete(cls: type[Self], sid: str = ..., **params) -> Self: ...
