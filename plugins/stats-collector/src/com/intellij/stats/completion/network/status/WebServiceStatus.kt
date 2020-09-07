// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.network.status

interface WebServiceStatus {
  val id: String

  fun isServerOk(): Boolean
  fun dataServerUrl(): String

  fun update()
}