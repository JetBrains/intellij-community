from collections.abc import Callable, Iterable
from typing import Any, Generic, NoReturn, TypeVar, overload, type_check_only

from django.core.exceptions import ObjectDoesNotExist
from django.db.models.base import Model
from django.db.models.fields import Field
from django.db.models.fields.related import ForeignKey, ManyToManyField, RelatedField
from django.db.models.fields.reverse_related import ManyToManyRel, ManyToOneRel, OneToOneRel
from django.db.models.manager import BaseManager, Manager
from django.db.models.query import QuerySet
from django.db.models.query_utils import DeferredAttribute
from django.utils.functional import cached_property
from typing_extensions import Self

_M = TypeVar("_M", bound=Model)
_F = TypeVar("_F", bound=Field)
_From = TypeVar("_From", bound=Model)
_Through = TypeVar("_Through", bound=Model, default=Model)
_To = TypeVar("_To", bound=Model)

class ForeignKeyDeferredAttribute(DeferredAttribute):
    field: RelatedField

class ForwardManyToOneDescriptor(Generic[_F]):
    field: _F
    def __init__(self, field_with_rel: _F) -> None: ...
    @cached_property
    def RelatedObjectDoesNotExist(self) -> type[ObjectDoesNotExist]: ...
    def is_cached(self, instance: Model) -> bool: ...
    def get_queryset(self, **hints: Any) -> QuerySet[Any]: ...
    def get_prefetch_queryset(
        self, instances: list[Model], queryset: QuerySet[Any] | None = None
    ) -> tuple[QuerySet[Any], Callable[..., Any], Callable[..., Any], bool, str, bool]: ...
    def get_prefetch_querysets(
        self, instances: list[Model], querysets: list[QuerySet[Any]] | None = None
    ) -> tuple[QuerySet[Any], Callable[..., Any], Callable[..., Any], bool, str, bool]: ...
    def get_object(self, instance: Model) -> Model: ...
    def __get__(
        self, instance: Model | None, cls: type[Model] | None = None
    ) -> Model | ForwardManyToOneDescriptor | None: ...
    def __set__(self, instance: Model, value: Model | None) -> None: ...
    def __reduce__(self) -> tuple[Callable[..., Any], tuple[type[Model], str]]: ...

class ForwardOneToOneDescriptor(ForwardManyToOneDescriptor[_F]):
    def get_object(self, instance: Model) -> Model: ...

class ReverseOneToOneDescriptor(Generic[_From, _To]):
    """
    In the example::

        class Restaurant(Model):
            place = OneToOneField(Place, related_name='restaurant')

    ``Place.restaurant`` is a ``ReverseOneToOneDescriptor`` instance.
    """

    related: OneToOneRel
    def __init__(self, related: OneToOneRel) -> None: ...
    @cached_property
    def RelatedObjectDoesNotExist(self) -> type[ObjectDoesNotExist]: ...
    def is_cached(self, instance: _From) -> bool: ...
    def get_queryset(self, **hints: Any) -> QuerySet[_To]: ...
    def get_prefetch_queryset(
        self, instances: list[_From], queryset: QuerySet[_To] | None = None
    ) -> tuple[QuerySet[_To], Callable[..., Any], Callable[..., Any], bool, str, bool]: ...
    def get_prefetch_querysets(
        self, instances: list[_From], querysets: list[QuerySet[_To]] | None = None
    ) -> tuple[QuerySet[_To], Callable[..., Any], Callable[..., Any], bool, str, bool]: ...
    @overload
    def __get__(self, instance: None, cls: Any | None = None) -> ReverseOneToOneDescriptor[_From, _To]: ...
    @overload
    def __get__(self, instance: _From, cls: Any | None = None) -> _To: ...
    def __set__(self, instance: _From, value: _To | None) -> None: ...
    def __reduce__(self) -> tuple[Callable[..., Any], tuple[type[_To], str]]: ...

