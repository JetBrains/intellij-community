package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.api.v2.ApiStandardPackage

internal fun UnifiedDependency.matchesCoordinates(apiPackage: ApiStandardPackage): Boolean =
    coordinates.groupId == apiPackage.groupId &&
        coordinates.artifactId == apiPackage.artifactId

internal fun InstalledDependency.matchesCoordinates(apiPackage: ApiStandardPackage): Boolean =
    groupId == apiPackage.groupId && artifactId == apiPackage.artifactId
