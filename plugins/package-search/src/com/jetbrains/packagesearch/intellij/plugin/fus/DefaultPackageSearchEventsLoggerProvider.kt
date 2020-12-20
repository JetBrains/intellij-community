package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.openapi.project.Project

class DefaultPackageSearchEventsLoggerProvider : PackageSearchEventsLoggerProvider {

    override fun logEvent(
        project: Project,
        groupId: String,
        event: String,
        @Suppress("SameParameterValue") version: String,
        extras: Array<out Pair<String, Any>>
    ) {
        // No-op: this does nothing by default. It's an entry point for a future implementation.
    }
}
