package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal data class InstalledDependency(val groupId: String, val artifactId: String) {

    val coordinatesString = "$groupId:$artifactId"

    companion object {

        fun from(dependency: UnifiedDependency): InstalledDependency? {
            val groupId = dependency.coordinates.groupId
            val artifactId = dependency.coordinates.artifactId
            if (groupId == null || artifactId == null) return null

            return InstalledDependency(groupId, artifactId)
        }

        fun from(standardV2Package: ApiStandardPackage) = InstalledDependency(standardV2Package.groupId, standardV2Package.artifactId)
    }
}
