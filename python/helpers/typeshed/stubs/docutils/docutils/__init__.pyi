from _typeshed import Self
from typing import Any, ClassVar, NamedTuple

__docformat__: str
__version__: str

class _VersionInfo(NamedTuple):
    major: int
    minor: int
    micro: int
    releaselevel: str
    serial: int
    release: bool

class VersionInfo(_VersionInfo):
    def __new__(
        cls: type[Self],
        major: int = ...,
        minor: int = ...,
        micro: int = ...,
        releaselevel: str = ...,
        serial: int = ...,
        release: bool = ...,
    ) -> Self: ...

__version_info__: VersionInfo
__version_details__: str

class ApplicationError(Exception): ...
class DataError(ApplicationError): ...

class SettingsSpec:
    settings_spec: ClassVar[tuple[Any, ...]]
    settings_defaults: ClassVar[dict[Any, Any] | None]
    settings_default_overrides: ClassVar[dict[Any, Any] | None]
    relative_path_settings: ClassVar[tuple[Any, ...]]
    config_section: ClassVar[str | None]
    config_section_dependencies: ClassVar[tuple[str, ...] | None]

class TransformSpec:
    def get_transforms(self) -> list[Any]: ...
    default_transforms: ClassVar[tuple[Any, ...]]
    unknown_reference_resolvers: ClassVar[list[Any]]

class Component(SettingsSpec, TransformSpec):
    component_type: ClassVar[str | None]
    supported: ClassVar[tuple[str, ...]]
    def supports(self, format: str) -> bool: ...
