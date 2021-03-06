// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status.bean

data class JetStatSettings(val status: String = "not ok",
                           val salt: String? = null,
                           val experimentVersion: Int = 2,
                           val performExperiment: Boolean = false,
                           val url: String? = null,
                           val urlForZipBase64Content: String = "")