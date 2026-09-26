from typing import Any

from django.core.management.commands.inspectdb import Command as InspectDBCommand
from django.db.backends.base.base import BaseDatabaseWrapper
from typing_extensions import override

class Command(InspectDBCommand):
    @override
    def get_field_type(
        self, connection: BaseDatabaseWrapper, table_name: str, row: Any
    ) -> tuple[str, dict[str, Any], list[str]]: ...
