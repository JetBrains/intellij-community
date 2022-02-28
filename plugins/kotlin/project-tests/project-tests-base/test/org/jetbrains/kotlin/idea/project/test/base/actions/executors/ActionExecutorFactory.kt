// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.project.test.base.actions.executors

import org.jetbrains.kotlin.idea.project.test.base.actions.HighlightFileAction
import org.jetbrains.kotlin.idea.project.test.base.actions.ProjectAction
import org.jetbrains.kotlin.idea.project.test.base.actions.TypeAndAutocompleteInFileAction

object ActionExecutorFactory {
    fun createExecutor(action: ProjectAction): ProjectActionExecutor<*, *> = when (action) {
        is HighlightFileAction -> HighlightFileProjectActionExecutor
        is TypeAndAutocompleteInFileAction -> TODO("Not yet supported")
    }
}