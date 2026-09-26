"""
Tests for usage of the typing.Self type with attributes.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#use-in-attribute-annotations

from typing import TypeVar, Generic, Self
from dataclasses import dataclass


T = TypeVar("T")

@dataclass
class LinkedList(Generic[T]):
    value: T
    next: Self | None = None


@dataclass
class OrdinalLinkedList(LinkedList[int]):
    def ordinal_value(self) -> str:
        return str(self.value)


# This should result in a type error.
xs = OrdinalLinkedList(value=1, next=LinkedList[int](value=2))  # E

if xs.next is not None:
    xs.next = OrdinalLinkedList(value=3, next=None)  # OK

    # This should result in a type error.
    xs.next = LinkedList[int](value=3, next=None)  # E
