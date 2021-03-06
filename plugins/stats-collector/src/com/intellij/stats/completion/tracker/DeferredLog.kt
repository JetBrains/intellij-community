// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.stats.completion.tracker

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
        lastAction()
        clear()
    }
}