// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.stats.completion.tracker

import com.intellij.internal.statistic.DeviceIdManager

interface InstallationIdProvider {
  fun installationId(): String
}

private class PermanentInstallationIdProvider : InstallationIdProvider {
  override fun installationId(): String {
    try {
      return DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "FUS")
    }
    catch (e: DeviceIdManager.InvalidDeviceIdTokenException) {
      return "000000000000000-0000-0000-0000-000000000000"
    }
  }
}
