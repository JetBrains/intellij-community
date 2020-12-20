package com.jetbrains.packagesearch.intellij.plugin.extensions.maven

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.DependencyOperationMetadata
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleOperationProvider
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ProjectModuleType
import com.jetbrains.packagesearch.patchers.buildsystem.OperationFailure
import com.jetbrains.packagesearch.patchers.buildsystem.OperationItem
import com.jetbrains.packagesearch.patchers.buildsystem.unified.UnifiedDependency
import com.jetbrains.packagesearch.patchers.buildsystem.unified.UnifiedDependencyRepository
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.jetbrains.idea.maven.utils.MavenUtil

class MavenProjectModuleOperationProvider : ProjectModuleOperationProvider {
    override fun hasSupportFor(projectModuleType: ProjectModuleType): Boolean =
        projectModuleType is MavenProjectModuleType

    override fun hasSupportFor(project: Project, psiFile: PsiFile?) = MavenUtil.isPomFile(project, psiFile?.virtualFile)

    override fun addDependenciesToProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {

        //val dependenciesToAdd = setOf(
        //    MavenDependency(
        //        MavenCoordinates(
        //            operationMetadata.groupId,
        //            operationMetadata.artifactId,
        //            operationMetadata.version
        //        ),
        //        MavenDependency.Scope.from(operationMetadata.scope)
        //    )
        //)
        //
        //return parseMavenPomFrom(project, virtualFile) { maven ->
        //    maven.doBatch(removeDependencies = dependenciesToAdd, addDependencies = dependenciesToAdd)
        //        .filter { it.operationType == OperationType.ADD }
        //}
        return emptyList()
    }

    override fun removeDependenciesFromProject(
        operationMetadata: DependencyOperationMetadata,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {

        //val dependenciesToRemove = setOf(
        //    MavenDependency(
        //        MavenCoordinates(
        //            operationMetadata.groupId,
        //            operationMetadata.artifactId,
        //            operationMetadata.version
        //        ),
        //        MavenDependency.Scope.from(operationMetadata.scope)
        //    )
        //)
        //
        //return parseMavenPomFrom(project, virtualFile) { maven ->
        //    maven.doBatch(removeDependencies = dependenciesToRemove)
        //}
        return emptyList()
    }

    override fun listDependenciesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependency> =
        //parseMavenPomFrom(project, virtualFile) { maven -> maven.listDependencies() }
        //    .map { MavenUnifiedDependencyConverter.convert(it) }
        emptyList()

    override fun addRepositoriesToProject(
        repository: UnifiedDependencyRepository,
        project: Project,
        virtualFile: VirtualFile
    ): List<OperationFailure<out OperationItem>> {

        //val mavenRepository = MavenRepository(
        //    id = repository.id,
        //    url = repository.url,
        //    name = repository.name
        //)
        //
        //return parseMavenPomFrom(project, virtualFile) { maven ->
        //    if (!maven.listRepositories()
        //            .andMavenCentralIfNotPresent()
        //            .any { it.isEquivalentTo(mavenRepository) }) {
        //
        //        maven.doBatch(addRepositories = setOf(mavenRepository))
        //    } else {
        //        emptyList()
        //    }
        //}
        return emptyList()
    }

    override fun listRepositoriesInProject(project: Project, virtualFile: VirtualFile): Collection<UnifiedDependencyRepository> =
        //parseMavenPomFrom(project, virtualFile) { maven -> maven.listRepositories() }
        //    .andMavenCentralIfNotPresent()
        //    .map { MavenUnifiedDependencyRepositoryConverter.convert(it) }
        emptyList()

    override fun refreshProject(project: Project, virtualFile: VirtualFile) {
        if (!PackageSearchGeneralConfiguration.getInstance(project).refreshProject) return

        val projectsManager = MavenProjectsManager.getInstance(project)
        if (projectsManager.importingSettings.isImportAutomatically) return

        ProjectRefreshingListener.doOnNextChange(projectsManager) {
            projectsManager.forceUpdateProjects(projectsManager.projects)
        }
    }
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

//private fun Collection<MavenRepository>.andMavenCentralIfNotPresent(): Collection<MavenRepository> {
//    // In Maven modules, Maven Central should be there by default
//    if (this.any { it.id == "central" || it.url == "https://repo.maven.apache.org/maven2/" }) {
//        return this
//    }
//
//    return this + listOf(
//        MavenRepository(
//            id = "central",
//            name = "Maven Central",
//            url = "https://repo.maven.apache.org/maven2/"
//    ))
//}
