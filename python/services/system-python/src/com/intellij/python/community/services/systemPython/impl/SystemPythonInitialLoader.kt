// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.services.systemPython.impl

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.community.services.systemPython.getCacheTimeout

private val logger = fileLogger()

// Preload pythons as soon as a project gets loaded
internal class SystemPythonInitialLoader : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (getCacheTimeout() == null) return // Cache is disabled, no need to preload it
    logger.debug("Preloading pythons for $project")
    SystemPythonService().findSystemPythons(project.getEelDescriptor().upgrade())
  }
}