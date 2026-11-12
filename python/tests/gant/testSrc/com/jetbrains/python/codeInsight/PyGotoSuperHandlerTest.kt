// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.idea.TestFor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.awt.event.MouseEvent
import javax.swing.JLabel

@TestFor(issues = ["PY-91546"], classes = [PyGotoSuperHandler::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyGotoSuperHandlerTest : PyCodeInsightTestCase() {

  @Test
  fun `overriding method navigates to the overridden one`() = assertNavigatesTo("""
    class A:
        def f(self):
            return "base"

    class B(A):
        def f(self):
    #       └ CARET
            return "override"
  """, "f(self):")

  @Test
  fun `navigation starts from the method body as well`() = assertNavigatesTo("""
    class A:
        def f(self):
            return "base"

    class B(A):
        def f(self):
            return "override"
    #              └ CARET
  """, "f(self):")

  @Test
  fun `method declared several classes up is still found`() = assertNavigatesTo("""
    class A:
        def f(self):
            return "base"

    class B(A):
        pass

    class C(B):
        def f(self):
    #       └ CARET
            return "override"
  """, "f(self):")

  @Test
  fun `overriding class attribute navigates to the overridden one`() = assertNavigatesTo("""
    class A:
        attr = 1

    class B(A):
        attr = 2
    #     └ CARET
  """, "attr = 1")

  @Test
  fun `method with no super method does not navigate`() = assertStaysPut("""
    class A:
        def f(self):
    #       └ CARET
            pass
  """)

  @Test
  fun `class attribute with no super attribute does not navigate`() = assertStaysPut("""
    class A:
        attr = 1
    #     └ CARET
  """)

  @Test
  fun `caret outside of a class does nothing`() = assertStaysPut("""
    def f():
        pass
    #   └ CARET
  """)

  @Test
  fun `caret in an empty file does nothing`() = runInEdtAndWait {
    myFixture.configureByText("a.py", "")
    PyGotoSuperHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)
    assertEquals(0, myFixture.caretOffset, "Caret was expected to stay where it was")
  }

  @Test
  @TestFor(issues = ["PY-83908"])
  fun `super method declared in a stub navigates to the implementation`() = assertNavigatesTo("""
    from lib import Base

    class Child(Base):
        def delete(self) -> None:
    #       └ CARET
            pass
  """, "delete(self):", "lib.py", libStub = LIB_STUB, libImplementation = LIB_IMPLEMENTATION)

  @Test
  @TestFor(issues = ["PY-83908"])
  fun `super attribute declared in a stub navigates to the implementation`() = assertNavigatesTo("""
    from lib import Base

    class Child(Base):
        attr = 2
    #     └ CARET
  """, "attr = 1", "lib.py", libStub = LIB_STUB, libImplementation = LIB_IMPLEMENTATION)

  @Test
  @TestFor(issues = ["PY-83908"])
  fun `super method declared in a stub without an implementation navigates to the stub`() = assertNavigatesTo("""
    from lib import Base

    class Child(Base):
        def delete(self) -> None:
    #       └ CARET
            pass
  """, "delete(self) -> None: ...", "lib.pyi", libStub = LIB_STUB)

  @Test
  @TestFor(issues = ["PY-83908"])
  fun `super method navigation inside a stub stays in the stub`() = runInEdtAndWait {
    myFixture.addFileToProject("lib.py", LIB_IMPLEMENTATION)
    configureWithCaret("""
      class Base:
          def delete(self) -> None: ...

      class Child(Base):
          def delete(self) -> None: ...
      #       └ CARET
    """, "lib.pyi")
    PyGotoSuperHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)
    assertCaretAt("lib.pyi", "delete(self) -> None: ...")
  }

  @Test
  @TestFor(issues = ["PY-83908"], classes = [PyLineMarkerProvider::class])
  fun `gutter icon of a method overriding a stub method navigates to the implementation`() = runInEdtAndWait {
    myFixture.addFileToProject("lib.pyi", LIB_STUB)
    myFixture.addFileToProject("lib.py", LIB_IMPLEMENTATION)
    configureWithCaret("""
      from lib import Base

      class Child(Base):
          def delete(self) -> None:
      #       └ CARET
              pass
    """)
    val identifier = myFixture.file.findElementAt(myFixture.caretOffset)!!
    val marker = PyLineMarkerProvider().getLineMarkerInfo(identifier) ?: fail("No gutter icon on the overriding method")
    @Suppress("UNCHECKED_CAST")
    val handler = marker.navigationHandler as GutterIconNavigationHandler<PsiElement>
    handler.navigate(MouseEvent(JLabel(), 0, 0, 0, 0, 0, 0, false), identifier)
    val target = PyLineMarkerNavigator.getNavigationTargets(identifier)?.singleOrNull() ?: fail("Expected one navigation target")
    assertEquals("lib.py", target.containingFile.name, "The gutter icon did not navigate to the implementation")
  }

  private fun assertNavigatesTo(
    @Language("Python") code: String,
    expectedTextAtCaret: String,
    expectedFileName: String? = null,
    @Language("Python") libStub: String? = null,
    @Language("Python") libImplementation: String? = null,
  ) = runInEdtAndWait {
    libStub?.let { myFixture.addFileToProject("lib.pyi", it.trimIndent()) }
    libImplementation?.let { myFixture.addFileToProject("lib.py", it.trimIndent()) }
    configureAndInvoke(code)
    assertCaretAt(expectedFileName ?: myFixture.file.name, expectedTextAtCaret)
  }

  private fun assertCaretAt(expectedFileName: String, expectedTextAtCaret: String) {
    val editor = FileEditorManager.getInstance(myFixture.project).selectedTextEditor ?: fail("No editor is open")
    val file = FileDocumentManager.getInstance().getFile(editor.document)
    assertEquals(expectedFileName, file?.name, "Navigation opened a wrong file")
    val expectedOffset = editor.document.text.indexOf(expectedTextAtCaret)
    assertEquals(expectedOffset, editor.caretModel.offset, "Caret did not land on the super element")
  }

  private fun assertStaysPut(@Language("Python") code: String) = runInEdtAndWait {
    val caretBefore = configureAndInvoke(code)
    assertEquals(caretBefore, myFixture.editor.caretModel.offset, "Caret was expected to stay where it was")
  }

  private fun configureAndInvoke(code: String): Int {
    configureWithCaret(code)
    val caretBefore = myFixture.caretOffset
    PyGotoSuperHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)
    return caretBefore
  }

  private companion object {
    val LIB_STUB = """
      class Base:
          attr: int
          def delete(self) -> None: ...
    """.trimIndent()

    val LIB_IMPLEMENTATION = """
      class Base:
          attr = 1

          def delete(self):
              pass
    """.trimIndent()
  }
}
