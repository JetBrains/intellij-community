// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

data class ExperimentInfo(val version: Int,
                          val experimentBucketsCount: Int,
                          val groups: List<ExperimentGroupInfo>) {
  companion object {
    private val DISABLED_EXPERIMENT = ExperimentInfo(version = 2, experimentBucketsCount = 1, groups = emptyList())
    fun disabledExperiment(): ExperimentInfo = DISABLED_EXPERIMENT
  }
}