from argparse import ArgumentParser, Namespace
from collections.abc import Mapping

from kafka.admin import ConfigResource

def add_resource_arguments(parser: ArgumentParser) -> None: ...
def parse_resources(args: Namespace, configs: Mapping[str, str] | None = None) -> list[ConfigResource]: ...
