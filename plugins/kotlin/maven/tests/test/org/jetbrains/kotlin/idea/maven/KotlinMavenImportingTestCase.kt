// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.maven

import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import com.intellij.maven.testFramework.MavenImportingTestCase
import org.jetbrains.kotlin.config.ResourceKotlinRootType
import org.jetbrains.kotlin.config.SourceKotlinRootType
import org.jetbrains.kotlin.config.TestResourceKotlinRootType
import org.jetbrains.kotlin.config.TestSourceKotlinRootType
import org.jetbrains.kotlin.idea.test.KotlinSdkCreationChecker

abstract class KotlinMavenImportingTestCase : MavenImportingTestCase() {
    private var sdkCreationChecker: KotlinSdkCreationChecker? = null

    override fun setUp() {
        super.setUp()
        sdkCreationChecker = KotlinSdkCreationChecker()
    }

    override fun tearDown() {
        RunAll.runAll(
            ThrowableRunnable<Throwable> { sdkCreationChecker!!.removeNewKotlinSdk() },
            ThrowableRunnable<Throwable> { super.tearDown() },
        )
    }

    protected fun assertKotlinSources(moduleName: String, vararg expectedSources: String) {
        doAssertContentFolders(moduleName, SourceKotlinRootType, *expectedSources)
    }

    protected fun assertKotlinResources(moduleName: String, vararg expectedSources: String) {
        doAssertContentFolders(moduleName, ResourceKotlinRootType, *expectedSources)
    }

    protected fun assertKotlinTestSources(moduleName: String, vararg expectedSources: String) {
        doAssertContentFolders(moduleName, TestSourceKotlinRootType, *expectedSources)
    }

    protected fun assertKotlinTestResources(moduleName: String, vararg expectedSources: String) {
        doAssertContentFolders(moduleName, TestResourceKotlinRootType, *expectedSources)
    }
}