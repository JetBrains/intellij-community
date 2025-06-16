from typing import Type, TypeVar, Optional

class MyClass:
    pass

def expects_myclass(x: Type[MyClass]):
    pass

expects_myclass(<warning descr="Expected type 'type[MyClass]', got 'MyClass' instead">MyClass()</warning>)
expects_class(MyClass)

T1 = TypeVar('T1')
def expects_any_class(x: Type[T1]):
    pass
    
expects_any_class(MyClass)
expects_any_class(<warning descr="Expected type 'type[T1]', got 'MyClass' instead">MyClass()</warning>)
expects_any_class(object)
expects_any_class(<warning descr="Expected type 'type[T1]', got 'object' instead">object()</warning>)

T2 = TypeVar('T2', MyClass)
def expects_myclass_descendant(x: Type[T2]):
    pass
    
expects_myclass_descendant(MyClass)
expects_myclass_descendant(<warning descr="Expected type 'type[T2 ≤: MyClass]', got 'MyClass' instead">MyClass()</warning>)
expects_myclass_descendant(<warning descr="Expected type 'type[T2 ≤: MyClass]', got 'type[object]' instead">object</warning>)
expects_myclass_descendant(<warning descr="Expected type 'type[T2 ≤: MyClass]', got 'object' instead">object()</warning>)

def expects_myclass_descendant_or_none(x: Optional[Type[T2]]):
    pass
    
expects_myclass_descendant_or_none(MyClass)
expects_myclass_descendant_or_none(<warning descr="Expected type 'type[T2 ≤: MyClass] | None', got 'MyClass' instead">MyClass()</warning>)
expects_myclass_descendant_or_none(<warning descr="Expected type 'type[T2 ≤: MyClass] | None', got 'type[object]' instead">object</warning>)
expects_myclass_descendant_or_none(<warning descr="Expected type 'type[T2 ≤: MyClass] | None', got 'object' instead">object()</warning>)