// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.impl.logs.MLEventLoggerProvider.Companion.ML_RECORDER_ID

@Service
internal class FinalImportRankingStatusService {
  private val REGISTRY_KEY = "quickfix.ranking.ml"

  enum class RegistryOption {
    IN_EXPERIMENT,
    ENABLED,
    DISABLED
  }

  private val bucket: Int
    by lazy { service<EventLogConfiguration>().getOrCreate(ML_RECORDER_ID).bucket }

  private val mlEnabledOnBucket: Boolean
    by lazy { bucket % 2 == 0 }

  private fun getRegistryOption(): RegistryOption = RegistryOption.valueOf(requireNotNull(Registry.get(REGISTRY_KEY).selectedOption) { "Registry key $REGISTRY_KEY can't be empty" })

  val status: FinalImportRankingStatus
    get() {
      return when (getRegistryOption()) {
        RegistryOption.IN_EXPERIMENT -> FinalImportRankingStatus(mlEnabledOnBucket, true)
        RegistryOption.ENABLED -> FinalImportRankingStatus(true, false)
        RegistryOption.DISABLED -> FinalImportRankingStatus(false, false)
      }
    }
}

internal class FinalImportRankingStatus(
  val mlEnabled: Boolean,
  val mlStatusCorrespondsToBucket: Boolean,
)
