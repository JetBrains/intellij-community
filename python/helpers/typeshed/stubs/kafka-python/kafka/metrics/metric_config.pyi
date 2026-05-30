from _typeshed import Incomplete

class MetricConfig:
    quota: Incomplete
    event_window: Incomplete
    time_window_ms: Incomplete
    tags: Incomplete
    def __init__(self, quota=None, samples: int = 2, event_window=..., time_window_ms=30000, tags=None) -> None: ...

    @property
    def samples(self): ...
    @samples.setter
    def samples(self, value) -> None: ...
