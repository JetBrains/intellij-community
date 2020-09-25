package com.jetbrains.packagesearch.patchers.buildsystem

open class DependencyInfo<T : BuildDependency>(
    open val dependency: T,
    open val metadata: BuildScriptEntryMetadata
)
