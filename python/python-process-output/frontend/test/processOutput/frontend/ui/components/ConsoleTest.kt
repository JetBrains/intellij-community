package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.intellij.python.processOutput.frontend.ConsoleTag
import com.intellij.python.processOutput.frontend.ConsoleTagFormatter
import com.intellij.python.processOutput.frontend.ProcessOutputTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow

internal class ConsoleTest : ProcessOutputTest() {
    private var linesFlow = MutableStateFlow<List<ConsoleLine<TestTag>>>(emptyList())
    private var displayTagsFlow = MutableStateFlow(true)
    private var displayCopyButtonsFlow = MutableStateFlow(false)
    private var wrapContentFlow = MutableStateFlow(false)
    private var verticalScrollStateFlow = MutableStateFlow(ScrollState(0))
    private var horizontalScrollStateFlow = MutableStateFlow(ScrollState(0))

    private fun consoleTest(
        initialLines: List<ConsoleLine<TestTag>> =
            listOf(
                ConsoleLine(TestTag.FOO, AnnotatedString("FooContent")),
                ConsoleLine(TestTag.BAR, AnnotatedString("BarContent")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("BazContent")),
            ),
        initialDisplayTags: Boolean = true,
        initialDisplayCopyButtons: Boolean = false,
        onCopy: (ConsoleLine<TestTag>, Int) -> Unit = { _, _ -> },
        initialWrapContent: Boolean = true,
        initialVerticalScrollState: ScrollState = ScrollState(0),
        initialHorizontalScrollState: ScrollState = ScrollState(0),
        body: suspend ComposeContentTestRule.() -> Unit,
    ) {
        linesFlow.value = initialLines
        displayTagsFlow.value = initialDisplayTags
        displayCopyButtonsFlow.value = initialDisplayCopyButtons
        wrapContentFlow.value = initialWrapContent
        verticalScrollStateFlow.value = initialVerticalScrollState
        horizontalScrollStateFlow.value = initialHorizontalScrollState

        scaffoldTestContent {
            val lines by linesFlow.collectAsState()
            val displayTags by displayTagsFlow.collectAsState()
            val displayCopyButtons by displayCopyButtonsFlow.collectAsState()
            val wrapContent by wrapContentFlow.collectAsState()
            val verticalScrollState by verticalScrollStateFlow.collectAsState()
            val horizontalScrollState by horizontalScrollStateFlow.collectAsState()

            ConsoleContainer(verticalScrollState, horizontalScrollState, wrapContent) {
                ConsoleOutput(
                    lines = lines,
                    formatter = TestTag.formatter,
                    displayTags = displayTags,
                    displayCopyButtons = displayCopyButtons,
                    inputTestTag = ConsoleTestTags.INPUT,
                    tagTestTag = ConsoleTestTags.TAG,
                    copyButtonTestTag = ConsoleTestTags.COPY,
                    onCopy = onCopy,
                )
            }
        }

        processOutputTest {
            this.body()
        }
    }

    @Test
    fun `tags are displayed with correct contents`() = consoleTest {
        // tags are displayed with correct text contents
        onAllNodesWithTag(ConsoleTestTags.TAG).apply {
            assertCountEquals(3)
            get(0).assertTextEquals(TestTag.formatter.colonTagString(TestTag.FOO))
            get(1).assertTextEquals(TestTag.formatter.colonTagString(TestTag.BAR))
            get(2).assertTextEquals(TestTag.formatter.colonTagString(TestTag.BAZ))
        }
    }

    @Test
    fun `tag is not repeated when previous line is of the same tag`() = consoleTest(
        initialLines = listOf(
            ConsoleLine(TestTag.FOO, AnnotatedString("1 FooContent")),
            ConsoleLine(TestTag.FOO, AnnotatedString("2 FooContent")),
            ConsoleLine(TestTag.BAR, AnnotatedString("3 BarContent")),
            ConsoleLine(TestTag.BAR, AnnotatedString("4 BarContent")),
            ConsoleLine(TestTag.BAZ, AnnotatedString("5 BazContent")),
            ConsoleLine(TestTag.BAZ, AnnotatedString("6 BazContent")),
            ConsoleLine(TestTag.FOO, AnnotatedString("7 FooContent")),
        ),
    ) {
        // tags are displayed with correct text contents
        onAllNodesWithTag(ConsoleTestTags.TAG).apply {
            assertCountEquals(4)
            get(0).assertTextEquals(TestTag.formatter.colonTagString(TestTag.FOO))
            get(1).assertTextEquals(TestTag.formatter.colonTagString(TestTag.BAR))
            get(2).assertTextEquals(TestTag.formatter.colonTagString(TestTag.BAZ))
            get(3).assertTextEquals(TestTag.formatter.colonTagString(TestTag.FOO))
        }
    }

