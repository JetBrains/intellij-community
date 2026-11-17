// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.devkit

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture

/**
 * The fixture of the tests of the DevKit support of the `PyCodeInsightTestCase` assertion language.
 * Create it with [pyTestAssertionEditorFixture].
 *
 * It marks the project as an IntelliJ Platform project. It adds stubs for the [org.intellij.lang.annotations.Language] annotation and
 * for the `PyTestAssertionText` annotation. A test class adds a stub of the test base class in its own language.
 */
class PyTestAssertionEditorFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
) {
  /** Returns the errors that the highlighting of the file finds. */
  fun errors(): List<HighlightInfo> = fixture.doHighlighting(HighlightSeverity.ERROR)

  /** Returns the editor of the injected Python text at the caret. Call it in a read action. */
  fun injectedEditor(): Editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(fixture.editor, fixture.file)

  /** Returns the injected Python file of [editor]. Call it in a read action. */
  fun injectedFile(editor: Editor): PsiFile =
    PsiDocumentManager.getInstance(fixture.project).getPsiFile(editor.document) ?: error("No injected Python at the caret")
}

/**
 * Creates a [PyTestAssertionEditorFixture] for one test. Declare it as an instance field.
 */
fun pyTestAssertionEditorFixture(
  projectFixture: TestFixture<LightJavaProject>,
): TestFixture<PyTestAssertionEditorFixture> = testFixture("pyTestAssertionEditorFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  val project = fixture.project
  IntelliJProjectUtil.markAsIntelliJPlatformProject(project, true)
  fixture.addClass("package org.intellij.lang.annotations; public @interface Language { String value(); }")
  fixture.addClass("package com.intellij.python.testAssertions; public @interface PyTestAssertionText {}")
  initialized(PyTestAssertionEditorFixture(fixture)) {
    IntelliJProjectUtil.markAsIntelliJPlatformProject(project, false)
  }
}
