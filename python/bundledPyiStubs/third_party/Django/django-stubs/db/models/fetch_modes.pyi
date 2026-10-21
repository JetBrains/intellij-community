from typing import Any, NoReturn

from django.db.models import Model
from typing_extensions import override

class FetchMode:
    track_peers: bool

    def fetch(self, fetcher: Any, instance: Model) -> None: ...

class FetchOne(FetchMode):
    @override
    def fetch(self, fetcher: Any, instance: Model) -> None: ...
    @override
    def __reduce__(self) -> str: ...

FETCH_ONE: FetchOne

class FetchPeers(FetchMode):
    track_peers: bool

    @override
    def fetch(self, fetcher: Any, instance: Model) -> None: ...
    @override
    def __reduce__(self) -> str: ...

FETCH_PEERS: FetchPeers

class FetchRaise(FetchMode):
    @override
    def fetch(self, fetcher: Any, instance: Model) -> NoReturn: ...
    @override
    def __reduce__(self) -> str: ...

FETCH_RAISE: FetchRaise
