// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.coroutine.view

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestDataPath
import com.intellij.unscramble.CompoundDumpItem
import com.intellij.unscramble.dumpItems
import com.intellij.unscramble.parseIntelliJThreadDump
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
        assertTrue(dumpItem.stackTrace.startsWith("\"scope:1@300\" #1 prio=5 tid=0x1 nid=0x1 running [Coroutine] [dispatcher=Dispatchers.Default]"))
    }

    @Test
    fun `coroutine dump item uses single serialized coroutine state in header`() {
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
        assertTrue(dumpItem.stackTrace.startsWith(
            "\"scope:1@300\" virtual tid=0x0 nid=NA suspended [Coroutine] [dispatcher=Dispatchers.Default, job=StandaloneCoroutine{Active}]"
        ))
        assertFalse(dumpItem.stackTrace.contains("java.lang.Thread.State"))
    }

    @Test
    fun `regular thread with coroutine marker in name is not restored as coroutine`() {
        val parsedThreadDump = requireNotNull(parseIntelliJThreadDump(loadThreadDump("regularThreadWithCoroutineMarkerInName.txt")))

        val dumpItem = parsedThreadDump.dumpItems().single { !it.isContainer }
        assertFalse(dumpItem is CoroutineDumpItem)
        assertEquals("worker[Coroutine]@101", dumpItem.name)
        assertEquals(" (runnable)", dumpItem.stateDesc)
    }

    private fun createCoroutineInfo(job: String, jobId: Long, dispatcher: String = "Dispatchers.Default"): CoroutineInfoData {
        return CoroutineInfoData(
            name = "scope",
            id = jobId,
            state = "SUSPENDED",
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
