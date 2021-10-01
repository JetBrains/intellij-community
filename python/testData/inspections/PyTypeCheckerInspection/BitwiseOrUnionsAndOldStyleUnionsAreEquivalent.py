from typing import Union, Optional


def expect_old_union(u: Union[int, str]):
    expect_new_union(u)
    expect_new_union(42)
    expect_new_union("42")
    expect_new_union(<warning descr="Expected type 'int | str', got 'list[int]' instead">[42]</warning>)


def expect_new_union(u: int | str):
    expect_old_union(u)
    expect_old_union(42)
    expect_old_union("42")
    expect_old_union(<warning descr="Expected type 'int | str', got 'list[int]' instead">[42]</warning>)


def expect_old_optional(u: Optional[int]):
    expect_new_optional_none_first(u)
    expect_new_optional_none_first(42)
    expect_new_optional_none_first(None)
    expect_new_optional_none_first(<warning descr="Expected type 'int | None', got 'list[int]' instead">[42]</warning>)
    expect_new_optional_none_last(u)
    expect_new_optional_none_last(42)
    expect_new_optional_none_last(None)
    expect_new_optional_none_last(<warning descr="Expected type 'int | None', got 'list[int]' instead">[42]</warning>)


def expect_new_optional_none_first(u: None | int):
    expect_old_optional(u)
    expect_old_optional(42)
    expect_old_optional(None)
    expect_old_optional(<warning descr="Expected type 'int | None', got 'list[int]' instead">[42]</warning>)
    expect_new_optional_none_last(u)
    expect_new_optional_none_last(42)
    expect_new_optional_none_last(None)
    expect_new_optional_none_last(<warning descr="Expected type 'int | None', got 'list[int]' instead">[42]</warning>)


def expect_new_optional_none_last(u: int | None):
    expect_old_optional(u)
    expect_old_optional(42)
    expect_old_optional(None)
    expect_old_optional(<warning descr="Expected type 'int | None', got 'list[int]' instead">[42]</warning>)
    expect_new_optional_none_first(u)
    expect_new_optional_none_first(42)
    expect_new_optional_none_first(None)
    expect_new_optional_none_first(<warning descr="Expected type 'int | None', got 'list[int]' instead">[42]</warning>)
