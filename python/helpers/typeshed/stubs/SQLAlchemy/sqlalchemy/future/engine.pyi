from typing import Any, overload
from typing_extensions import Literal

from ..engine import Connection as _LegacyConnection, Engine as _LegacyEngine
from ..engine.base import OptionEngineMixin
from ..engine.mock import MockConnection
from ..engine.url import URL

NO_OPTIONS: Any

@overload
def create_engine(url: URL | str, *, strategy: Literal["mock"], **kwargs) -> MockConnection: ...  # type: ignore[misc]
@overload
def create_engine(
    url: URL | str, *, module: Any | None = ..., enable_from_linting: bool = ..., future: bool = ..., **kwargs
) -> Engine: ...

class Connection(_LegacyConnection):
    def begin(self): ...
    def begin_nested(self): ...
    def commit(self) -> None: ...
    def rollback(self) -> None: ...
    def close(self) -> None: ...
    def execute(self, statement, parameters: Any | None = ..., execution_options: Any | None = ...): ...  # type: ignore[override]
    def scalar(self, statement, parameters: Any | None = ..., execution_options: Any | None = ...): ...  # type: ignore[override]

class Engine(_LegacyEngine):
    transaction: Any
    run_callable: Any
    execute: Any
    scalar: Any
    table_names: Any
    has_table: Any
    def begin(self) -> None: ...  # type: ignore[override]
    def connect(self): ...

class OptionEngine(OptionEngineMixin, Engine): ...  # type: ignore[misc]
