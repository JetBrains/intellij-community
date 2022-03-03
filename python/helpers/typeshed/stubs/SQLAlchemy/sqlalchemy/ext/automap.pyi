from typing import Any

def classname_for_table(base, tablename, table): ...
def name_for_scalar_relationship(base, local_cls, referred_cls, constraint): ...
def name_for_collection_relationship(base, local_cls, referred_cls, constraint): ...
def generate_relationship(base, direction, return_fn, attrname, local_cls, referred_cls, **kw): ...

class AutomapBase:
    __abstract__: bool
    classes: Any
    @classmethod
    def prepare(
        cls,
        autoload_with: Any | None = ...,
        engine: Any | None = ...,
        reflect: bool = ...,
        schema: Any | None = ...,
        classname_for_table: Any | None = ...,
        collection_class: Any | None = ...,
        name_for_scalar_relationship: Any | None = ...,
        name_for_collection_relationship: Any | None = ...,
        generate_relationship: Any | None = ...,
        reflection_options=...,
    ) -> None: ...

def automap_base(declarative_base: Any | None = ..., **kw): ...
