package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion


data class ProjectModule(
    @NlsSafe val name: String,
    val nativeModule: Module,
    val parent: ProjectModule?,
    val buildFile: VirtualFile,
    val buildSystemType: BuildSystemType,
    val moduleType: ProjectModuleType,
    val navigatableDependency: NavigatableDependency = { _, _, _ -> null },
    val availableScopes: List<String>
) {

    @Deprecated(
        "Use main constructor",
        ReplaceWith("ProjectModule(name, nativeModule, parent, buildFile, buildSystemType, moduleType, navigatableDependency, emptyList())")
    )
    constructor(
        name: String,
        nativeModule: Module,
        parent: ProjectModule,
        buildFile: VirtualFile,
        buildSystemType: BuildSystemType,
        moduleType: ProjectModuleType,
        navigatableDependency: NavigatableDependency
    ) : this(name, nativeModule, parent, buildFile, buildSystemType, moduleType, navigatableDependency, emptyList())

    @NlsSafe
    fun getFullName(): String =
        parent?.let { it.getFullName() + ":$name" } ?: name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectModule) return false

        if (name != other.name) return false
        if (!nativeModule.isTheSameAs(other.nativeModule)) return false // This can't be automated
        if (parent != other.parent) return false
        if (buildFile.path != other.buildFile.path) return false
        if (buildSystemType != other.buildSystemType) return false
        if (moduleType != other.moduleType) return false
        // if (navigatableDependency != other.navigatableDependency) return false // Intentionally excluded
        if (availableScopes != other.availableScopes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + nativeModule.hashCodeOrZero()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + buildFile.path.hashCode()
        result = 31 * result + buildSystemType.hashCode()
        result = 31 * result + moduleType.hashCode()
        // result = 31 * result + navigatableDependency.hashCode() // Intentionally excluded
        result = 31 * result + availableScopes.hashCode()
        return result
    }
}

internal fun Module.isTheSameAs(other: Module) =
    runCatching { moduleFilePath == other.moduleFilePath && name == other.name }
        .getOrDefault(false)

private fun Module.hashCodeOrZero() =
    runCatching { moduleFilePath.hashCode() + 31 * name.hashCode() }
        .getOrDefault(0)

typealias NavigatableDependency = (groupId: String, artifactId: String, version: PackageVersion) -> Navigatable?
