// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.space.vcs.share

import circlet.client.api.PR_Project
import com.intellij.space.messages.SpaceBundle
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

class SpaceProjectListComboRenderer : ColoredListCellRenderer<PR_Project>() {
  override fun customizeCellRenderer(list: JList<out PR_Project>,
                                     project: PR_Project?,
                                     index: Int,
                                     selected: Boolean,
                                     hasFocus: Boolean) {
    project ?: return
    append(project.name) // NON-NLS
    append("  ")
    append(SpaceBundle.message("project.list.project.key.description", project.key.key), SimpleTextAttributes.GRAY_SMALL_ATTRIBUTES)
  }
}
