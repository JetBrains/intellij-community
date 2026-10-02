from typing import Any, ClassVar

from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.oracle.base import DatabaseWrapper
from typing_extensions import override

TEST_DATABASE_PREFIX: str

class DatabaseCreation(BaseDatabaseCreation):
    destroy_test_db_connection_close_method: ClassVar[str | None]
    connection: DatabaseWrapper
    @override
    def set_as_test_mirror(self, primary_settings_dict: Any) -> None: ...
    @override
    def test_db_signature(self) -> Any: ...
