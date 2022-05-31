package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.jetbrains.annotations.ApiStatus

class BuildSystemType @JvmOverloads constructor(
    val name: String,
    val language: String,
    @Suppress("unused")
    @Deprecated("This property will be removed soon as it is unused.")
    @ApiStatus.ScheduledForRemoval
    val statisticsKey: String,
    val dependencyAnalyzerKey: ProjectSystemId? = null
) {

    companion object
}
