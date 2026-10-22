// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElementTypeHolderEP
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyElement
import com.jetbrains.python.psi.PyStubElementType
import com.jetbrains.python.psi.impl.PyBaseElementImpl
import com.jetbrains.python.psi.impl.stubs.PyLegacyStubElementType
import java.lang.reflect.Modifier

/**
 * The deprecated constants of [PyStubElementTypes] stay a [PyStubElementType] for an external plugin.
 * Each one must delegate to the plain element type of [PyElementTypes] with the same name, and the deprecated accessors of
 * a PSI element and of a stub must return it.
 */
@Subsystems.CodeInsight
@Components.Parsing
@Layers.Functional
@Suppress("DEPRECATION")
class PyStubElementTypesLegacyTest : PyTestCase() {
  private val legacyTypes: Map<String, PyStubElementType<*, *>>
    get() = PyStubElementTypes::class.java.declaredFields
      .filter { Modifier.isStatic(it.modifiers) && !it.isSynthetic }
      .associate { it.name to (it.get(null) as PyStubElementType<*, *>) }

  private val plainTypes: Map<String, IElementType>
    get() = PyElementTypes::class.java.declaredFields
      .filter { Modifier.isStatic(it.modifiers) && IElementType::class.java.isAssignableFrom(it.type) }
      .associate { it.name to (it.get(null) as IElementType) }

  fun testExternalIdsMatchTheHolderPrefix() {
    val holder = StubElementTypeHolderEP.EP_NAME.extensionList.single { it.holderClass == PyStubElementTypes::class.java.name }
    assertEquals(PyLegacyStubElementType.EXTERNAL_ID_PREFIX, holder.externalIdPrefix)
    for ((name, type) in legacyTypes) {
      assertEquals(PyLegacyStubElementType.EXTERNAL_ID_PREFIX + name, type.externalId)
      assertEquals(name, type.debugName)
    }
  }

  fun testEveryLegacyTypeHasAPlainCounterpart() {
    val plain = plainTypes
    for ((name, type) in legacyTypes) {
      val counterpart = plain[name] ?: fail("No PyElementTypes.$name for PyStubElementTypes.$name")
      assertNotSame("PyStubElementTypes.$name must be a separate object", counterpart, type)
    }
  }

  fun testEveryPlainTypeWithAStubMapsToItsLegacyType() {
    val plain = plainTypes
    for ((name, legacyType) in legacyTypes) {
      assertSame("PyLegacyStubElementType.forElementType must give PyStubElementTypes.$name", legacyType,
                 PyLegacyStubElementType.forElementType(plain.getValue(name)))
    }
    assertNull(PyLegacyStubElementType.forElementType(PyElementTypes.ARGUMENT_LIST))
  }

  fun testDeprecatedAccessorsReturnTheLegacyType() {
    val plain = plainTypes
    val legacy = legacyTypes
    runWithLanguageLevel(LanguageLevel.PYTHON312) {
      myFixture.configureByText(PythonFileType.INSTANCE, STUB_ELEMENTS_TEXT)
      val coveredElements = HashSet<String>()
      for (element in PsiTreeUtil.findChildrenOfType(myFixture.file, PyBaseElementImpl::class.java)) {
        val name = plain.entries.firstOrNull { it.value === element.node.elementType }?.key ?: continue
        val legacyType = legacy[name] ?: continue
        coveredElements.add(name)
        assertSame("getElementType() of a $name element", legacyType, element.elementType)
        assertSame("getIElementType() of a $name element", plain.getValue(name), element.iElementType)
      }
      assertEquals(legacy.keys - "TUPLE_PARAMETER", coveredElements)

      val coveredStubs = HashSet<String>()
      for (stub in (myFixture.file as PsiFileImpl).calcStubTree().plainList) {
        val name = plain.entries.firstOrNull { it.value === stub.elementType }?.key ?: continue
        coveredStubs.add(name)
        assertSame("getStubType() of a $name stub", legacy.getValue(name), stub.stubType)
      }
      assertEquals(legacy.keys - "TUPLE_PARAMETER", coveredStubs)
    }
  }

  fun testLegacyTypeCreatesTheSamePsiAsThePlainType() {
    val plain = plainTypes
    val legacy = legacyTypes
    runWithLanguageLevel(LanguageLevel.PYTHON312) {
      myFixture.configureByText(PythonFileType.INSTANCE, STUB_ELEMENTS_TEXT)
      val covered = HashSet<String>()
      for (element in PsiTreeUtil.findChildrenOfType(myFixture.file, PyElement::class.java)) {
        val node = element.node
        val name = plain.entries.firstOrNull { it.value === node.elementType }?.key ?: continue
        val legacyType = legacy[name] ?: continue
        covered.add(name)
        assertSame("PyStubElementTypes.$name creates another PSI class", element.javaClass, legacyType.createElement(node).javaClass)
      }
      assertEquals(legacy.keys - "TUPLE_PARAMETER", covered)
    }
  }

  private companion object {
    /** One element of each stub kind, apart from the Python 2 tuple parameter. */
    const val STUB_ELEMENTS_TEXT = """
      from os import path as p
      from sys import *
      import os

      @dec
      class C[T]:
          x: int = 1

          def f(self, a, /, *, b: int = 2, **kw) -> None:
              try:
                  pass
              except E:
                  pass

      type Alias = int
    """.trimIndent()
  }
}
