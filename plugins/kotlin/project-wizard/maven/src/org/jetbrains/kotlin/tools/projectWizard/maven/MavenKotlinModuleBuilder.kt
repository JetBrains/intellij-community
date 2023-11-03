// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.roots.ModifiableRootModel
import org.jetbrains.idea.maven.project.MavenProjectBundle
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.AbstractMavenModuleBuilder
import org.jetbrains.kotlin.tools.projectWizard.BuildSystemKotlinNewProjectWizard.Companion.DEFAULT_KOTLIN_VERSION

class MavenKotlinModuleBuilder(private val outputDirectory: String): AbstractMavenModuleBuilder() {

    var kotlinPluginWizardVersion = DEFAULT_KOTLIN_VERSION

    internal val filesToOpen = mutableListOf<String>()

    override fun setupRootModel(rootModel: ModifiableRootModel) {

        val project = rootModel.getProject()
        val root = createAndGetContentEntry()
        rootModel.addContentEntry(root)

        inheritOrSetSDK(rootModel)

        if (isCreatingNewProject) {
            setupNewProject(project)
        }

        MavenUtil.runWhenInitialized(project, DumbAwareRunnable {
            if (myEnvironmentForm != null) {
                myEnvironmentForm.setData(MavenProjectsManager.getInstance(project).getGeneralSettings())
            }
            MavenKotlinModuleBuilderHelper(
                myProjectId,
                myAggregatorProject,
                myParentProject,
                myInheritGroupId,
                myInheritVersion,
                myArchetype,
                myPropertiesToCreateByArtifact,
                MavenProjectBundle.message("command.name.create.new.maven.module"),
                kotlinPluginWizardVersion,
                outputDirectory,
                filesToOpen
            ).configure(project, root, false)
        })
    }
}