// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem

interface Repository {
    val url: String
    val idForMaven: String
}

data class DefaultRepository(val type: Type) : Repository {
    override val url: String
        get() = type.url

    override val idForMaven: String
        get() = type.gradleName

    enum class Type(val gradleName: String, val url: String) {
        JCENTER("jcenter", "https://jcenter.bintray.com/"),
        MAVEN_CENTRAL("mavenCentral", "https://repo1.maven.org/maven2/"),
        GOOGLE("google", "https://dl.google.com/dl/android/maven2/"),
        GRADLE_PLUGIN_PORTAL("gradlePluginPortal", "https://plugins.gradle.org/m2/"),
        MAVEN_LOCAL("mavenLocal", "")
    }

    companion object {
        val JCENTER = DefaultRepository(Type.JCENTER)
        val MAVEN_CENTRAL = DefaultRepository(Type.MAVEN_CENTRAL)
        val GOOGLE = DefaultRepository(Type.GOOGLE)
        val GRADLE_PLUGIN_PORTAL = DefaultRepository(Type.GRADLE_PLUGIN_PORTAL)
        val MAVEN_LOCAL = DefaultRepository(Type.MAVEN_LOCAL)
    }
}


interface CustomMavenRepository : Repository

@Suppress("unused")
data class CustomMavenRepositoryImpl(val repository: String, val base: String) : CustomMavenRepository {
    override val url: String = "$base/$repository"

    override val idForMaven: String
        get() = "bintray." + repository.replace('/', '.')
}

data class JetBrainsSpace(val repository: String) : CustomMavenRepository {
    override val url: String = "https://maven.pkg.jetbrains.space/$repository"

    override val idForMaven: String
        get() = "jetbrains." + repository.replace('/', '.')
}

data class CacheRedirector(val redirectedRepositoryBase: String, val repository: String) : CustomMavenRepository {
    override val url: String = "https://cache-redirector.jetbrains.com/$redirectedRepositoryBase/$repository"

    override val idForMaven: String
        get() = "cache-redirector.jetbrains." +
                redirectedRepositoryBase.replace('/', '.') + "." +
                repository.replace('/', '.')
}

object Repositories {
    val KTOR = DefaultRepository.MAVEN_CENTRAL
    val KOTLINX_HTML = JetBrainsSpace("public/p/kotlinx-html/maven")
    val KOTLIN_JS_WRAPPERS = DefaultRepository.MAVEN_CENTRAL
    val KOTLIN_EAP_MAVEN_CENTRAL = DefaultRepository.MAVEN_CENTRAL
    val JETBRAINS_KOTLIN_DEV = JetBrainsSpace("kotlin/p/kotlin/dev")
    val JETBRAINS_KOTLIN_BOOTSTRAP = CacheRedirector(
        "maven.pkg.jetbrains.space",
        "kotlin/p/kotlin/bootstrap"
    )
}
