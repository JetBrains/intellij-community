// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.update

import com.intellij.ide.util.RunOnceUtil
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinPluginUpdater
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.util.application.isHeadlessEnvironment
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.isKotlinFileType

class KotlinPluginUpdateCheckerStartActivity : ProjectPostStartupActivity {
    init {
        if (isUnitTestMode() || isHeadlessEnvironment()) {
            throw ExtensionNotApplicableException.create()
        }
    }

    override suspend fun execute(project: Project) {
        val documentListener: DocumentListener = object : DocumentListener {
            override fun documentChanged(e: DocumentEvent) {
                FileDocumentManager.getInstance().getFile(e.document)?.let { virtualFile ->
                    if (virtualFile.isKotlinFileType() && virtualFile.isInLocalFileSystem) {
                        KotlinPluginUpdater.getInstance().pluginUsed()
                        showEapAdvertisementNotification()
                    }
                }
            }
        }

        EditorFactory.getInstance().eventMulticaster
            .addDocumentListener(documentListener, KotlinPluginDisposable.getInstance(project))
    }
}

private fun showEapAdvertisementNotification() {
    val compilerVersion = KotlinPluginLayout.ideCompilerVersion
    if (compilerVersion.kotlinVersion != KotlinVersion(major = 1, minor = 7, patch = 20)) return
    if (compilerVersion.kind != IdeKotlinVersion.Kind.Beta(number = 1)) return

    RunOnceUtil.runOnceForApp("kotlin.eap.advertisement.was.shown.once") {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Kotlin EAP")
            .createNotification(
                KotlinPluginUpdaterBundle.message("kotlin.eap.advertisement.notification.title"),
                KotlinPluginUpdaterBundle.message("kotlin.eap.advertisement.notification.text"),
                NotificationType.INFORMATION,
            )
            .addAction(
                BrowseNotificationAction(
                    KotlinPluginUpdaterBundle.message("kotlin.eap.advertisement.notification.action"),
                    KotlinPluginUpdaterBundle.message("kotlin.eap.advertisement.notification.link"),
                )
            )
            .setSuggestionType(true)
            .setIcon(KotlinIcons.SMALL_LOGO)
            .setImportant(true)
            .notify(null)
    }
}