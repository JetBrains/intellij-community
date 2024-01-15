// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.nj2k

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkModificator
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

fun descriptorByFileDirective(testDataFile: File, languageLevel: LanguageLevel = LanguageLevel.JDK_1_8): LightProjectDescriptor {
    val fileText = FileUtil.loadFile(testDataFile, true)
    val descriptor = when {
        InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_FULL_JDK") ->
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceFullJdk()

        InTextDirectivesUtils.isDirectiveDefined(fileText, "RUNTIME_WITH_STDLIB_JDK8") ->
            KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstanceWithStdlibJdk8()

        else -> KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    return object : KotlinWithJdkAndRuntimeLightProjectDescriptor(descriptor.libraryFiles, descriptor.librarySourceFiles) {
        override fun getSdk(): Sdk? {
            val sdk = descriptor.sdk ?: return null
            runWriteAction {
                val modificator: SdkModificator = sdk.clone().sdkModificator
                JavaSdkImpl.attachJdkAnnotations(modificator)
                modificator.commitChanges()
            }
            return sdk
        }

        override fun configureModule(module: Module, model: ModifiableRootModel) {
            super.configureModule(module, model)
            model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = languageLevel
        }
    }
}

