from typing import Any, ClassVar, NamedTuple, Tuple

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
        cls, major: int = ..., minor: int = ..., micro: int = ..., releaselevel: str = ..., serial: int = ..., release: bool = ...
    ) -> VersionInfo: ...

__version_info__: VersionInfo
__version_details__: str

class ApplicationError(Exception): ...
class DataError(ApplicationError): ...

class SettingsSpec:
    settings_spec: ClassVar[Tuple[Any, ...]]
    settings_defaults: ClassVar[dict[Any, Any] | None]
    settings_default_overrides: ClassVar[dict[Any, Any] | None]
    relative_path_settings: ClassVar[Tuple[Any, ...]]
    config_section: ClassVar[str | None]
    config_section_dependencies: ClassVar[Tuple[str, ...] | None]

class TransformSpec:
    def get_transforms(self) -> list[Any]: ...
    default_transforms: ClassVar[Tuple[Any, ...]]
    unknown_reference_resolvers: ClassVar[list[Any]]

class Component(SettingsSpec, TransformSpec):
    component_type: ClassVar[str | None]
    supported: ClassVar[Tuple[str, ...]]
    def supports(self, format: str) -> bool: ...
