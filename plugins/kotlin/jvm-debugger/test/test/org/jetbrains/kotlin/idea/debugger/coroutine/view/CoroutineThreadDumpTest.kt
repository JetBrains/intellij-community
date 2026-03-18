// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.dumpItems
import com.intellij.unscramble.parseIntelliJThreadDump
import com.intellij.unscramble.serializeIntelliJThreadDump
import junit.framework.TestCase.assertEquals
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.test.DEBUGGER_TESTDATA_PATH_BASE
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Files
import kotlin.io.path.Path

@RunWith(JUnit4::class)
@TestDataPath($$"$CONTENT_ROOT/testData/threadDump")
class CoroutineThreadDumpTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR

    @Test
    fun `imported coroutine dump items are restored properly`() {
        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("importedCoroutineDumpItems.txt")))

        val dumpItems = parsedThreadDump.dumpItems().filter { !it.isContainer }
        assertEquals(2, dumpItems.size)

        val parentCoroutine = dumpItems.single { it.treeId == 300L }
        assertTrue(parentCoroutine is CoroutineDumpItem)
        assertEquals("scope:1", parentCoroutine.name)
        assertEquals(" (suspended)", parentCoroutine.stateDesc)
        assertEquals("Coroutine", parentCoroutine.iconToolTip)

        val childCoroutine = dumpItems.single { it.treeId == 301L }
        assertTrue(childCoroutine is CoroutineDumpItem)
        assertEquals("scope:2", childCoroutine.name)
        assertEquals(300L, childCoroutine.parentTreeId)
        assertEquals(" (running)", childCoroutine.stateDesc)
    }

    @Test
    fun `imported coroutine hierarchy and dispatcher context are preserved`() {
        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("importedCoroutineHierarchy.txt")))

        val dumpItems = parsedThreadDump.dumpItems().filter { !it.isContainer }
        val parentCoroutine = dumpItems.single { it.treeId == 300L }
        val childCoroutine = dumpItems.single { it.treeId == 301L }
        val otherDispatcherCoroutine = dumpItems.single { it.treeId == 302L }

        assertTrue(parentCoroutine is CoroutineDumpItem)
        assertTrue(childCoroutine is CoroutineDumpItem)
        assertTrue(otherDispatcherCoroutine is CoroutineDumpItem)
        assertEquals(300L, childCoroutine.parentTreeId)
        assertEquals(300L, otherDispatcherCoroutine.parentTreeId)
        assertEquals(childCoroutine.mergeableToken, parentCoroutine.mergeableToken)
        assertFalse(childCoroutine.mergeableToken == otherDispatcherCoroutine.mergeableToken)
    }

    @Test
    fun `coroutine items with different jobs but same dispatcher are merged`() {
        val commonDispatcher = "Dispatchers.IO"
        val firstCoroutine = CoroutineDumpItem(createCoroutineInfo(job = "StandaloneCoroutine{Active}", jobId = 300L, dispatcher = commonDispatcher))
        val secondCoroutine = CoroutineDumpItem(createCoroutineInfo(job = "DeferredCoroutine{Active}", jobId = 301L, dispatcher = commonDispatcher))

        val mergedDumpItems = CompoundDumpItem.mergeThreadDumpItems(listOf(firstCoroutine, secondCoroutine))

        assertEquals(1, mergedDumpItems.size)
        assertTrue(mergedDumpItems.single() is CompoundDumpItem<*>)
        assertEquals(2, (mergedDumpItems.single() as CompoundDumpItem<*>).counter)
    }

    @Test
    fun `coroutine header state is restored from serialized state without exact synthetic prefix match`() {
        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("coroutineHeaderStateWithoutSyntheticPrefix.txt")))

        val dumpItem = parsedThreadDump.dumpItems().single { !it.isContainer }
        assertTrue(dumpItem is CoroutineDumpItem)
        assertEquals("scope:1", dumpItem.name)
        assertEquals(" (running)", dumpItem.stateDesc)
        assertEquals(
            "\"scope:1@300\" RUNNING [Dispatchers.Default]\n    at example.Parent.one(Parent.kt:1)",
            dumpItem.stackTrace,
        )
        assertTrue(dumpItem.exportedStackTrace.startsWith("\"scope:1@300\" virtual tid=0x0 nid=NA running [Coroutine] [dispatcher=Dispatchers.Default]"))
    }

    @Test
    fun `coroutine dump item exposes clean ui stacktrace and synthetic exported stacktrace`() {
        val coroutineInfo = CoroutineInfoData(
            name = "scope",
            id = 1L,
            state = "SUSPENDED",
            dispatcher = "Dispatchers.Default",
            lastObservedFrame = null,
            lastObservedThread = null,
            debugCoroutineInfoRef = null,
            stackFrameProvider = null,
        ).also {
            it.job = "StandaloneCoroutine{Active}"
            it.jobId = 300L
        }

        val dumpItem = CoroutineDumpItem(coroutineInfo)
        assertEquals("scope:1", dumpItem.name)
        assertEquals(
            "\"scope:1@300\" SUSPENDED [Dispatchers.Default, StandaloneCoroutine{Active}]\n",
            dumpItem.stackTrace,
        )
        assertFalse(dumpItem.stackTrace.contains("java.lang.Thread.State"))
        assertFalse(dumpItem.stackTrace.contains("virtual tid=0x0"))
        assertFalse(dumpItem.stackTrace.contains("[Coroutine]"))
        assertEquals(
            "\"scope:1@300\" virtual tid=0x0 nid=NA suspended [Coroutine] [dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}]\n",
            dumpItem.exportedStackTrace,
        )
    }

    @Test
    fun `running coroutine dump item exposes running thread in visible and exported stacktrace`() {
        val dumpItem = CoroutineDumpItem(createCoroutineInfo(job = "StandaloneCoroutine{Active}", jobId = 300L, state = "RUNNING"))

        assertEquals(
            "\"scope:300@300\" RUNNING on thread UNKNOWN_THREAD [Dispatchers.Default, StandaloneCoroutine{Active}]\n",
            dumpItem.stackTrace,
        )
        assertEquals(
            "\"scope:300@300\" virtual tid=0x0 nid=NA running [Coroutine] [dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}, runningThread=UNKNOWN_THREAD]\n",
            dumpItem.exportedStackTrace,
        )
    }

    @Test
    fun `coroutine dump serialization uses exported stacktrace instead of ui stacktrace`() {
        val dumpItem = CoroutineDumpItem(createCoroutineInfo(job = "StandaloneCoroutine{Active}", jobId = 300L))

        val serializedDump = serializeIntelliJThreadDump(listOf(dumpItem), listOf("Full thread dump"))

        assertTrue(serializedDump.contains(dumpItem.exportedStackTrace))
        assertFalse(serializedDump.contains(dumpItem.stackTrace))
    }

    @Test
    fun `coroutine tree preserves visible stacktrace after serialize deserialize`() {
        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("importedCoroutineHierarchy.txt")))
        val expectedStackTraces = parsedThreadDump.dumpItems().sortedBy { it.treeId }.map { it.stackTrace }

        val serializedDump = serializeIntelliJThreadDump(parsedThreadDump.dumpItems(), listOf("Full thread dump"))
        val reparsedThreadDump = requireNotNull(parseIntelliJThreadDump(serializedDump))
        val actualStackTraces = reparsedThreadDump.dumpItems().sortedBy { it.treeId }.map { it.stackTrace }

        assertEquals(expectedStackTraces, actualStackTraces)
    }

    @Test
    fun `running thread is preserved after serialize deserialize`() {
        val originalDump = loadThreadDump("runningCoroutineWithObservedThread.txt")

        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(originalDump))
        val expectedStackTrace = parsedThreadDump.dumpItems().single { !it.isContainer }.stackTrace
        val serializedDump = serializeIntelliJThreadDump(parsedThreadDump.dumpItems(), listOf("Full thread dump"))
        val reparsedThreadDump = requireNotNull(parseIntelliJThreadDump(serializedDump))
        val actualStackTrace = reparsedThreadDump.dumpItems().single { !it.isContainer }.stackTrace

        assertEquals(expectedStackTrace, actualStackTrace)
    }

    @Test
    fun `running coroutine dump item restores concrete running thread in visible stacktrace`() {
        val originalDump = loadThreadDump("runningCoroutineWithObservedThread.txt")

        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(originalDump))
        val dumpItem = parsedThreadDump.dumpItems().single { !it.isContainer }

        assertEquals(
            "\"scope:1@300\" RUNNING on thread DefaultDispatcher-worker-1 [Dispatchers.Default]\n    at example.Shared.run(Shared.kt:1)",
            dumpItem.stackTrace,
        )
    }

    @Test
    fun `regular thread with coroutine marker in name is not restored as coroutine`() {
        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("regularThreadWithCoroutineMarkerInName.txt")))

        val dumpItem = parsedThreadDump.dumpItems().single { !it.isContainer }
        assertFalse(dumpItem is CoroutineDumpItem)
        assertEquals("worker[Coroutine]@101", dumpItem.name)
        assertEquals(" (runnable)", dumpItem.stateDesc)
    }

    private fun createCoroutineInfo(job: String, jobId: Long, dispatcher: String = "Dispatchers.Default", state: String = "SUSPENDED"): CoroutineInfoData {
        return CoroutineInfoData(
            name = "scope",
            id = jobId,
            state = state,
            dispatcher = dispatcher,
            lastObservedFrame = null,
            lastObservedThread = null,
            debugCoroutineInfoRef = null,
            stackFrameProvider = null,
        ).also {
            it.job = job
            it.jobId = jobId
        }
    }

    private fun loadThreadDump(fileName: String): String = Files.readString(Path(DEBUGGER_TESTDATA_PATH_BASE, "threadDump", fileName))
}
