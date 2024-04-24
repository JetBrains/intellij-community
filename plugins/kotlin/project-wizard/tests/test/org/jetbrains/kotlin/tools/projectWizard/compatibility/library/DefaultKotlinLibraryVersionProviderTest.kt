// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility.library

import com.intellij.openapi.roots.ExternalLibraryDescriptor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.tools.projectWizard.compatibility.libraries.DefaultKotlinLibraryVersionProvider

class DefaultKotlinLibraryVersionProviderTest : BasePlatformTestCase() {
    private lateinit var provider: DefaultKotlinLibraryVersionProvider

    override fun setUp() {
        super.setUp()
        provider = DefaultKotlinLibraryVersionProvider()
    }

    private fun getCoroutinesVersion(kotlinVersion: KotlinVersion): ExternalLibraryDescriptor? {
        val groupId = "org.jetbrains.kotlinx"
        val artifactId = "kotlinx-coroutines-core"
        val descriptor = provider.getVersion(groupId, artifactId, kotlinVersion)
        if (descriptor != null) {
            assertEquals(groupId, descriptor.libraryGroupId)
            assertEquals(artifactId, descriptor.libraryArtifactId)
            assertNotNull(descriptor.preferredVersion)
            assertEquals(descriptor.preferredVersion, descriptor.minVersion)
            assertEquals(descriptor.preferredVersion, descriptor.maxVersion)
        }
        return descriptor
    }

    fun testDefaultVersionProviderRegistered() {
        assertTrue(KotlinLibraryVersionProvider.EP_NAME.extensionList.any { it is DefaultKotlinLibraryVersionProvider })
    }

    fun testKnownVersion() {
        val returnedVersion = getCoroutinesVersion(KotlinVersion(1, 9))
        assertNotNull(returnedVersion)
    }

    fun testPatchedKotlinVersion() {
        val returnedVersion = getCoroutinesVersion(KotlinVersion(1, 9, 23))
        val nonPatchedVersion = getCoroutinesVersion(KotlinVersion(1, 9))
        assertNotNull(returnedVersion?.preferredVersion)
        assertEquals(returnedVersion?.preferredVersion, nonPatchedVersion?.preferredVersion)
    }

    fun testUnknownKotlin() {
        assertNull(getCoroutinesVersion(KotlinVersion(0, 3)))
    }

    fun testSpecificCoroutinesVersions() {
        // Here we test that versions for old Kotlin versions (which will not receive new updates)
        // return the correct exact values
        assertEquals("1.6.4", getCoroutinesVersion(KotlinVersion(1, 6))?.preferredVersion)
        assertEquals("1.5.2", getCoroutinesVersion(KotlinVersion(1, 5))?.preferredVersion)
    }
}