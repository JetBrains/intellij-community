// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.checkin

import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import org.jetbrains.idea.svn.SvnBundle
import org.jetbrains.idea.svn.SvnVcs
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

internal class KeepLocksComponent(private val mySvnVcs: SvnVcs) : RefreshableOnComponent {
  private val myKeepLocksBox: JCheckBox
  private val myPanel: JPanel
  private val myAutoUpdate: JCheckBox

  init {
    myPanel = JPanel(BorderLayout())
    myKeepLocksBox = JCheckBox(SvnBundle.message("checkbox.checkin.keep.files.locked"))
    myAutoUpdate = JCheckBox(SvnBundle.message("checkbox.checkin.auto.update.after.commit"))
    myPanel.add(myAutoUpdate, BorderLayout.NORTH)
    myPanel.add(myKeepLocksBox, BorderLayout.CENTER)
  }

  override fun getComponent(): JComponent {
    return myPanel
  }

  override fun saveState() {
    val configuration = mySvnVcs.svnConfiguration
    configuration.isKeepLocks = myKeepLocksBox.isSelected
    configuration.isAutoUpdateAfterCommit = myAutoUpdate.isSelected
  }

  override fun restoreState() {
    val configuration = mySvnVcs.svnConfiguration
    myKeepLocksBox.isSelected = configuration.isKeepLocks
    myAutoUpdate.isSelected = configuration.isAutoUpdateAfterCommit
  }
}