from typing import Type, Union

class MyClass:
    pass

def expects_myclass_or_str1(x: Type[Union[MyClass, str]]):
    pass

expects_myclass_or_str1(MyClass)
expects_myclass_or_str1(str)
expects_myclass_or_str1(<warning descr="Expected type 'type[MyClass | str]', got 'type[int]' instead">int</warning>)
expects_myclass_or_str1(<warning descr="Expected type 'type[MyClass | str]', got 'int' instead">42</warning>)


def expects_myclass_or_str2(x: Union[Type[MyClass], Type[str]]):
    pass

expects_myclass_or_str2(MyClass)
expects_myclass_or_str2(str)
expects_myclass_or_str2(<warning descr="Expected type 'type[MyClass | str]', got 'type[int]' instead">int</warning>)
expects_myclass_or_str2(<warning descr="Expected type 'type[MyClass | str]', got 'int' instead">42</warning>)
