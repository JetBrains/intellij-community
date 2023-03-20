// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.indices.findModuleInfoFile
import org.jetbrains.kotlin.idea.base.psi.findRequireDirective
import org.jetbrains.kotlin.idea.base.util.sdk
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.resolve.jvm.modules.KOTLIN_STDLIB_MODULE_NAME

@ApiStatus.Internal
object AddRequiresDirectiveFacility {
    fun canAddModuleRequirement(module: PsiJavaModule): Boolean {
        return getLBrace(module) != null
    }

    fun addModuleRequirement(module: PsiJavaModule, requiredName: String): Boolean {
        if (!module.isValid || !canAddModuleRequirement(module) || module.findRequireDirective(requiredName) != null) {
            return false
        }

        val parserFacade = JavaPsiFacade.getInstance(module.project).parserFacade
        val tempModule = parserFacade.createModuleFromText("module TempModuleName { requires $requiredName; }", module)
        val requiresStatement = tempModule.requires.first()

        val addingPlace = findAddingPlace(module) ?: return false
        addingPlace.parent.addAfter(requiresStatement, addingPlace)

        return true
    }

    private fun getLBrace(module: PsiJavaModule): PsiElement? {
        val nameElement = module.nameIdentifier
        var element: PsiElement? = nameElement.nextSibling
        while (element != null) {
            if (PsiUtil.isJavaToken(element, JavaTokenType.LBRACE)) {
                return element
            }
            element = element.nextSibling
        }
        return null // module-info is incomplete
    }

    private fun findAddingPlace(module: PsiJavaModule): PsiElement? {
        val addingPlace = module.requires.lastOrNull()
        return addingPlace ?: getLBrace(module)
    }
}

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
            AddRequiresDirectiveFacility.addModuleRequirement(javaModule, KOTLIN_STDLIB_MODULE_NAME)
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