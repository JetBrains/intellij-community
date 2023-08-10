from typing import NewType, Dict

UserId = NewType("UserId", int)

a = UserId(42)
b = UserId(<warning descr="Expected type 'int', got 'LiteralString' instead">"John"</warning>)

KeyValue = NewType("KeyValue", Dict[str, int])

KeyValue({"key": 13})
KeyValue(<warning descr="Expected type 'dict[str, int]', got 'int' instead">42</warning>)
KeyValue(<warning descr="Expected type 'dict[str, int]', got 'dict[LiteralString, LiteralString]' instead">{"key1": "key2"}</warning>)
