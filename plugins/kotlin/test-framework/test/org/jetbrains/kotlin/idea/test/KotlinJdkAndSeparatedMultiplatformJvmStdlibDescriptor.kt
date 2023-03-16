// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts

class KotlinJdkAndSeparatedMultiplatformJvmStdlibDescriptor : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk11()

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        ConfigLibraryUtil.addLibrary(model, STDLIB_NAME) {
            addRoot(TestKotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
            addRoot(TestKotlinArtifacts.kotlinStdlibSources, OrderRootType.SOURCES)
        }
        ConfigLibraryUtil.addLibrary(model, STDLIB_COMMON_NAME) {
            addRoot(TestKotlinArtifacts.kotlinStdlibCommon, OrderRootType.CLASSES)
            addRoot(TestKotlinArtifacts.kotlinStdlibCommonSources, OrderRootType.SOURCES)
        }
    }

    companion object {
        val TWO_PART_STDLIB_WITH_SOURCES = KotlinJdkAndSeparatedMultiplatformJvmStdlibDescriptor()

        private const val STDLIB_NAME = "kotlin-stdlib"
        private const val STDLIB_COMMON_NAME = "kotlin-stdlib-common"
    }
}
