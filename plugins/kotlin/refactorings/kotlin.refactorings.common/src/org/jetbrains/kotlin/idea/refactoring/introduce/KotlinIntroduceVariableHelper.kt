// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.introduce

import org.jetbrains.kotlin.psi.KtElement

object KotlinIntroduceVariableHelper {
    data class Containers(val targetContainer: KtElement, val occurrenceContainer: KtElement)
}