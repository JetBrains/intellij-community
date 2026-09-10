import re
from ipaddress import IPv4Address, IPv6Address
from pathlib import PurePath


class WithoutDunderMethods:
    pass


class WithDunderStr:
    def __str__(self):
        return "with str"


# The runtime `ipaddress.py` defines `__str__` on `_BaseAddress` and on `IPv6Address`.
str(IPv4Address("127.0.0.1"))
str(IPv6Address("::1"))
f"{IPv4Address('127.0.0.1')}"

# The runtime `pathlib.py` defines `__str__` on `PurePath`.
str(PurePath())

# The skeleton of the `builtins` binary module defines `__repr__`.
print("hello world")
str(123456)
repr(42)
repr([1, 2, 3])

# `re.py` binds `Pattern` to a binary type, so the IDE cannot read the methods of the type.
print(re.compile(""))

str(WithDunderStr())

# `zip` keeps the `object` methods, and `TYPES_WITHOUT_USEFUL_STRING_CONVERSION` holds it.
str(<weak_warning descr="Type 'zip[Any]' doesn't define '__str__' or '__repr__', so the result might not be useful">zip()</weak_warning>)
str(<weak_warning descr="Type 'WithoutDunderMethods' doesn't define '__str__' or '__repr__', so the result might not be useful">WithoutDunderMethods()</weak_warning>)
