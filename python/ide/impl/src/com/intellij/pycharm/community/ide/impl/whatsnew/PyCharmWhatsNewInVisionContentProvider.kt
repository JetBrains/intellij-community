// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider

// The files are missing in master. Both files were added, and version was updated only in stable brunches.
internal class PyCharmWhatsNewInVisionContentProvider : WhatsNewInVisionContentProvider() {
  override val baseResourcePathInClassLoader: String = "whatsNew"

  private var visionFileNames: List<String>? = null

  override val visionJsonFileNames: List<String>
    get() = visionFileNames.orEmpty()

  private suspend fun getVisionFileNames(): List<String> {
    val applicationInfo = ApplicationInfo.getInstance()

    val fileName = "pycharm${applicationInfo.majorVersion}.${applicationInfo.minorVersion}.json"
    if (getResource(getResourceNameByPath(fileName)).checkAvailability()) {
      return listOf(fileName)
    }

    return emptyList()
  }

  override suspend fun isAvailable(): Boolean {
    if (visionFileNames == null) {
      visionFileNames = getVisionFileNames()
    }

    return !InitialConfigImportState.isNewUser() && super.isAvailable()
  }
}