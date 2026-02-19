from decorator import my_dataclass_order_default, my_dataclass


@my_dataclass_order_default()
class A1:
    x: int = 0


@my_dataclass()
class A2:
    y: int = 0


print(A1(1) <error descr="'__lt__' not supported between instances of 'A1' and 'A2'"><</error> A2(2))
print(A1(1) <error descr="'__le__' not supported between instances of 'A1' and 'A2'"><=</error> A2(2))
print(A1(1) <error descr="'__gt__' not supported between instances of 'A1' and 'A2'">></error> A2(2))
print(A1(1) <error descr="'__ge__' not supported between instances of 'A1' and 'A2'">>=</error> A2(2))


print(A1 < A2)
print(A1 <= A2)
print(A1 > A2)
print(A1 >= A2)
