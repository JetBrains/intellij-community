#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import reveal_type


def example(x: int, y: float)/*<# -> float #>*/:
    return x + y


def gen(x: list[int])/*<# -> Generator[int, Any, str] #>*/:
    for i in x:
        yield i
    return "end"
