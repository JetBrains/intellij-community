package com.jetbrains.packagesearch.patchers.buildsystem

interface BuildDependency : OperationItem {
    val displayName: String

    interface Coordinates {
        val displayName: String
    }
}
