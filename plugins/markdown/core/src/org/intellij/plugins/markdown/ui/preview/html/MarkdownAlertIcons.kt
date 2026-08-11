// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.preview.html

import org.intellij.plugins.markdown.lang.psi.impl.MarkdownAlertTitle.AlertType
import javax.swing.Icon

internal object MarkdownAlertIcons {
  fun resourceName(type: AlertType): String = "$RESOURCE_DIRECTORY/${type.name.lowercase()}.png"

  val resources: Map<String, Icon> = AlertType.entries.associate { resourceName(it) to it.icon }

  private const val RESOURCE_DIRECTORY = "alertIcons"
}
