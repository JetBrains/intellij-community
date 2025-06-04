import zipfile
from collections.abc import Sequence
from io import BufferedReader
from typing import Callable, Literal

from django.apps.config import AppConfig
from django.core.management.base import BaseCommand
from django.core.serializers.base import DeserializedObject
from django.db.backends.base.base import BaseDatabaseWrapper
from django.db.models.base import Model
from django.utils.functional import cached_property
from typing_extensions import TypeAlias

has_bz2: bool
has_lzma: bool

READ_STDIN: str
_ReadBinaryMode: TypeAlias = Literal["r", "rb"]

class Command(BaseCommand):
    missing_args_message: str
    ignore: bool
    using: str
    app_label: str
    verbosity: int
    excluded_models: set[type[Model]]
    excluded_apps: set[AppConfig]
    format: str
    fixture_count: int
    loaded_object_count: int
    fixture_object_count: int
    models: set[type[Model]]
    serialization_formats: list[str]
    objs_with_deferred_fields: list[DeserializedObject]
    @cached_property
    def compression_formats(self) -> dict[str | None, tuple[Callable[[str, _ReadBinaryMode], BufferedReader]]]: ...
    def reset_sequences(self, connection: BaseDatabaseWrapper, models: set[type[Model]]) -> None: ...
    def loaddata(self, fixture_labels: Sequence[str]) -> None: ...
    def save_obj(self, obj: DeserializedObject) -> bool: ...
    def load_label(self, fixture_label: str) -> None: ...
    def get_fixture_name_and_dirs(self, fixture_name: str) -> tuple[str, list[str]]: ...
    def get_targets(self, fixture_name: str, ser_fmt: str | None, cmp_fmt: str | None) -> set[str]: ...
    def find_fixture_files_in_dir(
        self, fixture_dir: str, fixture_name: str, targets: set[str]
    ) -> list[tuple[str, str, str]]: ...
    def find_fixtures(self, fixture_label: str) -> list[tuple[str, str | None, str | None]]: ...
    @cached_property
    def fixture_dirs(self) -> list[str]: ...
    def parse_name(self, fixture_name: str) -> tuple[str, str | None, str | None]: ...

class SingleZipReader(zipfile.ZipFile):
    # Incompatible override
    #     zipfile.ZipFile.read(
    #         self,
    #         name: typing.Union[typing.Text, zipfile.ZipInfo],
    #         pwd: Optional[bytes] = ...,
    #     ) -> bytes: ...
    def read(self) -> bytes: ...  # type: ignore[override]

def humanize(dirname: str) -> str: ...
