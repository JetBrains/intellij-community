// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xray.frontend.impl.action

import com.intellij.codeInsight.hints.settings.language.isInlaySettingsEditor
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.icons.AllIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.InspectionWidgetActionProvider
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ui.JBUI
import com.intellij.xray.frontend.api.XRayBundle
import com.intellij.xray.frontend.api.XRayMode
import com.intellij.xray.frontend.api.editorTracking.XRAY_EDITOR_USE_MODE
import com.intellij.xray.frontend.api.editorTracking.XRayEditorUseMode
import com.intellij.xray.frontend.impl.service.XRayModeService
import javax.swing.JComponent

internal class XRayModeActionProvider : InspectionWidgetActionProvider {
  override fun createAction(editor: Editor): AnAction? {
    val project = editor.project ?: return null
    if (project.isDefault) return null
    // Don't offer the X-Ray toggle in the inlay-settings preview editor, where hints are driven by the settings panel.
    if (isInlaySettingsEditor(editor)) return null

    PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null

    return object : DumbAwareToggleAction(XRayBundle.message("action.x.ray.mode.text"), null, AllIcons.General.InspectionsEye),
                    CustomComponentAction {

      override fun update(e: AnActionEvent) {
        super.update(e)

        val service = XRayModeService.getInstance()

        if (isInlaySettingsEditor(editor)) {
          e.presentation.isEnabledAndVisible = false
          return
        }

        e.presentation.text = when (service.getXRayModeState()) {
          XRayMode.DISABLED -> null
          XRayMode.BASIC -> XRayBundle.message("xray.config.state.basic")
          XRayMode.EXTENDED -> XRayBundle.message("xray.config.state.extended")
        }
      }

      override fun isSelected(e: AnActionEvent): Boolean {
        val service = XRayModeService.getInstance()

        return service.isXRayModeEnabled()
      }

      override fun setSelected(e: AnActionEvent, state: Boolean) {
        val service = XRayModeService.getInstance()

        val newState = if (state) XRayMode.BASIC else XRayMode.DISABLED
        service.setXRayMode(newState, editor)
      }

      override fun actionPerformed(e: AnActionEvent) {
        val service = XRayModeService.getInstance()

        service.cycleXRayMode(editor)
        ActionToolbarImpl.updateAllToolbarsImmediately(false)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

      override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return object : ActionButtonWithText(this, presentation, place, JBUI.size(18)) {
          override fun updateToolTipText() {
            val service = XRayModeService.getInstance()
            if (UISettings.isIdeHelpTooltipEnabled()) {
              HelpTooltip.dispose(this)
              val tooltipTitleKey = when (service.getXRayModeState()) {
                XRayMode.DISABLED -> "xray.config.next.basic"
                XRayMode.BASIC -> "xray.config.next.extended"
                XRayMode.EXTENDED -> "xray.config.next.off"
              }
              val description = when (val editorUseMode = editor.getUserData(XRAY_EDITOR_USE_MODE)) {
                is XRayEditorUseMode ->  XRayBundle.message("xray.config.description_template.full",
                                                            XRayBundle.message("xray.config.mode", editorUseMode.title)
                )
                null -> XRayBundle.message("xray.config.description_template.short")
              }
              HelpTooltip().setTitle(HtmlChunk.text(XRayBundle.message(tooltipTitleKey)))
                .setDescription(HtmlChunk.text(description))
                .setLink(XRayBundle.message("xray.config.configure")) {
                  showInlaySettings(project = project, Language.ANY, null)
                }.installOn(this)
            }
          }
        }
      }
    }
  }
}
