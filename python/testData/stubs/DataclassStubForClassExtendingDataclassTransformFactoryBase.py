import typing

@typing.dataclass_transform(eq_default=True, order_default=True)
class ModelBase:
    def __init_subclass__(
        cls,
        *,
        init: bool = True,
        frozen: bool = False,
        eq: bool = True,
        order: bool = True,
    ):
        ...

class CustomerModel(
    ModelBase,
    init=False,
    frozen=True,
    eq=False,
    order=False,
):
    id: int
    name: str