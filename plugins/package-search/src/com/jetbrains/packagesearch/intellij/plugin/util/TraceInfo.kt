package com.jetbrains.packagesearch.intellij.plugin.util

import java.util.concurrent.atomic.AtomicInteger

private val traceId = AtomicInteger(0)

data class TraceInfo(
    val source: TraceSource,
    val id: Int = traceId.incrementAndGet()
) {

    override fun toString() = "[$id, source=${source.name}]"

    enum class TraceSource {
        EMPTY_VALUE,
        INIT,
        PROJECT_CHANGES,
        SEARCH_RESULTS,
        TARGET_MODULES,
        FILTERS,
        SEARCH_QUERY,
        TARGET_MODULES_KEYPRESS,
        TARGET_MODULES_SELECTION_CHANGE,
        STATUS_CHANGES,
        EXECUTE_OPS,
        DATA_CHANGED
    }

    companion object {

        val EMPTY = TraceInfo(TraceSource.EMPTY_VALUE, -1)
    }
}
