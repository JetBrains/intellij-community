// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.idea.ActionsBundle
import com.intellij.model.SideEffectGuard
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.statistics.PackageRequirementsIdsHolder.Companion.REQUIREMENTS_HAVE_BEEN_IGNORED
import org.jetbrains.annotations.Nls

internal class IgnoreRequirementFix(private val packagesToIgnore: Set<String>) : LocalQuickFix {
  override fun getFamilyName(): @Nls String = PyPsiBundle.message("QFIX.NAME.ignore.requirements", packagesToIgnore.size)
  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val element = descriptor.psiElement ?: return

    SideEffectGuard.Companion.checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)

    val inspection = PyPackageRequirementsInspection.Companion.getInstance(element) ?: return
    inspection.ignoredPackages = (inspection.ignoredPackages + packagesToIgnore).distinct().toMutableList()

    val profileManager = ProjectInspectionProfileManager.Companion.getInstance(project)
    profileManager.fireProfileChanged()

    val notificationMessage = when {
      packagesToIgnore.size == 1 -> PyPsiBundle.message("INSP.package.requirements.requirement.has.been.ignored", packagesToIgnore.first())
      else -> PyPsiBundle.message("INSP.package.requirements.requirements.have.been.ignored")
    }

    val notification = BALLOON_NOTIFICATIONS
      .createNotification(notificationMessage, NotificationType.INFORMATION)
      .setDisplayId(REQUIREMENTS_HAVE_BEEN_IGNORED)
    notification.addAction(createUndoAction(inspection, packagesToIgnore, profileManager))
    notification.addAction(createEditSettingsAction(project))
    notification.notify(project)
  }

  private fun createUndoAction(
    inspection: PyPackageRequirementsInspection,
    packagesToIgnore: Set<String>,
    profileManager: ProjectInspectionProfileManager,
  ): NotificationAction =
    NotificationAction.createSimpleExpiring(ActionsBundle.message("action.\$Undo.text")) {
      inspection.ignoredPackages = (inspection.ignoredPackages - packagesToIgnore).toMutableList()
      profileManager.fireProfileChanged()
    }

  private fun createEditSettingsAction(project: Project): NotificationAction =
    NotificationAction.createSimpleExpiring(PyBundle.message("notification.action.edit.settings")) {
      val profile = ProjectInspectionProfileManager.Companion.getInstance(project).currentProfile
      val toolName = PyPackageRequirementsInspection::class.java.simpleName
      EditInspectionToolsSettingsAction.editToolSettings(project, profile, toolName)
    }

  companion object {
    private const val NOTIFICATION_GROUP_ID = "Package requirements"
    private val BALLOON_NOTIFICATIONS: NotificationGroup = Cancellation.forceNonCancellableSectionInClassInitializer {
      NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
    }
  }
}