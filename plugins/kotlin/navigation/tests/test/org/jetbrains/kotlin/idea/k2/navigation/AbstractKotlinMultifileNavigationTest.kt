// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.navigation

import com.intellij.testFramework.common.runAll
import org.jetbrains.kotlin.idea.fir.invalidateCaches
import org.jetbrains.kotlin.idea.resolve.AbstractReferenceResolveTest
import java.io.File

abstract class AbstractKotlinMultifileNavigationTest : AbstractReferenceResolveTest() {

    override fun tearDown() = runAll(
        project::invalidateCaches,
        { super.tearDown() },
    )

    override val testDataDirectory: File
        get() = super.testDataDirectory.resolve(defaultTestName)

    override fun fileName(): String = "$defaultTestName.kt"

    override fun configureTest() {
        myFixture.copyDirectoryToProject("", "")
        super.configureTest()
    }

    private val defaultTestName
        get() = getTestName(false)
}