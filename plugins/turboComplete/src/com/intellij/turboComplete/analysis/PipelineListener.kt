// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.turboComplete.analysis

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.ml.impl.turboComplete.KindExecutionListener
import com.intellij.platform.ml.impl.turboComplete.ranking.KindRankingListener

interface PipelineListener : KindExecutionListener, KindRankingListener {
  companion object {
    val EP_NAME: ExtensionPointName<PipelineListener> = ExtensionPointName.create(
      "com.intellij.turboComplete.analysis.pipelineListener")
  }
}