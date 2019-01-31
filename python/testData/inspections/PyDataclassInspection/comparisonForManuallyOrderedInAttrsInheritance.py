from attr import dataclass

class A7:
    x: int = 1

    def __lt__(self, other):
        pass

@dataclass(cmp=True)
class B7(A7):
    y: str = "1"

print(A7() < B7())
print(B7() <error descr="'__lt__' not supported between instances of 'B7' and 'A7'"><</error> A7())
print(A7() < object())
print(B7() <error descr="'__lt__' not supported between instances of 'B7' and 'object'"><</error> object())

class A8:
    x: int = 1

    def __lt__(self, other):
        pass

@dataclass(cmp=False)
class B8(A8):
    y: str = "1"

print(A8() < B8())
print(B8() < A8())
print(A8() < object())
print(B8() < object())

@dataclass(cmp=True)
class A9:
    x: int = 1

class B9(A9):
    y: str = "1"

    def __lt__(self, other):
        pass

print(A9() < B9())
print(B9() < A9())
print(A9() <error descr="'__lt__' not supported between instances of 'A9' and 'object'"><</error> object())
print(B9() < object())

@dataclass(cmp=False)
class A10:
    x: int = 1

class B10(A10):
    y: str = "1"

    def __lt__(self, other):
        pass

print(A10() <error descr="'__lt__' not supported between instances of 'A10' and 'B10'"><</error> B10())
print(B10() < A10())
print(A10() <error descr="'__lt__' not supported between instances of 'A10' and 'object'"><</error> object())
print(B10() < object())