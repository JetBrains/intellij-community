// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.miscProject.impl

import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOption
import com.intellij.platform.experiment.ab.impl.experiment.ABExperimentOptionId
import com.intellij.platform.experiment.ab.impl.option.ABExperimentOptionGroupSize

/**
 * A/B testing option for misc pycharm project
 */
internal class PyMiscProjectExperimentOption : ABExperimentOption {
  override val id: ABExperimentOptionId = ABExperimentOptionId("pycharm.miscProject")

  override fun getGroupSizeForIde(isPopularIde: Boolean): ABExperimentOptionGroupSize = ABExperimentOptionGroupSize(128) // half: 256/2

  override fun checkIdeIsSuitable(): Boolean = true // This module only goes to PyCharm

  override fun checkIdeVersionIsSuitable(): Boolean = true
}