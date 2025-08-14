from collections.abc import Callable, Sequence
from typing import Any

from django.template.base import Origin
from django.template.library import Library
from django.template.loaders.base import Loader
from django.utils.functional import cached_property
from django.utils.safestring import SafeString
from typing_extensions import TypeAlias

from .base import Template

_Loader: TypeAlias = Any

class Engine:
    default_builtins: Any
    dirs: list[str]
    app_dirs: bool
    autoescape: bool
    context_processors: list[str] | tuple[str, ...]
    debug: bool
    loaders: Sequence[_Loader]
    string_if_invalid: str
    file_charset: str
    libraries: dict[str, str]
    template_libraries: dict[str, Library]
    builtins: list[str]
    template_builtins: list[Library]
    def __init__(
        self,
        dirs: list[str] | None = None,
        app_dirs: bool = False,
        context_processors: list[str] | tuple[str, ...] | None = None,
        debug: bool = False,
        loaders: Sequence[_Loader] | None = None,
        string_if_invalid: str = "",
        file_charset: str = "utf-8",
        libraries: dict[str, str] | None = None,
        builtins: list[str] | None = None,
        autoescape: bool = True,
    ) -> None: ...
    @staticmethod
    def get_default() -> Engine: ...
    @cached_property
    def template_context_processors(self) -> Sequence[Callable]: ...
    def get_template_builtins(self, builtins: list[str]) -> list[Library]: ...
    def get_template_libraries(self, libraries: dict[str, str]) -> dict[str, Library]: ...
    @cached_property
    def template_loaders(self) -> list[Loader]: ...
    def get_template_loaders(self, template_loaders: Sequence[_Loader]) -> list[Loader]: ...
    def find_template_loader(self, loader: _Loader) -> Loader: ...
    def find_template(
        self, name: str, dirs: None = None, skip: list[Origin] | None = None
    ) -> tuple[Template, Origin]: ...
    def from_string(self, template_code: str) -> Template: ...
    def get_template(self, template_name: str) -> Template: ...
    def render_to_string(self, template_name: str, context: dict[str, Any] | None = None) -> SafeString: ...
    def select_template(self, template_name_list: list[str]) -> Template: ...
