from typing import TypedDict


class Employee(TypedDict, total=False):
    name: str
    id: int


em = Employee()
em = Employee(id=42)
