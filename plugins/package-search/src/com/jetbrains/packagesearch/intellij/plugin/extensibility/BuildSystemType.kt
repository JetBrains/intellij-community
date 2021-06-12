package com.jetbrains.packagesearch.intellij.plugin.extensibility

class BuildSystemType(val name: String, val statisticsKey: String) {
    companion object {

        @JvmStatic
        val MAVEN = BuildSystemType(name = "MAVEN", statisticsKey = "maven")

        @JvmStatic
        val GRADLE_GROOVY = BuildSystemType(name = "GRADLE_GROOVY", statisticsKey = "gradle-groovy")

        @JvmStatic
        val GRADLE_KOTLIN = BuildSystemType(name = "GRADLE_KOTLIN", statisticsKey = "gradle-kts")
    }
}

class Build
