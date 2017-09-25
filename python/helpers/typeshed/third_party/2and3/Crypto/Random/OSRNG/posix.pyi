from typing import Any, Optional
from .rng_base import BaseRNG

class DevURandomRNG(BaseRNG):
    name = ...  # type: str
    def __init__(self, devname: Optional[Any] = ...) -> None: ...
