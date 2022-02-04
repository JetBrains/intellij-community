// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.configuration.KotlinMigrationProjectService
import org.jetbrains.kotlin.idea.configuration.notifications.checkExternalKotlinCompilerVersion
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.ProgressIndicatorUtils

class KotlinMavenImportListener(private val project: Project) : MavenImportListener {
    override fun importFinished(importedProjects: MutableCollection<MavenProject>, newModules: MutableList<Module>) {
      ProgressIndicatorUtils.runUnderDisposeAwareIndicator(KotlinPluginDisposable.getInstance(project)) {
        KotlinMigrationProjectService.getInstance(project).onImportFinished()
        checkExternalKotlinCompilerVersion(project)
      }
    }
}
