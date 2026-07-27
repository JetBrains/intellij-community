from _typeshed import Incomplete
from collections.abc import Generator
from contextlib import contextmanager
from typing_extensions import Never

from authlib.oauth2 import ResourceProtector as _ResourceProtector

class ResourceProtector(_ResourceProtector):
    def raise_error_response(self, error) -> Never: ...
    def acquire_token(self, scopes=None, **kwargs): ...
    @contextmanager
    def acquire(self, scopes=None) -> Generator[Incomplete]: ...
    def __call__(self, scopes=None, optional=False, **kwargs): ...

current_token: Incomplete
