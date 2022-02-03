from typing import Any

from ..util.langhelpers import public_factory as public_factory
from . import exc as exc, strategy_options as strategy_options
from .attributes import (
    AttributeEvent as AttributeEvent,
    InstrumentedAttribute as InstrumentedAttribute,
    Mapped as Mapped,
    QueryableAttribute as QueryableAttribute,
)
from .context import QueryContext as QueryContext
from .decl_api import (
    DeclarativeMeta as DeclarativeMeta,
    as_declarative as as_declarative,
    declarative_base as declarative_base,
    declarative_mixin as declarative_mixin,
    declared_attr as declared_attr,
    has_inherited_table as has_inherited_table,
    registry as registry,
    synonym_for as synonym_for,
)
from .descriptor_props import CompositeProperty as CompositeProperty, SynonymProperty as SynonymProperty
from .dynamic import AppenderQuery as AppenderQuery
from .events import (
    AttributeEvents as AttributeEvents,
    InstanceEvents as InstanceEvents,
    InstrumentationEvents as InstrumentationEvents,
    MapperEvents as MapperEvents,
    QueryEvents as QueryEvents,
    SessionEvents as SessionEvents,
)
from .identity import IdentityMap as IdentityMap
from .instrumentation import ClassManager as ClassManager
from .interfaces import (
    EXT_CONTINUE as EXT_CONTINUE,
    EXT_SKIP as EXT_SKIP,
    EXT_STOP as EXT_STOP,
    MANYTOMANY as MANYTOMANY,
    MANYTOONE as MANYTOONE,
    NOT_EXTENSION as NOT_EXTENSION,
    ONETOMANY as ONETOMANY,
    InspectionAttr as InspectionAttr,
    InspectionAttrInfo as InspectionAttrInfo,
    MapperProperty as MapperProperty,
    PropComparator as PropComparator,
    UserDefinedOption as UserDefinedOption,
)
from .loading import merge_frozen_result as merge_frozen_result, merge_result as merge_result
from .mapper import (
    Mapper as Mapper,
    class_mapper as class_mapper,
    configure_mappers as configure_mappers,
    reconstructor as reconstructor,
    validates as validates,
)
from .properties import ColumnProperty as ColumnProperty
from .query import AliasOption as AliasOption, FromStatement as FromStatement, Query as Query
from .relationships import RelationshipProperty as RelationshipProperty, foreign as foreign, remote as remote
from .scoping import scoped_session as scoped_session
from .session import (
    ORMExecuteState as ORMExecuteState,
    Session as Session,
    SessionTransaction as SessionTransaction,
    close_all_sessions as close_all_sessions,
    make_transient as make_transient,
    make_transient_to_detached as make_transient_to_detached,
    object_session as object_session,
    sessionmaker as sessionmaker,
)
from .state import AttributeState as AttributeState, InstanceState as InstanceState
from .strategy_options import Load as Load
from .unitofwork import UOWTransaction as UOWTransaction
from .util import (
    Bundle as Bundle,
    CascadeOptions as CascadeOptions,
    LoaderCriteriaOption as LoaderCriteriaOption,
    aliased as aliased,
    join as join,
    object_mapper as object_mapper,
    outerjoin as outerjoin,
    polymorphic_union as polymorphic_union,
    was_deleted as was_deleted,
    with_parent as with_parent,
    with_polymorphic as with_polymorphic,
)

def create_session(bind: Any | None = ..., **kwargs): ...

with_loader_criteria: Any
relationship: Any

def relation(*arg, **kw): ...
def dynamic_loader(argument, **kw): ...

column_property: Any
composite: Any

def backref(name, **kwargs): ...
def deferred(*columns, **kw): ...
def query_expression(default_expr=...): ...

mapper: Any
synonym: Any

def clear_mappers() -> None: ...

joinedload: Any
contains_eager: Any
defer: Any
undefer: Any
undefer_group: Any
with_expression: Any
load_only: Any
lazyload: Any
subqueryload: Any
selectinload: Any
immediateload: Any
noload: Any
raiseload: Any
defaultload: Any
selectin_polymorphic: Any

def eagerload(*args, **kwargs): ...

contains_alias: Any
