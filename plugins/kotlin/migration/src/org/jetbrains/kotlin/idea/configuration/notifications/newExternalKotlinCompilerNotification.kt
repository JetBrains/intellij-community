// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration.notifications

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.BrowseNotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.projectStructure.ExternalCompilerVersionProvider
import org.jetbrains.kotlin.idea.base.util.containsNonScriptKotlinFile
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.migration.KotlinMigrationBundle
import java.util.concurrent.Callable

@VisibleForTesting
const val LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME = "kotlin.updates.whats.new.shown.for"

class ExternalKotlinCompilerProjectDataImportListener(private val project: Project) : ProjectDataImportListener {
    override fun onImportFinished(projectPath: String?) {
        showNewKotlinCompilerAvailableNotificationIfNeeded(project)
    }
}

fun showNewKotlinCompilerAvailableNotificationIfNeeded(project: Project) {
    val bundledCompilerVersion = KotlinPluginLayout.standaloneCompilerVersion
    if (!bundledCompilerVersion.isRelease) return

    ReadAction.nonBlocking(Callable {
        if (!project.containsNonScriptKotlinFile()) return@Callable null

        fun findExternalVersion(): KotlinVersion {
            return (ExternalCompilerVersionProvider.findLatest(project) ?: KotlinPluginLayout.standaloneCompilerVersion).kotlinVersion
        }

        val bundledKotlinVersion = bundledCompilerVersion.kotlinVersion
        if (!newExternalKotlinCompilerShouldBePromoted(bundledKotlinVersion, ::findExternalVersion)) return@Callable null
        bundledKotlinVersion
    })
        .inSmartMode(project)
        .coalesceBy(bundledCompilerVersion)
        .expireWith(KotlinPluginDisposable.getInstance(project))
        .finishOnUiThread(ModalityState.defaultModalityState()) { bundledKotlinVersion ->
            if (bundledKotlinVersion == null) return@finishOnUiThread

            // Show the notification once for a project&version (checked in 'newExternalKotlinCompilerShouldBePromoted()')
            disableNewKotlinCompilerAvailableNotification(bundledKotlinVersion)

            NotificationGroupManager.getInstance()
                .getNotificationGroup("kotlin.external.compiler.updates")
                .createNotification(
                    KotlinMigrationBundle.message("kotlin.external.compiler.updates.notification.content.0", bundledKotlinVersion),
                    NotificationType.INFORMATION,
                )
                .setSuggestionType(true)
                .addAction(createWhatIsNewAction(bundledKotlinVersion))
                .setIcon(KotlinIcons.SMALL_LOGO)
                .setImportant(true)
                .notify(project)
        }
        .submit(AppExecutorUtil.getAppExecutorService())
}

fun disableNewKotlinCompilerAvailableNotification(version: KotlinVersion): Unit = runWriteAction {
    PropertiesComponent.getInstance().setValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME, version.toString())
}

@get:VisibleForTesting
val KotlinVersion.dropHotfixPart: KotlinVersion
    get() = KotlinVersion(
        major = major,
        minor = minor,
        patch = patch.div(10).let { if (it == 1) 0 else it } * 10,
    )

@VisibleForTesting
fun newExternalKotlinCompilerShouldBePromoted(
    bundledCompilerVersion: KotlinVersion,
    externalCompilerVersion: () -> KotlinVersion?,
): Boolean {
    val downgradedBundledKotlinCompilerVersion = bundledCompilerVersion.dropHotfixPart
    val lastBundledVersion = findLastBundledCompilerVersion()
    if (lastBundledVersion != null && lastBundledVersion >= downgradedBundledKotlinCompilerVersion) return false

    val externalKotlinCompilerVersion = externalCompilerVersion() ?: return false
    return externalKotlinCompilerVersion < downgradedBundledKotlinCompilerVersion
}

private fun findLastBundledCompilerVersion(): KotlinVersion? {
    val lastVersionValue = runReadAction {
        PropertiesComponent.getInstance().getValue(LAST_BUNDLED_KOTLIN_COMPILER_VERSION_PROPERTY_NAME)
    } ?: return null

    return IdeKotlinVersion.opt(lastVersionValue)?.kotlinVersion
}

private fun createWhatIsNewAction(kotlinVersion: KotlinVersion): AnAction = BrowseNotificationAction(
    KotlinMigrationBundle.message("kotlin.external.compiler.updates.notification.learn.what.is.new.action"),
    whatIsNewPageUrl(kotlinVersion),
)

private fun whatIsNewPageUrl(kotlinVersion: KotlinVersion): String =
    "https://kotlinlang.org/docs/whatsnew${kotlinVersion.whatIsNewPageVersion}.html?utm_source=ide&utm_medium=release-notification&utm_campaign=${kotlinVersion.campaignVersion}-release"

@get:VisibleForTesting
val KotlinVersion.whatIsNewPageVersion: String
    get() = buildString {
        append(major)
        append(minor)
        val majorPartOfPath = patch / 10
        if (majorPartOfPath > 1) {
            append(majorPartOfPath)
            append('0')
        }
    }

@get:VisibleForTesting
val KotlinVersion.campaignVersion: String get() = "$major-$minor-$patch"