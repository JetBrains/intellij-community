from dataclasses import dataclass


@dataclass
class Person:
    name: str
    age: int
    birth_location: str


Person(name="Bob",
       a<caret>ge=42,
       birth_location="New York")