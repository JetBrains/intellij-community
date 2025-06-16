// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.actions.ThreadDumpAction
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.impl.DebuggerContextImpl
import com.intellij.debugger.impl.ThreadDumpItemsProvider
import com.intellij.debugger.impl.ThreadDumpItemsProviderFactory
import com.intellij.debugger.statistics.DebuggerStatistics
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.SimpleTextAttributes
import com.intellij.unscramble.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.debugger.coroutine.KotlinDebuggerCoroutinesBundle
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.State
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import java.util.*
import javax.swing.Icon

/**
 * Provides the dump of coroutines in the Debug mode.
 * Coroutine dump items are represented as [CoroutineDumpItem] instances.
 */
@ApiStatus.Internal
class CoroutinesDumpAsyncProvider : ThreadDumpItemsProviderFactory() {
    override fun getProvider(context: DebuggerContextImpl): ThreadDumpItemsProvider = object : ThreadDumpItemsProvider {
        override val progressText: String get() = JavaDebuggerBundle.message("thread.dump.coroutines.progress")

        private val enabled: Boolean =
            Registry.`is`("debugger.kotlin.show.coroutines.in.threadDumpPanel") &&
                    // check that coroutines are in the project's classpath
                    context.debugProcess!!.virtualMachineProxy.classesByName("kotlinx.coroutines.debug.internal.DebugProbesImpl").isNotEmpty()

        override val requiresEvaluation get() = enabled

        override fun getItems(suspendContext: SuspendContextImpl?): List<MergeableDumpItem> {
            return (
              if (!enabled) emptyList()
              else {
                val coroutinesCache = CoroutineDebugProbesProxy(suspendContext!!).dumpCoroutines()
                if (coroutinesCache.isOk()) coroutinesCache.cache.map { CoroutineDumpItem(it) } else emptyList()
              })
              .also {
                DebuggerStatistics.logCoroutineDump(context.project, it.size)
              }
        }
    }
}

private class CoroutineDumpItem(info: CoroutineInfoData) : MergeableDumpItem {

    override val name: String = info.name + ":" + info.id

    override val stateDesc: String = " (${info.state.name.lowercase()})"

    override val iconToolTip: String
        get() = KotlinDebuggerCoroutinesBundle.message("dump.item.coroutine.tooltip")

    private val dispatcher = info.dispatcher

    override val stackTrace: String =
        info.coroutineDescriptor + "\n" +
                info.lastObservedStackTrace.joinToString(prefix = "\t", separator = "\n\t") { ThreadDumpAction.renderLocation(it) }

    override val interestLevel: Int = when {
        info.lastObservedStackTrace.isEmpty() -> -10
        else -> stackTrace.count { it == '\n' }
    }

    override val isDeadLocked: Boolean
        get() = false

    override val awaitingDumpItems: Set<DumpItem>
        get() = emptySet()

    override val icon: Icon =
        IconsCache.getIconWithVirtualOverlay(
            when (info.state) {
                State.SUSPENDED -> AllIcons.Debugger.ThreadFrozen
                State.RUNNING -> AllIcons.Debugger.ThreadRunning
                State.CREATED, State.UNKNOWN -> AllIcons.Debugger.ThreadGroup
            }
        )

    override val attributes: SimpleTextAttributes = when (info.state) {
        State.SUSPENDED -> DumpItem.SLEEPING_ATTRIBUTES
        State.RUNNING -> DumpItem.RUNNING_ATTRIBUTES
        State.CREATED, State.UNKNOWN -> DumpItem.UNINTERESTING_ATTRIBUTES
    }

    override val mergeableToken: MergeableToken get() = CoroutinesMergeableToken()

    private inner class CoroutinesMergeableToken : MergeableToken {
        private val comparableStackTrace: String =
            stackTrace.substringAfter("\n")

        override val item get() = this@CoroutineDumpItem

        override fun equals(other: Any?): Boolean {
            if (other !is CoroutinesMergeableToken) return false
            val otherItem = other.item
            if (stateDesc != otherItem.stateDesc) return false
            if (dispatcher != otherItem.dispatcher) return false
            if (this.comparableStackTrace != other.comparableStackTrace) return false
            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(
                stateDesc,
                dispatcher,
                comparableStackTrace
            )
        }
    }
}
