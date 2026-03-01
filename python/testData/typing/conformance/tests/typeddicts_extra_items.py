# pyright: enableExperimentalFeatures=true
from collections.abc import Mapping
from typing_extensions import ReadOnly, TypedDict, Unpack
from typing import assert_type, Required, NotRequired, Never


# > For a TypedDict type that specifies
# > ``extra_items``, during construction, the value type of each unknown item
# is expected to be non-required and assignable to the ``extra_items`` argument.

class Movie(TypedDict, extra_items=bool):
    name: str

a: Movie = {"name": "Blade Runner", "novel_adaptation": True}  # OK
b: Movie = {"name": "Blade Runner", "year": 1982}  # E: 'int' is not assignable to 'bool'

# > The alternative inline syntax is also supported::

MovieFunctional = TypedDict("MovieFunctional", {"name": str}, extra_items=bool)

c: MovieFunctional = {"name": "Blade Runner", "novel_adaptation": True}  # OK
d: MovieFunctional = {"name": "Blade Runner", "year": 1982}  # E: 'int' is not assignable to 'bool'

# > Accessing extra items is allowed. Type checkers must infer their value type from
# > the ``extra_items`` argument

def movie_keys(movie: Movie) -> None:
    assert_type(movie["name"], str)
    assert_type(movie["novel_adaptation"], bool)

# > ``extra_items`` is inherited through subclassing

class MovieBase(TypedDict, extra_items=ReadOnly[int | None]):
    name: str

class InheritedMovie(MovieBase):
    year: int

e: InheritedMovie = {"name": "Blade Runner", "year": None}  # E: 'None' is incompatible with 'int'
f: InheritedMovie = {
    "name": "Blade Runner",
    "year": 1982,
    "other_extra_key": None,
}  # OK

# > Similar to ``total``, only a literal ``True`` or ``False`` is supported as the
# > value of the ``closed`` argument. Type checkers should reject any non-literal value.

class IllegalTD(TypedDict, closed=42 == 42):  # E: Argument to "closed" must be a literal True or False
    name: str

# > Passing ``closed=False`` explicitly requests the default TypedDict behavior,
# > where arbitrary other keys may be present and subclasses may add arbitrary items.

class BaseTD(TypedDict, closed=False):
    name: str

class ChildTD(BaseTD):  # OK
    age: int

# > It is a type checker error to pass ``closed=False`` if a superclass has
# > ``closed=True`` or sets ``extra_items``.

class ClosedBase(TypedDict, closed=True):
    name: str

class IllegalChild1(ClosedBase, closed=False):  # E: Cannot set 'closed=False' when superclass is 'closed=True'
    pass

class ExtraItemsBase(TypedDict, extra_items=int):
    name: str

class IllegalChild2(ExtraItemsBase, closed=False):  # E: Cannot set 'closed=False' when superclass has 'extra_items'
    pass

# > If ``closed`` is not provided, the behavior is inherited from the superclass.
# > If the superclass is TypedDict itself or the superclass does not have ``closed=True``
# > or the ``extra_items`` parameter, the previous TypedDict behavior is preserved:
# > arbitrary extra items are allowed. If the superclass has ``closed=True``, the
# > child class is also closed.

class BaseMovie(TypedDict, closed=True):
    name: str

class MovieA(BaseMovie):  # OK, still closed
    pass

class MovieB(BaseMovie, closed=True):  # OK, but redundant
    pass

class MovieC(MovieA):  # E[MovieC]
    age: int  # E[MovieC]: "MovieC" is a closed TypedDict; extra key "age" not allowed

class MovieD(MovieB):  # E[MovieD]
    age: int  # E[MovieD]: "MovieD" is a closed TypedDict; extra key "age" not allowed

# > It is possible to use ``closed=True`` when subclassing if the ``extra_items``
# > argument is a read-only type.

class MovieES(TypedDict, extra_items=ReadOnly[str]):
    pass

class MovieClosed(MovieES, closed=True):  # OK
    pass

class MovieNever(MovieES, extra_items=Never):  # OK, but 'closed=True' is preferred
    pass

class IllegalCloseNonReadOnly(ExtraItemsBase, closed=True):  # E: Cannot set 'closed=True' when superclass has non-read-only 'extra_items'
    pass

# > It is an error to use ``Required[]`` or ``NotRequired[]`` with ``extra_items``.

class IllegalExtraItemsTD(TypedDict, extra_items=Required[int]):  # E: 'extra_items' value cannot be 'Required[...]'
    name: str

class AnotherIllegalExtraItemsTD(TypedDict, extra_items=NotRequired[int]):  # E: 'extra_items' value cannot be 'NotRequired[...]'
    name: str

# > The extra items are non-required, regardless of the totality of the
# > TypedDict. Operations that are available to ``NotRequired`` items should also be available to the
# > extra items.

