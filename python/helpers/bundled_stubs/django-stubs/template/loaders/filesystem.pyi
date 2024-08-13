from collections.abc import Iterator
from pathlib import Path

from django.template.base import Origin
from django.template.engine import Engine

from .base import Loader as BaseLoader

class Loader(BaseLoader):
    dirs: list[str | Path] | None
    def __init__(self, engine: Engine, dirs: list[str | Path] | None = ...) -> None: ...
    def get_dirs(self) -> list[str | Path]: ...
    def get_contents(self, origin: Origin) -> str: ...
    def get_template_sources(self, template_name: str) -> Iterator[Origin]: ...
