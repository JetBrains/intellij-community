package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.buildsystem.model.unified.UnifiedDependencyRepository
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageModel
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageScope
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageVersion
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.RepositoryModel

internal fun PackageModel.toUnifiedDependency(version: PackageVersion, scope: PackageScope) = UnifiedDependency(
    coordinates = UnifiedCoordinates(groupId, artifactId, version.versionName),
    scope = scope.scopeName.nullIfBlank()
)

internal fun RepositoryModel.toUnifiedRepository() = UnifiedDependencyRepository(id, name, url)
