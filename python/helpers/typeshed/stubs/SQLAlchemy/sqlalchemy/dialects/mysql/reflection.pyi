#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
