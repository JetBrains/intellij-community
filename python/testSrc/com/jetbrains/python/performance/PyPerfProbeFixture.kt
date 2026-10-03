// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.performance

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.python.community.testFramework.performance.PyPerfProbe
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Gives each test a [PyPerfProbe] on the editor of [fixture], and disposes it after the test.
 *
 * A [com.jetbrains.python.fixtures.PyCodeInsightTestCase] shares its fixture between the tests of a class, so the
 * disposable of the fixture lives too long for the state of the probe. The probe is created on the first access,
 * because the fixture of the first test exists only after the `@BeforeEach` methods.
 *
 * ```kotlin
 * @JvmField
 * @RegisterExtension
 * val perf = PyPerfProbeExtension { myFixture }
 * ```
 */
class PyPerfProbeExtension(private val fixture: () -> CodeInsightTestFixture) : AfterEachCallback {
  private var disposable: Disposable? = null
  private var current: PyPerfProbe? = null

  val probe: PyPerfProbe
    get() = current ?: Disposer.newDisposable("PyPerfProbeExtension").let { newDisposable ->
      disposable = newDisposable
      PyPerfProbe(fixture().project, FixtureEditor(fixture()), newDisposable).also { current = it }
    }

  override fun afterEach(context: ExtensionContext) {
    disposable?.let(Disposer::dispose)
    disposable = null
    current = null
  }
}

private class FixtureEditor(private val fixture: CodeInsightTestFixture) : PyPerfProbe.Editor {
  override val file: PsiFile get() = fixture.file
  override val document: Document get() = fixture.editor.document

  override fun highlight(): Int = fixture.doHighlighting().size

  override fun enableInspections(inspections: List<LocalInspectionTool>) = fixture.enableInspections(*inspections.toTypedArray())

  override fun disableInspections(inspections: List<LocalInspectionTool>) = fixture.disableInspections(*inspections.toTypedArray())
}