    @Test
    fun `tags are hidden when displayTags is false`() = consoleTest(initialDisplayTags = false) {
        // tags should not be displayed
        onAllNodesWithTag(ConsoleTestTags.TAG).assertCountEquals(0)
    }

    @Test
    fun `tags can be hidden in real time`() = consoleTest {
        // tags should be displayed
        onAllNodesWithTag(ConsoleTestTags.TAG).assertCountEquals(3)

        // turning display tags off
        displayTagsFlow.value = false
        waitForIdle()

        // tags should not be displayed
        onAllNodesWithTag(ConsoleTestTags.TAG).assertCountEquals(0)

        // turning display tags on
        displayTagsFlow.value = true
        waitForIdle()

        // tags should be displayed
        onAllNodesWithTag(ConsoleTestTags.TAG).assertCountEquals(3)
    }

    @Test
    fun `content is displayed correctly`() = consoleTest {
        // content should be correct
        onNodeWithTag(ConsoleTestTags.INPUT).assertTextEquals(
            """
                FooContent
                BarContent
                BazContent
                
            """.trimIndent(),
        )
    }

    @Test
    fun `content can be updated in real time`() = consoleTest {
        // content should be correct
        onNodeWithTag(ConsoleTestTags.INPUT).assertTextEquals(
            """
                FooContent
                BarContent
                BazContent
                
            """.trimIndent(),
        )

        // adding more lines
        linesFlow.emit(
            linesFlow.value + listOf(
                ConsoleLine(TestTag.FOO, AnnotatedString("Another foo line")),
                ConsoleLine(TestTag.BAR, AnnotatedString("Another bar line")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Another baz line")),
            ),
        )
        waitForIdle()

        // new content should be correct
        onNodeWithTag(ConsoleTestTags.INPUT).assertTextEquals(
            """
                FooContent
                BarContent
                BazContent
                Another foo line
                Another bar line
                Another baz line
                
            """.trimIndent(),
        )
    }

    @Test
    fun `copy buttons are displayed correctly`() = consoleTest(initialDisplayCopyButtons = true) {
        // only 3 copy buttons should be displayed
        onAllNodesWithTag(ConsoleTestTags.COPY).assertCountEquals(3)
    }

    @Test
    fun `copy button is not repeated when previous line is of the same tag`() = consoleTest(
        initialLines = listOf(
            ConsoleLine(TestTag.FOO, AnnotatedString("1 FooContent")),
            ConsoleLine(TestTag.FOO, AnnotatedString("2 FooContent")),
            ConsoleLine(TestTag.BAR, AnnotatedString("3 BarContent")),
            ConsoleLine(TestTag.BAR, AnnotatedString("4 BarContent")),
            ConsoleLine(TestTag.BAZ, AnnotatedString("5 BazContent")),
            ConsoleLine(TestTag.BAZ, AnnotatedString("6 BazContent")),
            ConsoleLine(TestTag.FOO, AnnotatedString("7 FooContent")),
        ),
        initialDisplayCopyButtons = true,
    ) {
        // only 4 copy buttons should be displayed
        onAllNodesWithTag(ConsoleTestTags.COPY).assertCountEquals(4)
    }

    @Test
    fun `copy buttons are hidden when displayTags is false`() = consoleTest(
        initialDisplayCopyButtons = false,
    ) {
        // copy buttons should not be displayed
        onAllNodesWithTag(ConsoleTestTags.COPY).assertCountEquals(0)
    }

    @Test
    fun `copy buttons can be hidden in real time`() = consoleTest {
        // copy buttons should not be displayed
        onAllNodesWithTag(ConsoleTestTags.COPY).assertCountEquals(0)

        // turning copy buttons on
        displayCopyButtonsFlow.value = true
        waitForIdle()

        // copy buttons should be displayed
        onAllNodesWithTag(ConsoleTestTags.COPY).assertCountEquals(3)

        // turning copy buttons off
        displayCopyButtonsFlow.value = false
        waitForIdle()

        // copy buttons should not be displayed
        onAllNodesWithTag(ConsoleTestTags.COPY).assertCountEquals(0)
    }

    @Test
    fun `clicking on copy buttons should call callback with correct arguments`() {
        val invocations = mutableListOf<Pair<ConsoleLine<TestTag>, Int>>()

        consoleTest(
            listOf(
                ConsoleLine(TestTag.FOO, AnnotatedString("Foo 1")),
                ConsoleLine(TestTag.FOO, AnnotatedString("Foo 2")),
                ConsoleLine(TestTag.FOO, AnnotatedString("Foo 3")),
                ConsoleLine(TestTag.FOO, AnnotatedString("Foo 4")),
                ConsoleLine(TestTag.BAR, AnnotatedString("Bar 1")),
                ConsoleLine(TestTag.BAR, AnnotatedString("Bar 2")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 1")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 2")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 3")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 4")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 5")),
                ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 6")),
            ),
            initialDisplayCopyButtons = true,
            onCopy = { line, index ->
                invocations += line to index
            },
        ) {
            onAllNodesWithTag(ConsoleTestTags.COPY).apply {
                // no clicks were performed, therefore no invocations should have been recorded
                assertEquals(0, invocations.size)

                // click on the first copy button
                get(0).performClick()

                // only one invocation should have been made
                assertEquals(1, invocations.size)

                val (line1, index1) = invocations[0]

                // invocation should pass line at index 0
                assertEquals(ConsoleLine(TestTag.FOO, AnnotatedString("Foo 1")), line1)
                assertEquals(0, index1)

                // click on the second copy button
                get(1).performClick()

                // only two invocations should have been made
                assertEquals(2, invocations.size)

                val (line2, index2) = invocations[1]

                // invocation should pass line at index 4
                assertEquals(ConsoleLine(TestTag.BAR, AnnotatedString("Bar 1")), line2)
                assertEquals(4, index2)

                // click on the third copy button
                get(2).performClick()

                // only three invocations should have been made
                assertEquals(3, invocations.size)

                val (line3, index3) = invocations[2]

                // invocation should pass line at index 6
                assertEquals(ConsoleLine(TestTag.BAZ, AnnotatedString("Baz 1")), line3)
                assertEquals(6, index3)
            }
        }
    }

