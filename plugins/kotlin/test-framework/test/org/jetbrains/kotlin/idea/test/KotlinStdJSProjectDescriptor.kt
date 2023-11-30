// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptLibraryKind
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts

object KotlinStdJSProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk? = null

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        ConfigLibraryUtil.addLibrary(model, "kotlin-stdlib-js", KotlinJavaScriptLibraryKind) {
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlibJs), OrderRootType.CLASSES)
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlibSources), OrderRootType.SOURCES)
        }
    }
}

object KotlinStdJSWithStdLibProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk? = null

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        ConfigLibraryUtil.addLibrary(model, "kotlin-stdlib-js", KotlinJavaScriptLibraryKind) {
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlibJs), OrderRootType.CLASSES)
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlib), OrderRootType.CLASSES)
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinStdlibSources), OrderRootType.SOURCES)
        }
    }
}

object KotlinStdJSWithDomApiCompatProjectDescriptor : KotlinLightProjectDescriptor() {
    override fun getSdk(): Sdk? = null

    override fun configureModule(module: Module, model: ModifiableRootModel) {
        KotlinStdJSProjectDescriptor.configureModule(module, model)
        ConfigLibraryUtil.addLibrary(model, "kotlin-dom-api-compat", KotlinJavaScriptLibraryKind) {
            addRoot(VfsUtil.getUrlForLibraryRoot(TestKotlinArtifacts.kotlinDomApiCompat), OrderRootType.CLASSES)
        }
    }
}