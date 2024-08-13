from typing import Literal

from django.db.backends.base.schema import BaseDatabaseSchemaEditor
from django.db.migrations import AddConstraint, AddIndex, RemoveIndex
from django.db.migrations.operations.base import Operation

class CreateExtension(Operation):
    reversible: bool
    name: str
    def __init__(self, name: str) -> None: ...
    def extension_exists(self, schema_editor: BaseDatabaseSchemaEditor, extension: str) -> bool: ...

class BloomExtension(CreateExtension):
    def __init__(self) -> None: ...

class BtreeGinExtension(CreateExtension):
    def __init__(self) -> None: ...

class BtreeGistExtension(CreateExtension):
    def __init__(self) -> None: ...

class CITextExtension(CreateExtension):
    def __init__(self) -> None: ...

class CryptoExtension(CreateExtension):
    def __init__(self) -> None: ...

class HStoreExtension(CreateExtension):
    def __init__(self) -> None: ...

class TrigramExtension(CreateExtension):
    def __init__(self) -> None: ...

class UnaccentExtension(CreateExtension):
    def __init__(self) -> None: ...

class NotInTransactionMixin:
    def _ensure_not_in_transaction(self, schema_editor: BaseDatabaseSchemaEditor) -> None: ...

class AddIndexConcurrently(NotInTransactionMixin, AddIndex):
    atomic: Literal[False]

class RemoveIndexConcurrently(NotInTransactionMixin, RemoveIndex):
    atomic: Literal[False]

class CollationOperation(Operation):
    name: str
    locale: str
    provider: str
    deterministic: bool
    def __init__(self, name: str, locale: str, *, provider: str = ..., deterministic: bool = ...) -> None: ...
    def create_collation(self, schema_editor: BaseDatabaseSchemaEditor) -> None: ...
    def remove_collation(self, schema_editor: BaseDatabaseSchemaEditor) -> None: ...

class CreateCollation(CollationOperation): ...
class RemoveCollation(CollationOperation): ...
class AddConstraintNotValid(AddConstraint): ...

class ValidateConstraint(Operation):
    model_name: str
    name: str
    def __init__(self, model_name: str, name: str) -> None: ...
