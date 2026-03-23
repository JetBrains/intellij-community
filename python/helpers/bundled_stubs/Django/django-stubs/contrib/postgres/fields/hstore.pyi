from typing import Any, ClassVar

from django.contrib.postgres.fields.array import ArrayField
from django.contrib.postgres.utils import CheckPostgresInstalledMixin
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models import Field, TextField, Transform
from django.db.models.fields.mixins import CheckFieldDefaultMixin
from django.db.models.sql.compiler import SQLCompiler, _AsSqlType
from typing_extensions import override

class HStoreField(CheckPostgresInstalledMixin, CheckFieldDefaultMixin, Field):
    @override
    def get_transform(self, name: str) -> Any: ...
    @override
    def formfield(self, **kwargs: Any) -> Any: ...  # type: ignore[override]

class KeyTransform(Transform):
    output_field: ClassVar[TextField]

    def __init__(self, key_name: str, *args: Any, **kwargs: Any) -> None: ...
    @override
    def as_sql(self, compiler: SQLCompiler, connection: BaseDatabaseWrapper) -> _AsSqlType: ...  # type: ignore[override]

class KeyTransformFactory:
    def __init__(self, key_name: str) -> None: ...
    def __call__(self, *args: Any, **kwargs: Any) -> KeyTransform: ...

class KeysTransform(Transform):
    output_field: ClassVar[ArrayField]

class ValuesTransform(Transform):
    output_field: ClassVar[ArrayField]

__all__ = ["HStoreField"]