class MovieEI(TypedDict, extra_items=int):
    name: str

def del_items(movie: MovieEI) -> None:
    del movie["name"]  # E: The value type of 'name' is 'Required[str]'
    del movie["year"]  # OK: The value type of 'year' is 'NotRequired[int]'

# > For type checking purposes, ``Unpack[SomeTypedDict]`` with extra items should be
# > treated as its equivalent in regular parameters.

class MovieNoExtra(TypedDict):
    name: str

class MovieExtra(TypedDict, extra_items=int):
    name: str

def unpack_no_extra(**kwargs: Unpack[MovieNoExtra]) -> None: ...
def unpack_extra(**kwargs: Unpack[MovieExtra]) -> None: ...

unpack_no_extra(name="No Country for Old Men", year=2007) # E: Unrecognized item
unpack_extra(name="No Country for Old Men", year=2007) # OK

# > Notably, if the TypedDict type specifies ``extra_items`` to be read-only,
# > subclasses of the TypedDict type may redeclare ``extra_items``.

class ReadOnlyBase(TypedDict, extra_items=ReadOnly[int]):
    pass

class ReadOnlyChild(ReadOnlyBase, extra_items=ReadOnly[bool]):  # OK
    pass

class MutableChild(ReadOnlyBase, extra_items=int):  # OK
    pass

# > Because a non-closed TypedDict type implicitly allows non-required extra items
# > of value type ``ReadOnly[object]``, its subclass can override the
# > ``extra_items`` argument with more specific types.

class NonClosedBase(TypedDict):
    name: str

class SpecificExtraItems(NonClosedBase, extra_items=bytes):  # OK
    year: int

# > First, it is not allowed to change the value of ``extra_items`` in a subclass
# > unless it is declared to be ``ReadOnly`` in the superclass.

class Parent(TypedDict, extra_items=int | None):
    pass

class Child(Parent, extra_items=int): # E: Cannot change 'extra_items' type unless it is 'ReadOnly' in the superclass
    pass

# > Second, ``extra_items=T`` effectively defines the value type of any unnamed
# > items accepted to the TypedDict and marks them as non-required. Thus, the above
# > restriction applies to any additional items defined in a subclass.

class MovieBase2(TypedDict, extra_items=int | None):
    name: str

class MovieRequiredYear(MovieBase2):  # E[MovieRequiredYear]: Required key 'year' is not known to 'MovieBase2'
    year: int | None  # E[MovieRequiredYear]

class MovieNotRequiredYear(MovieBase2):  # E[MovieNotRequiredYear]: 'int | None' is not consistent with 'int'
    year: NotRequired[int]  # E[MovieNotRequiredYear]

class MovieWithYear(MovieBase2):  # OK
    year: NotRequired[int | None]

class BookBase(TypedDict, extra_items=ReadOnly[int | None]):
    name: str

class BookWithPublisher(BookBase):  # E[BookWithPublisher]: 'str' is not assignable to 'int | None'
    publisher: str  # E[BookWithPublisher]

# > Let ``S`` be the set of keys of the explicitly defined items on a TypedDict
# > type. If it specifies ``extra_items=T``, the TypedDict type is considered to
# > have an infinite set of items that all satisfy the following conditions.
# > - If ``extra_items`` is read-only:
# >   - The key's value type is :term:`assignable` to ``T``.
# >   - The key is not in ``S``.
# > - If ``extra_items`` is not read-only:
# >   - The key is non-required.
# >   - The key's value type is :term:`consistent` with ``T``.
# >   - The key is not in ``S``.

class MovieDetails(TypedDict, extra_items=int | None):
    name: str
    year: NotRequired[int]

details2: MovieDetails = {"name": "Kill Bill Vol. 1", "year": 2003}
movie2: MovieBase2 = details2  # E: While 'int' is not consistent with 'int | None'

class MovieWithYear2(TypedDict, extra_items=int | None):
    name: str
    year: int | None

details3: MovieWithYear2 = {"name": "Kill Bill Vol. 1", "year": 2003}
movie3: MovieBase2 = details3  # E: 'year' is not required in 'MovieBase2', but it is required in 'MovieWithYear2'

# > When ``extra_items`` is specified to be read-only on a TypedDict type, it is
# > possible for an item to have a :term:`narrower <narrow>` type than the
# > ``extra_items`` argument.

class MovieSI(TypedDict, extra_items=ReadOnly[str | int]):
    name: str

class MovieDetails4(TypedDict, extra_items=int):
    name: str
    year: NotRequired[int]

class MovieDetails5(TypedDict, extra_items=int):
    name: str
    actors: list[str]

details4: MovieDetails4 = {"name": "Kill Bill Vol. 2", "year": 2004}
details5: MovieDetails5 = {"name": "Kill Bill Vol. 2", "actors": ["Uma Thurman"]}
movie4: MovieSI = details4  # OK. 'int' is assignable to 'str | int'.
movie5: MovieSI = details5  # E: 'list[str]' is not assignable to 'str | int'.

