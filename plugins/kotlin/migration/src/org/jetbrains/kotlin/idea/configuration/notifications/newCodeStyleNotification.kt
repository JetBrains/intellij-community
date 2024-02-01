// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.application.options.CodeStyle
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinIcons
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
        return
    }
    if (codeStyle != null) {
        // Code style is already explicitly specified
        return
    }

    val propertiesComponent = PropertiesComponent.getInstance()
    val notificationShowCount = propertiesComponent.getInt(NOTIFICATION_PROPERTIES_KEY, 0)
    if (notificationShowCount >= MAX_SHOW_NOTIFICATION) {
        return
    }
    propertiesComponent.setValue(NOTIFICATION_PROPERTIES_KEY, notificationShowCount + 1, 0)

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