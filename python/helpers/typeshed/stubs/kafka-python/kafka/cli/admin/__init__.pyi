from _typeshed import Incomplete
from argparse import ArgumentParser
from collections.abc import Iterable, Sequence
from typing import Final, Literal

DEFAULT_COMMAND_GROUPS: Final[tuple[type[Incomplete], ...]]

def main_parser(prog: str | None = None) -> ArgumentParser: ...
def build_parser(groups: Iterable[Incomplete] = (), prog: str | None = None) -> ArgumentParser: ...
def run_cli(args: Sequence[str] | None = None, prog: str | None = None) -> Literal[0, 1]: ...
