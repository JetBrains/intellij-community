from typing import ClassVar

from sassutils.builder import Manifest as Manifest
from setuptools import Command, Distribution

def validate_manifests(dist: Distribution, attr: str, value: object) -> None: ...

class build_sass(Command):
    description: str
    user_options: ClassVar[list[tuple[str, str, str]]]
    package_dir: dict[str, str] | None
    output_style: str
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...
    def get_package_dir(self, package: str) -> str: ...

__all__ = ("build_sass", "validate_manifests")
