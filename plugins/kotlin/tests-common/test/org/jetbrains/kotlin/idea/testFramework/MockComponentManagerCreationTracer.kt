// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.testFramework

import com.intellij.mock.MockComponentManager
import com.intellij.openapi.application.Application
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.kotlin.idea.test.testFramework.resetApplicationToNull

object MockComponentManagerCreationTracer {
    private val creationTraceMap = ContainerUtil.createConcurrentWeakMap<MockComponentManager, Throwable>()

    @JvmStatic
    fun diagnoseDisposedButNotClearedApplication(app: Application) {
        if (app is MockComponentManager) {
            resetApplicationToNull()
            throw IllegalStateException("Some test disposed, but forgot to clear MockApplication", creationTraceMap[app])
        }
    }
}