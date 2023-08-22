// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.platform.testFramework.io.ExternalResourcesChecker
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import kotlinx.coroutines.*
import org.jetbrains.kotlin.tools.projectWizard.Dependencies
import org.jetbrains.kotlin.tools.projectWizard.library.MavenLibraryDescriptor
import org.junit.Assert
import java.io.IOException
import java.net.URL
import kotlin.reflect.KClass

class DependencyTest : BasePlatformTestCase() {
    private fun checkMavenArtifact(repositoryURL: String, groupId: String, artifactId: String, version: String) {
        val path = groupId.replace('.', '/')
        val urlWithoutTrailingSlash = repositoryURL.removeSuffix("/")
        val url = "$urlWithoutTrailingSlash/$path/$artifactId/$version/$artifactId-$version.pom"
        val lastAttempt = 5
        for (attempt in 1..lastAttempt) {
            try {
                URL(url).openStream().close()
                break
            } catch (ex: IOException) {
                if (attempt == lastAttempt) {
                    try {
                        URL(url).openStream().close()
                    } catch (e: IOException) {
                        ExternalResourcesChecker.reportUnavailability(url, e)
                    }
                    Assert.fail("Could not download $groupId:$artifactId after $lastAttempt retries. Might be missing (incorrect version?)")
                }
                println("Re-try $attempt... $ex")
                Thread.sleep(1000)
            }
        }
    }

    private fun KClass<*>.initializeNestedObjects() {
        // initializes the object
        this.objectInstance
        // recursively instantiate objects nested even deeper
        nestedClasses.forEach { it.initializeNestedObjects() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun testMavenDependenciesExist() {
        // Cause all inner objects to be instantiated
        Dependencies::class.initializeNestedObjects()

        // Check multiple at the same time to speed up tests, but not too many
        runBlocking {
            withContext(Dispatchers.IO.limitedParallelism(4)) {
                Dependencies.allRegisteredArtifacts().filterIsInstance<MavenLibraryDescriptor>().map {
                    launch {
                        checkMavenArtifact(
                            it.artifact.repositories.first().url,
                            it.artifact.groupId,
                            it.artifact.artifactId,
                            it.version.text
                        )
                    }
                }.joinAll()
            }
        }
    }

    fun testMavenDependenciesInDefaultData() {
        Dependencies.allRegisteredArtifacts().filterIsInstance<MavenLibraryDescriptor>().forEach {
            val key = "${it.artifact.groupId}:${it.artifact.artifactId}"
            TestCase.assertNotNull("Could not find version for $key", DependencyVersionStore.getVersion(key))
        }
    }
}