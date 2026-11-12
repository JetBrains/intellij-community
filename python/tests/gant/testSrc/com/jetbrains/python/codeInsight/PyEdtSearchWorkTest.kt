// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.QueryExecutor
import com.intellij.util.ui.EDT
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.PyCodeInsightCounters.Counter
import com.jetbrains.python.codeInsight.override.PyMethodMember
import com.jetbrains.python.codeInsight.override.PyOverrideImplementUtil
import com.jetbrains.python.findUsages.PyFindUsagesHandlerFactory
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyCallable
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.impl.PyTypeProvider
import com.jetbrains.python.psi.types.PyType
import com.jetbrains.python.psi.types.PyTypeProviderBase
import com.jetbrains.python.psi.types.TypeEvalContext
import com.jetbrains.python.refactoring.rename.RenamePyFunctionProcessor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Searches and type evaluations that the editor actions run on their calling thread.
 * In the IDE, the platform calls these actions on the EDT.
 *
 * A recording executor, a recording type provider or the counters of the EDT note the work of each call. The work of a
 * background thread does not count as EDT work. Each test pins the current behaviour:
 * the work runs on the EDT, and for Override/Implement also inside the write action.
 * The correct behaviour runs the work in a background read action, so a fix makes the assertion fail.
 */
@TestFor(classes = [
  PyLineMarkerNavigator::class,
  RenamePyFunctionProcessor::class,
  PyMethodNameTypedHandler::class,
  PyGotoSuperHandler::class,
  PyFindUsagesHandlerFactory::class,
  PyOverrideImplementUtil::class,
])
@Subsystems.CodeInsight
@Layers.Functional
class PyEdtSearchWorkTest : PyCodeInsightTestCase() {
  private val disposable: Disposable = Disposer.newDisposable("PyEdtSearchWorkTest")

  @AfterEach
  fun disposeRecorders() {
    Disposer.dispose(disposable)
  }

  @TestFor(issues = ["PY-92732"])
  @Test
  fun `gutter navigation to the subclasses runs the deep inheritor search on the EDT`() = runInEdtAndWait {
    val file = myFixture.configureByText("a.py", """
      class Base:
          pass

      class Child(Base):
          pass
    """.trimIndent()) as PyFile
    val markers = mutableListOf<LineMarkerInfo<*>>()
    PyLineMarkerProvider().collectSlowLineMarkers(listOf(file.findTopLevelClass("Base")!!), markers)
    val marker = markers.single()
    val calls = recordQuery("Pythonid.pyClassInheritorsSearch")

    @Suppress("UNCHECKED_CAST")
    (marker.navigationHandler as GutterIconNavigationHandler<PsiElement>).navigate(null, marker.element!!)

    assertEquals(listOf(EDT_READ), calls, "Pinned defect: the correct search runs in a background read action")
  }

  @TestFor(issues = ["PY-92733"])
  @Test
  fun `rename prepares the overriding methods with a deep search on the EDT`() = runInEdtAndWait {
    val file = myFixture.configureByText("a.py", """
      class Base:
          def run(self):
              pass

      class Child(Base):
          def run(self):
              pass
    """.trimIndent()) as PyFile
    val method = file.findTopLevelClass("Base")!!.findMethodByName("run", false, null)!!
    val calls = recordQuery("Pythonid.pyOverridingMethodsSearch")
    val allRenames = LinkedHashMap<PsiElement, String>()

    // RenameProcessor.doRun calls prepareRenaming on the thread that started the refactoring, which is the EDT.
    RenamePyFunctionProcessor().prepareRenaming(method, "start", allRenames)

    assertEquals(listOf(EDT_READ), calls, "Pinned defect: the correct search runs under a progress, off the EDT")
    assertEquals(setOf("run"), allRenames.keys.map { (it as PyCallable).name }.toSet())
  }

