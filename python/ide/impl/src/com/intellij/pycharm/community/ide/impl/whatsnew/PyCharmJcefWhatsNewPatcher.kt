// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.whatsnew

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.updateSettings.impl.UpdateSettings
import com.intellij.platform.whatsNew.WhatsNewInVisionContentProvider
import java.util.concurrent.atomic.AtomicBoolean

// Disabling legacy whatsnew page in JcefWhatsNewProjectActivity if PyCharm Vision WhatsNewPage is available.
internal class PyCharmJcefWhatsNewPatcher : ProjectActivity {
  private val isStarted = AtomicBoolean(false)

  override suspend fun execute(project: Project) {
    if (isStarted.getAndSet(true)) return

    val provider = WhatsNewInVisionContentProvider.getInstance()
    if (provider.isAvailable()) {
      UpdateSettings.getInstance().whatsNewShownFor = 0
    }
  }
}
