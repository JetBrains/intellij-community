// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.icons.AllIcons
import com.intellij.idea.TestFor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@TestFor(issues = ["PY-91546"], classes = [PyCustomMember::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyCustomMemberTest : PyCodeInsightTestCase() {

  private val moduleContent = """
    class Outer:
        attr = 5

        def method(self):
            pass

    def top_level():
        pass
  """

  @Nested
  inner class PlainProperties {
    @Test
    fun `name is kept as given`() = withModule {
      assertEquals("member", PyCustomMember("member").name)
    }

    @Test
    fun `short type drops the package part`() = withModule {
      assertEquals("Outer", PyCustomMember("member", "mymod.Outer", false).shortType)
    }

    @Test
    fun `short type of an unqualified type is the type itself`() = withModule {
      assertEquals("Outer", PyCustomMember("member", "Outer", false).shortType)
    }

    @Test
    fun `short type of a member without a type is absent`() = withModule {
      assertNull(PyCustomMember("member").shortType)
    }

    @Test
    fun `a member is not a function or a class var by default`() = withModule {
      val member = PyCustomMember("member")
      assertFalse(member.isFunction)
      assertFalse(member.isClassVar)
    }

    @Test
    fun `asFunction and asClassVar flip the corresponding flags`() = withModule {
      assertTrue(PyCustomMember("member").asFunction().isFunction)
      assertTrue(PyCustomMember("member").asClassVar().isClassVar)
    }

    @Test
    fun `the default icon is the method icon`() = withModule {
      assertSame(AllIcons.Nodes.Method, PyCustomMember("member").icon)
    }

    @Test
    fun `withIcon overrides the default icon`() = withModule {
      assertSame(AllIcons.Nodes.Field, PyCustomMember("member").withIcon(AllIcons.Nodes.Field).icon)
    }

    // Icons are compared by presentation because the platform hands out a fresh wrapper instance per call.
    @Test
    fun `a member with a target takes the icon of its target`() = withModule {
      val target = topLevelFunction()
      assertEquals(target.getIcon(0).toString(), PyCustomMember("member", target).icon.toString())
    }
  }

  @Nested
  inner class EqualsAndHashCode {
    @Test
    fun `members built the same way are equal`() = withModule {
      val one = PyCustomMember("member", "mymod.Outer", false).asFunction()
      val other = PyCustomMember("member", "mymod.Outer", false).asFunction()
      assertEquals(one, other)
      assertEquals(one.hashCode(), other.hashCode())
    }

    @Test
    fun `a member equals itself`() = withModule {
      val member = PyCustomMember("member")
      assertEquals(member, member)
    }

    @Test
    fun `members with different names are not equal`() = withModule {
      assertNotEquals(PyCustomMember("one"), PyCustomMember("other"))
    }

    @Test
    fun `members with different types are not equal`() = withModule {
      assertNotEquals(PyCustomMember("member", "mymod.Outer", false), PyCustomMember("member", "mymod.Other", false))
    }

    @Test
    fun `asFunction makes a member different`() = withModule {
      assertNotEquals(PyCustomMember("member"), PyCustomMember("member").asFunction())
    }

    @Test
    fun `resolve paths take part in equality`() = withModule {
      assertNotEquals(PyCustomMember("member").resolvesTo("mymod"), PyCustomMember("member"))
    }

    @Test
    fun `a member is not equal to an unrelated object`() = withModule {
      val member: Any = PyCustomMember("member")
      assertFalse(member == "member")
      assertFalse(member.equals(null))
    }
  }

  @Nested
  inner class Resolve {
    @Test
    fun `a member with a target resolves to it`() = withModule {
      val target = topLevelFunction()
      assertSame(target, PyCustomMember("member", target).resolve(context, resolveContext))
    }

    @Test
    fun `a member with neither target nor path nor type does not resolve`() = withModule {
      assertNull(PyCustomMember("member").resolve(context, resolveContext))
    }

    // Only a function is handed back directly; anything else, a module included, is wrapped in a custom element.
    @Test
    fun `resolvesTo wraps the module it points at`() = withModule {
      val resolved = PyCustomMember("member").resolvesTo("mymod").resolve(context, resolveContext)
      assertNotNull(resolved, "Expected a custom element for the module")
      assertFalse(resolved is PyFile, "A module is not returned as is")
    }

    @Test
    fun `resolvesTo an unknown module does not resolve`() = withModule {
      assertNull(PyCustomMember("member").resolvesTo("no_such_module").resolve(context, resolveContext))
    }

    @Test
    fun `a function reached by a path is returned as is`() = withModule {
      val resolved = PyCustomMember("member").resolvesTo("mymod").toFunction("top_level").resolve(context, resolveContext)
      assertSame(topLevelFunction(), resolved)
    }

    @Test
    fun `alwaysResolveToCustomElement wraps even a function`() = withModule {
      val member = PyCustomMember("member").resolvesTo("mymod").toFunction("top_level").alwaysResolveToCustomElement()
      val resolved = member.resolve(context, resolveContext)
      assertNotNull(resolved, "Expected a custom element")
      assertNotEquals(topLevelFunction(), resolved)
    }

    @Test
    fun `resolvesToClass produces a custom element for the class`() = withModule {
      assertNotNull(PyCustomMember("member").resolvesToClass("mymod.Outer").resolve(context, resolveContext))
    }

    @Test
    fun `a type name alone produces a custom element`() = withModule {
      assertNotNull(PyCustomMember("member", "mymod.Outer", true).resolve(context, resolveContext))
    }

    @Test
    fun `an unresolvable type name alone does not resolve`() = withModule {
      assertNull(PyCustomMember("member", "mymod.Missing", true).resolve(context, resolveContext))
    }

    @Test
    fun `a type callback is accepted alongside a type name`() = withModule {
      val member = PyCustomMember("member", "mymod.Outer") { null as PyType? }
      assertNotNull(member.resolve(context, resolveContext))
    }

    @Test
    fun `custom type info is attached when a type name is present`() = withModule {
      val member = PyCustomMember("member", "mymod.Outer", false)
        .withCustomTypeInfo(PyCustomMemberTypeInfo(Key.create("py.custom.member.test"), "value"))
      assertNotNull(member.resolve(context, resolveContext))
    }

    @Test
    fun `toPsiElement resolves to the given element`() = withModule {
      val target = outerClass()
      val resolved = PyCustomMember("member").toPsiElement(target).resolve(context, resolveContext)
      assertNotNull(resolved, "Expected a custom element for the class")
    }

    @Test
    fun `toClass and toClassAttribute walk down to an attribute`() = withModule {
      val member = PyCustomMember("member").resolvesTo("mymod").toClass("Outer").toClassAttribute("attr")
      assertNotNull(member.resolve(context, resolveContext))
    }

    @Test
    fun `toFunctionRecursive finds a method of a class`() = withModule {
      val member = PyCustomMember("member").resolvesToClass("mymod.Outer").toFunctionRecursive("method")
      assertInstanceOf(PyFunction::class.java, member.resolve(context, resolveContext))
    }
  }

  private lateinit var context: PsiElement
  private lateinit var resolveContext: PyResolveContext

  private fun withModule(check: () -> Unit) = runInEdtAndWait {
    myFixture.createFile("mymod.py", moduleContent.trimIndent())
    context = myFixture.configureByText("a.py", "import mymod\n")
    resolveContext = PyResolveContext.defaultContext(TypeEvalContext.deepCodeInsight(myFixture.project))
    check()
  }

  private fun topLevelFunction(): PyFunction =
    PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "top_level").resolve(context, resolveContext) as PyFunction

  private fun outerClass(): PyClass =
    PyPsiPath.ToClassQName("mymod.Outer").resolve(context, resolveContext) as PyClass
}