  @TestFor(issues = ["PY-92751"])
  @Test
  fun `find usages on a method runs the super method search on the EDT`() = runInEdtAndWait {
    val file = myFixture.configureByText("a.py", """
      class Base:
          def run(self):
              pass
    """.trimIndent()) as PyFile
    val method = file.findTopLevelClass("Base")!!.findMethodByName("run", false, null)!!
    val calls = recordQuery("Pythonid.pySuperMethodsSearch")

    PyFindUsagesHandlerFactory().createFindUsagesHandler(method, false)

    assertEquals(listOf(EDT_READ), calls, "Pinned defect: the correct search runs in a background read action")
  }

  @TestFor(issues = ["PY-92734"])
  @Test
  fun `typing an opening parenthesis after a method name evaluates types on the EDT`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      class Base:
          pass

      class Child(Base):
          def run<caret>
    """.trimIndent())
    val counts = PyCodeInsightCounters.countOnCurrentThread { myFixture.type('(') }

    assertTrue(myFixture.editor.document.text.contains("def run(self):"), myFixture.editor.document.text)
    assertTrue(counts.getValue(Counter.CONTEXTS_CONSTRUCTED) > 0,
               "Pinned defect: the handler builds a type context for the MRO on the EDT. Counters: $counts")
  }

  @TestFor(issues = ["PY-92735"])
  @Test
  fun `goto super method computes the ancestor classes on the EDT`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      class Base:
          def run(self):
              pass

      class Child(Base):
          def r<caret>un(self):
              pass
    """.trimIndent())
    val counts = PyCodeInsightCounters.countOnCurrentThread {
      PyGotoSuperHandler().invoke(myFixture.project, myFixture.editor, myFixture.file)
    }

    assertEquals(myFixture.editor.document.text.indexOf("run(self):"), myFixture.caretOffset, "The caret did not reach Base.run")
    assertTrue(counts.getValue(Counter.CONTEXTS_CONSTRUCTED) > 0,
               "Pinned defect: the handler builds a type context for the MRO on the EDT. Counters: $counts")
  }

  @TestFor(issues = ["PY-92741"])
  @Test
  fun `override method infers the return type of the base method inside the write action`() = runInEdtAndWait {
    myFixture.configureByText("a.py", """
      class Base:
          def run(self):
              return 1

      class Child(Base):
          pass<caret>
    """.trimIndent())
    val base = (myFixture.file as PyFile).findTopLevelClass("Base")!!
    val child = (myFixture.file as PyFile).findTopLevelClass("Child")!!
    val calls = recordReturnTypeRequests()

    PyOverrideImplementUtil.overrideMethods(myFixture.editor, child, listOf(PyMethodMember(base.findMethodByName("run", false, null)!!)),
                                            false)

    assertTrue(myFixture.editor.document.text.contains("return super().run()"), myFixture.editor.document.text)
    assertTrue(calls.contains("run: $EDT_WRITE"),
               "Pinned defect: the correct override infers the type before the write action. Calls: $calls")
  }

  private fun recordQuery(extensionPointName: String): List<String> {
    val calls = CopyOnWriteArrayList<String>()
    val executor = QueryExecutor<Any, Any> { _, _ ->
      calls.add(currentThreadState())
      true
    }
    ExtensionTestUtil.addExtensions(ExtensionPointName.create(extensionPointName), listOf(executor), disposable)
    return calls
  }

  private fun recordReturnTypeRequests(): List<String> {
    val calls = CopyOnWriteArrayList<String>()
    val provider = object : PyTypeProviderBase() {
      override fun getReturnType(callable: PyCallable, context: TypeEvalContext): Ref<PyType>? {
        calls.add("${callable.name}: ${currentThreadState()}")
        return null
      }
    }
    ExtensionTestUtil.addExtensions(PyTypeProvider.EP_NAME, listOf(provider), disposable)
    return calls
  }

  private fun currentThreadState(): String {
    val thread = if (EDT.isCurrentThreadEdt()) "EDT" else "background"
    val lock = if (ApplicationManager.getApplication().isWriteAccessAllowed) "write action" else "read"
    return "$thread $lock"
  }

  private companion object {
    const val EDT_READ = "EDT read"
    const val EDT_WRITE = "EDT write action"
  }
}
