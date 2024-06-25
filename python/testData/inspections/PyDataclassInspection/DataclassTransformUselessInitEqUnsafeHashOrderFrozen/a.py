from decorator import my_dataclass, my_dataclass_order_default, my_dataclass_frozen_default, my_dataclass_eq_default


@my_dataclass()
class ImplicitInit:
    field: int

    def __init__(self, field):
        ...


@my_dataclass(<warning descr="'init' is ignored if the class already defines '__init__' method">init=True</warning>)
class ExplicitInit:
    field: int

    def __init__(self, field):
        ...


@my_dataclass()
class ImplicitEq:
    field: int

    def __eq__(self, other):
        pass


# Unclear semantic
@my_dataclass_eq_default()
class ImplicitEq2:
    field: int

    def __eq__(self, other):
        pass


@my_dataclass(<warning descr="'eq' is ignored if the class already defines '__eq__' method">eq=True</warning>)
class ExplicitEq:
    field: int

    def __eq__(self, other):
        pass


# Unclear semantic
@my_dataclass_order_default()
class ImplicitOrder:
    field: int

    def __gt__(self, other):
        pass


@my_dataclass(<error descr="'order' should be False if the class defines one of order methods">order=True</error>)
class ExplicitOrder:
    field: int

    def __gt__(self, other):
        pass


@my_dataclass(<error descr="'unsafe_hash' should be False if the class defines '__hash__'">unsafe_hash=True</error>)
class ExplicitUnsafeHash:
    field: int

    def __hash__(self):
        pass


# Unclear semantic
@my_dataclass_frozen_default()
class ImplicitFrozen:
    field: int

    def __setattr__(self, name, val):
        pass


@my_dataclass(<error descr="'frozen' should be False if the class defines '__setattr__' or '__delattr__'">frozen=True</error>)
class ExplicitFrozen:
    field: int

    def __setattr__(self, name, val):
        pass
