// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements

import com.intellij.psi.PsiElement
import com.intellij.python.requirements.parser.PyRequirementParser
import com.jetbrains.python.packaging.PyRequirement

/**
 * Parses the [PyRequirement]s declared in this [RequirementsFile].
 *
 * Lives in the backend module rather than on [RequirementsFile] itself: the PSI file type stays in
 * the shared language module, while [PyRequirement] and the parsing logic belong to the backend.
 */
fun RequirementsFile.requirements(): List<PyRequirement> = requirementPairs().map { it.second }

/**
 * Like [requirements] but pairs every [PyRequirement] with the [PsiElement] it was parsed from.
 */
fun RequirementsFile.requirementPairs(): List<Pair<PsiElement, PyRequirement>> =
  PyRequirementParser.fromPsiElements(children)
