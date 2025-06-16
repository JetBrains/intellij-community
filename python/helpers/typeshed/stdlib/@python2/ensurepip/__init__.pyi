#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

def version() -> str: ...
def bootstrap(
    root: str | None = ...,
    upgrade: bool = ...,
    user: bool = ...,
    altinstall: bool = ...,
    default_pip: bool = ...,
    verbosity: int = ...,
) -> None: ...
