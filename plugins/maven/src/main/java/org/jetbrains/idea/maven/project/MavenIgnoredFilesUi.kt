// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project

import com.intellij.ide.util.ElementsChooser
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

internal class MavenIgnoredFilesUi {

  lateinit var ignoredFilesPattersEditor: JBTextArea
  lateinit var ignoredFilesPathsChooser: ElementsChooser<String>

  @JvmField
  val panel = panel {
    row {
      ignoredFilesPattersEditor = textArea()
        .label(MavenConfigurableBundle.message("maven.settings.ignored.tooltip"), LabelPosition.TOP)
        .align(AlignX.FILL)
        .rows(2)
        .component
    }

    row {
      ignoredFilesPathsChooser = cell(ElementsChooser<String>(true))
        .label(MavenConfigurableBundle.message("maven.settings.ignored.label"), LabelPosition.TOP)
        .align(Align.FILL)
        .applyToComponent {
          emptyText.text = MavenConfigurableBundle.message("maven.settings.ignored.no.file")
        }
        .component
    }.resizableRow()
  }
}
