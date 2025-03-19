from typing_extensions import deprecated

@deprecated("Deprecated class")
class MyClass:
    pass

var = <warning descr="Deprecated class">MyClass</warning>()
