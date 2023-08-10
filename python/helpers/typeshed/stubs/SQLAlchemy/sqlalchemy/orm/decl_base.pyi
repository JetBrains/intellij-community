from typing import Any

class _MapperConfig:
    @classmethod
    def setup_mapping(cls, registry, cls_, dict_, table, mapper_kw): ...
    cls: Any
    classname: Any
    properties: Any
    declared_attr_reg: Any
    def __init__(self, registry, cls_, mapper_kw) -> None: ...
    def set_cls_attribute(self, attrname, value): ...

class _ImperativeMapperConfig(_MapperConfig):
    dict_: Any
    local_table: Any
    inherits: Any
    def __init__(self, registry, cls_, table, mapper_kw) -> None: ...
    def map(self, mapper_kw=...): ...

class _ClassScanMapperConfig(_MapperConfig):
    dict_: Any
    local_table: Any
    persist_selectable: Any
    declared_columns: Any
    column_copies: Any
    table_args: Any
    tablename: Any
    mapper_args: Any
    mapper_args_fn: Any
    inherits: Any
    def __init__(self, registry, cls_, dict_, table, mapper_kw) -> None: ...
    def map(self, mapper_kw=...): ...

class _DeferredMapperConfig(_ClassScanMapperConfig):
    @property
    def cls(self): ...
    @cls.setter
    def cls(self, class_) -> None: ...
    @classmethod
    def has_cls(cls, class_): ...
    @classmethod
    def raise_unmapped_for_cls(cls, class_) -> None: ...
    @classmethod
    def config_for_cls(cls, class_): ...
    @classmethod
    def classes_for_base(cls, base_cls, sort: bool = ...): ...
    def map(self, mapper_kw=...): ...
