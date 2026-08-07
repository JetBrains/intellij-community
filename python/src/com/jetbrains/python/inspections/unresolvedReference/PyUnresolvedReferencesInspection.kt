// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("CompanionObjectInExtension")

package com.jetbrains.python.inspections.unresolvedReference

import com.intellij.codeInspection.InspectionProfile
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.getEffectiveLanguageLevel
import com.jetbrains.python.inspections.PyInspection
import com.jetbrains.python.inspections.PyInspectionVisitor
import org.intellij.lang.annotations.Pattern

/**
 * Marks references that fail to resolve.
 */
class PyUnresolvedReferencesInspection : PyInspection() {
  @JvmField
  var ignoredIdentifiers: List<String> = ArrayList()

  @JvmField
  var strictClassAttributes: Boolean = true

  @JvmField
  var strictInstanceAttributes: Boolean = true

  @Pattern(VALID_ID_PATTERN)
  override fun getID(): String = "PyUnresolvedReferences"

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    val context = PyInspectionVisitor.getContext(session)
    if (context.usesExternalTypeEngine) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    val visitor = PyUnresolvedReferencesVisitor(holder,
                                                ignoredIdentifiers,
                                                context,
                                                getEffectiveLanguageLevel(session.file),
                                                strictClassAttributes,
                                                strictInstanceAttributes,
                                                PyUnresolvedReferenceQuickFixesImpl)
    // buildVisitor() will be called on injected files in the same session - don't overwrite if we already have one
    val existingVisitor = session.getUserData(KEY)
    if (existingVisitor == null) {
      session.putUserData(KEY, visitor)
    }
    return visitor
  }

  override fun inspectionFinished(session: LocalInspectionToolSession, holder: ProblemsHolder) {
    val visitor = session.getUserData(KEY)!!
    runReadActionBlocking {
      visitor.addInstallAllImports()
    }
    session.putUserData(KEY, null)
  }

  override fun getOptionsPane(): OptPane = OptPane.pane(
    OptPane.stringList("ignoredIdentifiers",
                       PyPsiBundle.message("INSP.unresolved.refs.ignore.references.label")),
    OptPane.checkbox("strictClassAttributes",
                     PyPsiBundle.message("INSP.unresolved.refs.strict.class.attr.option")),
    OptPane.checkbox("strictInstanceAttributes",
                     PyPsiBundle.message("INSP.unresolved.refs.strict.instance.attr.option")))

  companion object {
    private val KEY = Key.create<PyUnresolvedReferencesVisitor>("PyUnresolvedReferencesInspection.Visitor")

    private val SHORT_NAME_KEY = Key.create<PyUnresolvedReferencesInspection>(PyUnresolvedReferencesInspection::class.java.simpleName)

    fun getInstance(element: PsiElement?): PyUnresolvedReferencesInspection? {
      element ?: return null

      val inspectionProfile: InspectionProfile = InspectionProjectProfileManager.getInstance(element.project).currentProfile
      return inspectionProfile.getUnwrappedTool(SHORT_NAME_KEY.toString(), element) as PyUnresolvedReferencesInspection?
    }
  }
}
