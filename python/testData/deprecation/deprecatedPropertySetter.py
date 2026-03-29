#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class Foo:
    def __init__(self):
        self._value = None

    @property
    def value(self):
        return self._value

    @value.setter
    def value(self, new_value):
        import warnings
        warnings.warn("this setter is deprecated", DeprecationWarning, 2)
        self._value = new_value

foo = Foo()
foo.<warning descr="this setter is deprecated">value</warning> = 1
