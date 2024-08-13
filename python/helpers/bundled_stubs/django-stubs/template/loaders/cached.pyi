from collections.abc import Sequence
from typing import Any

from django.template.base import Origin
from django.template.engine import Engine

from .base import Loader as BaseLoader

class Loader(BaseLoader):
    template_cache: dict[str, Any]
    loaders: list[BaseLoader]
    def __init__(self, engine: Engine, loaders: Sequence[Any]) -> None: ...
    def get_contents(self, origin: Origin) -> str: ...
    def cache_key(self, template_name: str, skip: list[Origin] | None = ...) -> str: ...
    def generate_hash(self, values: list[str]) -> str: ...
