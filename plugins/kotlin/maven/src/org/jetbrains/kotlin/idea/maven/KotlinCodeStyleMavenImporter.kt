// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.maven

import org.jetbrains.idea.maven.importing.MavenAfterImportConfigurator
import org.jetbrains.idea.maven.importing.MavenApplicableConfigurator
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.maven.KotlinMavenImporter.Companion.KOTLIN_PLUGIN_ARTIFACT_ID
import org.jetbrains.kotlin.idea.maven.KotlinMavenImporter.Companion.KOTLIN_PLUGIN_GROUP_ID

private const val KOTLIN_CODE_STYLE_MAVEN_SETTING = "kotlin.code.style"

internal class KotlinCodeStyleMavenImporter : MavenApplicableConfigurator(KOTLIN_PLUGIN_GROUP_ID, KOTLIN_PLUGIN_ARTIFACT_ID), MavenAfterImportConfigurator {
    private fun getCodeStyleString(mavenProject: MavenProject): String? {
        return mavenProject.properties.getProperty(KOTLIN_CODE_STYLE_MAVEN_SETTING)
    }

    override fun isApplicable(mavenProject: MavenProject): Boolean {
        return super.isApplicable(mavenProject) && getCodeStyleString(mavenProject) != null
    }

    override fun afterImport(context: MavenAfterImportConfigurator.Context) {
        val project = context.project
        val tree = MavenProjectsManager.getInstance(project).projectsTree
        context.mavenProjectsWithModules.forEach { mavenProjectWithModules ->
            val mavenProject = mavenProjectWithModules.mavenProject
            if (mavenProject !in tree.rootProjects) return
            ProjectCodeStyleImporter.apply(project, getCodeStyleString(mavenProject))
        }
    }
}