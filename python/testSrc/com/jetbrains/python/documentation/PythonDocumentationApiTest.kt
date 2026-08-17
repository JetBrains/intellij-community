// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.documentation

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyNumericLiteralExpression
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.psi.PyTypedElement
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.TypeEvalContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Covers the API of [PythonDocumentationProvider] itself: the documentation link protocol, the qualified names used to
 * look up external documentation and the type renderers. The generated Quick Documentation HTML is covered by
 * `Py3QuickDocTest` instead.
 */
@TestFor(issues = ["PY-91546"], classes = [PythonDocumentationProvider::class])
@Subsystems.QuickDocumentation
@Layers.Functional
class PythonDocumentationApiTest : PyCodeInsightTestCase() {

  @Test
  fun `a version string is shortened to the feature release`() {
    assertEquals("3.12", PythonDocumentationProvider.pyVersion("Python 3.12.1"))
    // A version without a patch level is already what the documentation URLs use.
    assertEquals("3.12", PythonDocumentationProvider.pyVersion("Python 3.12"))
    // A version with no minor part at all names no documented release.
    assertNull(PythonDocumentationProvider.pyVersion("Python 3"))
    assertNull(PythonDocumentationProvider.pyVersion("Jython 2.7.2"))
    assertNull(PythonDocumentationProvider.pyVersion(null))
  }

  @Test
  fun `a class link resolves to the class enclosing the context element`() {
    inFile("""
      class C:
          def m(self):
              pass
    """) {
      val method = function("C.m")
      assertSame(file.findTopLevelClass("C"), linkTarget("#class#", method))
      // Any element inside the class body resolves to it as well, not just its methods.
      assertSame(file.findTopLevelClass("C"), linkTarget("#class#", method.statementList.statements.first()))
    }
  }

  @Test
  fun `a class link resolves to nothing outside a class`() {
    inFile("def f(): pass") {
      assertNull(linkTarget("#class#", function("f")))
    }
  }

  @Test
  fun `a parameter link resolves to the class of the parameter type`() {
    inFile("""
      def annotated(x: int): pass
      def bare(x): pass
    """) {
      assertEquals("int", (linkTarget("#param#", parameter("annotated")) as? PyClass)?.name)
      // Without an annotation there is no class to navigate to.
      assertNull(linkTarget("#param#", parameter("bare")))
    }
  }

  @Test
  fun `a type name link resolves to the named class`() {
    inFile("x = 1") {
      val target = linkTarget("#typename#int", file)
      assertEquals("int", (target as? PyClass)?.name)
      assertNull(linkTarget("#typename#NoSuchClass", file))
    }
  }

  @Test
  fun `a function link resolves both top level functions and methods`() {
    inFile("import mod", "mod.py" to """
      def f(): pass

      class C:
          def m(self): pass
    """.trimIndent()) {
      assertEquals("f", (linkTarget("#func#mod.f", file) as? PyFunction)?.name)
      assertEquals("m", (linkTarget("#func#mod.C.m", file) as? PyFunction)?.name)
      assertNull(linkTarget("#func#mod.missing", file))
    }
  }

  @Test
  fun `a module link resolves to the module file`() {
    inFile("import mod", "mod.py" to "def f(): pass") {
      assertEquals("mod.py", (linkTarget("#module#mod", file) as? PyFile)?.name)
      assertNull(linkTarget("#module#no_such_module", file))
    }
  }

  @Test
  fun `an unknown link resolves to nothing`() {
    inFile("x = 1") {
      assertNull(linkTarget("#unknown#int", file))
    }
  }

  @Test
  fun `a builtin is qualified with its bare name`() {
    inFile("len") {
      val len = PsiTreeUtil.findChildOfType(file, PyReferenceExpression::class.java)!!.reference.resolve()!!
      assertEquals("len", PythonDocumentationProvider.getFullQualifiedName(len).toString())
    }
  }

  @Test
  fun `a top level function is qualified with its module`() {
    inFile("import mod", "mod.py" to "def f(): pass") {
      val module = linkTarget("#module#mod", file) as PyFile
      assertEquals("mod.f", PythonDocumentationProvider.getFullQualifiedName(module.findTopLevelFunction("f")).toString())
      assertEquals("mod", PythonDocumentationProvider.getFullQualifiedName(module).toString())
    }
  }

  @Test
  fun `a class attribute is qualified with its class`() {
    inFile("import mod", "mod.py" to """
      class C:
          attr = 1

          def __init__(self):
              self.inst = 2
    """.trimIndent()) {
      val cls = (linkTarget("#module#mod", file) as PyFile).findTopLevelClass("C")!!
      assertEquals("mod.C.attr", PythonDocumentationProvider.getFullQualifiedName(cls.findClassAttribute("attr", false, null)).toString())
      // An attribute declared in the constructor belongs to the class, not to `__init__`.
      assertEquals("mod.C.inst", PythonDocumentationProvider.getFullQualifiedName(cls.findInstanceAttribute("inst", false)).toString())
    }
  }

  @Test
  fun `a local variable has no qualified name`() {
    inFile("""
      def f():
          local = 1
    """) {
      val local = PsiTreeUtil.findChildOfType(function("f"), PyTargetExpression::class.java)!!
      assertNull(PythonDocumentationProvider.getFullQualifiedName(local))
      assertNull(PythonDocumentationProvider.getFullQualifiedName(null))
    }
  }

