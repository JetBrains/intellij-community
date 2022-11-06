// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenImportListener
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.configuration.notifications.showNewKotlinCompilerAvailableNotificationIfNeeded

class KotlinMavenImportListener(private val project: Project) : MavenImportListener {
    override fun importFinished(importedProjects: Collection<MavenProject>, newModules: List<Module>) {
        KotlinCommonCompilerArgumentsHolder.getInstance(project).updateLanguageAndApi(project)
        showNewKotlinCompilerAvailableNotificationIfNeeded(project)
    }
}
