// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.openapi.progress.Cancellation
import org.jetbrains.kotlin.tools.projectWizard.compatibility.DependencyVersionStore
import org.jetbrains.kotlin.tools.projectWizard.library.LibraryDescriptor
import org.jetbrains.kotlin.tools.projectWizard.library.LibraryType
import org.jetbrains.kotlin.tools.projectWizard.library.MavenArtifact
import org.jetbrains.kotlin.tools.projectWizard.library.MavenLibraryDescriptor
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.DefaultRepository
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repositories
import org.jetbrains.kotlin.tools.projectWizard.settings.buildsystem.Repository
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version
import java.util.Collections

@Suppress("ClassName")
object Dependencies {
    private val registeredArtifacts = Collections.synchronizedList(mutableListOf<LibraryDescriptor>())

    fun allRegisteredArtifacts(): List<LibraryDescriptor> = registeredArtifacts.toList() // return defensive copy

    /**
     * When using this function, use it lazily to avoid class initializations depending on services
     */
    private fun loadVersion(key: String, default: String): String {
        return DependencyVersionStore.getVersion(key) ?: default
    }

    /**
     * When using this function, use it lazily to avoid class initializations depending on services,
     * because [loadVersion] under the hood calls the [DependencyVersionStore] service
     */
    private fun loadParsedVersion(key: String, default: String): Version {
        return Version.fromString(loadVersion(key, default))
    }

    /**
     * When using this function, use it lazily to avoid class initializations depending on services,
     * because [loadVersion] under the hood calls the [DependencyVersionStore] service
     */
    private fun registerMavenLibrary(
        repository: Repository,
        groupId: String,
        artifactId: String,
        default: String,
        libraryType: LibraryType = LibraryType.JVM_ONLY,
        version: Version = loadParsedVersion("$groupId:$artifactId", default)
    ): MavenLibraryDescriptor {
        val artifact = MavenArtifact(
            repositories = listOf(repository),
            groupId = groupId,
            artifactId = artifactId
        )
        val descriptor = MavenLibraryDescriptor(artifact, libraryType, version)
        registeredArtifacts.add(descriptor)
        return descriptor
    }

    val JUNIT: LibraryDescriptor by lazy {
        Cancellation.forceNonCancellableSectionInClassInitializer {
            registerMavenLibrary(DefaultRepository.MAVEN_CENTRAL, "junit", "junit", "4.13.2")
        }
    }
    val JUNIT5: MavenLibraryDescriptor by lazy {
        registerMavenLibrary(
            DefaultRepository.MAVEN_CENTRAL,
            "org.junit.jupiter",
            "junit-jupiter-engine",
            "5.8.2"
        )
    }

    object ANDROID {
        private const val DEFAULT_COMPOSE_VERSION = "1.2.1"

        val COMPOSE_UI: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                DefaultRepository.GOOGLE,
                "androidx.compose.ui",
                "ui",
                DEFAULT_COMPOSE_VERSION
            )
        }
        val COMPOSE_UI_TOOLING: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(DefaultRepository.GOOGLE, "androidx.compose.ui", "ui-tooling", DEFAULT_COMPOSE_VERSION)
        }
        val COMPOSE_UI_TOOLING_PREVIEW: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(DefaultRepository.GOOGLE, "androidx.compose.ui", "ui-tooling-preview", DEFAULT_COMPOSE_VERSION)
        }
        val COMPOSE_FOUNDATION: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(DefaultRepository.GOOGLE, "androidx.compose.foundation", "foundation", DEFAULT_COMPOSE_VERSION)
        }
        val COMPOSE_MATERIAL: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(DefaultRepository.GOOGLE, "androidx.compose.material", "material", DEFAULT_COMPOSE_VERSION)
        }
        val ACTIVITY: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                DefaultRepository.GOOGLE,
                "androidx.activity",
                "activity-compose",
                "1.5.1"
            )
        }
        val MATERIAL: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                DefaultRepository.GOOGLE,
                "com.google.android.material",
                "material",
                "1.5.0"
            )
        }
        val CONSTRAINT_LAYOUT: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                DefaultRepository.GOOGLE,
                "androidx.constraintlayout",
                "constraintlayout",
                "2.1.3"
            )
        }
        val APP_COMPAT: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                DefaultRepository.GOOGLE,
                "androidx.appcompat",
                "appcompat",
                "1.4.1"
            )
        }
    }

    object KOTLINX {
        val KOTLINX_HTML: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                Repositories.KOTLINX_HTML,
                "org.jetbrains.kotlinx",
                "kotlinx-html-jvm",
                "0.7.2"
            )
        }
        val KOTLINX_NODEJS: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                DefaultRepository.JCENTER,
                "org.jetbrains.kotlinx",
                "kotlinx-nodejs",
                "0.0.7"
            )
        }
    }

    object KTOR {
        val KTOR_SERVER_NETTY: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                Repositories.KTOR,
                "io.ktor",
                "ktor-server-netty",
                "2.0.2"
            )
        }
        val KTOR_SERVER_HTML_BUILDER: MavenLibraryDescriptor by lazy {
            registerMavenLibrary(
                Repositories.KTOR,
                "io.ktor",
                "ktor-server-html-builder-jvm",
                "2.0.2"
            )
        }
    }

    object JS_WRAPPERS {
        private const val WRAPPER_GROUP_ID = "org.jetbrains.kotlin-wrappers"
        private val KOTLIN_JS_WRAPPER_VERSION by lazy { loadVersion("kotlinjs.wrapper", "pre.346") }

        /**
         * When using this function, use it lazily to avoid class initializations depending on services,
         * because [loadVersion] under the hood calls the [DependencyVersionStore] service
         */
        private fun wrapperVersion(artifactId: String, default: String): Version {
            val key = "$WRAPPER_GROUP_ID:$artifactId"
            val version = loadParsedVersion(key, default)
            return version("$version-$KOTLIN_JS_WRAPPER_VERSION")
        }

        private fun wrapperDependency(
            artifactId: String,
            default: String
        ): LibraryDescriptor {
            return registerMavenLibrary(
                Repositories.KOTLIN_JS_WRAPPERS,
                WRAPPER_GROUP_ID,
                artifactId,
                default,
                LibraryType.JS_ONLY,
                wrapperVersion(artifactId, default)
            )
        }

        val KOTLIN_REACT: LibraryDescriptor by lazy { wrapperDependency("kotlin-react", "18.2.0") }
        val KOTLIN_REACT_DOM: LibraryDescriptor by lazy { wrapperDependency("kotlin-react-dom", "18.2.0") }
        val KOTLIN_REACT_ROUTER_DOM: LibraryDescriptor by lazy { wrapperDependency("kotlin-react-router-dom", "6.3.0") }
        val KOTLIN_EMOTION: LibraryDescriptor by lazy { wrapperDependency("kotlin-emotion", "11.9.3") }
        val KOTLIN_REDUX: LibraryDescriptor by lazy { wrapperDependency("kotlin-redux", "4.1.2") }
        val KOTLIN_REACT_REDUX: LibraryDescriptor by lazy { wrapperDependency("kotlin-react-redux", "7.2.6") }
    }
}

private fun version(version: String) = Version.fromString(version)
