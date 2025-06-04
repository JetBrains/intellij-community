from django.utils._os import _PathCompatible
from django.utils.deconstruct import _Deconstructible
from django.utils.functional import cached_property

from .base import Storage
from .mixins import StorageSettingsMixin

class FileSystemStorage(_Deconstructible, Storage, StorageSettingsMixin):
    OS_OPEN_FLAGS: int

    def __init__(
        self,
        location: _PathCompatible | None = None,
        base_url: str | None = None,
        file_permissions_mode: int | None = None,
        directory_permissions_mode: int | None = None,
    ) -> None: ...
    @cached_property
    def base_location(self) -> _PathCompatible: ...
    @cached_property
    def location(self) -> _PathCompatible: ...
    @cached_property
    def base_url(self) -> str: ...
    @cached_property
    def file_permissions_mode(self) -> int | None: ...
    @cached_property
    def directory_permissions_mode(self) -> int | None: ...
