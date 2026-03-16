// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard.compatibility

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.configuration.KotlinLibraryVersionProvider
import org.jetbrains.kotlin.idea.test.createFacet

class DefaultKotlinLibraryVersionProviderTest : BasePlatformTestCase() {
    private lateinit var provider: DefaultKotlinLibraryVersionProvider

    override fun setUp() {
        super.setUp()
        provider = DefaultKotlinLibraryVersionProvider()
        module.createFacet(useProjectSettings = false)
    }

    private fun getCoroutinesVersion(version: LanguageVersion): String? {
        return getCoroutinesVersion("${version.major}.${version.minor}.0")
    }

    private fun getCoroutinesVersion(version: String): String? {
        val groupId = "org.jetbrains.kotlinx"
        val artifactId = "kotlinx-coroutines-core"

        KotlinJpsPluginSettings.getInstance(project).setVersion(version)

        return provider.getVersion(myFixture.module, groupId, artifactId)
    }

    fun testDefaultVersionProviderRegistered() {
        assertTrue(KotlinLibraryVersionProvider.EP_NAME.extensionList.any { it is DefaultKotlinLibraryVersionProvider })
    }

    fun testKnownVersion() {
        val returnedVersion = getCoroutinesVersion(LanguageVersion.KOTLIN_1_9)
        assertNotNull(returnedVersion)
    }

    fun testUnknownKotlin() {
        assertNotNull(getCoroutinesVersion(LanguageVersion.KOTLIN_1_0))
    }

    fun testVeryHighNonExistingKotlin() {
        val version = getCoroutinesVersion("99.9.0")
        assertNotNull(version)
        assertEquals(version, getHighestCoroutinesVersionFromCompatibilityStore())
    }

    private fun getHighestCoroutinesVersionFromCompatibilityStore(): String? {
        val compatibilityStore = KotlinLibrariesCompatibilityStore.getInstance()
        val versions = compatibilityStore.getVersions("org.jetbrains.kotlinx", "kotlinx-coroutines-core") ?: return null
        return compatibilityStore.getLatestVersion(versions)
    }

    fun testSpecificCoroutinesVersions() {
        // Here we test that versions for old Kotlin versions (which will not receive new updates)
        // return the correct exact values
        assertEquals("1.6.4", getCoroutinesVersion(LanguageVersion.KOTLIN_1_6))
        assertEquals("1.5.2", getCoroutinesVersion(LanguageVersion.KOTLIN_1_5))
    }
}