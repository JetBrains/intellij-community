from typing import NewType, Dict, List

KeyValue = NewType('KeyValue', Dict[str, int])

def f(x: KeyValue):
    pass

kv = KeyValue({'a': 1})

def g(x: Dict[str, str]):
    pass

g(<error descr="Expected type 'dict[str, str]', got 'KeyValue' instead">kv</error>)
