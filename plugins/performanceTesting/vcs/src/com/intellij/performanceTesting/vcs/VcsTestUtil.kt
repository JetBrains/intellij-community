// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx

object VcsTestUtil {
  /**
   * This function modifies 'Preferences->Version Control->Confirmation->When files are created' option
   */
  fun provisionVcsAddFileConfirmation(project: Project, fileConfirmation: VcsAddFileConfirmation) {
    val addFileConfirmationSetting = ProjectLevelVcsManagerEx
      .getInstanceEx(project)
      .getConfirmation(VcsConfiguration.StandardConfirmation.ADD)

    when (fileConfirmation) {
      VcsAddFileConfirmation.ADD -> addFileConfirmationSetting.value = VcsShowConfirmationOption.Value.SHOW_CONFIRMATION
      VcsAddFileConfirmation.DO_NOTHING -> addFileConfirmationSetting.value = VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
      VcsAddFileConfirmation.DO_SILENTLY -> VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
    }
  }

  enum class VcsAddFileConfirmation {
    ADD,
    DO_NOTHING,
    DO_SILENTLY
  }
}