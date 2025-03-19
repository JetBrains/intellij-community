import typing

@typing.dataclass_transform(kw_only_default=True, order_default=True)
def create_model(
    *,
    frozen: bool = False,
    kw_only: bool = True,
) -> Callable[[Type[_T]], Type[_T]]: ...

@create_model(frozen=True, kw_only=False)
class CustomerModel:
    id: int
    name: str