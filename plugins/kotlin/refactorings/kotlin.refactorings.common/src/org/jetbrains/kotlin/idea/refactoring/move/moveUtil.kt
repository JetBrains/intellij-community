// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.move

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.UserDataProperty

var KtFile.allElementsToMove: List<PsiElement>? by UserDataProperty(Key.create("SCOPE_TO_MOVE"))