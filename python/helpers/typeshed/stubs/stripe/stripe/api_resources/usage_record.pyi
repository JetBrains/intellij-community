from typing import Any

from stripe import api_requestor as api_requestor
from stripe.api_resources.abstract.api_resource import APIResource as APIResource

class UsageRecord(APIResource):
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
