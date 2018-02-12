from typing import NewType, Dict

UserId = NewType("UserId", int)

a = UserId(42)
b = UserId(<warning descr="Expected type 'int', got 'str' instead">"John"</warning>)

KeyValue = NewType("KeyValue", Dict[str, int])

KeyValue({"key": 13})
KeyValue(<warning descr="Expected type 'Dict[str, int]', got 'int' instead">42</warning>)
KeyValue(<warning descr="Expected type 'Dict[str, int]', got 'Dict[str, str]' instead">{"key1": "key2"}</warning>)
