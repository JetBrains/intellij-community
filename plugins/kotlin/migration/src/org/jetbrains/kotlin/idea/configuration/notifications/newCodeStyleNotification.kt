// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.application.options.CodeStyle
import com.intellij.ide.IdeCoreBundle
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.*
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.formatter.KotlinStyleGuideCodeStyle
import org.jetbrains.kotlin.idea.formatter.ProjectCodeStyleImporter
import org.jetbrains.kotlin.idea.formatter.kotlinCodeStyleDefaults
import org.jetbrains.kotlin.idea.migration.KotlinMigrationBundle

@ApiStatus.Internal
fun notifyKotlinStyleUpdateIfNeeded(project: Project) {
    if (CodeStyle.getSettings(project).kotlinCodeStyleDefaults() == KotlinStyleGuideCodeStyle.CODE_STYLE_ID) return
    if (SuppressKotlinCodeStyleComponent.getInstance(project).state.disableForAll) {
        return
    }

    if (project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == java.lang.Boolean.TRUE) {
        // project has been just created, switch to new Kotlin code style automatically
        applyKotlinCodeStyleSetting(project)
        return
    }

    NotificationGroupManager.getInstance()
        .getNotificationGroup("Update Kotlin code style")
        .createNotification(
            KotlinMigrationBundle.message("configuration.kotlin.code.style"),
            KotlinMigrationBundle.htmlMessage("configuration.notification.update.code.style.to.official"),
            NotificationType.WARNING,
        )
        .setSuggestionType(true)
        .addAction(applyCodeStyleAction())
        .addAction(dontAskAgainAction())
        .setImportant(true)
        .setIcon(KotlinIcons.SMALL_LOGO)
        .notify(project)
}

private fun dontAskAgainAction() = NotificationAction.createExpiring(
    IdeCoreBundle.message("dialog.options.do.not.ask")
) { e, _ ->
    e.project?.takeIf { !it.isDisposed }?.let { project ->
        runWriteAction {
            SuppressKotlinCodeStyleComponent.getInstance(project).state.disableForAll = true
        }
    }
}

private fun applyCodeStyleAction() = NotificationAction.createExpiring(
    KotlinMigrationBundle.message("configuration.apply.new.code.style")
) { e, _ ->
    e.project
        ?.takeIf { !it.isDisposed }
        ?.let { project -> applyKotlinCodeStyleSetting(project) }
}

private fun applyKotlinCodeStyleSetting(project: Project) {
    runWriteAction {
        ProjectCodeStyleImporter.apply(project, KotlinStyleGuideCodeStyle.INSTANCE)
    }
}

class SuppressKotlinCodeStyleState : BaseState() {
    var disableForAll by property(false)
}

@Service(Service.Level.PROJECT)
@State(name = "SuppressKotlinCodeStyleNotification")
class SuppressKotlinCodeStyleComponent : SimplePersistentStateComponent<SuppressKotlinCodeStyleState>(SuppressKotlinCodeStyleState()) {
    companion object {
        fun getInstance(project: Project): SuppressKotlinCodeStyleComponent = project.service()
    }
}