    @Test
    fun `vertical scroll is enabled when content overflows vertically`() {
        val verticalScrollState = ScrollState(0)

        consoleTest(
            initialLines = generateLines(100),
            initialVerticalScrollState = verticalScrollState,
        ) {
            // maxValue should be some integer above 0, but not max integer, as the state is
            // attached and should be updated
            waitForIdle()
            assertTrue(verticalScrollState.maxValue > 0)
            assertNotEquals(Int.MAX_VALUE, verticalScrollState.maxValue)
        }
    }

    @Test
    fun `vertical scroll is not enabled when content fits`() {
        val verticalScrollState = ScrollState(0)

        consoleTest(
            initialVerticalScrollState = verticalScrollState,
        ) {
            // maxValue should be 0, as the state is attached and the content fits
            waitForIdle()
            assertEquals(0, verticalScrollState.maxValue)
        }
    }

    @Test
    fun `horizontal scroll is enabled when wrapContent is false and content overflows`() {
        val horizontalScrollState = ScrollState(0)

        consoleTest(
            initialLines = generateLines(5, lineLength = 500),
            initialHorizontalScrollState = horizontalScrollState,
            initialWrapContent = false,
        ) {
            // maxValue should be some integer above 0, but not max integer, as the state is
            // attached and should be updated
            waitForIdle()
            assertTrue(horizontalScrollState.maxValue > 0)
            assertNotEquals(Int.MAX_VALUE, horizontalScrollState.maxValue)
        }
    }

    @Test
    fun `horizontal scroll is not active when wrapContent is true`() {
        val horizontalScrollState = ScrollState(0)

        consoleTest(
            initialLines = generateLines(5, lineLength = 500),
            initialHorizontalScrollState = horizontalScrollState,
            initialWrapContent = true,
        ) {
            // maxValue should be max integer, as it is never updated if the state is not attached
            waitForIdle()
            assertEquals(Int.MAX_VALUE, horizontalScrollState.maxValue)
        }
    }

    private fun generateLines(count: Int, lineLength: Int = 10) =
        buildList {
            repeat(count) {
                add(
                    ConsoleLine(
                        TestTag.FOO,
                        AnnotatedString("Foo $count: ${"x".repeat(lineLength)}"),
                    ),
                )
            }
        }
}

private enum class TestTag(override val text: String) : ConsoleTag {
    FOO("foo"),
    BAR("bar"),
    BAZ("baz");

    companion object {
        val formatter = ConsoleTagFormatter.create<TestTag>()
    }
}

private object ConsoleTestTags {
    const val INPUT = "ConsoleTest.Input"
    const val TAG = "ConsoleTest.Tag"
    const val COPY = "ConsoleTest.CopyButtonTestTag"
}
