# Stubs for distutils.cmd

from typing import Callable, List, Tuple, Union
from abc import abstractmethod
from distutils.dist import Distribution

class Command:
    sub_commands = ...  # type: List[Tuple[str, Union[Callable[[], bool], str, None]]]
    def __init__(self, dist: Distribution) -> None: ...
    @abstractmethod
    def initialize_options(self) -> None: ...
    @abstractmethod
    def finalize_options(self) -> None: ...
    @abstractmethod
    def run(self) -> None: ...
