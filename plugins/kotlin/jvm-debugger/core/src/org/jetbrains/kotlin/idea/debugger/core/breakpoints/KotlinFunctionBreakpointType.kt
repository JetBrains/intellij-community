// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinBreakpointType
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle.message
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.ApplicabilityResult.Companion.maybe
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*

class KotlinFunctionBreakpointType :
    JavaMethodBreakpointType("kotlin-function", message("function.breakpoint.tab.title")),
    KotlinBreakpointType
{
    override fun getPriority() = 120

    override fun createJavaBreakpoint(project: Project, breakpoint: XBreakpoint<JavaMethodBreakpointProperties>) =
        KotlinFunctionBreakpoint(project, breakpoint)

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean =
        isKtFileWithCommonOrJvmPlatform(file, project) && isFunctionBreakpointApplicable(file, line, project)

    private fun isKtFileWithCommonOrJvmPlatform(file: VirtualFile, project: Project): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return false
        val platform = psiFile.platform
        return platform.isCommon() || platform.isJvm()
    }
}

fun isFunctionBreakpointApplicable(file: VirtualFile, line: Int, project: Project): Boolean =
    isBreakpointApplicable(file, line, project) { element ->
        when (element) {
            is KtConstructor<*> ->
                ApplicabilityResult.DEFINITELY_YES
            is KtFunction ->
                maybe(!KtPsiUtil.isLocal(element) && !element.isInlineOnly())
            is KtPropertyAccessor ->
                maybe(element.hasBody() && !KtPsiUtil.isLocal(element.property))
            is KtClass ->
                maybe(
                    element !is KtEnumEntry
                            && !element.isAnnotation()
                            && !element.isInterface()
                            && element.hasPrimaryConstructor()
                )
            else ->
                ApplicabilityResult.UNKNOWN
        }
    }
