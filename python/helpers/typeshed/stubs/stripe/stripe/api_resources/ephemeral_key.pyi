from typing import Any

from stripe import api_requestor as api_requestor
from stripe.api_resources.abstract import DeletableAPIResource as DeletableAPIResource

class EphemeralKey(DeletableAPIResource):
    OBJECT_NAME: str
    @classmethod
    def create(
        cls,
        api_key: Any | None = ...,
        idempotency_key: str | None = ...,
        stripe_version: Any | None = ...,
        stripe_account: Any | None = ...,
        **params,
    ): ...
