package com.jetbrains.packagesearch.patchers.buildsystem.unified

import com.jetbrains.packagesearch.patchers.buildsystem.BuildDependencyRepository

data class UnifiedDependencyRepository(
    val id: String?,
    val name: String?,
    val url: String?
) : BuildDependencyRepository
