from typing import Any


class A:
    def __pos__(self):
        pass

    def __add__(self, other):
        pass

    def __getitem__(self, item):
        pass


class B:
    def __pos__(self):
        pass

    def __add__(self, other):
        pass

    def __getitem__(self, item):
        pass


class C:
    pass


def all_union_members_match_no_any(x: A | B):
    print(+x)
    print(x + 1)
    print(x[42])


def some_union_members_match_no_any(x: A | B | None):
    print(<weak_warning descr="Some members of 'A | B | None' don't have attribute '__pos__'">+</weak_warning>x)
    print(x <weak_warning descr="Some members of 'A | B | None' don't have attribute '__add__'">+</weak_warning> 1)
    print(x<weak_warning descr="Some members of 'A | B | None' don't have attribute '__getitem__'">[</weak_warning>42])


def all_union_members_dont_match_no_any(x: C | None):
    print(<warning descr="Cannot find reference '+' in 'C | None'">+</warning>x)
    print(x <weak_warning descr="Some members of 'C | None' don't have attribute '__add__'">+</weak_warning> 1)
    print(x<warning descr="Cannot find reference '[' in 'C | None'">[</warning>42])


def all_union_members_match_with_any(x: A | B | Any):
    print(+x)
    print(x + 1)
    print(x[42])


def some_union_members_match_with_any(x: A | B | None | Any):
    print(<weak_warning descr="Some members of 'A | B | None | Any' don't have attribute '__pos__'">+</weak_warning>x)
    print(x <weak_warning descr="Some members of 'A | B | None | Any' don't have attribute '__add__'">+</weak_warning> 1)
    print(x<weak_warning descr="Some members of 'A | B | None | Any' don't have attribute '__getitem__'">[</weak_warning>42])


def all_union_members_dont_match_with_any(x: C | None | Any):
    print(<weak_warning descr="Some members of 'C | None | Any' don't have attribute '__pos__'">+</weak_warning>x)
    print(x <weak_warning descr="Some members of 'C | None | Any' don't have attribute '__add__'">+</weak_warning> 1)
    print(x<weak_warning descr="Some members of 'C | None | Any' don't have attribute '__getitem__'">[</weak_warning>42])
