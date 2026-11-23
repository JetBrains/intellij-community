// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.idea.TestFor
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.types.PyClassTypeImpl
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-94345"], classes = [PyClassTypeImpl::class])
@Subsystems.CodeCompletion
@Layers.Functional
class PyGetAttrLiteralNameCompletionTest : PyCodeInsightTestCase() {

  @Test
  fun `an instance offers the names of a Literal name parameter`() = assertOffered("""
    from typing import Literal

    class C:
        def __getattr__(self, name: Literal["alpha", "beta"]) -> int: ...

    C().
    #   └ CARET
  """, "alpha", "beta")

  @Test
  fun `an instance offers the names of a Literal type alias`() = assertOffered("""
    from typing import Literal

    Names = Literal["alpha", "beta"]

    class C:
        def __getattr__(self, name: Names) -> int: ...

    C().
    #   └ CARET
  """, "alpha", "beta")

  @Test
  fun `an instance offers the names of all overloads`() = assertOffered("""
    from typing import Literal, overload

    class C:
        @overload
        def __getattr__(self, name: Literal["alpha"]) -> int: ...
        @overload
        def __getattr__(self, name: Literal["beta"]) -> str: ...
        def __getattr__(self, name): ...

    C().
    #   └ CARET
  """, "alpha", "beta")

  @Test
  fun `an instance offers the names of an overloaded __getattr__ in another file`() {
    val variants = runInEdtAndGet {
      myFixture.addFileToProject("mod.py", """
        from typing import Literal, overload

        class C:
            @overload
            def __getattr__(self, name: Literal["alpha"]) -> int: ...
            @overload
            def __getattr__(self, name: Literal["beta"]) -> str: ...
            def __getattr__(self, name): ...
      """.trimIndent())
      configureWithCaret("""
        from mod import C

        C().
        #   └ CARET
      """)
      myFixture.completeBasic()
      myFixture.lookupElementStrings.orEmpty()
    }
    assertEquals(emptyList<String>(), listOf("alpha", "beta").filterNot { it in variants }, "Missing in $variants")
  }

  @Test
  fun `an instance of a subclass offers the names of an inherited __getattr__`() = assertOffered("""
    from typing import Literal

    class Base:
        def __getattr__(self, name: Literal["alpha"]) -> int: ...

    class C(Base):
        pass

    C().
    #   └ CARET
  """, "alpha")

  @Test
  fun `an overriding __getattr__ adds its names to the names of the overridden one`() = assertOffered("""
    from typing import Literal

    class Base:
        def __getattr__(self, name: Literal["alpha"]) -> int: ...

    class C(Base):
        def __getattr__(self, name: Literal["beta"]) -> int: ...

    C().
    #   └ CARET
  """, "alpha", "beta")

  @Test
  fun `an overriding __getattr__ with a str name parameter keeps the names of the overridden one`() = assertOffered("""
    from typing import Literal

    class Base:
        def __getattr__(self, name: Literal["alpha"]) -> int: ...

    class C(Base):
        def __getattr__(self, name: str) -> int: ...

    C().
    #   └ CARET
  """, "alpha")

  @Test
  fun `the overriding __getattr__ gives the variant of a name that both take`() {
    val element = completeSingle("alpha", """
      from typing import Literal

      class Base:
          def __getattr__(self, name: Literal["alpha"]) -> int: ...

      class C(Base):
          def __getattr__(self, name: Literal["alpha", "beta"]) -> int: ...

      C().
      #   └ CARET
    """)
    assertEquals("C", (element.psiElement as PyFunction).containingClass?.name)
  }

  @Test
  fun `an attribute of a later base wins over a __getattr__ name of an earlier base`() {
    val element = completeSingle("foo", """
      from typing import Literal

      class A:
          def __getattr__(self, name: Literal["foo"]) -> int: ...

      class B:
          foo: str

      class C(A, B):
          pass

      C().
      #   └ CARET
    """)
    assertInstanceOf(PyTargetExpression::class.java, element.psiElement)
  }

  @Test
  fun `an attribute of a later base of a class object wins over a __getattr__ name of the metaclass`() {
    val element = completeSingle("objects", """
      from typing import Literal

      class Meta(type):
          def __getattr__(cls, name: Literal["objects"]) -> int: ...

      class Base(metaclass=Meta):
          pass

      class Mixin:
          objects: str

      class C(Base, Mixin):
          pass

      C.
      # └ CARET
    """)
    assertInstanceOf(PyTargetExpression::class.java, element.psiElement)
  }

  @Test
  fun `an inherited attribute wins over a __getattr__ name of the subclass`() {
    val element = completeSingle("foo", """
      from typing import Literal

      class Base:
          foo: str

      class C(Base):
          def __getattr__(self, name: Literal["foo"]) -> int: ...

      C().
      #   └ CARET
    """)
    assertInstanceOf(PyTargetExpression::class.java, element.psiElement)
  }

  @Test
  fun `a declared attribute wins over a __getattr__ name of the same class`() {
    val element = completeSingle("alpha", """
      from typing import Literal

      class C:
          alpha: str

          def __getattr__(self, name: Literal["alpha"]) -> int: ...

      C().
      #   └ CARET
    """)
    assertInstanceOf(PyTargetExpression::class.java, element.psiElement)
  }

