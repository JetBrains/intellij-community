// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import com.intellij.completion.settings.CompletionMLRankingSettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.lang.Language

class ClientExperimentStatus : ExperimentStatus {
    companion object {
        const val DEFAULT_EXPERIMENT_VERSION: Int = 2
        const val GROUP_A_EXPERIMENT_VERSION: Int = 7
        const val GROUP_B_EXPERIMENT_VERSION: Int = 8
        const val GROUP_KT_WITH_DIFF_EXPERIMENT_VERSION: Int = 9
        const val GROUP_PY_WITH_DIFF_EXPERIMENT_VERSION: Int = 10

        private val ALL_GROUPS: Set<Int> = setOf(
          GROUP_A_EXPERIMENT_VERSION,
          GROUP_B_EXPERIMENT_VERSION,
          GROUP_KT_WITH_DIFF_EXPERIMENT_VERSION,
          GROUP_PY_WITH_DIFF_EXPERIMENT_VERSION
        )

        private val RANKING_GROUPS: Set<Int> = setOf(
          GROUP_B_EXPERIMENT_VERSION,
          GROUP_KT_WITH_DIFF_EXPERIMENT_VERSION,
          GROUP_PY_WITH_DIFF_EXPERIMENT_VERSION
        )

        const val DIFF_ENABLED_PROPERTY_KEY = "ml.completion.diff.registry.was.enabled"
    }

    private fun enableOnceDiffShowing() {
        val properties = PropertiesComponent.getInstance()
        if (!properties.getBoolean(DIFF_ENABLED_PROPERTY_KEY, false)) {
            CompletionMLRankingSettings.getInstance().isShowDiffEnabled = true
            properties.setValue(DIFF_ENABLED_PROPERTY_KEY, true)
        }
    }

    override fun isExperimentOnCurrentIDE(language: Language): Boolean = experimentVersion(language) in ALL_GROUPS

    //TODO: use config for versions describing
    override fun experimentVersion(language: Language): Int {
        return when (EventLogConfiguration.bucket % 8) {
            3 -> GROUP_A_EXPERIMENT_VERSION
            4 -> GROUP_B_EXPERIMENT_VERSION
            5 -> {
                if (language.id == "Kotlin") GROUP_KT_WITH_DIFF_EXPERIMENT_VERSION.apply { enableOnceDiffShowing() }
                else DEFAULT_EXPERIMENT_VERSION
            }
            6 -> {
                if (language.id == "Python") GROUP_PY_WITH_DIFF_EXPERIMENT_VERSION.apply { enableOnceDiffShowing() }
                else DEFAULT_EXPERIMENT_VERSION
            }
            else -> DEFAULT_EXPERIMENT_VERSION
        }
    }

    override fun shouldRank(language: Language): Boolean = experimentVersion(language) in RANKING_GROUPS
}