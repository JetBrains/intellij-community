import logging
from collections.abc import Callable
from typing import Any, TypeAlias, TypeVar

import click

_AnyCallable: TypeAlias = Callable[..., Any]
_FC = TypeVar("_FC", bound=_AnyCallable | click.Command)

def simple_verbosity_option(logger: logging.Logger | str | None = None, *names: str, **kwargs: Any) -> Callable[[_FC], _FC]: ...
