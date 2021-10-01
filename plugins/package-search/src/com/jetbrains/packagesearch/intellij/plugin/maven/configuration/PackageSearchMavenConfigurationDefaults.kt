package com.jetbrains.packagesearch.intellij.plugin.maven.configuration

internal object PackageSearchMavenConfigurationDefaults {

    @JvmField
    val MavenScopes = listOf("compile", "provided", "runtime", "test", "system", "import")

    const val MavenDefaultScope = "compile"
}
