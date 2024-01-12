// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginKindSwitcher
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.plugin.getPluginModeDescription
import org.jetbrains.kotlin.idea.preferences.KotlinPreferencesBundle
import javax.swing.JComponent

class KotlinPluginKindSwitcherController {
    private val initialValue: KotlinPluginMode = KotlinPluginKindSwitcher.getPluginKindByVmOptions()
    private var chosenKind: KotlinPluginMode = initialValue

    private val pluginKindWillBeSwitchedAfterRestart: Boolean
        get() = KotlinPluginModeProvider.currentPluginMode != chosenKind

    private lateinit var checkBox: JBCheckBox

    private lateinit var pluginTypeChooserPanel: Panel
    private lateinit var currentPluginPanel: Panel

    private val productName: @NlsSafe String
        get() = ApplicationNamesInfo.getInstance().fullProductName

    fun isModified(): Boolean {
        return KotlinPluginKindSwitcher.getPluginKindByVmOptions() != chosenKind
    }

    fun applyChanges() {
        KotlinPluginKindSwitcher.setPluginKindByVmOptions(chosenKind)
        updatePanels()
        if (pluginKindWillBeSwitchedAfterRestart) {
            suggestRestart()
        }
    }


    fun reset() {
        if (!isModified()) return
        chosenKind = KotlinPluginKindSwitcher.getPluginKindByVmOptions()
        updateCheckBoxToChosenKind()
        updatePanels()
    }

    fun createComponent(): JComponent = panel {
        currentPluginPanel = panel {
            row {
                text(KotlinPreferencesBundle.message("label.your.current.plugin", KotlinPluginModeProvider.currentPluginMode.getPluginModeDescription()))
            }
            row {
                val otherPlugin = KotlinPluginModeProvider.currentPluginMode.other()
                text(KotlinPreferencesBundle.message("label.plugin.will.be.switched.after.ide.restart", otherPlugin.getPluginModeDescription()))
            }
            row {
                link(
                    IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect", productName)
                ) {
                    suggestRestart()
                }
            }
            row {
                link(KotlinPreferencesBundle.message("link.label.cancel.switching")) {
                    cancelSwitching()
                }
            }
        }
        pluginTypeChooserPanel = panel {
            row {
                checkBox(KotlinPreferencesBundle.message("checkbox.enable.k2.based.kotlin.plugin")).also {
                    checkBox = it.component
                }.onChanged {
                    chosenKind = if (it.isSelected) KotlinPluginMode.K2 else KotlinPluginMode.K1
                }.gap(RightGap.SMALL)
                icon(AllIcons.General.Alpha).align(AlignY.BOTTOM)
                updateCheckBoxToChosenKind()
                comment(KotlinPreferencesBundle.message("kotlin.plugin.type.restart.required.comment"))
            }

            row {
                comment(KotlinPreferencesBundle.message("text.k2.based.kotlin.plugin"))
            }

            // here should be a link to a K2 IDE blogpost when it's out
            //row {
            //    browserLink(
            //        KotlinPreferencesBundle.message("link.label.about.k2.based.plugin"),
            //        "https://..."
            //    )
            //}

        }

        updatePanels()
    }

    private fun cancelSwitching() {
        chosenKind = KotlinPluginModeProvider.currentPluginMode
        updateCheckBoxToChosenKind()
        updatePanels()
    }

    private fun updatePanels() {
        currentPluginPanel.visible(pluginKindWillBeSwitchedAfterRestart)
        pluginTypeChooserPanel.visible(!pluginKindWillBeSwitchedAfterRestart)
    }

    private fun updateCheckBoxToChosenKind() {
        checkBox.isSelected = chosenKind == KotlinPluginMode.K2
    }

    private fun suggestRestart() {
        val application = ApplicationManager.getApplication() as ApplicationEx

        val result = Messages.showOkCancelDialog(
            IdeBundle.message("dialog.message.must.be.restarted.for.changes.to.take.effect", productName),
            IdeBundle.message("dialog.title.restart.required"),
            IdeBundle.message("button.now", if (application.isRestartCapable()) 0 else 1),
            IdeBundle.message("button.later", if (application.isRestartCapable()) 0 else 1),
            Messages.getQuestionIcon()
        )

        if (result == Messages.OK) {
            application.restart(/* exitConfirmed = */ true)
        }
    }

    companion object {
        fun createIfPluginSwitchIsPossible(): KotlinPluginKindSwitcherController? {
            if (!KotlinPluginKindSwitcher.canPluginBeSwitchedByVmOptions()) return null
            return KotlinPluginKindSwitcherController()
        }
    }
}