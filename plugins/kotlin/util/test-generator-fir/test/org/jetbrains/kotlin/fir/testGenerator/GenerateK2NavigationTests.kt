// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.fir.testGenerator

import org.jetbrains.kotlin.idea.k2.navigation.AbstractKotlinMultifileNavigationTest
import org.jetbrains.kotlin.idea.k2.navigation.AbstractKotlinNavigationToLibrarySourceTest
import org.jetbrains.kotlin.idea.k2.navigation.AbstractMultiModuleNavigationTest
import org.jetbrains.kotlin.idea.k2.navigation.AbstractResolveExtensionGeneratedSourcesFilterTest
import org.jetbrains.kotlin.testGenerator.model.GroupCategory.NAVIGATION
import org.jetbrains.kotlin.testGenerator.model.MutableTWorkspace
import org.jetbrains.kotlin.testGenerator.model.Patterns.DIRECTORY
import org.jetbrains.kotlin.testGenerator.model.model
import org.jetbrains.kotlin.testGenerator.model.testClass
import org.jetbrains.kotlin.testGenerator.model.testGroup

internal fun MutableTWorkspace.generateK2NavigationTests() {
    testGroup("navigation/tests", category = NAVIGATION, testDataPath = "testData") {
        testClass<AbstractKotlinNavigationToLibrarySourceTest> {
            model("navigationToLibrarySourcePolicy")
        }

        testClass<AbstractResolveExtensionGeneratedSourcesFilterTest> {
            model("resolveExtensionGeneratedSourcesFilter")
        }

        testClass<AbstractKotlinMultifileNavigationTest> {
            model("multifile", pattern = DIRECTORY, isRecursive = false)
        }

        testClass<AbstractMultiModuleNavigationTest> {
            model("multimodule", pattern = DIRECTORY, isRecursive = false)
        }
    }
}