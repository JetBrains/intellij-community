// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.stats.completion.tracker

import com.intellij.util.application

class DeferredLog {
    companion object {
        private val DO_NOTHING: () -> Unit = { }
    }

    private var lastAction: () -> Unit = DO_NOTHING

    fun clear() {
        lastAction = DO_NOTHING
    }

    fun defer(action: () -> Unit) {
        lastAction = action
    }

    fun log() {
        if (application.isReadAccessAllowed) {
          lastAction()
        } else {
          application.invokeLater(lastAction)
        }
        clear()
    }
}