// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test.navigation

import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.test.addDependency

abstract class AbstractNavigationToSourceOrDecompiledTest : AbstractNavigationWithMultipleLibrariesTest() {
    fun doTest(withSources: Boolean, expectedFileName: String) {
        val srcPath = getTestDataDirectory().resolve("src").absolutePath
        val moduleA = module("moduleA", srcPath)
        val moduleB = module("moduleB", srcPath)

        moduleA.addDependency(createProjectLib("libA", withSources))
        moduleB.addDependency(createProjectLib("libB", withSources))

        checkReferencesInModule(moduleA, "libA", expectedFileName)
        checkReferencesInModule(moduleB, "libB", expectedFileName)
    }

    abstract fun createProjectLib(libraryName: String, withSources: Boolean): Library
}
