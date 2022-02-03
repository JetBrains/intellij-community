// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.BuildSystemTypeDetector
import org.jetbrains.kotlin.idea.framework.MAVEN_SYSTEM_ID

class MavenDetector : BuildSystemTypeDetector {
    override fun detectBuildSystemType(module: Module): BuildSystemType? {
        val project = module.project
        return if (!project.isDisposed && MavenProjectsManager.getInstance(project).isMavenizedModule(module)) {
            BuildSystemType.Maven
        } else {
            null
        }
    }
}

fun Module.isMavenModule(): Boolean {
    return ExternalSystemApiUtil.isExternalSystemAwareModule(MAVEN_SYSTEM_ID, this)
}