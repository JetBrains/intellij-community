// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.components.service

/**
 * Service for various functionality which have different implementation in K1 and K2 plugin
 * and which is used in common Rename Refactoring code
 */
interface KotlinRenameRefactoringSupport {
    companion object {
        @JvmStatic
        fun getInstance(): KotlinRenameRefactoringSupport = service()
    }
}