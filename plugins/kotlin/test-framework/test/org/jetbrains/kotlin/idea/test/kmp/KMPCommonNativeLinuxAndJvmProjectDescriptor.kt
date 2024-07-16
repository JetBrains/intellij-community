// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.base.platforms.KotlinCommonLibraryKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.createMultiplatformFacetM3

object KMPCommonNativeLinuxAndJvmProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk? = null

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        module.createMultiplatformFacetM3(
            platformKind = KMPTestPlatform.CommonNativeJvm.targetPlatform,
        )

        ConfigLibraryUtil.addLibrary(
            model, "kotlin-stdlib-native-common", KotlinCommonLibraryKind
        ) {
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlibCommon), OrderRootType.CLASSES)
        }
    }
}

