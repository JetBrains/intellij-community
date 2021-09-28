// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven.importer

import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.config.KotlinFacetSettings
import org.jetbrains.kotlin.idea.caches.project.productionSourceInfo
import org.jetbrains.kotlin.idea.caches.project.testSourceInfo
import org.jetbrains.kotlin.idea.facet.KotlinFacet
import org.jetbrains.kotlin.idea.maven.KotlinImporterComponent
import org.jetbrains.kotlin.idea.maven.KotlinMavenImportingTestCase
import org.jetbrains.kotlin.idea.test.resetCodeStyle
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import org.jetbrains.kotlin.platform.TargetPlatform
import org.junit.Assert
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File

@RunWith(JUnit38ClassRunner::class)
abstract class AbstractKotlinMavenImporterTest : KotlinMavenImportingTestCase() {
    protected val kotlinVersion = "1.1.3"

    override fun setUp() {
        super.setUp()
        repositoryPath = File(myDir, "repo").path
        createStdProjectFolders()
    }

    override fun tearDown() = runAll(
        ThrowableRunnable { resetCodeStyle(myProject) },
        ThrowableRunnable { super.tearDown() },
    )

    protected fun checkStableModuleName(projectName: String, expectedName: String, platform: TargetPlatform, isProduction: Boolean) {
        val module = getModule(projectName)
        val moduleInfo = if (isProduction) module.productionSourceInfo() else module.testSourceInfo()

        val resolutionFacade = KotlinCacheService.getInstance(myProject).getResolutionFacadeByModuleInfo(moduleInfo!!, platform)!!
        val moduleDescriptor = resolutionFacade.moduleDescriptor

        Assert.assertEquals("<$expectedName>", moduleDescriptor.stableName?.asString())
    }

    protected fun facetSettings(moduleName: String) = KotlinFacet.get(getModule(moduleName))!!.configuration.settings

    protected val facetSettings: KotlinFacetSettings
        get() = facetSettings("project")

    protected fun assertImporterStatePresent() {
        assertNotNull("Kotlin importer component is not present", myTestFixture.module.getServiceSafe<KotlinImporterComponent>())
    }

    fun `test single class should contain no more than N tests`() {
        val numberOfTests = this::class.java.declaredMethods.count { method ->
            method.name.startsWith("test") || method.annotations.any { it is org.junit.Test }
        }
        val n = 4
        Assert.assertTrue("Each class should contain no more than $n tests (for being able to parallel tests on TC)", numberOfTests <= n)
    }
}
