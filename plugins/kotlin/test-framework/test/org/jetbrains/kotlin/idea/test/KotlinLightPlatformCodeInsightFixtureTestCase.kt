// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.base.test.KotlinRoot
import org.jetbrains.kotlin.idea.test.util.checkPluginIsCorrect
import java.io.File

abstract class KotlinLightPlatformCodeInsightFixtureTestCase : LightPlatformCodeInsightFixtureTestCase() {
    protected open fun isFirPlugin(): Boolean = false

    override fun setUp() {
        super.setUp()
        enableKotlinOfficialCodeStyle(project)
        VfsRootAccess.allowRootAccess(myFixture.testRootDisposable, KotlinRoot.DIR.path)

        if (!isFirPlugin()) {
            invalidateLibraryCache(project)
        }
        checkPluginIsCorrect(isFirPlugin())
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { disableKotlinOfficialCodeStyle(project) },
            ThrowableRunnable { super.tearDown() },
        )
    }

    protected fun dataFile(fileName: String): File = File(testDataPath, fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected fun dataPath(fileName: String = fileName()): String = dataFile(fileName).toString()

    protected fun dataPath(): String = dataPath(fileName())

    protected open fun fileName(): String = KotlinTestUtils.getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    override fun getTestDataPath() = TestMetadataUtil.getTestDataPath(this::class.java)
}
