from typing import Protocol

class Ageholder(Protocol):
    age: int


class Person:
  def __init__(self, name: str):
    self.name = name
    pass


def f(p: Person, salutation: str, ageHolder: Ageholder):
    return <caret>greeting(ageHolder, p, salutation)


def greeting(ageHolder_new, p_new, salutation_new):
    return salutation_new + p_new.name + "(" + ageHolder_new.age + ")"

