// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyAssignmentStatement
import com.jetbrains.python.psi.PyCallExpression
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * [PyPsiPath] is the resolve-path DSL behind [PyCustomMember]. Every path step has a stub-only branch and an
 * AST branch guarded by [TypeEvalContext.maySwitchToAST], so each step is exercised with a context that allows
 * AST access to `mymod.py` and with one that does not.
 */
@TestFor(issues = ["PY-91546"], classes = [PyPsiPath::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyPsiPathTest : PyCodeInsightTestCase() {

  private val moduleContent = """
    CONST = 1

    def top_level():
        pass

    class Outer:
        attr = 5

        def method(self):
            pass

        class Nested:
            pass

    def with_call():
        register("name", "second")
        other = 1

        def inner():
            pass
  """

  @Nested
  inner class ToFile {
    @Test
    fun `ToFile resolves a module by its qualified name`() = withModule {
      val resolved = PyPsiPath.ToFile("mymod").resolve(context, astContext)
      assertEquals("mymod.py", assertInstanceOf(PyFile::class.java, resolved).name)
    }

    @Test
    fun `ToFile does not resolve an unknown module`() = withModule {
      assertNull(PyPsiPath.ToFile("no_such_module").resolve(context, astContext))
    }
  }

  @Nested
  inner class ToClassQName {
    @Test
    fun `ToClassQName resolves a class by its qualified name`() = withModule {
      assertName("Outer", PyPsiPath.ToClassQName("mymod.Outer").resolve(context, astContext))
    }

    @Test
    fun `ToClassQName does not resolve an unknown class`() = withModule {
      assertNull(PyPsiPath.ToClassQName("mymod.Missing").resolve(context, astContext))
    }
  }

  @Nested
  inner class ToClass {
    @Test
    fun `ToClass finds a top level class of a module`() = withModule {
      assertName("Outer", toClass(PyPsiPath.ToFile("mymod"), "Outer").resolve(context, astContext))
    }

    @Test
    fun `ToClass does not find a missing top level class`() = withModule {
      assertNull(toClass(PyPsiPath.ToFile("mymod"), "Missing").resolve(context, astContext))
    }

    @Test
    fun `ToClass finds a nested class inside a class`() = withModule {
      assertName("Nested", toClass(PyPsiPath.ToClassQName("mymod.Outer"), "Nested").resolve(context, astContext))
    }

    @Test
    fun `ToClass falls back to the parent when the nested class is missing`() = withModule {
      assertName("Outer", toClass(PyPsiPath.ToClassQName("mymod.Outer"), "Missing").resolve(context, astContext))
    }

    @Test
    fun `ToClass returns the parent when AST access is not allowed`() = withModule {
      assertName("Outer", toClass(PyPsiPath.ToClassQName("mymod.Outer"), "Nested").resolve(context, stubContext))
    }

    @Test
    fun `ToClass propagates an unresolved parent`() = withModule {
      assertNull(toClass(PyPsiPath.ToFile("no_such_module"), "Outer").resolve(context, astContext))
    }
  }

  @Nested
  inner class ToFunction {
    @Test
    fun `ToFunction finds a top level function of a module`() = withModule {
      assertName("top_level", PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "top_level").resolve(context, astContext))
    }

    @Test
    fun `ToFunction does not find a missing top level function`() = withModule {
      assertNull(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "missing").resolve(context, astContext))
    }

    @Test
    fun `ToFunction finds a method of a class`() = withModule {
      assertName("method", PyPsiPath.ToFunction(PyPsiPath.ToClassQName("mymod.Outer"), "method").resolve(context, astContext))
    }

    @Test
    fun `ToFunction does not find a missing method of a class`() = withModule {
      assertNull(PyPsiPath.ToFunction(PyPsiPath.ToClassQName("mymod.Outer"), "missing").resolve(context, astContext))
    }

    @Test
    fun `ToFunction propagates an unresolved parent`() = withModule {
      assertNull(PyPsiPath.ToFunction(PyPsiPath.ToFile("no_such_module"), "top_level").resolve(context, astContext))
    }
  }

  @Nested
  inner class ToFunctionRecursive {
    @Test
    fun `ToFunctionRecursive finds a method of a class`() = withModule {
      assertName("method", PyPsiPath.ToFunctionRecursive(PyPsiPath.ToClassQName("mymod.Outer"), "method").resolve(context, astContext))
    }

    @Test
    fun `ToFunctionRecursive finds a function nested in a function`() = withModule {
      val path = PyPsiPath.ToFunctionRecursive(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "inner")
      assertName("inner", path.resolve(context, astContext))
    }

    @Test
    fun `ToFunctionRecursive falls back to the parent when the function is missing`() = withModule {
      assertName("Outer", PyPsiPath.ToFunctionRecursive(PyPsiPath.ToClassQName("mymod.Outer"), "missing").resolve(context, astContext))
    }

    @Test
    fun `ToFunctionRecursive gives up when AST access is not allowed`() = withModule {
      assertNull(PyPsiPath.ToFunctionRecursive(PyPsiPath.ToClassQName("mymod.Outer"), "method").resolve(context, stubContext))
    }
  }

  @Nested
  inner class ToClassAttribute {
    @Test
    fun `ToClassAttribute finds a class attribute`() = withModule {
      val resolved = PyPsiPath.ToClassAttribute(PyPsiPath.ToClassQName("mymod.Outer"), "attr").resolve(context, astContext)
      assertEquals("attr", assertInstanceOf(PyTargetExpression::class.java, resolved).name)
    }

    @Test
    fun `ToClassAttribute does not find a missing class attribute`() = withModule {
      assertNull(PyPsiPath.ToClassAttribute(PyPsiPath.ToClassQName("mymod.Outer"), "missing").resolve(context, astContext))
    }

    @Test
    fun `ToClassAttribute gives up when the parent is not a class`() = withModule {
      assertNull(PyPsiPath.ToClassAttribute(PyPsiPath.ToFile("mymod"), "attr").resolve(context, astContext))
    }
  }

  @Nested
  inner class ToCall {
    @Test
    fun `ToCall finds a call by name and string arguments`() = withModule {
      val path = PyPsiPath.ToCall(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "register", "name")
      val resolved = path.resolve(context, astContext)
      assertEquals("""register("name", "second")""", assertInstanceOf(PyCallExpression::class.java, resolved).text)
    }

    @Test
    fun `ToCall matches a call with fewer expected arguments than actual ones`() = withModule {
      val path = PyPsiPath.ToCall(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "register")
      assertInstanceOf(PyCallExpression::class.java, path.resolve(context, astContext))
    }

    @Test
    fun `ToCall falls back to the parent when the arguments do not match`() = withModule {
      val path = PyPsiPath.ToCall(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "register", "wrong")
      assertName("with_call", path.resolve(context, astContext))
    }

    @Test
    fun `ToCall falls back to the parent when the callee name does not match`() = withModule {
      val path = PyPsiPath.ToCall(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "unregister")
      assertName("with_call", path.resolve(context, astContext))
    }

    @Test
    fun `ToCall gives up when AST access is not allowed`() = withModule {
      val path = PyPsiPath.ToCall(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "register")
      assertNull(path.resolve(context, stubContext))
    }
  }

  @Nested
  inner class ToAssignment {
    @Test
    fun `ToAssignment finds an assignment by its target`() = withModule {
      val path = PyPsiPath.ToAssignment(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "other")
      assertEquals("other = 1", assertInstanceOf(PyAssignmentStatement::class.java, path.resolve(context, astContext)).text)
    }

    @Test
    fun `ToAssignment falls back to the parent when the target is missing`() = withModule {
      val path = PyPsiPath.ToAssignment(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "missing")
      assertName("with_call", path.resolve(context, astContext))
    }

    @Test
    fun `ToAssignment gives up when AST access is not allowed`() = withModule {
      val path = PyPsiPath.ToAssignment(PyPsiPath.ToFunction(PyPsiPath.ToFile("mymod"), "with_call"), "other")
      assertNull(path.resolve(context, stubContext))
    }
  }

  private lateinit var context: PsiElement

  /** Allows AST access everywhere, including `mymod.py`. */
  private lateinit var astContext: PyResolveContext

  /** Allows AST access only inside the file under the caret, so `mymod.py` stays stub-only. */
  private lateinit var stubContext: PyResolveContext

  private fun withModule(check: () -> Unit) = runInEdtAndWait {
    myFixture.createFile("mymod.py", moduleContent.trimIndent())
    val file = myFixture.configureByText("a.py", "import mymod\n")
    context = file
    astContext = PyResolveContext.defaultContext(TypeEvalContext.deepCodeInsight(myFixture.project))
    stubContext = PyResolveContext.defaultContext(TypeEvalContext.codeAnalysis(myFixture.project, file))
    check()
  }

  private fun assertName(expected: String, actual: PsiElement?) {
    assertEquals(expected, (actual as? PsiNamedElement)?.name, "Unexpected resolve result: $actual")
  }

  private fun toClass(parent: PyPsiPath, name: String) = PyPsiPath.ToClass(parent, name)
}
