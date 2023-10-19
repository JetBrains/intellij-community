from collections.abc import Callable, Iterable, Sequence
from logging import Logger
from typing import Any, TypeVar
from typing_extensions import ParamSpec, TypeAlias

import flask
from flask_sqlalchemy import SQLAlchemy

_T = TypeVar("_T")
_P = ParamSpec("_P")
_ConfigureCallback: TypeAlias = Callable[[Config], Config]

alembic_version: tuple[int, int, int]
log: Logger

class Config:  # should inherit from alembic.config.Config which is not possible yet
    template_directory: str | None
    def __init__(self, *args, **kwargs) -> None: ...
    def get_template_directory(self) -> str: ...

class Migrate:
    configure_callbacks: list[_ConfigureCallback]
    db: SQLAlchemy | None
    directory: str
    alembic_ctx_kwargs: dict[str, Any]
    def __init__(
        self,
        app: flask.Flask | None = None,
        db: SQLAlchemy | None = None,
        directory: str = "migrations",
        command: str = "db",
        compare_type: bool = True,
        render_as_batch: bool = True,
        **kwargs,
    ) -> None: ...
    def init_app(
        self,
        app: flask.Flask,
        db: SQLAlchemy | None = None,
        directory: str | None = None,
        command: str | None = None,
        compare_type: bool | None = None,
        render_as_batch: bool | None = None,
        **kwargs,
    ) -> None: ...
    def configure(self, f: _ConfigureCallback) -> _ConfigureCallback: ...
    def call_configure_callbacks(self, config: Config): ...
    def get_config(
        self, directory: str | None = None, x_arg: str | Sequence[str] | None = None, opts: Iterable[str] | None = None
    ): ...

def catch_errors(f: Callable[_P, _T]) -> Callable[_P, _T]: ...
def list_templates() -> None: ...
def init(directory: str | None = None, multidb: bool = False, template: str | None = None, package: bool = False) -> None: ...
def revision(
    directory: str | None = None,
    message: str | None = None,
    autogenerate: bool = False,
    sql: bool = False,
    head: str = "head",
    splice: bool = False,
    branch_label: str | None = None,
    version_path: str | None = None,
    rev_id: str | None = None,
) -> None: ...
def migrate(
    directory: str | None = None,
    message: str | None = None,
    sql: bool = False,
    head: str = "head",
    splice: bool = False,
    branch_label: str | None = None,
    version_path: str | None = None,
    rev_id: str | None = None,
    x_arg: str | Sequence[str] | None = None,
) -> None: ...
def edit(directory: str | None = None, revision: str = "current") -> None: ...
def merge(
    directory: str | None = None,
    revisions: str = "",
    message: str | None = None,
    branch_label: str | None = None,
    rev_id: str | None = None,
) -> None: ...
def upgrade(
    directory: str | None = None,
    revision: str = "head",
    sql: bool = False,
    tag: str | None = None,
    x_arg: str | Sequence[str] | None = None,
) -> None: ...
def downgrade(
    directory: str | None = None,
    revision: str = "-1",
    sql: bool = False,
    tag: str | None = None,
    x_arg: str | Sequence[str] | None = None,
) -> None: ...
def show(directory: str | None = None, revision: str = "head") -> None: ...
def history(
    directory: str | None = None, rev_range: str | None = None, verbose: bool = False, indicate_current: bool = False
) -> None: ...
def heads(directory: str | None = None, verbose: bool = False, resolve_dependencies: bool = False) -> None: ...
def branches(directory: str | None = None, verbose: bool = False) -> None: ...
def current(directory: str | None = None, verbose: bool = False) -> None: ...
def stamp(directory: str | None = None, revision: str = "head", sql: bool = False, tag: str | None = None) -> None: ...
