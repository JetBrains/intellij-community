// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.idea.compilerPlugin.kotlinxSerialization.KotlinSerializationEnabledChecker

object SerializationPluginRegistrar {
    fun registerSerializationPlugin(project: Project) {
        ApplicationManager.getApplication().apply {
            if (!extensionArea.hasExtensionPoint(KotlinSerializationEnabledChecker.Companion.EP_NAME.name)) {
                extensionArea.registerExtensionPoint(
                    KotlinSerializationEnabledChecker.Companion.EP_NAME.name,
                    KotlinSerializationEnabledChecker::class.java.name,
                    ExtensionPoint.Kind.INTERFACE,
                    false
                )
            }
            registerExtension(KotlinSerializationEnabledChecker.Companion.EP_NAME, AlwaysYesForEvaluator, project)
        }
    }

    private object AlwaysYesForEvaluator : KotlinSerializationEnabledChecker {
        override fun isEnabledFor(moduleDescriptor: ModuleDescriptor): Boolean {
            return true
        }
    }

}