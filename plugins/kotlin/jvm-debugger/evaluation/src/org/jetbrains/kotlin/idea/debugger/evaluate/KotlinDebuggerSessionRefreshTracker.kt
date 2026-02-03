// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker

// TODO: support per-session tracking, to avoid unnecessary invalidations in multiple sessions scenarios
@Service(Service.Level.PROJECT)
class KotlinDebuggerSessionRefreshTracker : ModificationTracker {

    private var counter = 0L

    fun incCounter() {
        counter++
    }

    override fun getModificationCount(): Long {
        return counter
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinDebuggerSessionRefreshTracker = project.service()
    }
}
