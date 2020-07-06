// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

data class ExperimentGroupInfo(val number: Int,
                               val description: String,
                               val experimentBucket: Int,
                               val useMLRanking: Boolean,
                               val showArrows: Boolean,
                               val calculateFeatures: Boolean)