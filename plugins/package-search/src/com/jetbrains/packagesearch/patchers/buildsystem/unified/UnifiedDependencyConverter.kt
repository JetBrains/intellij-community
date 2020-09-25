package com.jetbrains.packagesearch.patchers.buildsystem.unified

import com.jetbrains.packagesearch.patchers.buildsystem.BuildDependency

interface UnifiedDependencyConverter<T : BuildDependency> {
    fun convert(buildDependency: T): UnifiedDependency
}
