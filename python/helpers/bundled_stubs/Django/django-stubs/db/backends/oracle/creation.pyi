from typing import Any

from django.db.backends.base.creation import BaseDatabaseCreation
from django.db.backends.oracle.base import DatabaseWrapper
from typing_extensions import override

TEST_DATABASE_PREFIX: str

class DatabaseCreation(BaseDatabaseCreation):
    connection: DatabaseWrapper
    @override
    def set_as_test_mirror(self, primary_settings_dict: Any) -> None: ...
    @override
    def test_db_signature(self) -> Any: ...
