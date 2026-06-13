// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

class PyClassesTypeTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-79173"])
  fun `init subclass argument type mismatch`() = test("""
    class A:
        def __init_subclass__(cls, a: int): ...
    
    
    class B(A, a=123): ...
    
    class C(A, a="str"): ...
    #          ^^^^^^^ WARNING Expected type 'int', got 'str' instead
    """)
}
