// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.BuildSystemTypeDetector

class MavenDetector : BuildSystemTypeDetector {
    override fun detectBuildSystemType(module: Module): BuildSystemType? {
        val project = module.project
        return if (!project.isDisposed && MavenProjectsManager.getInstance(project).isMavenizedModule(module)) {
            BuildSystemType.Maven
        } else {
            null
        }
    }

    override fun isMavenizedProject(project: Project): Boolean {
        return !project.isDisposed && MavenProjectsManager.getInstance(project).isMavenizedProject
    }
}
