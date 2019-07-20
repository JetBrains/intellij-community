from b import A

<warning descr="'a' is 'Final' and could not be reassigned">A.a</warning> = 4

class B(A):
    @classmethod
    def my_cls_method(cls):
        <warning descr="'a' is 'Final' and could not be reassigned">cls.a</warning> = 6

<warning descr="'a' is 'Final' and could not be reassigned">B.a</warning> = 7

class C(A):
    <warning descr="'A.a' is 'Final' and could not be reassigned">a</warning> = 8