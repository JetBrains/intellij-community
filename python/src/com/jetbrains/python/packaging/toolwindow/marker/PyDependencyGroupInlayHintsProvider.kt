// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.marker

import com.intellij.codeInsight.hints.ChangeListener
import com.intellij.codeInsight.hints.FactoryInlayHintsCollector
import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.InlayHintsCollector
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSink
import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import com.intellij.codeInsight.hints.presentation.BasePresentation
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.dependencies.spi.resolveDependencyGroupName
import com.intellij.openapi.application.EDT
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.management.isDependencyGroupSupported
import com.jetbrains.python.packaging.toolwindow.ui.PyInstallPackageDialog
import com.jetbrains.python.sdk.PythonSdkUtil
import org.toml.lang.psi.TomlKeySegment
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Shows a grey, plain-text `+ Add package` inlay at the end of every dependency-group header line
 * in `pyproject.toml`. Hovering paints the inlay in the link foreground colour; left-clicking
 * (no modifier) opens the Install Package dialog with the owning module and the dependency group
 * pre-selected.
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
    return Collector(editor)
  }

  private class Collector(editor: Editor) : FactoryInlayHintsCollector(editor) {
    override fun collect(element: PsiElement, editor: Editor, sink: InlayHintsSink): Boolean {
      if (element !is TomlKeySegment) return true
      val groupName = resolveDependencyGroupName(element) ?: return true
      val label = PyBundle.message("INLAY.py.packaging.group.add")
      val presentation = AddPackagePresentation(editor, label) {
        val project = element.project
        val module = ModuleUtilCore.findModuleForPsiElement(element) ?: return@AddPackagePresentation
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
          withContext(Dispatchers.EDT) {
            PyInstallPackageDialog(project).show(preselectModuleName = preselectName, preselectGroupName = groupName)
          }
        }
      }
      val withCursor = factory.withCursorOnHover(presentation, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
      sink.addInlineElement(element.textRange.endOffset, relatesToPrecedingText = true, presentation = withCursor, placeAtTheEndOfLine = true)
      return true
    }
  }
}

/**
 * Custom presentation that paints `+` and `Add package` as two glyph runs separated by a tight
 * pixel gap (instead of a full space character) and switches the foreground to the link colour on
 * hover. Owns its own mouse handling so a single left-click without a modifier triggers
 * [onClick]; bypasses `factory.text` / `factory.smallText` to avoid the default inlay background.
 */
private class AddPackagePresentation(
  editor: Editor,
  label: String,
  private val onClick: () -> Unit,
) : BasePresentation() {
  private val font: Font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
  private val idleColor = editor.colorsScheme
                            .getAttributes(DefaultLanguageHighlighterColors.INLAY_TEXT_WITHOUT_BACKGROUND)
                            ?.foregroundColor
                          ?: JBColor.GRAY
  private val hoverColor = editor.colorsScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)?.foregroundColor
                           ?: JBUI.CurrentTheme.Link.Foreground.ENABLED
  private val fontMetrics = editor.contentComponent.getFontMetrics(font)

  private val rest: String = label.removePrefix("+").trimStart()
  private val plusWidth = fontMetrics.stringWidth("+")
  private val restWidth = fontMetrics.stringWidth(rest)
  private val gap = JBUI.scale(3)

  private var hovered = false

  override val width: Int = plusWidth + gap + restWidth
  override val height: Int = fontMetrics.height

  override fun paint(g: Graphics2D, attributes: TextAttributes) {
    g.font = font
    g.color = if (hovered) hoverColor else idleColor
    g.drawString("+", 0, fontMetrics.ascent)
    g.drawString(rest, plusWidth + gap, fontMetrics.ascent)
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    if (!hovered) {
      hovered = true
      fireContentChanged(Rectangle(0, 0, width, height))
    }
  }

  override fun mouseExited() {
    if (hovered) {
      hovered = false
      fireContentChanged(Rectangle(0, 0, width, height))
    }
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    if (event.button == MouseEvent.BUTTON1) {
      onClick()
      event.consume()
    }
  }

  override fun toString(): String = "+ $rest"
}
