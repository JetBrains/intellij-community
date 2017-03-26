from typing import Optional, TypeVar


T = TypeVar('T', int)


def expects_int_subclass_or_none(x: Optional[T]):
    pass


expects_int_subclass_or_none(<weak_warning descr="Expected type 'Optional[Any]' (matched generic type 'Optional[TypeVar('T', int)]'), got 'str' instead">'foo'</weak_warning>)