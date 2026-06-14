// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.intentions.ImportToggleAliasIntention
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyImportElement
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the in-place "add import alias" flow of [ImportToggleAliasIntention].
 *
 * The intention inserts `as <placeholder>` and starts an in-place template that gives the typed name to the usages.
 * Each way out of the template leaves the file consistent.
 */
@TestFor(issues = ["PY-85806"], classes = [ImportToggleAliasIntention::class])
@Subsystems.Refactoring
@Layers.Functional
class PyImportAliasInplaceTest : PyCodeInsightTestCase() {

  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  fun `add alias to import`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    typeNameAndFinish("f")

    checkText("""
      import foo as f
      f.bar()
    """)
  }

  @Test
  fun `add alias to from import`() = runInEdtAndWait {
    myFixture.addFileToProject("mod.py", "def bar(): pass")
    startAliasTemplate("""
      from mod import bar
      #               └ CARET
      bar()
    """, "bar")

    typeNameAndFinish("b")

    checkText("""
      from mod import bar as b
      b()
    """)
  }

  @Test
  fun `add alias to dotted import gives the alias to the whole qualifier`() = runInEdtAndWait {
    addDottedModule()
    startAliasTemplate("""
      import a.b.c
      #      └ CARET
      a.b.c.func()
    """, "a.b.c")

    typeNameAndFinish("x")

    checkText("""
      import a.b.c as x
      x.func()
    """)
  }

  @Test
  fun `add alias to unresolved import`() = runInEdtAndWait {
    startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    typeNameAndFinish("f")

    checkText("""
      import foo as f
      f.bar()
    """)
  }

  @Test
  fun `unresolved import keeps the name that another declaration hides`() = runInEdtAndWait {
    startAliasTemplate("""
      import foo
      #      └ CARET

      def g():
          foo = 1
          foo.bar()
    """, "foo")

    typeNameAndFinish("f")

    checkText("""
      import foo as f

      def g():
          foo = 1
          foo.bar()
    """)
  }

  @Test
  fun `unresolved import keeps the call in front of a name`() = runInEdtAndWait {
    startAliasTemplate("""
      import json
      #      └ CARET
      data = requests.get(url).json()
      print(json.dumps(data))
    """, "json")

    typeNameAndFinish("j")

    checkText("""
      import json as j
      data = requests.get(url).json()
      print(j.dumps(data))
    """)
  }

  @Test
  fun `unresolved dotted import keeps a usage whose first name is a parameter`() = runInEdtAndWait {
    startAliasTemplate("""
      import x.y
      #      └ CARET
      x.y.z()


      def g(x):
          x.y.z()
    """, "x.y")

    typeNameAndFinish("q")

    checkText("""
      import x.y as q
      q.z()


      def g(x):
          x.y.z()
    """)
  }

  @Test
  fun `template leaves out the imported name that repeats the placeholder`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    val template = startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    val text = myFixture.editor.document.text
    val segmentStarts = (0 until template.segmentsCount).map { template.getSegmentRange(it).startOffset }.sorted()
    template.gotoEnd(true)
    settle()

    assertEquals("""
      import foo as foo
      foo.bar()
    """.trimIndent(), text)
    assertEquals(listOf(14, 18), segmentStarts, "Only the alias and the usage are template segments")
  }

  @Test
  fun `dialog names the alias when in-place rename is off`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    answerInputDialog("f")
    addAliasWithoutInplaceRename("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    checkText("""
      import foo as f
      f.bar()
    """)
  }

  @Test
  fun `cancelled dialog removes the placeholder alias`() = runInEdtAndWait {
    addDottedModule()
    answerInputDialog(null)
    addAliasWithoutInplaceRename("""
      import a.b.c
      #      └ CARET
      a.b.c.func()
    """, "a.b.c")

    checkText("""
      import a.b.c
      a.b.c.func()
    """)
  }

  @Test
  fun `remove alias from unresolved from import keeps the module`() = runInEdtAndWait {
    configureWithCaret("""
      from foo import bar as foo
      #               └ CARET
      foo()
    """)
    myFixture.launchAction(myFixture.findSingleIntention(PyPsiBundle.message("INTN.remove.import.alias", "foo")))

    checkText("""
      from foo import bar
      bar()
    """)
  }

  @Test
  fun `remove alias stays non-interactive`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    configureWithCaret("""
      import foo as f
      #      └ CARET
      f.bar()
    """)
    myFixture.launchAction(myFixture.findSingleIntention(PyPsiBundle.message("INTN.remove.import.alias", "f")))

    checkText("""
      import foo
      foo.bar()
    """)
  }

  @Test
  fun `escape removes the placeholder alias`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    val template = startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    template.gotoEnd(true)
    settle()

    checkText("""
      import foo
      foo.bar()
    """)
  }

  @Test
  fun `escape restores the usages that the placeholder alias shortened`() = runInEdtAndWait {
    addDottedModule()
    val template = startAliasTemplate("""
      import a.b.c
      #      └ CARET
      a.b.c.func()
    """, "a.b.c")

    template.gotoEnd(true)
    settle()

    checkText("""
      import a.b.c
      a.b.c.func()
    """)
  }

  @Test
  fun `escape after typing discards the typed name`() = runInEdtAndWait {
    addDottedModule()
    val template = startAliasTemplate("""
      import a.b.c
      #      └ CARET
      a.b.c.func()
    """, "a.b.c")

    typeName(template, "x")
    template.gotoEnd(true)
    settle()

    checkText("""
      import a.b.c
      a.b.c.func()
    """)
  }

  @Test
  fun `escape restores a usage that a parameter hides`() = runInEdtAndWait {
    addDottedModule()
    val template = startAliasTemplate("""
      import a.b.c
      #      └ CARET


      def g(c):
          a.b.c.func()
    """, "a.b.c")

    template.gotoEnd(true)
    settle()

    checkText("""
      import a.b.c


      def g(c):
          a.b.c.func()
    """)
  }

  @Test
  fun `placeholder alias skips a name that a parameter binds at a usage`() = runInEdtAndWait {
    addDottedModule()
    val template = startAliasTemplate("""
      import a.b.c
      #      └ CARET


      def g(c):
          a.b.c.func()
    """, "a.b.c")

    template.gotoEnd(false)
    settle()

    checkText("""
      import a.b.c as c1


      def g(c):
          c1.func()
    """)
  }

  @Test
  fun `new name reaches a usage that a parameter hides`() = runInEdtAndWait {
    addDottedModule()
    startAliasTemplate("""
      import a.b.c
      #      └ CARET


      def g(c):
          a.b.c.func()
    """, "a.b.c")

    typeNameAndFinish("x")

    checkText("""
      import a.b.c as x


      def g(c):
          x.func()
    """)
  }

  @Test
  fun `finish on the placeholder removes a pointless alias`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    val template = startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    template.gotoEnd(false)
    settle()

    checkText("""
      import foo
      foo.bar()
    """)
  }

  @Test
  fun `finish on the placeholder keeps the shorter alias of a dotted import`() = runInEdtAndWait {
    addDottedModule()
    val template = startAliasTemplate("""
      import a.b.c
      #      └ CARET
      a.b.c.func()
    """, "a.b.c")

    template.gotoEnd(false)
    settle()

    checkText("""
      import a.b.c as c
      c.func()
    """)
  }

  /** In a test, the renamer rolls an invalid name back without a popup. This is the Cancel button of the popup. */
  @Test
  fun `invalid name removes the placeholder alias`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    val template = startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    typeName(template, "1x")
    template.gotoEnd(false)
    settle()

    checkText("""
      import foo
      foo.bar()
    """)
  }

  /** The platform cancels a template on an undo, and on an edit outside the template that makes its segments invalid. */
  @Test
  fun `cancelled template removes the placeholder alias`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    val template = startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")

    template.cancelTemplate()
    settle()

    checkText("""
      import foo
      foo.bar()
    """)
  }

  /** `MemberInplaceRenamer.afterTemplateStart` restarts the template in this way when the user changes a rename option. */
  @Test
  fun `restart of the template keeps the alias`() = runInEdtAndWait {
    myFixture.addFileToProject("foo.py", "def bar(): pass")
    val template = startAliasTemplate("""
      import foo
      #      └ CARET
      foo.bar()
    """, "foo")
    try {
      template.gotoEnd(true)
      PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      val alias = PsiTreeUtil.findChildOfType(myFixture.file, PyImportElement::class.java)?.asNameElement
      assertNotNull(alias, "The placeholder alias is not in the file")
      myFixture.editor.caretModel.moveToOffset(alias!!.textOffset)
      assertTrue(MemberInplaceRenamer(alias, alias, myFixture.editor).performInplaceRename(), "The template did not start again")
      settle()

      checkText("""
        import foo as foo
        foo.bar()
      """)
      assertNotNull(TemplateManagerImpl.getTemplateState(myFixture.editor), "The restarted template is not active")
    }
    finally {
      TemplateManagerImpl.getTemplateState(myFixture.editor)?.gotoEnd(true)
      settle()
    }
  }

  private fun addDottedModule() {
    myFixture.addFileToProject("a/__init__.py", "")
    myFixture.addFileToProject("a/b/__init__.py", "")
    myFixture.addFileToProject("a/b/c.py", "def func(): pass")
  }

  private fun startAliasTemplate(@Language("Python") before: String, importedName: String): TemplateState {
    configureWithCaret(before)
    TemplateManagerImpl.setTemplateTesting(testDisposable)
    myFixture.editor.settings.isVariableInplaceRenameEnabled = true
    invokeAddAlias(importedName)

    val template = TemplateManagerImpl.getTemplateState(myFixture.editor)
    assertNotNull(template, "The in-place template did not start")
    return template!!
  }

  private fun addAliasWithoutInplaceRename(@Language("Python") before: String, importedName: String) {
    configureWithCaret(before)
    myFixture.editor.settings.isVariableInplaceRenameEnabled = false
    invokeAddAlias(importedName)
    assertNull(TemplateManagerImpl.getTemplateState(myFixture.editor), "The in-place template started")
  }

  private fun answerInputDialog(answer: String?) {
    val previous = TestDialogManager.setTestInputDialog { answer }
    Disposer.register(testDisposable) { TestDialogManager.setTestInputDialog(previous) }
  }

  private fun invokeAddAlias(importedName: String) {
    val intention = myFixture.findSingleIntention(PyPsiBundle.message("INTN.add.import.alias.to.name", importedName))
    CommandProcessor.getInstance().executeCommand(
      myFixture.project,
      { intention.invoke(myFixture.project, myFixture.editor, myFixture.file) },
      intention.text,
      null
    )
    settle()
  }

  private fun typeName(template: TemplateState, name: String) {
    val range = template.currentVariableRange
    assertNotNull(range, "The template has no variable under edit")
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      myFixture.editor.document.replaceString(range!!.startOffset, range.endOffset, name)
    }
  }

  private fun typeNameAndFinish(name: String) {
    val template = TemplateManagerImpl.getTemplateState(myFixture.editor)
    assertNotNull(template, "The in-place template is not active")
    typeName(template!!, name)
    template.gotoEnd(false)
    settle()
  }

  private fun checkText(@Language("Python") expected: String) {
    assertEquals(expected.trimIndent(), myFixture.editor.document.text)
  }

  /** The rename runs after a background check of the name, and the alias removal runs in a later EDT event. */
  private fun settle() {
    repeat(2) {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    }
  }
}
