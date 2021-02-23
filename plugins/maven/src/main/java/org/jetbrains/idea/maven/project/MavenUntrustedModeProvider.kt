// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project

import com.intellij.ide.impl.UntrustedProjectModeProvider
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.project.Project

class MavenUntrustedModeProvider : UntrustedProjectModeProvider {
  override fun shouldShowEditorNotification(project: Project): Boolean {
    return MavenProjectsManager.getInstance(project).isMavenizedProject && !project.isTrusted()
  }
}