  @Test
  fun `a constructor is documented as its class`() {
    inFile("""
      class C:
          def __init__(self): pass

      def f(): pass
    """) {
      val cls = file.findTopLevelClass("C")!!
      assertSame(cls, PythonDocumentationProvider.getNamedElement(cls.findMethodByName("__init__", false, null)))
      // Anything else is documented as itself.
      assertSame(file.findTopLevelFunction("f"), PythonDocumentationProvider.getNamedElement(function("f")))
      assertNull(PythonDocumentationProvider.getNamedElement(null))
    }
  }

  @Test
  fun `a package init file is documented as its directory`() {
    inFile("import pkg", "pkg/__init__.py" to "") {
      val initFile = linkTarget("#module#pkg", file) as PyFile
      val named = PythonDocumentationProvider.getNamedElement(initFile)
      assertEquals("pkg", (named as? PsiDirectory)?.name)
    }
  }

  @Test
  fun `hovering over a file or a directory shows its name`() {
    inFile("import mod", "mod.py" to "def f(): pass", "pkg/__init__.py" to "") {
      val anchor = PsiTreeUtil.findChildOfType(file, PyReferenceExpression::class.java)!!.lastChild
      val module = linkTarget("#module#mod", file) as PyFile
      val directory = PythonDocumentationProvider.getNamedElement(linkTarget("#module#pkg", file)) as PsiDirectory
      assertEquals("File \"mod.py\"", PythonDocumentationProvider().getQuickNavigateInfo(module, anchor))
      assertEquals("Directory \"pkg\"", PythonDocumentationProvider().getQuickNavigateInfo(directory, anchor))
    }
  }

  @Test
  fun `a function type is rendered as a signature but hinted as a Callable`() {
    inFile("def f(a: int) -> str: pass") {
      val type = typeOf(function("f"))
      assertEquals("(a: int) -> str", PythonDocumentationProvider.getTypeName(type, context))
      // `typing.Callable` cannot express named parameters, so the parameter list degrades to `...`.
      assertEquals("Callable[..., str]", PythonDocumentationProvider.getTypeHint(type, context))
    }
  }

  @Test
  fun `a hint can be rendered with fully qualified names`() {
    inFile("""
      from mod import C

      def f(x: C): pass
    """, "mod.py" to "class C: pass") {
      val type = typeOf(parameter("f"))
      assertEquals("C", PythonDocumentationProvider.getTypeHint(type, context))
      assertEquals("mod.C", PythonDocumentationProvider.getFullyQualifiedTypeHint(type, context))
    }
  }

  @Test
  fun `a verbose type name spells out the bound of a type variable`() {
    inFile("""
      from typing import TypeVar

      T = TypeVar("T", bound=str)

      def f(x: T): pass
    """) {
      val type = typeOf(parameter("f"))
      assertEquals("T", PythonDocumentationProvider.getTypeName(type, context))
      assertEquals("T ≤: str", PythonDocumentationProvider.getVerboseTypeName(type, context))
    }
  }

  @Test
  fun `type names can be rendered as tooltip links`() {
    inFile("""
      from typing import TypeVar

      T = TypeVar("T", bound=str)

      def f(x: T, y: int): pass
    """) {
      val parameterType = typeOf(parameter("f", 1))
      val linked = PythonDocumentationProvider.getTypeNameWithLinks(parameterType, context, parameter("f", 1))
      assertTrue(linked.contains("#element/builtins.int"), linked)
      assertTrue(linked.contains(">int</a>"), linked)

      // The bound is a type of its own, so it becomes a link as well.
      val verbose = PythonDocumentationProvider.getVerboseTypeNameWithLinks(typeOf(parameter("f")), context, parameter("f"))
      assertTrue(verbose.contains(" ≤: "), verbose)
      assertTrue(verbose.contains("#element/builtins.str"), verbose)
    }
  }

  // The rendered Quick Documentation HTML is checked by `Py3QuickDocTest`; here only the "nothing to say" result is.
  @Test
  fun `an expression without a definition is not documented`() {
    inFile("x = 1") {
      val literal = PsiTreeUtil.findChildOfType(file, PyNumericLiteralExpression::class.java)!!
      assertNull(PythonDocumentationProvider().generateDoc(literal, null))
    }
  }

  private fun <R> inFile(@Language("Python") text: String, vararg otherFiles: Pair<String, String>, body: Anchors.() -> R): R {
    for ((name, content) in otherFiles) {
      myFixture.addFileToProject(name, content)
    }
    val file = myFixture.configureByText("aaa.py", text.trimIndent()) as PyFile
    return runReadActionBlocking { Anchors(file).body() }
  }

  private class Anchors(val file: PyFile) {
    val context: TypeEvalContext = TypeEvalContext.userInitiated(myFixture.project, file)

    fun function(name: String): PyFunction = findFunction(file, name)

    fun parameter(functionName: String, index: Int = 0): PyNamedParameter =
      function(functionName).parameterList.parameters[index] as PyNamedParameter

    fun typeOf(element: PyTypedElement): PyType? = context.getType(element)

    fun linkTarget(link: String, anchor: PsiElement): PsiElement? =
      PythonDocumentationProvider().getDocumentationElementForLink(PsiManager.getInstance(myFixture.project), link, anchor)
  }
}
