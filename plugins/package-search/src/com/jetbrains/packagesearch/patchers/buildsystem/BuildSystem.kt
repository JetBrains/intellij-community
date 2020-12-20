package com.jetbrains.packagesearch.patchers.buildsystem

interface BuildSystem<D : BuildDependency, R : BuildDependencyRepository> : BuildManager<D, R> {

    val name: String
}
