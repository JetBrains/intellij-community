// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.documentation.PythonDocumentationProvider
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.AccessDirection
import com.jetbrains.python.psi.PyExpression
import com.jetbrains.python.psi.impl.PyBuiltinCache
import com.jetbrains.python.psi.resolve.PyResolveContext
import com.jetbrains.python.psi.types.PyCloningTypeVisitor
import com.jetbrains.python.psi.types.PyTopType
import com.jetbrains.python.psi.types.PyTypeChecker
import com.jetbrains.python.psi.types.TypeEvalContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Layers.Functional
@TestFor(classes = [PyTopType::class], issues = ["PY-90942"])
class PyTopTypeTest : PyCodeInsightTestCase() {
  @Test
  fun `matches like builtins object in the type checker`() {
    myFixture.configureByText("aaa.py", "")
    runReadActionBlocking {
      val cache = PyBuiltinCache.getInstance(myFixture.file)
      val obj = cache.objectType!!
      val int = cache.intType!!
      val ctx = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)

      // Everything is assignable to the top type, just like `object`.
      assertTrue(PyTypeChecker.match(PyTopType, int, ctx))
      assertTrue(PyTypeChecker.match(PyTopType, obj, ctx))
      assertTrue(PyTypeChecker.match(PyTopType, PyTopType, ctx))
      // The top type is accepted where `object` is expected.
      assertTrue(PyTypeChecker.match(obj, PyTopType, ctx))
      // ...but is not assignable to a narrower type.
      assertFalse(PyTypeChecker.match(int, PyTopType, ctx))
    }
  }

  @Test
  fun `resolves builtins object members`() {
    val file = myFixture.configureByText("aaa.py", "x = 1")
    runReadActionBlocking {
      val obj = PyBuiltinCache.getInstance(file).objectType!!
      val ctx = TypeEvalContext.codeAnalysis(myFixture.project, file)
      val resolveContext = PyResolveContext.defaultContext(ctx)
      val anchor = PsiTreeUtil.findChildOfType(file, PyExpression::class.java)!!

      // A member defined on `object` resolves through the top type, same as through `object` itself.
      val resolved = PyTopType.resolveMember("__class__", anchor, AccessDirection.READ, resolveContext)
      assertFalse(resolved.isEmpty())
      assertEquals(
        obj.resolveMember("__class__", anchor, AccessDirection.READ, resolveContext)!!.map { it.element },
        resolved.map { it.element },
      )

      // A name that `object` does not define resolves to nothing.
      assertTrue(PyTopType.resolveMember("no_such_member", anchor, AccessDirection.READ, resolveContext).isEmpty())

      // `findMember` / `getAllMembers` mirror `object` too (anchor taken from the context's origin file).
      assertEquals(obj.findMember("__class__", resolveContext), PyTopType.findMember("__class__", resolveContext))
      assertEquals(obj.getAllMembers(resolveContext), PyTopType.getAllMembers(resolveContext))
      assertFalse(PyTopType.getAllMembers(resolveContext).isEmpty())
    }
  }

  @Test
  fun `renders like builtins object`() {
    myFixture.configureByText("aaa.py", "")
    runReadActionBlocking {
      val ctx = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)
      val obj = PyBuiltinCache.getInstance(myFixture.file).objectType!!

      assertEquals("object", PythonDocumentationProvider.getTypeName(PyTopType, ctx))
      assertEquals(PythonDocumentationProvider.getTypeName(obj, ctx), PythonDocumentationProvider.getTypeName(PyTopType, ctx))

      // Under fully qualified rendering the top type is spelled out like the class it stands for.
      assertEquals("builtins.object", PythonDocumentationProvider.getFullyQualifiedTypeHint(PyTopType, ctx))
      assertEquals(
        PythonDocumentationProvider.getFullyQualifiedTypeHint(obj, ctx),
        PythonDocumentationProvider.getFullyQualifiedTypeHint(PyTopType, ctx),
      )
    }
  }

  @Test
  fun `cloning returns the same singleton`() {
    myFixture.configureByText("aaa.py", "")
    runReadActionBlocking {
      val ctx = TypeEvalContext.codeAnalysis(myFixture.project, myFixture.file)
      val cloner = object : PyCloningTypeVisitor(ctx) {}
      assertSame(PyTopType, PyCloningTypeVisitor.clone(PyTopType, cloner))
    }
  }
}
