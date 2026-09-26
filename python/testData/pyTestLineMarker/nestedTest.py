#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import pytest

class TestOuter<caret>most:
    class TestAlsoOuter:
        class TestInner:
            def test_(self):
                pass