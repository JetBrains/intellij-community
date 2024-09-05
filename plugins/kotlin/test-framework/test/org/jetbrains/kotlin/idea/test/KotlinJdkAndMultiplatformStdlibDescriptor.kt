// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.testFramework.IdeaTestUtil
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts

class KotlinJdkAndMultiplatformStdlibDescriptor private constructor(private val withSources: Boolean) : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk = IdeaTestUtil.getMockJdk18()

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        ConfigLibraryUtil.addLibrary(model, STDLIB_LIB_NAME) {
            //first we should search in platform specific libraries and if failed, in common part
            addRoot(TestKotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
            if (withSources) {
                addRoot(TestKotlinArtifacts.kotlinStdlibSources, OrderRootType.SOURCES)
            }

            addRoot(TestKotlinArtifacts.kotlinStdlibCommon, OrderRootType.CLASSES)
            if (withSources) {
                addRoot(TestKotlinArtifacts.kotlinStdlibCommonSources, OrderRootType.SOURCES)
            }
        }
    }

    companion object {
        val JDK_AND_MULTIPLATFORM_STDLIB_WITH_SOURCES = KotlinJdkAndMultiplatformStdlibDescriptor(true)
        val JDK_AND_MULTIPLATFORM_STDLIB = KotlinJdkAndMultiplatformStdlibDescriptor(false)

        private const val STDLIB_LIB_NAME = "stdlib"
    }
}