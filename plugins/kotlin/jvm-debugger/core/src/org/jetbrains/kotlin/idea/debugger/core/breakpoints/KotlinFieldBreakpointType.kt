// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// The package directive doesn't match the file location to prevent API breakage
package org.jetbrains.kotlin.idea.debugger.breakpoints

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.ui.breakpoints.Breakpoint
import com.intellij.debugger.ui.breakpoints.BreakpointManager
import com.intellij.debugger.ui.breakpoints.BreakpointWithHighlighter
import com.intellij.debugger.ui.breakpoints.JavaBreakpointType
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.classes.KtLightClassForFacade
import org.jetbrains.kotlin.asJava.classes.KtLightClassForSourceDeclaration
import org.jetbrains.kotlin.idea.debugger.core.KotlinDebuggerCoreBundle
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.*
import org.jetbrains.kotlin.idea.debugger.core.breakpoints.dialog.AddFieldBreakpointDialog
import org.jetbrains.kotlin.psi.KtDeclarationContainer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import javax.swing.Icon
import javax.swing.JComponent

class KotlinFieldBreakpointType :
    JavaBreakpointType<KotlinPropertyBreakpointProperties>,
    XLineBreakpointType<KotlinPropertyBreakpointProperties>(
        "kotlin-field", KotlinDebuggerCoreBundle.message("property.watchpoint.tab.title")
    ),
    KotlinBreakpointType
{
    override fun createJavaBreakpoint(
        project: Project,
        breakpoint: XBreakpoint<KotlinPropertyBreakpointProperties>
    ): Breakpoint<KotlinPropertyBreakpointProperties> {
        return KotlinFieldBreakpoint(project, breakpoint)
    }

    override fun canPutAt(file: VirtualFile, line: Int, project: Project): Boolean {
        return isBreakpointApplicable(file, line, project) { element ->
            when (element) {
                is KtProperty -> ApplicabilityResult.definitely(!element.isLocal)
                is KtParameter -> ApplicabilityResult.definitely(element.hasValOrVar())
                else -> ApplicabilityResult.UNKNOWN
            }
        }
    }

    override fun getPriority() = 120

    override fun createBreakpointProperties(file: VirtualFile, line: Int): KotlinPropertyBreakpointProperties {
        return KotlinPropertyBreakpointProperties()
    }

    override fun addBreakpoint(project: Project, parentComponent: JComponent?): XLineBreakpoint<KotlinPropertyBreakpointProperties>? {
        var result: XLineBreakpoint<KotlinPropertyBreakpointProperties>? = null

        val dialog = object : AddFieldBreakpointDialog(project) {
            override fun validateData(): Boolean {
                val className = className
                if (className.isEmpty()) {
                    reportError(project, JavaDebuggerBundle.message("error.field.breakpoint.class.name.not.specified"))
                    return false
                }

                val psiClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project))
                if (psiClass !is KtLightClass) {
                    reportError(project, KotlinDebuggerCoreBundle.message("property.watchpoint.error.couldnt.find.0.class", className))
                    return false
                }

                val fieldName = fieldName
                if (fieldName.isEmpty()) {
                    reportError(project, JavaDebuggerBundle.message("error.field.breakpoint.field.name.not.specified"))
                    return false
                }

                result = when (psiClass) {
                    is KtLightClassForFacade -> psiClass.files.asSequence().mapNotNull {
                        createBreakpointIfPropertyExists(it, it, className, fieldName)
                    }.firstOrNull()

                    is KtLightClassForSourceDeclaration -> {
                        val ktClassOrObject = psiClass.kotlinOrigin
                        createBreakpointIfPropertyExists(ktClassOrObject, ktClassOrObject.containingKtFile, className, fieldName)
                    }
                    else -> null
                }

                if (result == null) {
                    reportError(
                        project,
                        JavaDebuggerBundle.message("error.field.breakpoint.field.not.found", className, fieldName, fieldName)
                    )
                }

                return result != null
            }
        }

        dialog.show()
        return result
    }

    private fun createBreakpointIfPropertyExists(
        declaration: KtDeclarationContainer,
        file: KtFile,
        className: String,
        fieldName: String
    ): XLineBreakpoint<KotlinPropertyBreakpointProperties>? {
        val project = file.project
        val property = declaration.declarations.firstOrNull { it is KtProperty && it.name == fieldName } ?: return null

        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
        val line = document.getLineNumber(property.textOffset)
        return runWriteAction {
            XDebuggerManager.getInstance(project).breakpointManager.addLineBreakpoint(
                this,
                file.virtualFile.url,
                line,
                KotlinPropertyBreakpointProperties(fieldName, className)
            )
        }
    }

    private fun reportError(project: Project, @NlsContexts.DialogMessage message: String) {
        Messages.showMessageDialog(project, message, JavaDebuggerBundle.message("add.field.breakpoint.dialog.title"), Messages.getErrorIcon())
    }

    override fun isAddBreakpointButtonVisible() = true

    override fun getMutedEnabledIcon(): Icon = AllIcons.Debugger.Db_muted_field_breakpoint

    override fun getDisabledIcon(): Icon = AllIcons.Debugger.Db_disabled_field_breakpoint

    override fun getEnabledIcon(): Icon = AllIcons.Debugger.Db_field_breakpoint

    override fun getMutedDisabledIcon(): Icon = AllIcons.Debugger.Db_muted_disabled_field_breakpoint

    override fun canBeHitInOtherPlaces() = true

    override fun getShortText(breakpoint: XLineBreakpoint<KotlinPropertyBreakpointProperties>): String {
        val properties = breakpoint.properties
        val className = properties.myClassName
        @Suppress("HardCodedStringLiteral")
        return if (className.isNotEmpty()) className + "." + properties.myFieldName else properties.myFieldName
    }

    override fun createProperties(): KotlinPropertyBreakpointProperties = KotlinPropertyBreakpointProperties()

    override fun createCustomPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XLineBreakpoint<KotlinPropertyBreakpointProperties>> {
        return KotlinFieldBreakpointPropertiesPanel()
    }

    override fun getDisplayText(breakpoint: XLineBreakpoint<KotlinPropertyBreakpointProperties>): String? {
        val kotlinBreakpoint = BreakpointManager.getJavaBreakpoint(breakpoint) as? BreakpointWithHighlighter
        return kotlinBreakpoint?.description ?: super.getDisplayText(breakpoint)
    }

    override fun getEditorsProvider(
        breakpoint: XLineBreakpoint<KotlinPropertyBreakpointProperties>,
        project: Project,
    ): XDebuggerEditorsProvider? = null

    override fun createCustomRightPropertiesPanel(project: Project): XBreakpointCustomPropertiesPanel<XLineBreakpoint<KotlinPropertyBreakpointProperties>> {
        return KotlinBreakpointFiltersPanel(project)
    }

    override fun isSuspendThreadSupported() = true
}
