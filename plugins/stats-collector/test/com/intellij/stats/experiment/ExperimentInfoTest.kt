// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import com.intellij.testFramework.LightIdeaTestCase
import org.assertj.core.api.Assertions

class ExperimentInfoTest : LightIdeaTestCase() {

  fun `test experiment info is correct`() {
    val experimentInfo = ClientExperimentStatus.loadExperimentInfo()

    Assertions.assertThat(experimentInfo).isNotEqualTo(ExperimentInfo.emptyExperiment())
    Assertions.assertThat(experimentInfo.version).isNotNull()
    Assertions.assertThat(experimentInfo.experimentBucketsCount).isNotNull()
    Assertions.assertThat(experimentInfo.groups.size).isNotEqualTo(0)
    for (group in experimentInfo.groups) {
      Assertions.assertThat(group.id).isNotNull()
      Assertions.assertThat(group.experimentBucket).isNotNull()
    }
  }
}