# > ``extra_items`` as a pseudo-item follows the same rules that other items have,
# > so when both TypedDicts types specify ``extra_items``, this check is naturally
# > enforced.

class MovieExtraInt(TypedDict, extra_items=int):
    name: str

class MovieExtraStr(TypedDict, extra_items=str):
    name: str

extra_int: MovieExtraInt = {"name": "No Country for Old Men", "year": 2007}
extra_str: MovieExtraStr = {"name": "No Country for Old Men", "description": ""}
extra_int = extra_str  # E: 'str' is not assignable to extra items type 'int'
extra_str = extra_int  # E: 'int' is not assignable to extra items type 'str'

# > A non-closed TypedDict type implicitly allows non-required extra keys of value
# > type ``ReadOnly[object]``. Applying the assignability rules between this type
# > and a closed TypedDict type is allowed.

class MovieNotClosed(TypedDict):
    name: str

extra_int2: MovieExtraInt = {"name": "No Country for Old Men", "year": 2007}
not_closed: MovieNotClosed = {"name": "No Country for Old Men"}
extra_int2 = not_closed  # E: 'extra_items=ReadOnly[object]' implicitly on 'MovieNotClosed' is not assignable to with 'extra_items=int'
not_closed = extra_int2  # OK

# > TypedDicts that allow extra items of type ``T`` also allow arbitrary keyword
# > arguments of this type when constructed by calling the class object.

class NonClosedMovie(TypedDict):
    name: str

NonClosedMovie(name="No Country for Old Men")  # OK
NonClosedMovie(name="No Country for Old Men", year=2007)  # E: Unrecognized item

class ExtraMovie(TypedDict, extra_items=int):
    name: str

ExtraMovie(name="No Country for Old Men")  # OK
ExtraMovie(name="No Country for Old Men", year=2007)  # OK
ExtraMovie(name="No Country for Old Men", language="English")  # E: Wrong type for extra item 'language'

# This implies 'extra_items=Never',
# so extra keyword arguments would produce an error
class ClosedMovie(TypedDict, closed=True):
    name: str

ClosedMovie(name="No Country for Old Men")  # OK
ClosedMovie(name="No Country for Old Men", year=2007)  # E: Extra items not allowed

# > A TypedDict type is :term:`assignable` to a type of the form ``Mapping[str, VT]``
# > when all value types of the items in the TypedDict
# > are assignable to ``VT``.

extra_str3: MovieExtraStr = {"name": "Blade Runner", "summary": ""}
str_mapping: Mapping[str, str] = extra_str3  # OK

extra_int3: MovieExtraInt = {"name": "Blade Runner", "year": 1982}
int_mapping: Mapping[str, int] = extra_int3  # E: 'int | str' is not assignable with 'int'
int_str_mapping: Mapping[str, int | str] = extra_int3  # OK

# > Type checkers should infer the precise signatures of ``values()`` and ``items()``
# > on such TypedDict types.

def foo(movie: MovieExtraInt) -> None:
    assert_type(list(movie.items()), list[tuple[str, int | str]])
    assert_type(list(movie.values()), list[int | str])

# > The TypedDict type is :term:`assignable` to ``dict[str, VT]`` if all
# > items on the TypedDict type satisfy the following conditions:
# > - The value type of the item is :term:`consistent` with ``VT``.
# > - The item is not read-only.
# > - The item is not required.

class IntDict(TypedDict, extra_items=int):
    pass

class IntDictWithNum(IntDict):
    num: NotRequired[int]

def clear_intdict(x: IntDict) -> None:
    v: dict[str, int] = x  # OK
    v.clear()  # OK

not_required_num_dict: IntDictWithNum = {"num": 1, "bar": 2}
regular_dict: dict[str, int] = not_required_num_dict  # OK
clear_intdict(not_required_num_dict)  # OK

# > In this case, methods that are previously unavailable on a TypedDict are allowed,
# > with signatures matching ``dict[str, VT]``
# > (e.g.: ``__setitem__(self, key: str, value: VT) -> None``).

not_required_num_dict.clear()  # OK

assert_type(not_required_num_dict.popitem(), tuple[str, int])

def nrnd(not_required_num_dict: IntDictWithNum, key: str):
    not_required_num_dict[key] = 42  # OK
    del not_required_num_dict[key]  # OK

# > ``dict[str, VT]`` is not assignable to a TypedDict type,
# > because such dict can be a subtype of dict.

class CustomDict(dict[str, int]):
    pass

def might_not_be(might_not_be_a_builtin_dict: dict[str, int]):
    int_dict: IntDict = might_not_be_a_builtin_dict # E
    print(int_dict)

not_a_builtin_dict = CustomDict({"num": 1})
might_not_be(not_a_builtin_dict)
