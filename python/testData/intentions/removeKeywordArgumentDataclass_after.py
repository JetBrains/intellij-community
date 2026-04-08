from dataclasses import dataclass


@dataclass
class Person:
    name: str
    age: int
    birth_location: str


Person("Bob", 42, birth_location="New York")