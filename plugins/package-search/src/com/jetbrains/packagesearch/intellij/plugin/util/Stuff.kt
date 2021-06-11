package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project.MavenProjectsManager

internal fun MavenProjectsManager.tryFindProjectOrNull(nativeModule: Module) =
    runCatching { findProject(nativeModule) }.getOrNull()
