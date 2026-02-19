from dataclasses import dataclass


@dataclass
class C:
    some_attr: int = 42


C(some_attr=3)
#   <ref>
