package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AbstractProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class MavenProjectModuleOperationProvider : AbstractProjectModuleOperationProvider() {

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is MavenProjectModuleType

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = MavenUtil.isPomFile(project, psiFile?.virtualFile)
}

private object ProjectRefreshingListener : MavenProjectsManager.Listener {

    private val runOnNextChange = AtomicReference<(() -> Unit)?>()
    private val needsRegistering = AtomicBoolean(true)

    fun doOnNextChange(mavenProjectsManager: MavenProjectsManager, action: () -> Unit) {
        registerIfNeeded(mavenProjectsManager)
        runOnNextChange.set(action)
    }

    private fun registerIfNeeded(mavenProjectsManager: MavenProjectsManager) {
        if (needsRegistering.getAndSet(false)) {
            mavenProjectsManager.addManagerListener(this)
        }
    }

    override fun projectsScheduled() {
        runOnNextChange.getAndSet(null)?.invoke()
    }
}
