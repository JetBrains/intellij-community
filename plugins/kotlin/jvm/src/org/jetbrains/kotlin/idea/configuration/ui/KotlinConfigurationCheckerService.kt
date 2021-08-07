// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration.ui

import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.configuration.getModulesWithKotlinFiles
import org.jetbrains.kotlin.idea.configuration.notifyOutdatedBundledCompilerIfNecessary
import org.jetbrains.kotlin.idea.core.KotlinPluginDisposable
import org.jetbrains.kotlin.idea.project.getAndCacheLanguageLevelByDependencies
import org.jetbrains.kotlin.idea.util.application.getServiceSafe
import java.util.concurrent.atomic.AtomicInteger

class KotlinConfigurationCheckerStartupActivity : StartupActivity.Background {
    override fun runActivity(project: Project) {
        NotificationsConfiguration.getNotificationsConfiguration()
            .register(
                KotlinConfigurationCheckerService.CONFIGURE_NOTIFICATION_GROUP_ID,
                NotificationDisplayType.STICKY_BALLOON, true
            )

        val connection = project.messageBus.connect(KotlinPluginDisposable.getInstance(project))
        connection.subscribe(ProjectDataImportListener.TOPIC, ProjectDataImportListener {
            notifyOutdatedBundledCompilerIfNecessary(project)
        })

        DumbService.getInstance(project).runWhenSmart {
            KotlinConfigurationCheckerService.getInstance(project).performProjectPostOpenActions()
        }
    }
}

class KotlinConfigurationCheckerService(val project: Project) {
    private val syncDepth = AtomicInteger()

    fun performProjectPostOpenActions() {
        val task = object : Task.Backgroundable(project, KotlinJvmBundle.message("configure.kotlin.language.settings"), false) {
            override fun run(indicator: ProgressIndicator) {
                val ktModules = getModulesWithKotlinFiles(project)
                indicator.isIndeterminate = false
                for ((idx, module) in ktModules.withIndex()) {
                    if (project.isDisposed) return
                    indicator.fraction = 1.0 * idx / ktModules.size
                    runReadAction {
                        if (module.isDisposed) return@runReadAction
                        indicator.text2 = KotlinJvmBundle.message("configure.kotlin.language.settings.0.module", module.name)
                        module.getAndCacheLanguageLevelByDependencies()
                    }
                }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    val isSyncing: Boolean get() = syncDepth.get() > 0

    fun syncStarted() {
        syncDepth.incrementAndGet()
    }

    fun syncDone() {
        syncDepth.decrementAndGet()
    }

    companion object {
        const val CONFIGURE_NOTIFICATION_GROUP_ID = "Configure Kotlin in Project"

        fun getInstance(project: Project): KotlinConfigurationCheckerService = project.getServiceSafe()

    }
}
