// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.j2k.post.processing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor

internal fun runUndoTransparentActionInEdt(inWriteAction: Boolean, action: () -> Unit) {
    ApplicationManager.getApplication().invokeAndWait {
        CommandProcessor.getInstance().runUndoTransparentAction {
            if (inWriteAction) {
                runWriteAction(action)
            } else {
                action()
            }
        }
    }
}