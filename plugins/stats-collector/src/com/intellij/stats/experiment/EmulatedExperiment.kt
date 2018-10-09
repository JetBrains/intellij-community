// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.experiment

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PermanentInstallationID

class EmulatedExperiment {
    fun emulate(experimentVersion: Int, performExperiment: Boolean, salt: String): Pair<Int, Boolean>? {
        val application = ApplicationManager.getApplication()
        if (!application.isEAP || application.isUnitTestMode || experimentVersion != 2 || performExperiment) {
            return null
        }

        val userId = PermanentInstallationID.get()
        val hash = (userId + salt).hashCode() % 10
        val version = when (hash) {
            3, 4 -> 4
            else -> 3
        }
        val perform = hash == 4
        return Pair(version, perform)
    }
}