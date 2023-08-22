from dataclasses import dataclass, field

@dataclass
class B:
    a: int
    b: int = 0

@dataclass
class A1(B):
    a: int
    c: int = 1
    b: int

@dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">A2</error>(B):
    a: int
    c: int
    b: int

@dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">A3</error>(B):
    a: int
    b: int
    c: int

@dataclass
class A4(B):
    a: int
    b: int
    c: int = 1

@dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">A5</error>(B):
    a: int
    <error descr="Fields with a default value must come after any fields without a default.">c</error>: int = 1
    <error descr="Fields with a default value must come after any fields without a default.">d</error>: int = 1
    b: int
    e: int

@dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">A6</error>(B):
    a: int
    <error descr="Fields with a default value must come after any fields without a default.">c</error>: int = 1
    d: int
    e: int
    b: int = 1

@dataclass
class <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">A7</error>(B):
    a: int
    b: int
    <error descr="Fields with a default value must come after any fields without a default.">c</error>: int = 1
    <error descr="Fields with a default value must come after any fields without a default.">d</error>: int = 1
    e: int
