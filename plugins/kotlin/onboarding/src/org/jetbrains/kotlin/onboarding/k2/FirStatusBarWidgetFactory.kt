// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.onboarding.k2

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager
import com.intellij.ui.ClickListener
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2UserTracker
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities

private class FirStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ID

    override fun getDisplayName(): String = DISPLAY_NAME

    override fun isAvailable(project: Project): Boolean {
        return KotlinPluginModeProvider.isK2Mode() && Registry.`is`(REGISTRY_KEY, /* defaultValue = */ false)
    }

    override fun createWidget(project: Project): StatusBarWidget = Widget(project)

    companion object {
        const val ID = "kotlin.k2.mode"
    }
}

@NlsSafe
private const val DISPLAY_NAME = "Kotlin plugin K2 mode"

private class FirStatusBarWidgetListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
        if (!KotlinPluginModeProvider.isK2Mode() || value.key != REGISTRY_KEY) return
        ApplicationManager.getApplication().invokeLater {
            for (project in ProjectManager.getInstance().openProjects) {
                if (project.isDisposed || project.isDefault) continue
                val manager = project.service<StatusBarWidgetsManager>()
                manager.updateWidget(FirStatusBarWidgetFactory::class.java)
            }
        }
    }
}

private const val REGISTRY_KEY: String = "kotlin.k2.show.fir.statusbar.icon"

private class Widget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget.IconPresentation {
    private var icon: JLabel? = null

    override fun ID(): String = FirStatusBarWidgetFactory.ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun getTooltipText(): String = DISPLAY_NAME
    override fun getIcon(): Icon = KotlinIcons.FIR
    override fun getComponent(): JComponent {
        val icon = JLabel()
            .apply {
                object : ClickListener() {
                    override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            K2UserTracker.getInstance().forceShowFeedbackForm(project)
                        }
                        return true
                    }
                }.installOn(this)
            }.apply {
                toolTipText = DISPLAY_NAME
                icon = KotlinIcons.FIR
            }
        this.icon = icon
        return icon
    }

    override fun dispose() {
        icon = null
    }
}