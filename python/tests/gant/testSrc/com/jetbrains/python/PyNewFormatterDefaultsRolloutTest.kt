// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python

import com.intellij.formatting.FormattingRangesInfo
import com.intellij.formatting.service.FormattingService
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.TestFor
import com.intellij.lang.ImportOptimizer
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.options.advanced.AdvancedSettingBean
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.python.junit5Tests.framework.pyModuleFixture
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.runInEdtAndWait
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.formatter.PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY
import com.jetbrains.python.formatter.PY_NEW_FORMATTER_DEFAULTS_SETTING_ID
import com.jetbrains.python.formatter.PyNewFormatterDefaultsReformatListener
import com.jetbrains.python.formatter.isPyNewFormatterDefaultsActive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the rollout of the PY-85946 new formatter defaults: the switch balloon on "Reformat Code", and the
 * advanced setting that holds the per-installation rollout state.
 *
 * No test turns on the master switch and the advanced setting together, because that changes the code style
 * baseline for the whole test JVM.
 */
@TestApplication
@Subsystems.Formatter
@Layers.Functional
class PyNewFormatterDefaultsRolloutTest {
  companion object {
    private const val MIGRATION_DONE_PROPERTY = "py.code.style.new.defaults.migration.done"

    private val tempDir = tempPathFixture()
    private val project = projectFixture(tempDir, openAfterCreation = true)

    @Suppress("unused")
    private val module = project.pyModuleFixture(tempDir, addPathToSourceRoot = true)
  }

  private val codeInsightFixture by codeInsightFixture(project, tempDir)

  @Test
  @TestFor(issues = ["PY-92100"])
  fun `reformat in a Python editor offers the switch balloon once`() {
    assertFalse(PropertiesComponent.getInstance().getBoolean(MIGRATION_DONE_PROPERTY, false))
    val fixture = codeInsightFixture
    val listener = PyNewFormatterDefaultsReformatListener()
    val disposable = Disposer.newDisposable()
    try {
      val masterSwitch = Registry.get(PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY)
      masterSwitch.setValue(false, disposable)
      val balloons = collectSwitchBalloons(fixture.project, disposable)

      fixture.configureByText("a.py", "x = 1\n")
      listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
      assertTrue(balloons.isEmpty(), "The master switch is off")

      masterSwitch.setValue(true, disposable)
      listener.reformat(projectViewContext(fixture.file.virtualFile))
      assertTrue(balloons.isEmpty(), "A reformat from the Project view can be a cancelled dialog")

      fixture.configureByText("a.txt", "text\n")
      listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
      assertTrue(balloons.isEmpty(), "The file is not a Python file")

      fixture.configureByText("b.py", "x = 1\n")
      val externalFormatterDisposable = Disposer.newDisposable(disposable)
      FormattingService.EP_NAME.point.registerExtension(ExternalPythonFormattingService(), LoadingOrder.FIRST, externalFormatterDisposable)
      listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
      assertTrue(balloons.isEmpty(), "An alternative formatter formats the file")
      Disposer.dispose(externalFormatterDisposable)

      val schemes = CodeStyleSchemes.getInstance()
      val customScheme = schemes.createNewScheme("Custom", schemes.defaultScheme)
      schemes.addScheme(customScheme)
      schemes.setCurrentScheme(customScheme)
      try {
        listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
        assertTrue(balloons.isEmpty(), "The switch does not change a custom IDE-level scheme")
      }
      finally {
        schemes.deleteScheme(customScheme)
      }

      val codeStyleManager = CodeStyleSettingsManager.getInstance(fixture.project)
      val usedPerProjectSettings = codeStyleManager.USE_PER_PROJECT_SETTINGS
      codeStyleManager.USE_PER_PROJECT_SETTINGS = true
      try {
        listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
        assertTrue(balloons.isEmpty(), "The switch does not change a project-level scheme")
      }
      finally {
        codeStyleManager.USE_PER_PROJECT_SETTINGS = usedPerProjectSettings
      }

      listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
      assertEquals(1, balloons.size)

      listener.reformat(EditorUtil.getEditorDataContext(fixture.editor))
      assertEquals(1, balloons.size, "The balloon shows only once in a session")
      balloons.forEach { it.expire() }
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  @TestFor(issues = ["PY-92100"])
  fun `advanced setting has no effect while the master switch is off`() {
    val disposable = Disposer.newDisposable()
    try {
      Registry.get(PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY).setValue(false, disposable)
      assertFalse(AdvancedSettings.getBoolean(PY_NEW_FORMATTER_DEFAULTS_SETTING_ID))
      (AdvancedSettings.getInstance() as AdvancedSettingsImpl).setSetting(PY_NEW_FORMATTER_DEFAULTS_SETTING_ID, true, disposable)

      assertTrue(AdvancedSettings.getBoolean(PY_NEW_FORMATTER_DEFAULTS_SETTING_ID))
      assertFalse(isPyNewFormatterDefaultsActive())
      assertFalse(PropertiesComponent.getInstance().getBoolean(MIGRATION_DONE_PROPERTY, false))
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  @Test
  @TestFor(issues = ["PY-92100"])
  fun `advanced setting shows only while the master switch is on`() {
    val bean = AdvancedSettingBean.EP_NAME.extensionList.single { it.id == PY_NEW_FORMATTER_DEFAULTS_SETTING_ID }
    assertEquals(PyBundle.message("advanced.setting.python.formatter.use.new.defaults"), bean.title())
    val disposable = Disposer.newDisposable()
    try {
      Registry.get(PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY).setValue(false, disposable)
      assertFalse(bean.isVisible())
      Registry.get(PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY).setValue(true, disposable)
      assertTrue(bean.isVisible())
    }
    finally {
      Disposer.dispose(disposable)
    }
  }

  private fun collectSwitchBalloons(project: Project, disposable: Disposable): List<Notification> {
    val balloons = mutableListOf<Notification>()
    project.messageBus.connect(disposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.displayId == "python.code.style.migration") balloons += notification
      }
    })
    return balloons
  }

  private fun projectViewContext(file: VirtualFile): DataContext =
    SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, codeInsightFixture.project)
      .add(CommonDataKeys.VIRTUAL_FILE_ARRAY, arrayOf(file))
      .build()

  // The platform calls the listener on the EDT, after the action.
  private fun AnActionListener.reformat(context: DataContext) = runInEdtAndWait {
    val action = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_REFORMAT)
    afterActionPerformed(action, TestActionEvent.createTestEvent(action, context), AnActionResult.PERFORMED)
  }

  /** Claims every Python file, the same way Black or Ruff does when it is on. */
  private class ExternalPythonFormattingService : FormattingService {
    override fun getFeatures(): Set<FormattingService.Feature> = emptySet()
    override fun canFormat(file: PsiFile): Boolean = file.language.isKindOf(PythonLanguage.getInstance())
    override fun formatElement(element: PsiElement, canChangeWhiteSpaceOnly: Boolean): PsiElement = element
    override fun formatElement(element: PsiElement, range: TextRange, canChangeWhiteSpaceOnly: Boolean): PsiElement = element
    override fun formatRanges(file: PsiFile, rangesInfo: FormattingRangesInfo, canChangeWhiteSpaceOnly: Boolean, quickFormat: Boolean) {}
    override fun getImportOptimizers(file: PsiFile): Set<ImportOptimizer> = emptySet()
  }
}
