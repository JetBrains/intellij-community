import attr
import typing
from dataclasses import dataclass


@dataclass
class StdlibModel:
    x: int


@attr.s
class AttrsModel:
    x = attr.ib()


@typing.dataclass_transform(eq_default=True, order_default=True)
class TransformBase:
    def __init_subclass__(
        cls,
        *,
        init: bool = True,
        frozen: bool = False,
        eq: bool = True,
        order: bool = True,
    ):
        ...


class TransformModel(
    TransformBase,
    init=False,
    frozen=True,
    eq=False,
    order=False,
):
    id: int
    name: str
