from _typeshed import Incomplete, Unused
from collections.abc import Callable
from typing import ClassVar

from ..ccompiler import CCompiler
from ..cmd import Command
from ..extension import Extension

class build_ext(Command):
    description: ClassVar[str]
    sep_by: Incomplete
    user_options: ClassVar[list[tuple[str, str | None, str]]]
    boolean_options: ClassVar[list[str]]
    help_options: ClassVar[list[tuple[str, str | None, str, Callable[[], Unused]]]]
    extensions: list[Extension] | None
    build_lib: str
    plat_name: str
    build_temp: str
    inplace: bool
    package: str | None
    include_dirs: list[str]
    define: Incomplete
    undef: Incomplete
    libraries: list[str]
    library_dirs: list[str]
    rpath: list[str]
    link_objects: Incomplete
    debug: Incomplete
    force: bool | None
    compiler: str | CCompiler | None
    swig: Incomplete
    swig_cpp: Incomplete
    swig_opts: list[str]
    user: Incomplete
    parallel: int | None
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...
    def check_extensions_list(self, extensions) -> None: ...
    def get_source_files(self): ...
    def get_outputs(self): ...
    def build_extensions(self) -> None: ...
    def build_extension(self, ext) -> None: ...
    def swig_sources(self, sources, extension): ...
    def find_swig(self): ...
    def get_ext_fullpath(self, ext_name: str) -> str: ...
    def get_ext_fullname(self, ext_name: str) -> str: ...
    def get_ext_filename(self, ext_name: str) -> str: ...
    def get_export_symbols(self, ext: Extension) -> list[str]: ...
    def get_libraries(self, ext: Extension) -> list[str]: ...
