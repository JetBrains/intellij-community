from typing import Protocol

class Ageholder(Protocol):
    age: int


class Person:
  def __init__(self, name: str):
    self.name = name
    pass


def f(p: Person, salutation: str, ageHolder: Ageholder):
    return <caret>greeting(ageHolder, p, salutation)


def greeting(ageHolder_new: Ageholder, p_new: Person, salutation_new: str) -> str:
    return salutation_new + p_new.name + "(" + ageHolder_new.age + ")"

