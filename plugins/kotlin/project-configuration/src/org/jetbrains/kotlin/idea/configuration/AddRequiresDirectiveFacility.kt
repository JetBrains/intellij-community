// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.indices.findModuleInfoFile
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.resolve.jvm.modules.KOTLIN_STDLIB_MODULE_NAME

@ApiStatus.Internal
fun addStdlibToJavaModuleInfo(module: Module, collector: NotificationMessageCollector): Boolean {
    val callable = addStdlibToJavaModuleInfoLazy(module, collector) ?: return false
    callable()
    return true
}

@ApiStatus.Internal
fun addStdlibToJavaModuleInfoLazy(module: Module, collector: NotificationMessageCollector): (() -> Unit)? {
    val sdk = module.sdk ?: return null

    val sdkVersion = JavaSdk.getInstance().getVersion(sdk)
    if (sdkVersion == null || !sdkVersion.isAtLeast(JavaSdkVersion.JDK_1_9)) {
        return null
    }

    val project = module.project

    val javaModule = findModuleInfoFile(project, module.moduleScope) ?: return null

    return fun() {
        val success = WriteCommandAction.runWriteCommandAction<Boolean>(project) {
            JavaModuleGraphUtil.addDependency(javaModule, KOTLIN_STDLIB_MODULE_NAME, null, false)
        }

        if (success) {
            collector.addMessage(
                KotlinProjectConfigurationBundle.message(
                    "added.0.requirement.to.module.info.in.1",
                    KOTLIN_STDLIB_MODULE_NAME,
                    module.name
                )
            )
        }
    }
}