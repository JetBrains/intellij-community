// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.maven

import com.intellij.ide.actions.OpenFileAction
import com.intellij.openapi.GitSilentFileAdderProvider
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.idea.maven.dom.MavenDomUtil
import org.jetbrains.idea.maven.model.MavenArchetype
import org.jetbrains.idea.maven.model.MavenConstants
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.idea.maven.wizards.MavenModuleBuilderHelper
import org.jetbrains.kotlin.tools.projectWizard.Versions
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import java.io.IOException
import java.util.*

class MavenKotlinModuleBuilderHelper(
    projectId: MavenId,
    aggregatorProject: MavenProject?,
    parentProject: MavenProject?,
    inheritGroupId: Boolean,
    inheritVersion: Boolean,
    archetype: MavenArchetype?,
    propertiesToCreateByArtifact: Map<String, String>?,
    commandName: @NlsContexts.Command String,
    private val kotlinPluginWizardVersion: String
) : MavenModuleBuilderHelper(
    projectId,
    aggregatorProject,
    parentProject,
    inheritGroupId,
    inheritVersion,
    archetype,
    propertiesToCreateByArtifact,
    commandName
) {

    override fun configure(project: Project, root: VirtualFile, isInteractive: Boolean) {

        val aggregatorProject = myAggregatorProject
        val psiFiles = if (aggregatorProject != null) arrayOf(getPsiFile(project, aggregatorProject.file)) else PsiFile.EMPTY_ARRAY

        val pom =
            WriteCommandAction.writeCommandAction(project, *psiFiles).withName(myCommandName).compute<VirtualFile?, RuntimeException> {
                val vcsFileAdder = GitSilentFileAdderProvider.create(project)
                var file: VirtualFile? = null
                try {
                    try {
                        file = root.findChild(MavenConstants.POM_XML)
                        file?.delete(this)
                        file = root.createChildData(this, MavenConstants.POM_XML)
                        vcsFileAdder.markFileForAdding(file)
                        MavenUtil.runOrApplyMavenProjectFileTemplate(
                            project, file, myProjectId, null, null, Properties(), getConditions(project),
                            MavenKotlinFileTemplateGroupFactory.MAVEN_KOTLIN_PROJECT_XML_TEMPLATE, isInteractive
                        )
                    } catch (e: IOException) {
                        showError(project, e)
                        return@compute file
                    }
                    updateProjectPom(project, file)
                } finally {
                    vcsFileAdder.finish()
                }

                if (myAggregatorProject != null) {
                    setPomPackagingForAggregatorProject(project, file)
                }
                file
            } ?: return

        if (myAggregatorProject == null) {
            val manager = MavenProjectsManager.getInstance(project)
            manager.addManagedFilesOrUnignore(listOf(pom))
        }

        MavenProjectsManager.getInstance(project).forceUpdateAllProjectsOrFindAllAvailablePomFiles()

        // execute when current dialog is closed (e.g. Project Structure)
        MavenUtil.invokeLater(project, ModalityState.nonModal()) {
            if (!pom.isValid()) {
                showError(project, RuntimeException("Project is not valid"))
                return@invokeLater
            }
            OpenFileAction.openFile(pom, project)
        }
    }

    private fun getConditions(project: Project): Properties {
        val conditions = Properties()
        conditions.setProperty("KOTLIN_PLUGIN_WIZARD_VERSION", kotlinPluginWizardVersion)
        conditions.setProperty("MAVEN_SUREFIRE_PLUGIN_VERSION", Versions.MAVEN_PLUGINS.SUREFIRE.text)
        conditions.setProperty("MAVEN_FAILSAFE_PLUGIN_VERSION", Versions.MAVEN_PLUGINS.FAILSAFE.text)
        conditions.setProperty("MAVEN_CODEHAUS_MOJO_EXEC_PLUGIN_VERSION", Versions.MAVEN_PLUGINS.CODEHAUS_MOJO_EXEC.text)
        conditions.setProperty("JUNIT_JUPITER_ENGINE_VERSION", Versions.JUNIT5.text)
        conditions.setProperty("MAVEN_CENTRAL_REPOSITORY_ID", DefaultRepository.MAVEN_CENTRAL.idForMaven)
        conditions.setProperty("MAVEN_CENTRAL_REPOSITORY_URL", DefaultRepository.MAVEN_CENTRAL.url)

        conditions.setProperty(
            "INHERIT_MAVEN_KOTLIN_PLUGIN_VERSION",
            findBuildPluginInRootPom(project, "org.jetbrains.kotlin", "kotlin-maven-plugin").toString()
        )
        conditions.setProperty(
            "INHERIT_MAVEN_SUREFIRE_PLUGIN_VERSION",
            findBuildPluginInRootPom(project, groupId = null, "maven-surefire-plugin").toString()
        )
        conditions.setProperty(
            "INHERIT_MAVEN_FAILSAFE_PLUGIN_VERSION",
            findBuildPluginInRootPom(project, groupId = null, "maven-failsafe-plugin").toString()
        )
        conditions.setProperty(
            "INHERIT_CODEHAUS_MOJO_EXEC_PLUGIN_VERSION",
            findBuildPluginInRootPom(project, "org.codehaus.mojo", "exec-maven-plugin").toString()
        )
        return conditions
    }

    private fun findBuildPluginInRootPom(project: Project, groupId: String?, artifactId: String): Boolean {
        val aggregatorProjectFile = myAggregatorProject?.file ?: return false
        val model = MavenDomUtil.getMavenDomProjectModel(project, aggregatorProjectFile)
        if (model != null) {
            return model.build.plugins.plugins.any { (it.groupId.value?.equals(groupId) ?: true) && it.artifactId.value == artifactId }
        } else {
            return false
        }
    }
}