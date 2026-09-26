// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.idea.maven.execution.MavenRunner
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.compiler.configuration.FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor

internal class MavenFailedToDownloadSuggestedSolutionsContributorImpl(val project: Project) :
    FailedToDownloadJpsMavenArtifactSuggestedSolutionsContributor {

    @Nls
    override fun getSuggestion(): String? {
        val isAnyModuleCompiledByJps = MavenProjectsManager.getInstance(project).isMavenizedProject &&
                !MavenRunner.getInstance(project).settings.isDelegateBuildToMaven
        return if (isAnyModuleCompiledByJps) KotlinMavenBundle.message("you.can.delegate.to.maven") else null
    }
}
