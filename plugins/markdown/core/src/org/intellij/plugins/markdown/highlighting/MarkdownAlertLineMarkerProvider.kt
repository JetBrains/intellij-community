// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.highlighting

import com.intellij.application.options.editor.GutterIconsConfigurable
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import org.intellij.plugins.markdown.MarkdownBundle
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle
import java.util.Locale.getDefault
import javax.swing.Icon

internal class MarkdownAlertLineMarkerProvider : LineMarkerProviderDescriptor() {
  override fun getName(): String = MarkdownBundle.message("markdown.alert.line.marker.name")

  override fun getIcon(): Icon = AllIcons.General.Note

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    val type = (element as? MarkdownAlertTitle)?.getType() ?: return null
    val icon = getIcon(type) ?: return null
    val tooltip = type.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(getDefault()) else it.toString() }
    return AlertLineMarkerInfo(element, icon, tooltip)
  }

  private class AlertLineMarkerInfo(element: PsiElement, icon: Icon, @param:NlsSafe private val tooltip: String) :
    LineMarkerInfo<PsiElement>(
      element, element.textRange, icon, { tooltip }, null,
      GutterIconRenderer.Alignment.LEFT, { tooltip }
    ) {
    override fun createGutterRenderer(): GutterIconRenderer {
      return object : LineMarkerGutterIconRenderer<PsiElement>(this) {
        override fun isNavigateAction() = true
        override fun getPopupMenuActions() =
          DefaultActionGroup(
            GutterIconsConfigurable.ShowSettingsAction(
              MarkdownBundle.messagePointer("markdown.alert.gutter.icon.disable.action")
            )
          )
      }
    }
  }

  private fun getIcon(type: MarkdownAlertTitle.AlertType?): Icon? = when (type) {
    MarkdownAlertTitle.AlertType.NOTE -> AllIcons.General.BalloonInformation
    MarkdownAlertTitle.AlertType.TIP -> AllIcons.Actions.IntentionBulb
    MarkdownAlertTitle.AlertType.IMPORTANT -> AllIcons.General.Balloon
    MarkdownAlertTitle.AlertType.WARNING -> AllIcons.General.BalloonWarning
    MarkdownAlertTitle.AlertType.CAUTION -> AllIcons.General.BalloonError
    null -> null
  }
}
