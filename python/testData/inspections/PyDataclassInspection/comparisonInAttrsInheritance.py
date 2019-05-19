from attr import dataclass

@dataclass(cmp=True)
class A1:
    x: int = 1

@dataclass(cmp=False)
class B1(A1):
    y: str = "1"

print(A1() < B1())
print(B1() < B1())

@dataclass(cmp=False)
class A2:
    x: int = 1

@dataclass(cmp=True)
class B2(A2):
    y: str = "1"

print(A2() <error descr="'__lt__' not supported between instances of 'A2' and 'B2'"><</error> B2())
print(B2() < B2())

@dataclass(cmp=False)
class A3:
    x: int = 1

@dataclass(cmp=False)
class B3(A3):
    y: str = "1"

print(A3() <error descr="'__lt__' not supported between instances of 'A3' and 'B3'"><</error> B3())
print(B3() <error descr="'__lt__' not supported between instances of 'B3'"><</error> B3())

@dataclass(cmp=True)
class A4:
    x: int = 1

@dataclass(cmp=True)
class B4(A4):
    y: str = "1"

print(A4() < B4())
print(B4() < B4())

class A5:
    x: int = 1

@dataclass(cmp=True)
class B5(A5):
    y: str = "1"

print(A5() <error descr="'__lt__' not supported between instances of 'A5' and 'B5'"><</error> B5())
print(B5() < B5())

@dataclass(cmp=True)
class A6:
    x: int = 1

class B6(A6):
    y: str = "1"

print(A6() < B6())
print(B6() < B6())
