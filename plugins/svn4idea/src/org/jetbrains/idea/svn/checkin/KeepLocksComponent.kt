// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.checkin

import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.idea.svn.SvnBundle
import org.jetbrains.idea.svn.SvnVcs
import javax.swing.JCheckBox
import javax.swing.JComponent

internal class KeepLocksComponent(private val svnVcs: SvnVcs) : RefreshableOnComponent {
  private val keepLocksCheckbox = JCheckBox(SvnBundle.message("checkbox.checkin.keep.files.locked"))
  private val autoUpdateCheckbox = JCheckBox(SvnBundle.message("checkbox.checkin.auto.update.after.commit"))

  override fun getComponent(): JComponent {
    return panel {
      row {
        cell(autoUpdateCheckbox)
      }
      row {
        cell(keepLocksCheckbox)
      }
    }
  }

  override fun saveState() {
    val configuration = svnVcs.svnConfiguration
    configuration.isKeepLocks = keepLocksCheckbox.isSelected
    configuration.isAutoUpdateAfterCommit = autoUpdateCheckbox.isSelected
  }

  override fun restoreState() {
    val configuration = svnVcs.svnConfiguration
    keepLocksCheckbox.isSelected = configuration.isKeepLocks
    autoUpdateCheckbox.isSelected = configuration.isAutoUpdateAfterCommit
  }
}