// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.actions

import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProfileManager

/**
 * Utility for enabling/disabling Python typing-related inspections in the user's current inspection profile.
 */
object PyTypingInspectionsUtil {

  private const val UNRESOLVED_REFERENCES_ID = "PyUnresolvedReferencesInspection"

  private val TYPING_INSPECTION_IDS = listOf(
    "PyArgumentListInspection",
    "PyAssertTypeInspection",
    "PyCallingNonCallableInspection",
    "PyClassVarInspection",
    "PyDataclassInspection",
    "PyDocstringTypesInspection",
    "PyFinalInspection",
    "PyInvalidCastInspection",
    "PyNamedTupleInspection",
    "PyNewStyleGenericSyntaxInspection",
    "PyNewTypeInspection",
    "PyOverloadsInspection",
    "PyOverridesInspection",
    "PyProtocolInspection",
    "PyStubPackagesAdvertiser",
    "PyStubPackagesCompatibilityInspection",
    "PyTypeAliasRedeclarationInspection",
    "PyTypeCheckerInspection",
    "PyTypeHintsInspection",
    "PyTypedDictInspection",
  )

  /**
   * Sets the enabled state of all Python typing-related inspections in the current inspection profile.
   *
   * @param project the project whose inspection profile should be modified
   * @param enabled true to enable inspections, false to disable them
   */
  @JvmStatic
  fun setTypingInspectionsEnabled(project: Project, enabled: Boolean) {
    val profile = InspectionProfileManager.getInstance(project).currentProfile

    profile.modifyProfile { model ->
      for (inspectionId in TYPING_INSPECTION_IDS) {
        if (model.getToolsOrNull(inspectionId, project) != null) {
          model.setToolEnabled(inspectionId, enabled, project)
        }
      }
    }
  }

  /**
   * Disables all Python typing-related inspections in the current inspection profile.
   */
  @JvmStatic
  fun disableTypingInspections(project: Project) {
    setTypingInspectionsEnabled(project, false)
  }

  /**
   * Enables all Python typing-related inspections in the current inspection profile.
   */
  @JvmStatic
  fun enableTypingInspections(project: Project) {
    setTypingInspectionsEnabled(project, true)
  }

  /**
   * Checks if any of the typing-related inspections are currently enabled.
   *
   * @param project the project whose inspection profile should be checked
   * @return true if at least one typing inspection is enabled, false if all are disabled
   */
  @JvmStatic
  fun areAnyTypingInspectionsEnabled(project: Project): Boolean {
    val profile = InspectionProfileManager.getInstance(project).currentProfile

    for (inspectionId in TYPING_INSPECTION_IDS) {
      val tools = profile.getToolsOrNull(inspectionId, project)
      if (tools != null && tools.isEnabled) {
        return true
      }
    }
    return false
  }

  /**
   * Checks if all typing-related inspections are currently enabled.
   *
   * @param project the project whose inspection profile should be checked
   * @return true if all typing inspections are enabled, false otherwise
   */
  @JvmStatic
  fun areAllTypingInspectionsEnabled(project: Project): Boolean {
    val profile = InspectionProfileManager.getInstance(project).currentProfile

    for (inspectionId in TYPING_INSPECTION_IDS) {
      val tools = profile.getToolsOrNull(inspectionId, project)
      if (tools != null && !tools.isEnabled) {
        return false
      }
    }
    return true
  }
}
