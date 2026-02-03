from decorator import my_dataclass_order_default, my_dataclass


@my_dataclass(<error descr="'eq' must be true if 'order' is true">eq=False</error>, order=True)
class A1:
    x: int


@my_dataclass(eq=False)
class A2:
    x: int


@my_dataclass(eq=False, order=False)
class A3:
    x: int


@my_dataclass_order_default(<error descr="'eq' must be true if 'order' is true">eq=False</error>)
class A4:
    x: int
