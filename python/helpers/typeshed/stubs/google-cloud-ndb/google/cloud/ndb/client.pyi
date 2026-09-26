from _typeshed import Incomplete
from collections.abc import Callable, Generator
from contextlib import contextmanager
from typing import ClassVar

from google.cloud.ndb import context as context_module, key

DATASTORE_API_HOST: str

class Client:
    SCOPE: ClassVar[tuple[str, ...]]
    namespace: str | None
    host: str
    client_info: Incomplete
    secure: bool
    stub: Incomplete
    database: str | None
    def __init__(
        self,
        project: str | None = None,
        namespace: str | None = None,
        credentials=None,
        client_options=None,
        database: str | None = None,
    ) -> None: ...
    @contextmanager
    def context(
        self,
        namespace=...,
        cache_policy: Callable[[key.Key], bool] | None = None,
        global_cache=None,
        global_cache_policy: Callable[[key.Key], bool] | None = None,
        global_cache_timeout_policy: Callable[[key.Key], int] | None = None,
        legacy_data: bool = True,
    ) -> Generator[context_module.Context]: ...
