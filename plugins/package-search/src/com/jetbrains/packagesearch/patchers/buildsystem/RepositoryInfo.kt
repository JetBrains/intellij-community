package com.jetbrains.packagesearch.patchers.buildsystem

open class RepositoryInfo<T : BuildDependencyRepository>(
    open val repository: T,
    open val metadata: BuildScriptEntryMetadata
)
