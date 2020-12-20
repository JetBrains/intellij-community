package com.jetbrains.packagesearch.patchers.buildsystem.unified

import com.jetbrains.packagesearch.patchers.buildsystem.BuildDependencyRepository

interface UnifiedDependencyRepositoryConverter<T : BuildDependencyRepository> {
    fun convert(buildDependencyRepository: T): UnifiedDependencyRepository
}
