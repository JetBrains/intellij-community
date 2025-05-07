// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.quickfix

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.core.CoreBundle
import com.intellij.model.SideEffectGuard
import com.intellij.model.SideEffectGuard.Companion.checkSideEffectAllowed
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.inspections.PyInterpreterInspection
import com.jetbrains.python.inspections.requirement.RunningPackagingTasksListener
import com.jetbrains.python.packaging.PyPackageManagerUI
import com.jetbrains.python.packaging.PyRequirement
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.adminPermissionsNeeded
import com.jetbrains.python.ui.PyUiUtil
import org.jetbrains.annotations.Nls

internal class PyInstallRequirementsFix(
  private val quickFixName: @Nls String?,
  private val module: Module,
  private val sdk: Sdk,
  private val unsatisfied: List<PyRequirement>,
  private val installOptions: List<String> = emptyList(),
  private val listener: RunningPackagingTasksListener? = null,
) : LocalQuickFix {

  override fun getFamilyName(): @Nls String = quickFixName ?: PyPsiBundle.message( "QFIX.NAME.install.requirements", unsatisfied.size)

  override fun startInWriteAction(): Boolean = false

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (hasAdminPermissionsAndConfigureInterpreter(project, descriptor, sdk)) return
    checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)
    PyUiUtil.clearFileLevelInspectionResults(descriptor.psiElement.containingFile)
    installRequirements(project, unsatisfied, descriptor)
  }

  private fun installRequirements(project: Project, requirements: List<PyRequirement>, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile ?: return
    val listener = listener ?: RunningPackagingTasksListener(module)
    val ui = PyPackageManagerUI(project, sdk, listener)
    ui.install(requirements, installOptions)
    DaemonCodeAnalyzer.getInstance(project).restart(file)
  }

  private fun hasAdminPermissionsAndConfigureInterpreter(
    project: Project,
    descriptor: ProblemDescriptor,
    sdk: Sdk,
  ): Boolean {
    if (PythonSdkUtil.isRemote(sdk) || !sdk.adminPermissionsNeeded()) return false

    val answer = askToConfigureInterpreter(project, sdk)
    if (answer == Messages.YES) {
      PyInterpreterInspection.ConfigureInterpreterFix().applyFix(project, descriptor)
    }

    /**
     * @return `true` if:
     * - The user agreed to configure the interpreter (`Messages.YES`).
     * - The user canceled the dialog (`Messages.CANCEL`).
     * - The response is indeterminate or invalid (`-1`).
     */
    return answer == Messages.YES || answer == Messages.CANCEL || answer == -1
  }

  /**
   * Displays a dialog asking the user to configure an interpreter, proceed with installation, or cancel the operation.
   *
   * @param project The project in which the dialog is displayed.
   * @param sdk The SDK for which the operation is being prompted.
   * @return An integer representing the user's choice:
   *         <ul>
   *             <li><code>0</code>: The user selected the "Configure" option.</li>
   *             <li><code>1</code>: The user selected the "Install Anyway" option.</li>
   *             <li><code>2</code>: The user selected the "Cancel" option.</li>
   *         </ul>
   */
  private fun askToConfigureInterpreter(project: Project, sdk: Sdk): Int {
    val sdkName = StringUtil.shortenTextWithEllipsis(sdk.name, SDK_NAME_MAX_LENGTH, 0)
    val text = PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required.description", sdkName)
    val options = arrayOf(
      PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required.button.configure"),
      PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required.button.install.anyway"),
      CoreBundle.message("button.cancel")
    )
    return Messages.showIdeaMessageDialog(
      project,
      text,
      PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required"),
      options,
      DEFAULT_OPTION_INDEX,
      Messages.getWarningIcon(),
      null
    )
  }

  companion object {
    private const val DEFAULT_OPTION_INDEX = 0
    private const val SDK_NAME_MAX_LENGTH = 25
  }
}