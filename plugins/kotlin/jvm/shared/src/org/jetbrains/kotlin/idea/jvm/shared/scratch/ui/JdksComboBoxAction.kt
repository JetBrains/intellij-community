// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.shared.scratch.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.vcs.changes.committed.LabeledComboBoxAction
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchFile
import org.jetbrains.kotlin.idea.jvm.shared.scratch.defaultScratchJavaHome
import org.jetbrains.kotlin.idea.jvm.shared.scratch.scratchToolbarLabel
import javax.swing.JComponent

private val defaultJdk: Sdk?
    get() {
        val javaHome = defaultScratchJavaHome ?: return null
        return ProjectJdkTable.getInstance().allJdks.firstOrNull { sdk ->
            sdk.sdkType is JavaSdkType && sdk.homePath == javaHome
        }
    }

class JdksComboBoxAction(private val scratchFile: ScratchFile, private val jdkSelectedListener: (Sdk?) -> Unit) :
    LabeledComboBoxAction(KotlinJvmBundle.message("scratch.jdk.combobox")) {
    override fun createPopupActionGroup(button: JComponent, context: DataContext): DefaultActionGroup {
        val actionGroup = DefaultActionGroup()
        val jdks = buildList {
            addIfNotNull(defaultJdk?.let { SelectDefaultJdkAction(it) })
            addAll(
                ProjectJdkTable.getInstance().allJdks
                .filter { it.sdkType is JavaSdkType }
                .map { SelectJdkAction(it) }
            )
        }

        actionGroup.addAll(jdks)

        return actionGroup
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        return super.createCustomComponent(presentation, place).apply {
            components.forEach { it.font = UIUtil.getFont(UIUtil.FontSize.SMALL, it.font) }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val jdk = scratchFile.jdk
        val module = scratchFile.module

        e.presentation.apply {
            icon = jdk?.let { (it.sdkType as? SdkType)?.icon }
            text = scratchToolbarLabel(
                jdk?.name ?: KotlinJvmBundle.message("list.item.no.jdk")
            )
            isVisible = true
            if (module == null) {
                isEnabled = true
                description = templatePresentation.description
            } else {
                isEnabled = false
                description = KotlinJvmBundle.message("scratch.jdk.combobox.module.disabled.description")
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private inner class SelectDefaultJdkAction(private val jdk: Sdk) : DumbAwareAction(
        KotlinJvmBundle.message("list.item.default.jdk", jdk.name), null, (jdk.sdkType as? SdkType)?.icon
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            scratchFile.resetJdk()
            jdkSelectedListener(jdk)
        }
    }

    private inner class SelectJdkAction(private val jdk: Sdk) : DumbAwareAction(jdk.name, null, (jdk.sdkType as? SdkType)?.icon) {
        override fun actionPerformed(e: AnActionEvent) {
            scratchFile.selectJdk(jdk)
            jdkSelectedListener(jdk)
        }
    }
}
