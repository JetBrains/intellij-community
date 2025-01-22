// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction
import com.intellij.idea.ActionsBundle
import com.intellij.model.SideEffectGuard
import com.intellij.model.SideEffectGuard.Companion.checkSideEffectAllowed
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager.Companion.getInstance
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyPackageRequirementsInspection
import com.jetbrains.python.packaging.syncWithImports
import org.jetbrains.annotations.Nls

internal class IgnoreRequirementFix(private val packageNames: Set<String>) : LocalQuickFix {

  override fun getFamilyName(): @Nls String = PyPsiBundle.message("QFIX.NAME.ignore.requirements", packageNames.size)

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    descriptor.psiElement?.let { element ->
      checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)

      val inspection = PyPackageRequirementsInspection.getInstance(element)
      if (inspection == null) return

      val packagesToIgnore = packageNames - inspection.getIgnoredPackages()
      if (packagesToIgnore.isEmpty()) return
      inspection.setIgnoredPackages(packagesToIgnore)

      val profileManager = getInstance(project)
      profileManager.fireProfileChanged()

      val notificationMessage = when {
        packagesToIgnore.size == 1 -> PyPsiBundle.message("INSP.package.requirements.requirement.has.been.ignored", packagesToIgnore.first())
        else -> PyPsiBundle.message("INSP.package.requirements.requirements.have.been.ignored")
      }

      val notification = BALLOON_NOTIFICATIONS.createNotification(notificationMessage, NotificationType.INFORMATION)
      notification.addAction(createUndoAction(inspection, packagesToIgnore, profileManager))
      notification.addAction(createEditSettingsAction(project))
      notification.notify(project)
    }
  }

  private fun createUndoAction(
    inspection: PyPackageRequirementsInspection,
    packagesToIgnore: Set<String>,
    profileManager: ProjectInspectionProfileManager,
  ): NotificationAction =
    NotificationAction.createSimpleExpiring(ActionsBundle.actionText("action.\$Undo.text")) {
      val packagesToRestore = inspection.getIgnoredPackages() - packagesToIgnore
      inspection.setIgnoredPackages(packagesToRestore.toSet())
      profileManager.fireProfileChanged()
    }

  private fun createEditSettingsAction(project: Project): NotificationAction =
    NotificationAction.createSimpleExpiring(PyBundle.message("notification.action.edit.settings")) {
      val profile = getInstance(project).currentProfile
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


class PyGenerateRequirementsFileQuickFix(private val myModule: Module) : LocalQuickFix {

  override fun getFamilyName(): @Nls String = PyPsiBundle.message("QFIX.add.imported.packages.to.requirements")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    syncWithImports(myModule)
  }

  override fun startInWriteAction(): Boolean = false
}