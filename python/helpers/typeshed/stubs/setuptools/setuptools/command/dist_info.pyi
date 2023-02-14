from distutils.core import Command
from typing import Any

class dist_info(Command):
    description: str
    user_options: Any
    egg_base: Any
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...
