// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.test.util

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent

@OptIn(KaPlatformInterface::class)
fun Project.invalidateCaches() {
    invokeAndWaitIfNeeded {
        runWriteAction {
            publishGlobalModuleStateModificationEvent()
        }
    }
}
