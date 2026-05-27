// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.project.Project
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.search.refIndex.bta.BtaMavenCriApplicabilityProvider
import org.jetbrains.kotlin.idea.search.refIndex.bta.KOTLIN_CRI_GENERATION_PROPERTY

class MavenBtaCriApplicabilityProvider : BtaMavenCriApplicabilityProvider {
    /**
     * Returns `true` when any Maven project has enabled [KOTLIN_CRI_GENERATION_PROPERTY] property
     */
    override fun isApplicable(project: Project): Boolean =
        MavenProjectsManager.getInstanceIfCreated(project)?.projects?.any { mavenProject ->
            mavenProject.properties.getProperty(KOTLIN_CRI_GENERATION_PROPERTY).toBoolean()
        } ?: false
}
