// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.navigation.actions.GotoTypeDeclarationAction
import com.intellij.psi.PsiElement
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.fixtures.PyTestCase
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
    val type = findSymbolType(
      "from typing import List\n" +
      "foo: List[int] = undefined\n" +
      "f<caret>oo"
    )

    assertEquals(PyBuiltinCache.getInstance(myFixture.file).listType?.pyClass, type)
  }

  // PY-41452
  fun testUnion() {
    val types = findSymbolTypes(
      "from typing import Union\n" +
      "def foo() -> Union[str, int]\n" +
      "    pass\n" +
      "b<caret>ar = foo()"
    )

    val cache = PyBuiltinCache.getInstance(myFixture.file)
    assertContainsElements(types, cache.intType?.pyClass, cache.strType?.pyClass)
  }

  // PY-41452
  fun testUnionOfTypingLiterals() {
    val type = findSymbolType(
      "from typing import Literal\n" +
      "def foo() -> Literal[\"a\", \"b\"]\n" +
      "    pass\n" +
      "b<caret>ar = foo()"
    )

    assertEquals(PyBuiltinCache.getInstance(myFixture.file).strType?.pyClass, type)
  }

  // PY-41452
  fun testModule() {
    val type = findSymbolType("import typing\ntyp<caret>ing")
    assertEquals("types.ModuleType", (type as PyClass).qualifiedName)
  }

  // PY-41452
  fun testTypingNewType() {
    val type = findSymbolType(
      "from typing import NewType\n" +
      "NT = NewType(\"NT\", int)\n" +
      "def foo(b<caret>ar: NT) -> None:\n" +
      "  pass"
    )

    assertEquals((myFixture.file as PyFile).findTopLevelAttribute("NT"), type)
  }

  // PY-41452
  fun testNamedTupleClass() {
    val type = findSymbolType(
      "from typing import NamedTuple\n" +
      "class MyNT(NamedTuple):\n" +
      "    field_1: int\n" +
      "    field_2: str\n" +
      "my_n<caret>t: MyNT = undefined"
    )

    assertEquals((myFixture.file as PyFile).findTopLevelClass("MyNT"), type)
  }

  // PY-41452
  fun testNamedTupleTarget() {
    val type = findSymbolType(
      "from typing import NamedTuple\n" +
      "Employee = NamedTuple('Employee', [('name', str), ('id', int)])\n" +
      "my_n<caret>t: Employee = undefined"
    )

    assertEquals((myFixture.file as PyFile).findTopLevelAttribute("Employee"), type)
  }

  // PY-41452
  fun testTypedDictClass() {
    val type = findSymbolType(
      "from typing import TypedDict\n" +
      "class MyDict(TypedDict):\n" +
      "    field_1: int\n" +
      "    field_2: str\n" +
      "my_d<caret>ict: MyDict = undefined"
    )

    assertEquals((myFixture.file as PyFile).findTopLevelClass("MyDict"), type)
  }

  // PY-41452
  fun testTypedDictTarget() {
    val type = findSymbolType(
      "from typing import TypedDict\n" +
      "Movie = TypedDict('Movie', {'name': str, 'year': int})\n" +
      "my_d<caret>ict: Movie = undefined"
    )

    assertEquals((myFixture.file as PyFile).findTopLevelAttribute("Movie"), type)
  }

  private fun findSymbolType(text: String): PsiElement = findSymbolTypes(text).single()

  private fun findSymbolTypes(text: String): List<PsiElement> {
    myFixture.configureByText(PythonFileType.INSTANCE, text)
    return GotoTypeDeclarationAction.findSymbolTypes(myFixture.editor, myFixture.caretOffset)?.asList() ?: emptyList()
  }
}