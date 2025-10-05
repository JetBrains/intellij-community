from typing import Protocol

class Ageholder(Protocol):
    age: int


class Person:
  def __init__(self, name: str):
    self.name = name
    pass


def f(p: Person, salutation: str, ageHolder: Ageholder):
    return <selection>salutation + p.name + "(" + ageHolder.age + ")"</selection>

