from abc import abstractmethod
from typing import Any

from setuptools import Command

def config_file(kind: str = ...): ...
def edit_config(filename, settings, dry_run: bool = ...) -> None: ...

class option_base(Command):
    user_options: Any
    boolean_options: Any
    global_config: Any
    user_config: Any
    filename: Any
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    @abstractmethod
    def run(self) -> None: ...

class setopt(option_base):
    description: str
    user_options: Any
    boolean_options: Any
    command: Any
    option: Any
    set_value: Any
    remove: Any
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...
