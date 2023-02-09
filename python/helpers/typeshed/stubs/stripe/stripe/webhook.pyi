from typing_extensions import Literal

from stripe import Event, error as error

class Webhook:
    DEFAULT_TOLERANCE: int
    @staticmethod
    def construct_event(
        payload: bytes | str, sig_header: str, secret: str, tolerance: int = ..., api_key: str | None = ...
    ) -> Event: ...

class WebhookSignature:
    EXPECTED_SCHEME: str
    @classmethod
    def verify_header(cls, payload: bytes | str, header: str, secret: str, tolerance: int | None = ...) -> Literal[True]: ...
    @staticmethod
    def _compute_signature(payload: str, secret: str) -> str: ...
