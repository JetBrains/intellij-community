from typing import Any, Literal

class StorageSettingsMixin:
    def _clear_cached_properties(
        self,
        setting: Literal["MEDIA_ROOT", "MEDIA_URL", "FILE_UPLOAD_PERMISSIONS", "FILE_UPLOAD_DIRECTORY_PERMISSIONS"],
        **kwargs: Any,
    ) -> None: ...
    def _value_or_setting(self, value: Any, setting: Any) -> Any: ...
