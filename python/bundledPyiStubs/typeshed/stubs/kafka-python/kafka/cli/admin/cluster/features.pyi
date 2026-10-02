from argparse import ArgumentParser, Namespace
from typing import ClassVar

class DescribeFeatures:
    COMMAND: ClassVar[str]
    HELP: ClassVar[str]
    @classmethod
    def add_arguments(cls, parser: ArgumentParser) -> None: ...
    @classmethod
    def command(cls, client, args: Namespace): ...

class UpdateFeatures:
    COMMAND: ClassVar[str]
    HELP: ClassVar[str]
    @classmethod
    def add_arguments(cls, parser: ArgumentParser) -> None: ...
    @classmethod
    def command(cls, client, args: Namespace): ...
