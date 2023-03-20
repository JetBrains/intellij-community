package com.intellij.refactoring.detector.semantic.diff

import com.intellij.openapi.util.Key
import org.jetbrains.research.refactorinsight.common.data.RefactoringEntry

internal val REFACTORING_ENTRY = Key.create<RefactoringEntry>("Diff.SEMANTIC.REFACTORING_ENTRY")
