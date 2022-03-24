package com.jetbrains.packagesearch.intellij.plugin.extensibility

import org.jetbrains.annotations.ApiStatus

class BuildSystemType(
    val name: String,
    val language: String,
    @Deprecated("This property will be removed soon as it is unused.")
    @ApiStatus.ScheduledForRemoval
    val statisticsKey: String
) {

    companion object {

        @JvmStatic
        val MAVEN = BuildSystemType(name = "MAVEN", language = "xml", statisticsKey = "maven")

        @JvmStatic
        val GRADLE_GROOVY = BuildSystemType(name = "GRADLE", language = "groovy", statisticsKey = "gradle-groovy")

        @JvmStatic
        val GRADLE_KOTLIN = BuildSystemType(name = "GRADLE", language = "kotlin", statisticsKey = "gradle-kts")
    }
}
