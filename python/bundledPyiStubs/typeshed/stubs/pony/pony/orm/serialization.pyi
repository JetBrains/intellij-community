from _typeshed import Incomplete
from collections import defaultdict
from collections.abc import Iterable
from datetime import date, datetime
from decimal import Decimal

from pony.orm.core import Database, Entity

class Bag:
    database: Database
    session_cache: Incomplete
    entity_configs: dict[Entity, tuple[Incomplete, bool]]
    objects: defaultdict[type[Entity], set[Entity]]
    vars: dict[Incomplete, Incomplete]
    dicts: defaultdict[Incomplete, dict[Incomplete, Incomplete]]
    def __init__(self, database: Database) -> None: ...
    def config(
        self,
        entity: Entity,
        only=None,
        exclude=None,
        with_collections: bool = True,
        with_lazy: bool = False,
        related_objects: bool = True,
    ) -> tuple[Incomplete, bool]: ...
    def put(self, x: Entity | Iterable[Entity]) -> None: ...
    def to_dict(self): ...
    def to_json(self) -> str: ...

def to_dict(objects): ...
def to_json(objects) -> str: ...
def json_converter(x: datetime | date | Decimal) -> str: ...
