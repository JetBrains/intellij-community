package com.jetbrains.packagesearch.intellij.plugin.extensibility

internal enum class BuildSystemType(val statisticsKey: String) {
    MAVEN(statisticsKey = "maven"),
    GRADLE_GROOVY(statisticsKey = "gradle-groovy"),
    GRADLE_KOTLIN(statisticsKey = "gradle-kts")
}
