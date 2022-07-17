from typing import Any, Iterable, Text

ATTRIBUTES: dict[str, int]
COLORS: dict[str, int]
HIGHLIGHTS: dict[str, int]
RESET: str

def colored(text: Text, color: Text | None = ..., on_color: Text | None = ..., attrs: Iterable[Text] | None = ...) -> Text: ...
def cprint(
    text: Text, color: Text | None = ..., on_color: Text | None = ..., attrs: Iterable[Text] | None = ..., **kwargs: Any
) -> None: ...
