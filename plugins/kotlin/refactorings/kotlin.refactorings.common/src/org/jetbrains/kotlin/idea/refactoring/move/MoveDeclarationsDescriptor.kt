// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.move.MoveCallback

class MoveDeclarationsDescriptor @JvmOverloads constructor(
  val project: Project,
  val moveSource: KotlinMoveSource,
  val moveTarget: KotlinMoveTarget,
  val delegate: KotlinMoveDeclarationDelegate,
  val searchInCommentsAndStrings: Boolean = true,
  val searchInNonCode: Boolean = true,
  val deleteSourceFiles: Boolean = false,
  val moveCallback: MoveCallback? = null,
  val openInEditor: Boolean = false,
  val allElementsToMove: List<PsiElement>? = null,
  val analyzeConflicts: Boolean = true,
  val searchReferences: Boolean = true
)