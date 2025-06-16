#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import reveal_type


def example(x: int, y: float):
    reveal_type(x + y)/*<# float #>*/
    return x + y


reveal_type(example(1, 2.5))/*<# float #>*/
