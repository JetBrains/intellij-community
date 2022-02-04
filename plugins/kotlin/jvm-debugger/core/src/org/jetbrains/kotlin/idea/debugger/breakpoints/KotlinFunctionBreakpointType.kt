// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.breakpoints

import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties
import org.jetbrains.kotlin.idea.debugger.KotlinDebuggerCoreBundle.message
import org.jetbrains.kotlin.idea.debugger.breakpoints.ApplicabilityResult.Companion.maybe
import org.jetbrains.kotlin.psi.*

class KotlinFunctionBreakpointType :
    JavaMethodBreakpointType("kotlin-function", message("function.breakpoint.tab.title")),
    KotlinBreakpointType
{
    override fun getPriority() = 120

    override fun createJavaBreakpoint(project: Project, breakpoint: XBreakpoint<JavaMethodBreakpointProperties>) =
        KotlinFunctionBreakpoint(project, breakpoint)

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        return isBreakpointApplicable(file, line, project) { element: PsiElement ->
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
    }
}
