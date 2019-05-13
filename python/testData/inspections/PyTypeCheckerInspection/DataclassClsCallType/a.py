from dataclasses import dataclass


@dataclass
class Point:
    x: int
    y: int

    @classmethod
    def from_str(cls, string: str) -> 'Point':
        return cls(1, 2)