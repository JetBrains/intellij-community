class A:
    pass

assert isinstance(A(), <warning descr="Python versions 2.7, 3.7, 3.8, 3.9 do not allow writing union types as X | Y">(int<caret> | str | (list[str] | bool | float)) | dict[str, int]</warning>)