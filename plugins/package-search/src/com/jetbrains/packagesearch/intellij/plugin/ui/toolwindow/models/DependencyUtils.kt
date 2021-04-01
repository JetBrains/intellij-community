package com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models

import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.jetbrains.packagesearch.intellij.plugin.api.model.StandardV2Package

internal fun UnifiedDependency.matchesCoordinates(apiPackage: StandardV2Package): Boolean =
    coordinates.groupId == apiPackage.groupId &&
        coordinates.artifactId == apiPackage.artifactId

internal fun InstalledDependency.matchesCoordinates(apiPackage: StandardV2Package): Boolean =
    groupId == apiPackage.groupId && artifactId == apiPackage.artifactId
