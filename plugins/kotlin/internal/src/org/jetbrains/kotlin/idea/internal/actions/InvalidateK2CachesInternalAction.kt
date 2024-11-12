// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.internal.actions

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.icons.AllIcons
import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.analysis.api.platform.analysisMessageBus
import org.jetbrains.kotlin.analysis.api.platform.modification.KotlinModificationTopics
import org.jetbrains.kotlin.analysis.low.level.api.fir.LLFirInternals
import org.jetbrains.kotlin.analysis.low.level.api.fir.projectStructure.LLFirBuiltinsSessionFactory
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.projectStructure.ProjectStructureProviderService
import org.jetbrains.kotlin.idea.util.application.isApplicationInternalMode
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import javax.swing.JComponent

internal class InvalidateK2CachesInternalAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val invalidationMode = InvalidateCachesDialog.show() ?: return
        invalidateCaches(project, invalidationMode)
        DaemonCodeAnalyzer.getInstance(project).restart()

        showNotificationAboutInvalidatedCaches(project, invalidationMode)
    }

    @OptIn(LLFirInternals::class)
    @Suppress("TestOnlyProblems")
    private fun invalidateCaches(project: Project, invalidationMode: InvalidationMode) = runWriteAction {
        if (invalidationMode.invalidateSources) {
            ProjectStructureProviderService.getInstance(project).incOutOfBlockModificationCount()
            project.analysisMessageBus.apply {
                syncPublisher(KotlinModificationTopics.GLOBAL_SOURCE_OUT_OF_BLOCK_MODIFICATION).onModification()
                syncPublisher(KotlinModificationTopics.GLOBAL_SCRIPT_MODULE_STATE_MODIFICATION).onModification()
            }
        }
        if (invalidationMode.invalidateLibraries) {
            LLFirBuiltinsSessionFactory.getInstance(project).clearForTheNextTest()
            JavaLibraryModificationTracker.incModificationCount(project)
            project.analysisMessageBus.apply {
                syncPublisher(KotlinModificationTopics.GLOBAL_MODULE_STATE_MODIFICATION).onModification()
            }
        }
    }

    private fun showNotificationAboutInvalidatedCaches(project: Project, invalidationMode: InvalidationMode) {
        val content: @NlsContexts.NotificationContent String = when (invalidationMode) {
            InvalidationMode.OnlySourceCaches -> KotlinInternalBundle.message("notification.content.source.caches")
            InvalidationMode.LibraryAndSourceCaches -> KotlinInternalBundle.message("notification.content.source.library.caches")
        }
        val notification = Notification(
            "Internal Kotlin Plugin Actions",
            KotlinInternalBundle.message("notification.content.kotlin.internal.resolution.caches.were.invalidated.title"),
            content,
            NotificationType.INFORMATION
        ).apply {
            icon = KotlinIcons.FIR
        }

        Notifications.Bus.notify(notification, project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.project != null
                    && isApplicationInternalMode()
                    && KotlinPluginModeProvider.isK2Mode()
    }
}

private class InvalidateCachesDialog : DialogWrapper(true) {
    private var result: InvalidationMode? = null

    init {
        init()
        title = KotlinInternalBundle.message("dialog.title.invalidate.caches")
    }

    override fun isResizable(): Boolean {
        return false
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            button(KotlinInternalBundle.message("button.source.caches")) {
                result = InvalidationMode.OnlySourceCaches
                close(OK_EXIT_CODE)
            }.applyToComponent {
                icon = AllIcons.Actions.ModuleDirectory
                toolTipText = KotlinInternalBundle.message("invalidate.source.caches.tooltip")
            }
            button(KotlinInternalBundle.message("button.source.library.caches")) {
                result = InvalidationMode.LibraryAndSourceCaches
                close(OK_EXIT_CODE)
            }.applyToComponent {
                icon = AllIcons.Nodes.Library
                toolTipText = KotlinInternalBundle.message("invalidate.source.library.caches.tooltip")
            }
        }
    }

    override fun createSouthPanel(): JComponent = panel {}


    companion object {
        fun show(): InvalidationMode? {
            val dialog = InvalidateCachesDialog()
            if (!dialog.showAndGet()) return null
            return dialog.result
        }
    }
}


private enum class InvalidationMode(
    val invalidateSources: Boolean,
    val invalidateLibraries: Boolean,
) {
    LibraryAndSourceCaches(invalidateSources = true, invalidateLibraries = true),
    OnlySourceCaches(invalidateSources = true, invalidateLibraries = false),
}
