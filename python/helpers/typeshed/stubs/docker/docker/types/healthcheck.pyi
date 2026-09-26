from typing import Any

from .base import DictType

class Healthcheck(DictType[Any]):
    def __init__(
        self,
        *,
        test: str | list[str] | None = None,
        Test: str | list[str] | None = None,
        interval: int | None = None,
        Interval: int | None = None,
        timeout: int | None = None,
        Timeout: int | None = None,
        retries: int | None = None,
        Retries: int | None = None,
        start_period: int | None = None,
        StartPeriod: int | None = None,
    ) -> None: ...

    @property
    def test(self) -> list[str] | None: ...
    @test.setter
    def test(self, value: str | list[str] | None) -> None: ...

    @property
    def interval(self) -> int | None: ...
    @interval.setter
    def interval(self, value: int | None) -> None: ...

    @property
    def timeout(self) -> int | None: ...
    @timeout.setter
    def timeout(self, value: int | None) -> None: ...

    @property
    def retries(self) -> int | None: ...
    @retries.setter
    def retries(self, value: int | None) -> None: ...

    @property
    def start_period(self) -> int | None: ...
    @start_period.setter
    def start_period(self, value: int | None) -> None: ...
