// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.maven

import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexStorageProvider
import org.jetbrains.kotlin.idea.search.refIndex.bta.isBtaCriProvider
import org.junit.Test

class MavenCompilerReferenceIndexApplicabilityTest : AbstractKotlinMavenImporterTest() {
    @Test
    fun `test BTA CRI provider is not applicable when Maven CRI generation property is disabled`() {
        withBtaCriRegistryEnabled {
            runBlocking {
                importProjectAsync(mavenProjectWithCriGenerationProperty("false"))
            }

            assertFalse(isBtaCriProviderApplicable())
        }
    }

    @Test
    fun `test BTA CRI provider is applicable when Maven CRI generation property is enabled`() {
        withBtaCriRegistryEnabled {
            runBlocking {
                importProjectAsync(mavenProjectWithCriGenerationProperty("true"))
            }

            assertTrue(isBtaCriProviderApplicable())
        }
    }

    @Test
    fun `test BTA CRI provider is not applicable when Maven CRI generation property is absent`() {
        withBtaCriRegistryEnabled {
            runBlocking {
                importProjectAsync(mavenProjectWithoutCriGenerationProperty())
            }

            assertFalse(isBtaCriProviderApplicable())
        }
    }

    private fun withBtaCriRegistryEnabled(action: () -> Unit) {
        val registryValue = Registry.get(BTA_CRI_REGISTRY_KEY)
        val oldValue = registryValue.asBoolean()
        registryValue.setValue(true, testRootDisposable)
        try {
            action()
        } finally {
            registryValue.setValue(oldValue)
        }
    }

    private fun isBtaCriProviderApplicable(): Boolean =
        KotlinCompilerReferenceIndexStorageProvider.getApplicableProvider(project).isBtaCriProvider()

    private fun mavenProjectWithCriGenerationProperty(value: String): String =
        """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1.0.0</version>

<properties>
    <kotlin.compiler.generateCompilerRefIndex>$value</kotlin.compiler.generateCompilerRefIndex>
</properties>
"""

    private fun mavenProjectWithoutCriGenerationProperty(): String =
        """
<groupId>test</groupId>
<artifactId>project</artifactId>
<version>1.0.0</version>
"""

    private companion object {
        private const val BTA_CRI_REGISTRY_KEY = "kotlin.cri.bta.support.enabled"
    }
}