  @Test
  fun `a str name parameter adds no name`() {
    val variants = variants("""
      class C:
          x: int

          def __getattr__(self, name: str) -> int: ...

      C().
      #   └ CARET
    """)
    assertEquals(listOf("x"), variants.filterNot { it.startsWith("_") })
  }

  @Test
  fun `a name that is not an identifier or not a str is not offered`() = assertOffered("""
    from typing import Literal

    class C:
        def __getattr__(self, name: Literal["alpha", "foo-bar", "class", "1x", b"beta", 1]) -> int: ...

    C().
    #   └ CARET
  """, "alpha", absent = listOf("foo-bar", "class", "1x", "beta", "1"))

  @Test
  fun `a position-only separator before the name parameter is skipped`() = assertOffered("""
    from typing import Literal

    class C:
        def __getattr__(self, /, name: Literal["alpha"]) -> int: ...

    C().
    #   └ CARET
  """, "alpha")

  @Test
  fun `a star parameter adds no name`() = assertOffered("""
    from typing import Literal

    class C:
        x: int

        def __getattr__(self, *args: Literal["alpha"]) -> int: ...

    C().
    #   └ CARET
  """, "x", absent = listOf("alpha"))

  @Test
  fun `a protected name is not offered without a prefix`() = assertOffered("""
    from typing import Literal

    class C:
        def __getattr__(self, name: Literal["alpha", "_hidden"]) -> int: ...

    C().
    #   └ CARET
  """, "alpha", absent = listOf("_hidden"))

  @Test
  fun `a super call does not offer the names`() = assertOffered("""
    from typing import Literal

    class Base:
        x: int

        def __getattr__(self, name: Literal["alpha"]) -> int: ...

    class C(Base):
        def m(self):
            super().
    #               └ CARET
  """, "x", absent = listOf("alpha"))

  @Test
  fun `a class object does not offer the names of its own __getattr__`() = assertOffered("""
    from typing import Literal

    class C:
        x: int

        def __getattr__(self, name: Literal["alpha"]) -> int: ...

    C.
    # └ CARET
  """, "x", absent = listOf("alpha"))

  @Test
  fun `a class object offers the names of the __getattr__ of its metaclass`() = assertOffered("""
    from typing import Literal

    class Meta(type):
        def __getattr__(cls, name: Literal["objects"]) -> int: ...

    class C(metaclass=Meta):
        pass

    C.
    # └ CARET
  """, "objects")

  @Test
  fun `a class object of a subclass offers the names of the __getattr__ of the inherited metaclass`() = assertOffered("""
    from typing import Literal

    class Meta(type):
        def __getattr__(cls, name: Literal["objects"]) -> int: ...

    class Base(metaclass=Meta):
        pass

    class C(Base):
        pass

    C.
    # └ CARET
  """, "objects")

  @Test
  fun `an instance does not offer the names of the __getattr__ of its metaclass`() = assertOffered("""
    from typing import Literal

    class Meta(type):
        def __getattr__(cls, name: Literal["objects"]) -> int: ...

    class C(metaclass=Meta):
        x: int

    C().
    #   └ CARET
  """, "x", absent = listOf("objects"))

  @Test
  fun `the variant shows the class of the __getattr__ and no tail text`() {
    val presentation = runInEdtAndGet {
      LookupElementPresentation.renderElement(completeSingle("alpha", """
        from typing import Literal

        class Base:
            def __getattr__(self, name: Literal["alpha"]) -> int: ...

        class C(Base):
            pass

        C().
        #   └ CARET
      """))
    }
    assertEquals("alpha", presentation.itemText)
    assertEquals("Base", presentation.typeText)
    assertNull(presentation.tailText)
  }

  @Test
  fun `the variant navigates to the __getattr__`() {
    val element = completeSingle("alpha", """
      from typing import Literal

      class C:
          def __getattr__(self, name: Literal["alpha"]) -> int: ...

      C().
      #   └ CARET
    """)
    assertInstanceOf(PyFunction::class.java, element.psiElement)
  }

  @Test
  fun `the insert adds no parentheses`() = runInEdtAndWait {
    configureWithCaret("""
      from typing import Literal

      class C:
          def __getattr__(self, name: Literal["alpha"]) -> int: ...

      C().alp
      #      └ CARET
    """)
    myFixture.completeBasic()
    myFixture.checkResult("""
      from typing import Literal

      class C:
          def __getattr__(self, name: Literal["alpha"]) -> int: ...

      C().alpha<caret>
    """.trimIndent())
  }

  private fun assertOffered(@Language("Python") text: String, vararg names: String, absent: List<String> = emptyList()) {
    val variants = variants(text)
    assertEquals(emptyList<String>(), names.filterNot { it in variants }, "Missing in $variants")
    assertEquals(emptyList<String>(), absent.filter { it in variants }, "Unexpected in $variants")
  }

  private fun variants(@Language("Python") text: String): List<String> = runInEdtAndGet {
    configureWithCaret(text)
    myFixture.completeBasic()
    myFixture.lookupElementStrings.orEmpty()
  }

  private fun completeSingle(name: String, @Language("Python") text: String): LookupElement = runInEdtAndGet {
    configureWithCaret(text)
    myFixture.completeBasic()
    val elements = myFixture.lookupElements.orEmpty().filter { it.lookupString == name }
    assertEquals(1, elements.size, "Variants named $name: $elements")
    elements.single()
  }
}
