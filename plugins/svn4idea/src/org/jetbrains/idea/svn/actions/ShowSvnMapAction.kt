// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.actions

import com.intellij.vcs.VcsShowToolWindowTabAction
import org.jetbrains.idea.svn.WorkingCopiesContent

class ShowSvnMapAction : VcsShowToolWindowTabAction() {
  override val tabName: String get() = WorkingCopiesContent.getTabName()
}