from typing import Any


class A:
    a = 1

class Anish(Any):
    pass

a = A() if bool() else Anish()

_ = a.a
_ = a.<weak_warning descr="Member 'A' of 'A | Anish' does not have attribute 'b'">b</weak_warning>