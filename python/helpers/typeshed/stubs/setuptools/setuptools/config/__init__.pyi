from _typeshed import Incomplete, StrPath
from collections.abc import Callable
from typing import TypeVar

from setuptools.config.setupcfg import AllCommandOptions, ConfigMetadataHandler, ConfigOptionsHandler
from setuptools.dist import Distribution

Fn = TypeVar("Fn", bound=Callable[..., Incomplete])  # noqa: Y001 # Exists at runtime
__all__ = ("parse_configuration", "read_configuration")

def read_configuration(
    filepath: StrPath, find_others: bool = False, ignore_option_errors: bool = False
) -> dict[Incomplete, Incomplete]: ...
def parse_configuration(
    distribution: Distribution, command_options: AllCommandOptions, ignore_option_errors: bool = False
) -> tuple[ConfigMetadataHandler, ConfigOptionsHandler]: ...
