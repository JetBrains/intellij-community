// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.lang.psi.impl

import com.intellij.icons.AllIcons
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

internal class MarkdownAlertTitle(type: IElementType, text: CharSequence) : MarkdownLeafPsiElement(type, text) {

  fun getType(): AlertType? = AlertType.fromTitleText(text)

  enum class AlertType(val icon: Icon) {
    NOTE(AllIcons.General.BalloonInformation),
    TIP(AllIcons.Actions.IntentionBulb),
    IMPORTANT(AllIcons.General.Balloon),
    WARNING(AllIcons.General.BalloonWarning),
    CAUTION(AllIcons.General.BalloonError);

    companion object {
      fun fromTitleText(text: CharSequence): AlertType? {
        val name = text.toString().substringAfter("[!").substringBefore("]").trim().uppercase()
        return entries.find { it.name == name }
      }
    }
  }
}
