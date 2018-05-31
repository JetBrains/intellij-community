from google.protobuf.message import (
    Message,
)
from google.protobuf.internal import well_known_types

from typing import (
    Optional,
)


class Duration(Message, well_known_types.Duration):
    seconds = ...  # type: int
    nanos = ...  # type: int

    def __init__(self,
                 seconds: Optional[int] = ...,
                 nanos: Optional[int] = ...,
                 ) -> None: ...

    @classmethod
    def FromString(cls, s: str) -> Duration: ...
