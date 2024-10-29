// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.refactoring.suggested

import org.jetbrains.kotlin.idea.refactoring.suggested.KotlinSuggestedRefactoringSupportBase

class KotlinSuggestedRefactoringSupport: KotlinSuggestedRefactoringSupportBase() {
    override val availability = KotlinSuggestedRefactoringAvailability(this)
    override val execution = KotlinSuggestedRefactoringExecution(this)
}