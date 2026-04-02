// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.idea.maven.model.MavenId
import org.jetbrains.idea.maven.utils.MavenArtifactScope
import org.jetbrains.idea.maven.utils.MavenUtil
import org.jetbrains.kotlin.idea.base.util.isMavenModule
import org.jetbrains.kotlin.idea.configuration.KotlinBuildSystemDependencyManager
import org.jetbrains.kotlin.idea.configuration.KotlinDependencyProvider
import org.jetbrains.kotlin.idea.configuration.KotlinProjectConfigurationService
import org.jetbrains.kotlin.idea.maven.configuration.KotlinMavenConfigurator

class MavenKotlinBuildSystemDependencyManager(
    private val project: Project,
    val coroutineScope: CoroutineScope
) : KotlinBuildSystemDependencyManager {

    override fun isApplicable(module: Module): Boolean {
        return module.isMavenModule
    }

    private fun findPomFile(module: Module): XmlFile? {
        return KotlinMavenConfigurator.findModulePomFile(module) as? XmlFile
    }

    @Deprecated(
        "Use addDependencyModCommand instead",
        replaceWith = ReplaceWith("addDependencyModCommand(context, module, libraryDescriptor)")
    )
    override fun addDependency(module: Module, libraryDescriptor: ExternalLibraryDescriptor): Job {
        return coroutineScope.launchTracked {
            writeAction {
                val pomFile = findPomFile(module) ?: return@writeAction
                val pom = PomFile.forFileOrNull(pomFile) ?: return@writeAction
                val version = libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion
                val mavenId = MavenId(libraryDescriptor.libraryGroupId, libraryDescriptor.libraryArtifactId, version)

                val scope = when (libraryDescriptor.preferredScope) {
                    DependencyScope.COMPILE -> MavenArtifactScope.COMPILE
                    DependencyScope.TEST -> MavenArtifactScope.TEST
                    DependencyScope.RUNTIME -> MavenArtifactScope.RUNTIME
                    DependencyScope.PROVIDED -> MavenArtifactScope.PROVIDED
                    else -> MavenArtifactScope.COMPILE
                }

                executeCommand(project = project) {
                    pom.addDependency(mavenId, scope)
                }
            }
        }
    }

    override fun addDependencyModCommand(contextFile: PsiFile, module: Module, libraryDescriptor: ExternalLibraryDescriptor): ModCommand {
        val pomFile = findPomFile(module) ?: return ModCommand.nop()

        val actionContext = ActionContext.from(null, contextFile)

        val version = libraryDescriptor.preferredVersion ?: libraryDescriptor.maxVersion ?: libraryDescriptor.minVersion
        val mavenId = MavenId(libraryDescriptor.libraryGroupId, libraryDescriptor.libraryArtifactId, version)

        val scope = when (libraryDescriptor.preferredScope) {
            DependencyScope.COMPILE -> MavenArtifactScope.COMPILE
            DependencyScope.TEST -> MavenArtifactScope.TEST
            DependencyScope.RUNTIME -> MavenArtifactScope.RUNTIME
            DependencyScope.PROVIDED -> MavenArtifactScope.PROVIDED
            else -> MavenArtifactScope.COMPILE
        }

        return ModCommand.psiUpdate(actionContext) {
            val writablePomFile = it.getWritable(pomFile)
            val pom = PomFile.forFileOrNull(writablePomFile)
            pom?.addDependency(mavenId, scope)
        }.andThen(KotlinDependencyProvider.syncModCommand(pomFile))
    }

    override fun getBuildScriptFile(module: Module): VirtualFile? {
        return findPomFile(module)?.virtualFile
    }

    override fun isProjectSyncPending(): Boolean {
        val isNotificationVisible =
            ExternalSystemProjectNotificationAware.isNotificationVisibleProperty(project, MavenUtil.SYSTEM_ID).get()
        return isNotificationVisible
    }

    override fun isProjectSyncInProgress(): Boolean {
        return KotlinProjectConfigurationService.getInstance(project).isSyncInProgress()
    }

    override fun startProjectSync() {
        ExternalSystemProjectTracker.getInstance(project).scheduleProjectRefresh()
    }
}