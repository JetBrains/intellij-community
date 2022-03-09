from typing import Any, Protocol

from docutils.utils import Reporter

class _LanguageModule(Protocol):
    labels: dict[str, str]
    author_separators: list[str]
    bibliographic_fields: list[str]

class LanguageImporter:
    def __call__(self, language_code: str, reporter: Reporter | None = ...) -> _LanguageModule: ...
    def __getattr__(self, __name: str) -> Any: ...  # incomplete

get_language: LanguageImporter
