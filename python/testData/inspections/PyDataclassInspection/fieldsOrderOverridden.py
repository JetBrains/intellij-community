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
class A2(B):
    a: int
    <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">c</error>: int
    b: int

@dataclass
class A3(B):
    a: int
    b: int
    <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">c</error>: int

@dataclass
class A4(B):
    a: int
    b: int
    c: int = 1

@dataclass
class A5(B):
    a: int
    <error descr="Fields with a default value must come after any fields without a default.">c</error>: int = 1
    <error descr="Fields with a default value must come after any fields without a default.">d</error>: int = 1
    b: int
    <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">e</error>: int

@dataclass
class A6(B):
    a: int
    <error descr="Fields with a default value must come after any fields without a default.">c</error>: int = 1
    <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">d</error>: int
    e: int
    b: int = 1

@dataclass
class A7(B):
    a: int
    b: int
    <error descr="Fields with a default value must come after any fields without a default.">c</error>: int = 1
    <error descr="Fields with a default value must come after any fields without a default.">d</error>: int = 1
    <error descr="Non-default argument(s) follows default argument(s) defined in 'B'">e</error>: int
