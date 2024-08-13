from typing import Any

from django.contrib.messages.storage.base import BaseStorage
from django.http.request import HttpRequest
from django.utils.functional import _StrOrPromise

class MessageFailure(Exception): ...

def add_message(
    request: HttpRequest | None,
    level: int,
    message: _StrOrPromise,
    extra_tags: str = ...,
    fail_silently: bool | str = ...,
) -> None: ...
def get_messages(request: HttpRequest) -> list[Any] | BaseStorage: ...
def get_level(request: HttpRequest) -> int: ...
def set_level(request: HttpRequest, level: int) -> bool: ...
def debug(
    request: HttpRequest,
    message: _StrOrPromise,
    extra_tags: str = ...,
    fail_silently: bool | str = ...,
) -> None: ...
def info(
    request: HttpRequest,
    message: _StrOrPromise,
    extra_tags: str = ...,
    fail_silently: bool | str = ...,
) -> None: ...
def success(
    request: HttpRequest,
    message: _StrOrPromise,
    extra_tags: str = ...,
    fail_silently: bool | str = ...,
) -> None: ...
def warning(
    request: HttpRequest,
    message: _StrOrPromise,
    extra_tags: str = ...,
    fail_silently: bool | str = ...,
) -> None: ...
def error(
    request: HttpRequest,
    message: _StrOrPromise,
    extra_tags: str = ...,
    fail_silently: bool | str = ...,
) -> None: ...
