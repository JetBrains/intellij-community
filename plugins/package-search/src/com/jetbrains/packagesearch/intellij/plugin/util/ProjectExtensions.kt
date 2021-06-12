package com.jetbrains.packagesearch.intellij.plugin.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.jetbrains.packagesearch.intellij.plugin.ui.toolwindow.models.PackageSearchDataService

internal fun Project.dataService() = service<PackageSearchDataService>()
