package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.module.Module
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable

enum class BuildSystemType(val statisticsKey: String) {
    MAVEN(statisticsKey = "maven"),
    GRADLE_GROOVY(statisticsKey = "gradle-groovy"),
    GRADLE_KOTLIN(statisticsKey = "gradle-kts")
}

data class ProjectModule(
    @NlsSafe val name: String,
    val nativeModule: Module,
    val parent: ProjectModule?,
    val buildFile: VirtualFile,
    val buildSystemType: BuildSystemType,
    val moduleType: ProjectModuleType
) {

    var getNavigatableDependency: (groupId: String, artifactId: String, version: String) -> Navigatable? =
        { _: String, _: String, _: String -> null }

    @NlsSafe
    fun getFullName(): String {
        if (parent != null) {
            return parent.getFullName() + ":$name"
        }
        return name
    }
}
