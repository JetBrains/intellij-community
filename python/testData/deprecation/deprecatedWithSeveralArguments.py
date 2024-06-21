from warnings import deprecated

@deprecated("Deprecated class", category=DeprecationWarning, stacklevel=1)
class MyClass:
    pass

var = <warning descr="Deprecated class">MyClass</warning>()