class ReverseManyToOneDescriptor(Generic[_To]):
    """
    In the example::

        class Child(Model):
            parent = ForeignKey(Parent, related_name='children')

    ``Parent.children`` is a ``ReverseManyToOneDescriptor`` instance.
    """

    rel: ManyToOneRel
    field: ForeignKey[_To, _To]
    def __init__(self, rel: ManyToOneRel) -> None: ...
    @cached_property
    def related_manager_cls(self) -> type[RelatedManager[_To]]: ...
    @overload
    def __get__(self, instance: None, cls: Any | None = None) -> Self: ...
    @overload
    def __get__(self, instance: Model, cls: Any | None = None) -> RelatedManager[_To]: ...
    def __set__(self, instance: Any, value: Any) -> NoReturn: ...

# Fake class, Django defines 'RelatedManager' inside a function body
@type_check_only
class RelatedManager(Manager[_To], Generic[_To]):
    related_val: tuple[int, ...]
    def add(self, *objs: _To | int, bulk: bool = ...) -> None: ...
    async def aadd(self, *objs: _To | int, bulk: bool = ...) -> None: ...
    def remove(self, *objs: _To | int, bulk: bool = ...) -> None: ...
    async def aremove(self, *objs: _To | int, bulk: bool = ...) -> None: ...
    def set(self, objs: QuerySet[_To] | Iterable[_To | int], *, bulk: bool = ..., clear: bool = ...) -> None: ...
    async def aset(self, objs: QuerySet[_To] | Iterable[_To | int], *, bulk: bool = ..., clear: bool = ...) -> None: ...
    def clear(self) -> None: ...
    async def aclear(self) -> None: ...
    def __call__(self, *, manager: str) -> RelatedManager[_To]: ...

def create_reverse_many_to_one_manager(
    superclass: type[BaseManager[_M]], rel: ManyToOneRel
) -> type[RelatedManager[_M]]: ...

class ManyToManyDescriptor(ReverseManyToOneDescriptor, Generic[_To, _Through]):
    """
    In the example::

        class Pizza(Model):
            toppings = ManyToManyField(Topping, related_name='pizzas')

    ``Pizza.toppings`` and ``Topping.pizzas`` are ``ManyToManyDescriptor``
    instances.
    """

    # 'field' here is 'rel.field'
    rel: ManyToManyRel  # type: ignore[assignment]
    field: ManyToManyField[_To, _Through]  # type: ignore[assignment]
    reverse: bool
    def __init__(self, rel: ManyToManyRel, reverse: bool = False) -> None: ...
    @property
    def through(self) -> type[_Through]: ...
    @cached_property
    def related_manager_cls(self) -> type[ManyRelatedManager[_To, _Through]]: ...  # type: ignore[override]
    @overload  # type: ignore[override]
    def __get__(self, instance: None, cls: Any | None = None) -> Self: ...
    @overload
    def __get__(self, instance: Model, cls: Any | None = None) -> ManyRelatedManager[_To, _Through]: ...

# Fake class, Django defines 'ManyRelatedManager' inside a function body
@type_check_only
class ManyRelatedManager(Manager[_To], Generic[_To, _Through]):
    related_val: tuple[int, ...]
    through: type[_Through]
    def add(self, *objs: _To | int, bulk: bool = ..., through_defaults: dict[str, Any] | None = ...) -> None: ...
    async def aadd(self, *objs: _To | int, bulk: bool = ..., through_defaults: dict[str, Any] | None = ...) -> None: ...
    def remove(self, *objs: _To | int, bulk: bool = ...) -> None: ...
    async def aremove(self, *objs: _To | int, bulk: bool = ...) -> None: ...
    def set(
        self,
        objs: QuerySet[_To] | Iterable[_To | int],
        *,
        bulk: bool = ...,
        clear: bool = ...,
        through_defaults: dict[str, Any] | None = ...,
    ) -> None: ...
    async def aset(
        self,
        objs: QuerySet[_To] | Iterable[_To | int],
        *,
        bulk: bool = ...,
        clear: bool = ...,
        through_defaults: dict[str, Any] | None = ...,
    ) -> None: ...
    def clear(self) -> None: ...
    async def aclear(self) -> None: ...
    def __call__(self, *, manager: str) -> ManyRelatedManager[_To, _Through]: ...

def create_forward_many_to_many_manager(
    superclass: type[BaseManager[_To]], rel: ManyToManyRel, reverse: bool
) -> type[ManyRelatedManager[_To, _Through]]: ...
