from argparse import ArgumentParser, Namespace
from typing import ClassVar

class DescribeACLs:
    COMMAND: ClassVar[str]
    HELP: ClassVar[str]
    @classmethod
    def add_arguments(cls, parser: ArgumentParser) -> None: ...
    @classmethod
    def command(cls, client, args: Namespace) -> list[str]: ...
