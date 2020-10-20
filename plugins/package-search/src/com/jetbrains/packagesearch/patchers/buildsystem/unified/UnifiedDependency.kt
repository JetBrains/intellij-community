package com.jetbrains.packagesearch.patchers.buildsystem.unified

import com.jetbrains.packagesearch.patchers.buildsystem.BuildDependency

data class UnifiedDependency(
    val coordinates: UnifiedCoordinates,
    val scope: String?
) : BuildDependency {

    override val displayName by lazy {
        buildString {
            append(coordinates.displayName)
            if (scope != null) {
                append(":$scope")
            }
        }
    }
}
