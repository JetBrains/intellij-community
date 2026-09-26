from dataclasses import dataclass


@dataclass
class Person:
    name: str
    age: int
    birth_location: str


Person("Bob", 4<caret>2, "New York")