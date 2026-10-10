// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * The base of the tests of the DevKit support of the `PyCodeInsightTestCase` assertion language.
 *
 * It marks the project as an IntelliJ Platform project. It adds stubs for the [org.intellij.lang.annotations.Language] annotation and
 * for the `PyTestAssertionText` annotation. A subclass adds a stub of the test base class in its own language.
 */
abstract class PyTestAssertionEditorTestBase : LightJavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    super.setUp()
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
    myFixture.addClass("package org.intellij.lang.annotations; public @interface Language { String value(); }")
    myFixture.addClass("package com.intellij.python.testAssertions; public @interface PyTestAssertionText {}")
  }

  override fun tearDown() {
    try {
      IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  protected fun errors(): List<HighlightInfo> = myFixture.doHighlighting(HighlightSeverity.ERROR)

  /** Returns the editor of the injected Python text at the caret. */
  protected fun injectedEditor(): Editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myFixture.editor, myFixture.file)

  protected fun injectedFile(editor: Editor): PsiFile =
    PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: error("No injected Python at the caret")
}
