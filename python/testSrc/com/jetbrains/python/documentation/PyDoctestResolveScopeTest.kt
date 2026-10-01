// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.doctest.PyDoctestReference
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * Resolve of a name in a doctest. Python runs a doctest with a copy of the module globals, so a name of the function that
 * owns the docstring is not visible.
 */
@TestFor(classes = [PyDoctestReference::class])
@Subsystems.CodeInsight
@Components.Docstrings
@Layers.Functional
class PyDoctestResolveScopeTest : PyCodeInsightTestCase() {

  @Test
  fun `doctest name does not resolve to a parameter of the documented function`() = test("""
    def func(param):
        '''
        >>> param # WARNING FIXME Unresolved reference 'param'
        '''
    """)

  @Test
  fun `doctest name does not resolve to a local of the documented function`() = test("""
    def func():
        '''
        >>> local # WARNING FIXME Unresolved reference 'local'
        '''
        local = 1
    """)
}
