// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.testFramework.RunAll
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinSdkCreationChecker
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinMavenImportingTestCase : MavenMultiVersionImportingTestCase(),
                                              ExpectedPluginModeProvider {

    private var sdkCreationChecker: KotlinSdkCreationChecker? = null

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
        sdkCreationChecker = KotlinSdkCreationChecker()
    }

    override fun tearDown() {
        RunAll.runAll(
            { sdkCreationChecker!!.removeNewKotlinSdk() },
            { super.tearDown() },
        )
    }

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    protected fun assertKotlinSources(moduleName: String, vararg expectedSources: String) {
        doAssertContentFolders(moduleName, SourceKotlinRootType, *expectedSources)
    }

    protected fun assertDefaultKotlinResources(moduleName: String, vararg additionalSources: String) {
        assertDefaultResources(moduleName, ResourceKotlinRootType, *additionalSources)
    }

    protected fun assertKotlinTestSources(moduleName: String, vararg expectedSources: String) {
        doAssertContentFolders(moduleName, TestSourceKotlinRootType, *expectedSources)
    }

    protected fun assertDefaultKotlinTestResources(moduleName: String, vararg additionalSources: String) {
        assertDefaultTestResources(moduleName, TestResourceKotlinRootType, *additionalSources)
    }
}