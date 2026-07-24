// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.updateSettings.impl.UpdateSettings


internal class HackyWhatsNewPatcher : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: List<String?>) {
    val settings = UpdateSettings.getInstance()
    if (settings.whatsNewShownFor == 0 && !InitialConfigImportState.isNewUser()) {
      settings.whatsNewShownFor = 1
    }
  }
}
