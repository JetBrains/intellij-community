package com.jetbrains.packagesearch.intellij.plugin.maven

import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AbstractProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModule
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private val MAVEN_CENTRAL_UNIFIED_REPOSITORY = UnifiedDependencyRepository(
    "central",
    "Central Repository",
    "https://repo.maven.apache.org/maven2",
)

internal class MavenProjectModuleOperationProvider : AbstractProjectModuleOperationProvider() {

    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is MavenProjectModuleType

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = MavenUtil.isPomFile(project, psiFile?.virtualFile)

    // This is a (sad) workaround for IDEA-267229 — when that's sorted, we shouldn't need this anymore.
    override fun listRepositoriesInModule(module: ProjectModule): Collection<UnifiedDependencyRepository> {
        val repositories = super.listRepositoriesInModule(module)
        return if (repositories.none { it.id == MAVEN_CENTRAL_UNIFIED_REPOSITORY.id })
            repositories + MAVEN_CENTRAL_UNIFIED_REPOSITORY
        else
            repositories
    }
}
