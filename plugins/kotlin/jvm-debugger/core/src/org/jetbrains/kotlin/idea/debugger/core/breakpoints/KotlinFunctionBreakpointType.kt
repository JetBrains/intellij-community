// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.core.breakpoints

import com.intellij.debugger.ui.breakpoints.JavaMethodBreakpointType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NotNull
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtTypeAliasSymbol
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.debugger.breakpoints.KotlinBreakpointType
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle.message
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.ApplicabilityResult.Companion.maybe
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.*

open class KotlinFunctionBreakpointType protected constructor(@NotNull id: String, @Nls @NotNull message: String) :
    JavaMethodBreakpointType(id, message),
    KotlinBreakpointType {

    @Suppress("unused") // Used by plugin XML
    constructor() : this("kotlin-function", message("function.breakpoint.tab.title"))

    override fun getGeneralDescription(variant: XLineBreakpointVariant) =
        message("function.breakpoint.description")

    override fun getGeneralDescription(breakpoint: XLineBreakpoint<JavaMethodBreakpointProperties>) =
        message("function.breakpoint.description")

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

    open fun isFunctionBreakpointApplicable(file: VirtualFile, line: Int, project: Project): Boolean =
        isBreakpointApplicable(file, line, project) { element ->
            when (element) {
                is KtConstructor<*> ->
                    ApplicabilityResult.DEFINITELY_YES

                is KtFunction ->
                    maybe(
                        !KtPsiUtil.isLocal(element)
                                && !element.isInlineOnly()
                                && !element.isComposable()
                    )

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

private val COMPOSABLE_CLASS_ID = ClassId.fromString("androidx/compose/runtime/Composable")

/**
 * Don't allow method breakpoints on Composable functions because we can't match their signature.
 *
 * This will be handled by the Compose plugin.
 */
private fun KtFunction.isComposable(): Boolean {
    analyze(this) {
        for (annotationEntry in annotationEntries) {
            val classSymbol = when (val symbol = annotationEntry.typeReference?.mainReference?.resolveToSymbol()) {
                is KtTypeAliasSymbol -> symbol.expandedType.expandedClassSymbol
                is KtClassOrObjectSymbol -> symbol
                else -> null
            }

            if (classSymbol != null && classSymbol.classIdIfNonLocal == COMPOSABLE_CLASS_ID) {
                return true
            }
        }

        return false
    }
}