// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.application.options.CodeStyle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.projectStructure.RootKindFilter
import org.jetbrains.kotlin.idea.base.projectStructure.matches
import org.jetbrains.kotlin.idea.base.util.isInDumbMode
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.formatter.KotlinOfficialStyleGuide
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.migration.KotlinMigrationBundle

private const val MAX_SHOW_NOTIFICATION = 1
private const val NOTIFICATION_PROPERTIES_KEY = "kotlin.code.style.migration.dialog.show.count"

@ApiStatus.Internal
fun notifyKotlinStyleUpdateIfNeeded(project: Project) {
    val codeStyle = CodeStyle.getSettings(project).kotlinCodeStyleDefaults()
    if (codeStyle == KotlinOfficialStyleGuide.CODE_STYLE_ID) return
    if (project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == java.lang.Boolean.TRUE) {
        // project has been just created, switch to new Kotlin code style automatically
        applyKotlinCodeStyleSetting(project)
    }
}

@Service(Service.Level.PROJECT)
internal class KotlinCodeStyleChangedFileListenerService(val coroutineScope: CoroutineScope)

private fun shouldShowCodeStyleNotification(): Boolean {
    val propertiesComponent = PropertiesComponent.getInstance()
    val notificationShowCount = propertiesComponent.getInt(NOTIFICATION_PROPERTIES_KEY, 0)
    return notificationShowCount < MAX_SHOW_NOTIFICATION
}

private fun increaseShowCodeStyleShowCount() {
    val propertiesComponent = PropertiesComponent.getInstance()
    val notificationShowCount = propertiesComponent.getInt(NOTIFICATION_PROPERTIES_KEY, 0)
    propertiesComponent.setValue(NOTIFICATION_PROPERTIES_KEY, notificationShowCount + 1, 0)
}

class KotlinCodeStyleChangedFileListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode || app.isHeadlessEnvironment) return
        if (!file.nameSequence.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)) return
        val project = source.project
        if (project.isInDumbMode) return
        if (!shouldShowCodeStyleNotification()) return
        val service = project.service<KotlinCodeStyleChangedFileListenerService>()
        service.coroutineScope.launch {
            val shouldDisplayNotification = readAction {
                val psiFile = file.toPsiFile(project) ?: return@readAction false
                if (!RootKindFilter.projectSources.matches(psiFile)) return@readAction false
                CodeStyle.getSettings(psiFile).kotlinCodeStyleDefaults() == null
            }
            if (!shouldDisplayNotification) return@launch
            withContext(Dispatchers.EDT) {
                // Count could have changed since we checked the first time
                if (!shouldShowCodeStyleNotification()) return@withContext
                increaseShowCodeStyleShowCount()

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Update Kotlin code style")
                    .createNotification(
                        KotlinMigrationBundle.message("kotlin.code.style.default.changed"),
                        KotlinMigrationBundle.htmlMessage("kotlin.code.style.default.changed.description"),
                        NotificationType.WARNING,
                    )
                    .setSuggestionType(true)
                    .addAction(readBlogPost())
                    .addAction(dismiss())
                    .setIcon(KotlinIcons.SMALL_LOGO)
                    .notify(project)
            }
        }
    }
}

private fun readBlogPost() = NotificationAction.createSimpleExpiring(
    KotlinMigrationBundle.message("kotlin.code.style.default.changed.action"),
) {
    BrowserUtil.open(KotlinMigrationBundle.message("kotlin.code.style.default.changed.action.url"))
}

private fun dismiss() = NotificationAction.createSimpleExpiring(
    KotlinMigrationBundle.message("kotlin.code.style.default.changed.dismiss"),
) { }

private fun applyKotlinCodeStyleSetting(project: Project) {
    runWriteAction {
        ProjectCodeStyleImporter.apply(project, KotlinStyleGuideCodeStyle.INSTANCE)
    }
}