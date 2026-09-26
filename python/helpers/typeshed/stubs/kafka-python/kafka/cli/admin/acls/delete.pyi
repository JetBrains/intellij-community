from argparse import ArgumentParser, Namespace
from typing import ClassVar

class DeleteACLs:
    COMMAND: ClassVar[str]
    HELP: ClassVar[str]
    @classmethod
    def add_arguments(cls, parser: ArgumentParser) -> None: ...
    @classmethod
    def command(cls, client, args: Namespace) -> list[dict[str, str | list[str]]]: ...
