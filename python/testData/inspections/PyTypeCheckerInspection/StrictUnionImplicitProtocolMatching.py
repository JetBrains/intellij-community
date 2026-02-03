from typing import Any


class A:
    def __iter__(self):
        return self

    def __next__(self):
        return 42


class B:
    def __iter__(self):
        return self

    def __next__(self):
        return 42


class C:
    pass


def all_union_members_match_no_any(iterable: A | B):
    for _ in iterable:
        pass


def some_union_members_match_no_any(iterable: A | B | None):
    for _ in <warning descr="Expected type 'collections.Iterable', got 'A | B | None' instead">iterable</warning>:
        pass


def all_union_members_dont_match_no_any(iterable: C | None):
    for _ in <warning descr="Expected type 'collections.Iterable', got 'C | None' instead">iterable</warning>:
        pass


def all_union_members_match_with_any(iterable: A | B | Any):
    for _ in iterable:
        pass


def some_union_members_match_with_any(iterable: A | B | None | Any):
    for _ in <warning descr="Expected type 'collections.Iterable', got 'A | B | None | Any' instead">iterable</warning>:
        pass


def all_union_members_dont_match_with_any(iterable: C | None | Any):
    for _ in <warning descr="Expected type 'collections.Iterable', got 'C | None | Any' instead">iterable</warning>:
        pass
