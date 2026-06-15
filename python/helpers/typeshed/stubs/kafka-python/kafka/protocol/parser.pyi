from _typeshed import Incomplete
from collections import deque
from logging import Logger

log: Logger

class KafkaProtocol:
    in_flight_requests: deque[tuple[int, Incomplete]]
    bytes_to_send: list[bytes]
    def __init__(
        self, client_id: str | None = None, api_version: tuple[int, int, int] | None = None, max_frame_size: int = 100000000
    ) -> None: ...
    def send_request(self, request, correlation_id: int | None = None) -> int: ...
    def send_bytes(self) -> bytes: ...
    def receive_bytes(self, data: bytes) -> list[Incomplete]: ...
