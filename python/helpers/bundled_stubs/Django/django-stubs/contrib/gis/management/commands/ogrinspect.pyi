from argparse import Action, ArgumentParser, Namespace
from collections.abc import Sequence
from typing import Any

from django.core.management.base import BaseCommand
from typing_extensions import override

class LayerOptionAction(Action):
    @override
    def __call__(
        self,
        parser: ArgumentParser,
        namespace: Namespace,
        value: str | Sequence[Any] | None,
        option_string: str | None = None,
    ) -> None: ...

class ListOptionAction(Action):
    @override
    def __call__(
        self,
        parser: ArgumentParser,
        namespace: Namespace,
        value: str | Sequence[Any] | None,
        option_string: str | None = None,
    ) -> None: ...

class Command(BaseCommand):
    @override
    def add_arguments(self, parser: ArgumentParser) -> None: ...
    @override
    def handle(self, *args: Any, **options: Any) -> str: ...
