#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

class Foo:
    @property
    def value(self): ...

    @value.setter
    def value(self, new_value):
        import warnings
        warnings.warn("this setter is deprecated in stub", DeprecationWarning, 2)
