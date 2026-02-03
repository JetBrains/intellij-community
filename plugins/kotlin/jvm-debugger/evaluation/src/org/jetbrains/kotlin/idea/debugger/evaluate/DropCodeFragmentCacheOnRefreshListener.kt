// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import com.intellij.debugger.impl.DebuggerManagerListener
import com.intellij.debugger.impl.DebuggerSession

class DropCodeFragmentCacheOnRefreshListener : DebuggerManagerListener {

    override fun sessionAttached(session: DebuggerSession) {
        val tracker = getTracker(session) ?: return
        // for invalidating caches on restarting debug sessions
        tracker.incCounter()
        session.contextManager.addListener { _, event ->
            if (event == DebuggerSession.Event.REFRESH || event == DebuggerSession.Event.REFRESH_WITH_STACK) {
                tracker.incCounter()
            }
        }
    }

    private fun getTracker(session: DebuggerSession): KotlinDebuggerSessionRefreshTracker? {
        val project = session.project ?: return null
        return KotlinDebuggerSessionRefreshTracker.getInstance(project)
    }

    override fun sessionDetached(session: DebuggerSession) {
        // to clear the cache after the last debugger session
        getTracker(session)?.incCounter()
    }
}
