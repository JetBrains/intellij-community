// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.framework.KotlinSdkType
import org.jetbrains.kotlin.projectModel.FullJdk
import org.jetbrains.kotlin.projectModel.KotlinSdk
import org.jetbrains.kotlin.projectModel.MockJdk
import org.jetbrains.kotlin.projectModel.ResolveSdk
import org.jetbrains.kotlin.test.TestJdkKind

fun Module.addDependency(
    library: Library,
    dependencyScope: DependencyScope = DependencyScope.COMPILE,
    exported: Boolean = false
) = ModuleRootModificationUtil.addDependency(this, library, dependencyScope, exported)

fun Module.addDependency(sdk: ResolveSdk, testRootDisposable: Disposable) {
    when (sdk) {
        FullJdk -> ConfigLibraryUtil.configureSdk(this, PluginTestCaseBase.addJdk(testRootDisposable) {
            PluginTestCaseBase.jdk(TestJdkKind.FULL_JDK)
        })

        MockJdk -> ConfigLibraryUtil.configureSdk(this, PluginTestCaseBase.addJdk(testRootDisposable) {
            PluginTestCaseBase.jdk(TestJdkKind.MOCK_JDK)
        })

        KotlinSdk -> {
            KotlinSdkType.setUpIfNeeded(testRootDisposable)
            ConfigLibraryUtil.configureSdk(
                this,
                runReadAction { ProjectJdkTable.getInstance() }.findMostRecentSdkOfType(KotlinSdkType.INSTANCE)
                    ?: error("Kotlin SDK wasn't created")
            )
        }

        else -> error("Don't know how to set up SDK of type: ${sdk::class}")
    }
}
