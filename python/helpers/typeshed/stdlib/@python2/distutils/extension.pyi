#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class Extension:
    def __init__(
        self,
        name: str,
        sources: list[str],
        include_dirs: list[str] = ...,
        define_macros: list[tuple[str, str | None]] = ...,
        undef_macros: list[str] = ...,
        library_dirs: list[str] = ...,
        libraries: list[str] = ...,
        runtime_library_dirs: list[str] = ...,
        extra_objects: list[str] = ...,
        extra_compile_args: list[str] = ...,
        extra_link_args: list[str] = ...,
        export_symbols: list[str] = ...,
        swig_opts: str | None = ...,  # undocumented
        depends: list[str] = ...,
        language: str = ...,
    ) -> None: ...
