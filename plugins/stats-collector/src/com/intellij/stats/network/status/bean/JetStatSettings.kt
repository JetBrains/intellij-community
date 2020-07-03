// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.network.status.bean

data class JetStatSettings(val status: String,
                           val salt: String,
                           val experimentVersion: Int,
                           val performExperiment: Boolean,
                           val url: String,
                           val urlForZipBase64Content: String)