from __future__ import annotations

from google.protobuf.struct_pb2 import ListValue, Struct

list_value = ListValue()

lst = list(list_value)  # Ensure type checkers recognise that the class is iterable (doesn't have an `__iter__` method at runtime)

list_value[0] = 42.42
list_value[0] = "42"
list_value[0] = None
list_value[0] = True
list_value[0] = [42.42, "42", None, True, [42.42, "42", None, True], {"42": 42}]
list_value[0] = ListValue()
list_value[0] = Struct()

list_element = list_value[0]
