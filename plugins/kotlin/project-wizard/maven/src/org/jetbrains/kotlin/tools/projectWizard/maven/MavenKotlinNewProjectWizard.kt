// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.openapi.project.Project
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.idea.maven.wizards.MavenNewProjectWizardStep
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.KotlinNewProjectWizard
import org.jetbrains.kotlin.tools.projectWizard.plugins.buildSystem.BuildSystemType

internal class MavenKotlinNewProjectWizard : BuildSystemKotlinNewProjectWizard {

    override val name = "Maven"

    override fun createStep(parent: KotlinNewProjectWizard.Step) =
        object : MavenNewProjectWizardStep<KotlinNewProjectWizard.Step>(parent) {
            override fun setupProject(project: Project) {
                KotlinNewProjectWizard.generateProject(
                    project = project,
                    projectPath = parent.projectPath.systemIndependentPath,
                    projectName = parent.name,
                    sdk = sdk,
                    buildSystemType = BuildSystemType.Maven,
                    projectGroupId = groupId,
                    artifactId = artifactId,
                    version = version
                )
            }
        }
}
