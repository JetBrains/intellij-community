// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.ml.features.imports

import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ml.impl.logs.MLEventLoggerProvider.Companion.ML_RECORDER_ID
import com.intellij.python.ml.features.imports.FinalImportRankingStatusService.RegistryOption
import com.jetbrains.ml.api.model.MLModel

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
      val mlModel = service<ImportsRankingModelService>().getModelOwnership()
      val registryOption = getRegistryOption()
      val registryOptionAllowsEnabling = registryOption == RegistryOption.ENABLED || (registryOption == RegistryOption.IN_EXPERIMENT && mlEnabledOnBucket)
      return if (mlModel != null && registryOptionAllowsEnabling)
        FinalImportRankingStatus.Enabled(mlModel, registryOption)
      else
        FinalImportRankingStatus.Disabled(mlModel == null, registryOption)
    }

  val shouldLoadModel: Boolean
    get() = getRegistryOption() == RegistryOption.ENABLED || (getRegistryOption() == RegistryOption.IN_EXPERIMENT && mlEnabledOnBucket)
}

internal sealed class FinalImportRankingStatus(
  val mlEnabled: Boolean,
  val mlModelUnavailable: Boolean,
  val registryOption: RegistryOption,
) {
  class Enabled(val mlModel: MLModel<Double>, registryOption: RegistryOption) : FinalImportRankingStatus(true, false, registryOption)
  class Disabled(mlModelUnavailable: Boolean, registryOption: RegistryOption) : FinalImportRankingStatus(false, mlModelUnavailable, registryOption)
}
