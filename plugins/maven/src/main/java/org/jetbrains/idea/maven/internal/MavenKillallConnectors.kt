// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.internal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.idea.maven.server.MavenServerManager

class MavenKillallConnectors : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    MavenServerManager.getInstance().allConnectors.forEach {
      it.stop(false)
    }
  }
}