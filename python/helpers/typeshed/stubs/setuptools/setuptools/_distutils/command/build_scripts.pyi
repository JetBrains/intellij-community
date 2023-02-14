from re import Pattern
from typing import Any

from ..cmd import Command

first_line_re: Pattern[str]

class build_scripts(Command):
    description: str
    user_options: Any
    boolean_options: Any
    build_dir: Any
    scripts: Any
    force: Any
    executable: Any
    outfiles: Any
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def get_source_files(self): ...
    def run(self) -> None: ...
    def copy_scripts(self): ...
