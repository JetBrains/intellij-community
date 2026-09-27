// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.imports

import com.intellij.idea.TestFor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.IPopupChooserBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.components.JBList
import com.intellij.util.Function
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyImportElement
import java.util.concurrent.TimeUnit
import javax.swing.ListCellRenderer
import org.junit.jupiter.api.Test

/**
 * The import popup renders and filters its items on the EDT, where Swing gives no read lock.
 * The test calls the renderer and the filter namer on a pooled thread, which has no read lock either.
 */
@Subsystems.CodeInsight
@Layers.Functional
@TestFor(classes = [PyImportChooser::class], issues = ["PY-72797"])
class PyImportChooserReadAccessTest : PyCodeInsightTestCase() {

  @Test
  fun `the renderer and the filter namer take a read lock`() = runInEdtAndWait {
    val file = myFixture.configureByText("a.py", """
      import os

      def foo():
          pass
    """.trimIndent())
    val importElement = PsiTreeUtil.findChildOfType(file, PyImportElement::class.java)!!
    val function = PsiTreeUtil.findChildOfType(file, PyFunction::class.java)!!
    val candidate = ImportCandidateHolder(function, file, importElement, null)

    val chooser = CapturingChooser()
    chooser.install(listOf(candidate))

    ApplicationManager.getApplication().executeOnPooledThread {
      chooser.renderer.getListCellRendererComponent(JBList(), candidate, 0, false, false)
      chooser.namer.`fun`(candidate)
    }.get(1, TimeUnit.MINUTES)
  }

  private class CapturingChooser : PyImportChooser() {
    lateinit var renderer: ListCellRenderer<in ImportCandidateHolder>
    lateinit var namer: Function<in ImportCandidateHolder, String>

    fun install(candidates: List<ImportCandidateHolder>) {
      val delegate = JBPopupFactory.getInstance().createPopupChooserBuilder(candidates)
      processPopup(object : IPopupChooserBuilder<ImportCandidateHolder> by delegate {
        override fun setRenderer(renderer: ListCellRenderer<in ImportCandidateHolder>): IPopupChooserBuilder<ImportCandidateHolder> {
          this@CapturingChooser.renderer = renderer
          return this
        }

        override fun setNamerForFiltering(namer: Function<in ImportCandidateHolder, String>): IPopupChooserBuilder<ImportCandidateHolder> {
          this@CapturingChooser.namer = namer
          return this
        }

        override fun setTitle(title: String): IPopupChooserBuilder<ImportCandidateHolder> = this
      }, false)
    }
  }
}
