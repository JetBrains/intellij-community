from typing import Any
from typing_extensions import Literal, Self

from stripe.api_resources.abstract import CreateableAPIResource, DeletableAPIResource, ListableAPIResource

class TestClock(CreateableAPIResource, DeletableAPIResource, ListableAPIResource):
    OBJECT_NAME: Literal["test_helpers.test_clock"]

    @classmethod
    def advance(cls, idempotency_key: str | None = None, **params: Any) -> Self: ...
