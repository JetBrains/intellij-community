// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.projectStructure.ModuleSourceRootMap
import org.jetbrains.kotlin.idea.configuration.ui.notifications.ConfigureKotlinNotification
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

object ConfigureKotlinNotificationManager : KotlinSingleNotificationManager<ConfigureKotlinNotification> {
    suspend fun notify(project: Project, excludeModules: List<Module> = emptyList()) {
        val notificationState = readAction {
            ConfigureKotlinNotification.getNotificationState(project, excludeModules)
        }

        withContext(Dispatchers.EDT) {
            notificationState?.let {
                KotlinJ2KOnboardingFUSCollector.logShowConfigureKtNotification(project)
                notify(project, ConfigureKotlinNotification(project, excludeModules, it))
            }
        }
    }

    fun getVisibleNotifications(project: Project): Array<out ConfigureKotlinNotification> {
        return NotificationsManager.getNotificationsManager().getNotificationsOfType(ConfigureKotlinNotification::class.java, project)
    }

    fun expireOldNotifications(project: Project) {
        expireOldNotifications(project, ConfigureKotlinNotification::class)
    }
}

interface KotlinSingleNotificationManager<in T : Notification> {
    fun notify(project: Project, notification: T) {
        if (!expireOldNotifications(project, notification::class, notification)) {
            notification.notify(project)
        }
    }

    fun expireOldNotifications(project: Project, notificationClass: KClass<out T>, notification: T? = null): Boolean {
        val notificationsManager = NotificationsManager.getNotificationsManager()
        var isNotificationExists = false

        val notifications = notificationsManager.getNotificationsOfType(notificationClass.java, project)
        for (oldNotification in notifications) {
            if (oldNotification == notification) {
                isNotificationExists = true
            } else {
                oldNotification?.expire()
            }
        }
        return isNotificationExists
    }
}

private val checkInProgress = AtomicBoolean(false)

fun checkHideNonConfiguredNotifications(project: Project) {
    if (checkInProgress.get()) return
    val notification = ConfigureKotlinNotificationManager.getVisibleNotifications(project).firstOrNull() ?: return

    ApplicationManager.getApplication().executeOnPooledThread {
        if (!checkInProgress.compareAndSet(false, true)) return@executeOnPooledThread

        DumbService.getInstance(project).waitForSmartMode()
        val moduleSourceRootMap = ModuleSourceRootMap(project)

        if (notification.notificationState.debugProjectName != project.name) {
            logger<ConfigureKotlinNotificationManager>().error("Bad notification check for project: ${project.name}\n${notification.notificationState}")
        }

        val hideNotification =
            if (!isUnitTestMode()) {
                try {
                    val moduleSourceRootGroups = notification.notificationState.notConfiguredModules
                        .mapNotNull { ModuleManager.getInstance(project).findModuleByName(it) }
                        .map { moduleSourceRootMap.getWholeModuleGroup(it) }
                    moduleSourceRootGroups.none(::isNotConfiguredNotificationRequired)
                } catch (e: IndexNotReadyException) {
                    checkInProgress.set(false)
                    ApplicationManager.getApplication().invokeLater {
                        checkHideNonConfiguredNotifications(project)
                    }
                    return@executeOnPooledThread
                }
            } else {
                true
            }

        if (hideNotification) {
            ApplicationManager.getApplication().invokeLater {
                ConfigureKotlinNotificationManager.expireOldNotifications(project)
                checkInProgress.set(false)
            }
        } else {
            checkInProgress.set(false)
        }
    }
}
