// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.marker

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.InlayHintsUtils
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.dependencies.spi.resolveDependencyGroupName
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.isDependencyGroupSupported
import com.jetbrains.python.packaging.statistics.PyInstallDialogSource
import com.jetbrains.python.packaging.statistics.PythonPackagesToolwindowStatisticsCollector
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog
import com.jetbrains.python.sdk.PythonSdkUtil
import org.toml.lang.psi.TomlKeySegment
import java.awt.Graphics2D
import javax.swing.JComponent
import kotlin.math.max

/**
 * Shows a grey, plain-text `+ Add package` inlay at the end of every dependency-group header line
 * in `pyproject.toml`. Hovering paints the inlay in the link foreground colour; a plain left-click
 * opens the Install Package dialog with the owning module and the dependency group pre-selected.
 *
 * This deliberately stays on the (non-declarative) [InlayHintsProvider] framework: it needs a plain
 * left-click, whereas declarative-inlay action handlers only fire on Ctrl/Cmd+click
 * ([com.intellij.codeInsight.hints.declarative.impl.interaction.DefaultInlayInteractionHandler]).
 * Interaction (plain click, link colour on hover, hand cursor) is delegated to
 * [com.intellij.codeInsight.hints.presentation.PresentationFactory.referenceOnHover]; text layout is
 * delegated to the platform's [InlayTextMetricsStorage] and the same baseline formula the declarative
 * renderer uses (see [AddPackagePresentation]).
 *
 * Format recognition is delegated to [PyDependencyGroupLocator] extensions.
 */
internal class PyDependencyGroupInlayHintsProvider : InlayHintsProvider<NoSettings> {
  override val key: SettingsKey<NoSettings> = SettingsKey("python.packaging.dependency.group.inlay")
  override val name: String get() = PyBundle.message("INLAY.py.packaging.group.name")
  override val previewText: String? = null
  override val group: InlayGroup get() = InlayGroup.OTHER_GROUP

  override fun createSettings(): NoSettings = NoSettings()

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable = object : ImmediateConfigurable {
    override fun createComponent(listener: ChangeListener): JComponent = panel {}
  }

  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    val virtualFile = file.virtualFile ?: return null
    if (virtualFile.name != PY_PROJECT_TOML) return null
    val module = ModuleUtilCore.findModuleForFile(file) ?: return null
    val sdk = PythonSdkUtil.findPythonSdk(module) ?: return null
    if (!isDependencyGroupSupported(sdk)) return null
    if (hasParseErrors(file)) return null
    return Collector(editor)
  }

  companion object {
    /**
     * Whether [file] carries any TOML parse errors. The inlay resolver runs on segments that
     * survive the TOML parser's error recovery (a bare `test` line under `[dependency-groups]`
     * still produces a valid `TomlKeySegment`), so it cannot tell an incomplete entry from a
     * finished one on its own. We hide the "+ Add package" inlay whenever the file has *any*
     * `PsiErrorElement` — the click handler eventually shells out to `uv add` / `poetry add`,
     * both of which refuse malformed TOML with a raw stderr trace at the user (PY-91037).
     */
    @JvmStatic
    fun hasParseErrors(file: PsiFile): Boolean =
      PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null
  }

  private class Collector(editor: Editor) : FactoryInlayHintsCollector(editor) {
    private val metricsStorage = InlayHintsUtils.getTextMetricStorage(editor)

    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
      if (element !is TomlKeySegment) return true
      val groupName = resolveDependencyGroupName(element) ?: return true
      val label = PyBundle.message("INLAY.py.packaging.group.add")
      val text = AddPackagePresentation(editor, metricsStorage, label)
      val presentation = factory.referenceOnHover(text) { _, _ -> openInstallDialog(element, groupName) }
      sink.addInlineElement(element.textRange.endOffset, relatesToPrecedingText = true, presentation = presentation, placeAtTheEndOfLine = true)
      return true
    }

    private fun openInstallDialog(element: PsiElement, groupName: String) {
      val project = element.project
      val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return
      val pyprojectVf = element.containingFile?.virtualFile
      // Workspace members (uv) are indexed by `[project].name` from the member's pyproject.toml
      // when present, otherwise by the IntelliJ module name. Prefer project.name so the dialog
      // preselects the *clicked* member, not the SDK-owning root module. parseCached is
      // suspend, so dispatch through the packaging coroutine scope and open the dialog back on
      // EDT.
      PyPackageCoroutine.launch(project) {
        val preselectName = pyprojectVf
                              ?.let { PyProjectToml.parseCached(project, it) }
                              ?.project?.name
                            ?: module.name
        // Bind the packaging service to the *clicked* module's SDK before the dialog opens.
        // Without this, the dialog falls back to `findFirstPythonSdk()`, which in a multi-project
        // workspace (e.g. poetry subprojects) may pick the wrong SDK — or the service may still
        // be uninitialized, in which case the install click silently no-ops because
        // `packagingService.currentSdk` is null (PY-91300).
        val moduleSdk = readAction { PythonSdkUtil.findPythonSdk(module) }
        if (moduleSdk != null) {
          project.service<PyPackagingToolWindowService>().initForSdk(moduleSdk)
        }
        withContext(Dispatchers.EDT) {
          PythonPackagesToolwindowStatisticsCollector.installDialogOpenedEvent.log(PyInstallDialogSource.INLAY_HINT)
          PyInstallPackageDialog(project).show(preselectModuleName = preselectName, preselectGroupName = groupName)
        }
      }
    }
  }
}

/**
 * Plain-text inlay whose glyphs sit on the editor's own text baseline. All layout comes from the
 * platform [InlayTextMetricsStorage]; the baseline is computed with the same formula the declarative
 * inlay renderer uses (`TextInlayPresentationEntry`) so the hint lines up with the surrounding code
 * regardless of font size, zoom, or line spacing.
 *
 * Colour is attribute-driven so [com.intellij.codeInsight.hints.presentation.PresentationFactory.referenceOnHover]
 * can recolour it on hover: the idle grey comes from `INLAY_TEXT_WITHOUT_BACKGROUND`, and when the
 * reference wrapper injects a foreground (the link colour on hover) that colour wins.
 */
private class AddPackagePresentation(
  private val editor: Editor,
  private val metricsStorage: InlayTextMetricsStorage,
  private val text: String,
) : BasePresentation() {
  private val idleColor = editor.colorsScheme
                            .getAttributes(DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND)
                            ?.foregroundColor
                          ?: JBColor.GRAY

  private fun metrics() = metricsStorage.getFontMetrics(small = true)

  override val width: Int get() = metrics().getStringWidth(text)
  override val height: Int get() = editor.lineHeight

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    val metrics = metrics()
    g.font = metrics.font
    g.color = attributes.foregroundColor ?: idleColor
    val baseline = max(editor.ascent, (height + metrics.ascent - metrics.descent) / 2) - 1
    g.drawString(text, 0, baseline)
  }

  override fun toString(): String = text
}
