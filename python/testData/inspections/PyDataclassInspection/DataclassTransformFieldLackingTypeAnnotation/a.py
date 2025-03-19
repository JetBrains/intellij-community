from decorator import my_dataclass, my_field


@my_dataclass()
class A1:
    <error descr="Attribute 'a' lacks a type annotation">a</error> = my_field()
    b = 1
    c: int = 1
