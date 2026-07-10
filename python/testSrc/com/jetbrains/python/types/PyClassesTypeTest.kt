// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyClassesTypeTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-79173"])
  fun `init subclass argument type mismatch`() = test("""
    class A:
        def __init_subclass__(cls, a: int): ...
    
    
    class B(A, a=123): ...
    
    class C(A, a="str"): ...
    #          ^^^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead
    """)
}
