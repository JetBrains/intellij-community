// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyBuiltinCache

class PyGotoTypeDeclarationTest : PyTestCase() {

  // PY-41452
  fun testSimple() {
    val type = findSymbolType(
      "class Foo:\n" +
      "    pass\n" +
      "foo = Foo()\n" +
      "f<caret>oo"
    )

    assertEquals((myFixture.file as PyFile).findTopLevelClass("Foo"), type)
  }

  // PY-41452
  fun testGeneric() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      val type = findSymbolType(
        "from typing import List\n" +
        "foo: List[int] = undefined\n" +
        "f<caret>oo"
      )

      assertEquals(PyBuiltinCache.getInstance(myFixture.file).listType?.pyClass, type)
    }
  }

  // PY-41452
  fun testUnion() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      val types = findSymbolTypes(
        "from typing import Union\n" +
        "def foo() -> Union[str, int]\n" +
        "    pass\n" +
        "b<caret>ar = foo()"
      )

      val cache = PyBuiltinCache.getInstance(myFixture.file)
      assertContainsElements(types, cache.intType?.pyClass, cache.strType?.pyClass)
    }
  }

  // PY-41452
  fun testUnionOfTypingLiterals() {
    runWithLanguageLevel(LanguageLevel.getLatest()) {
      val type = findSymbolType(
        "from typing import Literal\n" +
        "def foo() -> Literal[\"a\", \"b\"]\n" +
        "    pass\n" +
        "b<caret>ar = foo()"
      )

      assertEquals(PyBuiltinCache.getInstance(myFixture.file).strType?.pyClass, type)
    }
  }

  // PY-41452
  fun testModule() {
    val type = findSymbolType("import typing\ntyp<caret>ing")
    assertEquals("types.ModuleType", (type as PyClass).qualifiedName)
  }

  private fun findSymbolType(text: String): PsiElement = findSymbolTypes(text).single()

  private fun findSymbolTypes(text: String): List<PsiElement> {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    return GotoTypeDeclarationAction.findSymbolTypes(myFixture.editor, myFixture.caretOffset).asList()
  }
}