// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.test.kmp

import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModuleProvider
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.idea.base.projectStructure.getKaModule
import org.jetbrains.kotlin.idea.test.KotlinStdJSProjectDescriptor
import org.jetbrains.kotlin.idea.test.ProjectDescriptorWithStdlibSources
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.isJs
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.platform.konan.NativePlatform
import org.jetbrains.kotlin.platform.konan.isNative
import org.junit.Assume

object KMPProjectDescriptorTestUtilities {
    fun createKMPProjectDescriptor(platform: KMPTestPlatform): LightProjectDescriptor? {
        Assume.assumeTrue("Current host doesn't support the platform: $platform", platform.isSupportedOnCurrentHost())

        return when (platform) {
            KMPTestPlatform.Jvm -> ProjectDescriptorWithStdlibSources.getInstanceWithStdlibSources()
            KMPTestPlatform.Js -> KotlinStdJSProjectDescriptor
            KMPTestPlatform.NativeLinux -> KMPNativeLinuxProjectDescriptor
            KMPTestPlatform.CommonNativeJvm -> KMPCommonNativeLinuxAndJvmProjectDescriptor
            KMPTestPlatform.Unspecified -> null
        }
    }

    fun validateTest(files: List<PsiFile>, platform: KMPTestPlatform) {
        if (platform == KMPTestPlatform.Unspecified) return

        for (file in files) {
            val module = file.getKaModule(file.project, useSiteModule = null)
            check(module is KaSourceModule)
            val targetPlatform = module.targetPlatform
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
