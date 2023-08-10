from typing import Any

class ReflectedState:
    columns: Any
    table_options: Any
    table_name: Any
    keys: Any
    fk_constraints: Any
    ck_constraints: Any
    def __init__(self) -> None: ...

class MySQLTableDefinitionParser:
    logger: Any
    dialect: Any
    preparer: Any
    def __init__(self, dialect, preparer) -> None: ...
    def parse(self, show_create, charset): ...
