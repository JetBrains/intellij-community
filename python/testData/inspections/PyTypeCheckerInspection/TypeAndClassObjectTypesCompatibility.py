from typing import TypeVar, Type

T = TypeVar('T')
S = TypeVar('T', str)


def expects_type(x: type):
    pass
    
    
def expects_typing_type(x: Type):
    expects_type(x)


def expects_typing_type_any(x: Type[Any]):
    expects_type(x)


def expects_any_type_via_type_var(x: Type[T]):
    expects_type(x)


def expects_str_class(x: Type[str]):
    expects_type(x)


def expects_str_subclass(x: Type[S]):
    expects_type(x)
    
    
def expects_object(x: object):
    expects_type(<warning descr="Expected type 'type', got 'object' instead">x</warning>)
    

expects_type(type)
expects_type(object)
expects_typing_type(type)
expects_typing_type_any(type)
expects_typing_type(object)
expects_str_class(<warning descr="Expected type 'type[str]', got 'type' instead">type</warning>)
expects_any_type_via_type_var(type)
expects_str_subclass(<warning descr="Expected type 'type[T ≤: str]', got 'type' instead">type</warning>)
expects_object(type)

    
    

