// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.python.requirements.parser.psi.RequirementsTypes
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.requirement.PyRequirementEnvMarkerType

/**
 * Flags environment-marker names that are not one of [PyRequirementEnvMarkerType] (PEP 508), e.g. a
 * typo like `pyton_version`. Reported as a warning rather than an error so requirements using marker
 * names outside the enum still lex/parse and install normally.
 */
class RequirementsEnvMarkerAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element.elementType != RequirementsTypes.ENV_MARKER_NAME) return
    val name = element.text
    if (PyRequirementEnvMarkerType.entries.any { it.name.equals(name, ignoreCase = true) }) return
    holder.newAnnotation(HighlightSeverity.WARNING, PyBundle.message("python.requirements.marker.unknown.name", name))
      .range(element)
      .create()
  }
}
