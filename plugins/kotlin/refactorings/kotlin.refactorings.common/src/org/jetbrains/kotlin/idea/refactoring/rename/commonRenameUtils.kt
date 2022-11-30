// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.refactoring.rename

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.search.SearchScope
import org.jetbrains.kotlin.psi.*

/**
 * Parameter can be referenced via named arguments,
 * so we have to expand it's [KtParameter.getUseScope] in case we want to rename it.
 *
 * @see KtParameter.getUseScope
 */
val KtParameter.useScopeForRename: SearchScope
    get() {
        val owner = ownerFunction as? KtFunction
        return owner?.useScope ?: useScope
    }

/**
 * A utility function to call [ProgressManager.runProcessWithProgressSynchronously] more conveniently.
 */
inline fun <T> runProcessWithProgressSynchronously(
    @NlsSafe @NlsContexts.DialogTitle progressTitle: String,
    canBeCancelled: Boolean,
    project: Project,
    crossinline action: () -> T
): T = ProgressManager.getInstance().runProcessWithProgressSynchronously(
    ThrowableComputable { action() },
    progressTitle,
    canBeCancelled,
    project
)