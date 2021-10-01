package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion

internal fun Module.hashCodeOrZero() =
    runCatching { moduleFilePath.hashCode() + 31 * name.hashCode() }
        .getOrDefault(0)

internal fun Module.areTheSame(other: Module) =
    runCatching { moduleFilePath == other.moduleFilePath && name == other.name }
        .getOrDefault(false)

typealias NavigatableDependency = (groupId: String, artifactId: String, version: PackageVersion) -> Navigatable?

data class ProjectModule(
    @NlsSafe val name: String,
    val nativeModule: Module,
    val parent: ProjectModule?,
    val buildFile: VirtualFile,
    val buildSystemType: BuildSystemType,
    val moduleType: ProjectModuleType,
    val navigatableDependency: NavigatableDependency = { _, _, _ -> null }
) {

    @NlsSafe
    fun getFullName(): String =
        parent?.let { it.getFullName() + ":$name" } ?: name

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProjectModule) return false

        if (name != other.name) return false
        if (!nativeModule.areTheSame(other.nativeModule)) return false // This can't be automated
        if (parent != other.parent) return false
        if (buildFile.path != other.buildFile.path) return false
        if (buildSystemType != other.buildSystemType) return false
        if (moduleType != other.moduleType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + nativeModule.hashCodeOrZero()
        result = 31 * result + (parent?.hashCode() ?: 0)
        result = 31 * result + buildFile.path.hashCode()
        result = 31 * result + buildSystemType.hashCode()
        result = 31 * result + moduleType.hashCode()
        return result
    }
}
