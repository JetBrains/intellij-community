#  Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Callable

class C[**P: [int] = [bool]]:
    def f(self) -> Callable[P<the_ref>, None]: ...