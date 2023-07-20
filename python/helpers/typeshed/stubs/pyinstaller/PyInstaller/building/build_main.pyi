# Referenced in: https://pyinstaller.org/en/stable/hooks.html?highlight=get_hook_config#PyInstaller.utils.hooks.get_hook_config
# Not to be imported during runtime, but is the type reference for hooks and analysis configuration

from _typeshed import Incomplete, StrPath
from collections.abc import Iterable
from typing import Any

from PyInstaller.building.datastruct import Target

class Analysis(Target):
    # https://pyinstaller.org/en/stable/hooks-config.html#hook-configuration-options
    hooksconfig: dict[str, dict[str, object]]
    def __init__(
        self,
        scripts: Iterable[StrPath],
        pathex: Incomplete | None = ...,
        binaries: Incomplete | None = ...,
        datas: Incomplete | None = ...,
        hiddenimports: Incomplete | None = ...,
        hookspath: Incomplete | None = ...,
        hooksconfig: dict[str, dict[str, Any]] | None = ...,
        excludes: Incomplete | None = ...,
        runtime_hooks: Incomplete | None = ...,
        cipher: Incomplete | None = ...,
        win_no_prefer_redirects: bool = ...,
        win_private_assemblies: bool = ...,
        noarchive: bool = ...,
        module_collection_mode: Incomplete | None = ...,
    ) -> None: ...
