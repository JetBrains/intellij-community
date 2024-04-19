// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.project.structure.KtSourceModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.test.KotlinStdJSProjectDescriptor
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.isNative

object KMPProjectDescriptorTestUtilities {
    fun createKMPProjectDescriptor(platform: KMPTestPlatform): LightProjectDescriptor? = when (platform) {
        KMPTestPlatform.Jvm -> ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
        KMPTestPlatform.Js -> KotlinStdJSProjectDescriptor
        KMPTestPlatform.NativeLinux -> KMPNativeLinuxProjectDescriptor
        KMPTestPlatform.CommonNativeJvm -> KMPCommonNativeLinuxAndJvmProjectDescriptor
        KMPTestPlatform.Unspecified -> null
    }

    fun validateTest(files: List<PsiFile>, platform: KMPTestPlatform) {
        if (platform == KMPTestPlatform.Unspecified) return

        for (file in files) {
            val ktModule = ProjectStructureProvider.getModule(file.project, file, contextualModule = null)
            check(ktModule is KtSourceModule)
            val targetPlatform = ktModule.platform
            when (platform) {
                KMPTestPlatform.Jvm -> check(targetPlatform.isJvm())
                KMPTestPlatform.Js -> check(targetPlatform.isJs())
                KMPTestPlatform.NativeLinux -> check(targetPlatform.isNative())
                KMPTestPlatform.CommonNativeJvm -> {
                    check(targetPlatform.has<JvmPlatform>())
                    check(targetPlatform.has<NativePlatform>())
                }

                KMPTestPlatform.Unspecified -> {}
            }
        }
    }
}
