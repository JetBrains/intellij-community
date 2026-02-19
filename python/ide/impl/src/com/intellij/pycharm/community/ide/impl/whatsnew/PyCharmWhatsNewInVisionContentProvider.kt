// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider
import com.intellij.util.PlatformUtils.isCommunityEdition

// The files are missing in master. Both files were added, and version was updated only in stable brunches.
class PyCharmWhatsNewInVisionContentProvider : WhatsNewInVisionContentProvider() {
  override val baseResourcePathInClassLoader: String = "whatsNew"
  override val visionJsonFileNames: List<String> = listOf(if (isCommunityEdition()) "pycharmCE2025.2.json" else "pycharm2025.2.json")

  override suspend fun isAvailable(): Boolean {
    // Will skip WhatsNew if the first launch / new user, not to overload user with info.
    if (InitialConfigImportState.isNewUser()) {
      return false
    }

    return super.isAvailable()
  }
}