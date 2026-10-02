from typing import NoReturn

from django.db.models.base import ModelBase
from django.http.request import HttpRequest

class RequestSite:
    name: str
    domain: str
    def __init__(self, request: HttpRequest) -> None: ...
    def save(self, force_insert: bool | tuple[ModelBase, ...] = False, force_update: bool = False) -> NoReturn: ...
    def delete(self) -> NoReturn: ...
