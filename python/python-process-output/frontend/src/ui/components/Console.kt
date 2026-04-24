package com.intellij.python.processOutput.frontend.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.intellij.python.processOutput.frontend.ConsoleTag
import com.intellij.python.processOutput.frontend.ConsoleTagFormatter
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.Icons
import com.intellij.python.processOutput.frontend.ui.thenIfNotNull
import org.jetbrains.jewel.foundation.modifier.thenIf
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.HorizontalScrollbar
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.component.scrollbarContentSafePadding

private object ConsoleStyling {
    val LINE_START_PADDING = 8.dp
    val COPY_SECTION_BUTTON_SPACE_SIZE = 18.dp
}

internal data class ConsoleContext(
    val consoleContainerSize: IntSize,
    val verticalScrollState: ScrollState,
    val horizontalScrollState: ScrollState,
    val wrapContent: Boolean,
)

internal data class ConsoleLine<TTag : ConsoleTag>(
    val tag: TTag,
    val text: AnnotatedString,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConsoleContainer(
    verticalScrollState: ScrollState = rememberScrollState(),
    horizontalScrollState: ScrollState = rememberScrollState(),
    wrapContent: Boolean = false,
    content: @Composable ConsoleContext.() -> Unit,
) {
    var consoleContext by remember {
        mutableStateOf(
            ConsoleContext(IntSize.Zero, verticalScrollState, horizontalScrollState, wrapContent),
        )
    }

    LaunchedEffect(wrapContent) {
        consoleContext = consoleContext.copy(wrapContent = wrapContent)
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { coordinates ->
                    consoleContext = consoleContext.copy(consoleContainerSize = coordinates.size)
                },
    ) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides NoScrollOnFocusBringIntoViewSpec) {
            Box {
                Column(
                    modifier =
                        Modifier
                            .verticalScroll(verticalScrollState)
                            .thenIf(!wrapContent) {
                                horizontalScroll(horizontalScrollState)
                            }
                            .fillMaxSize(),
                ) {
                    consoleContext.content()
                }

                VerticalScrollbar(
                    scrollState = verticalScrollState,
                    modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                )

                if (!wrapContent) {
                    HorizontalScrollbar(
                        scrollState = horizontalScrollState,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(end = scrollbarContentSafePadding()),
                    )
                }
            }
        }
    }
}

@Composable
internal fun <TTag> ConsoleContext.ConsoleOutput(
    lines: List<ConsoleLine<TTag>>,
    formatter: ConsoleTagFormatter<TTag>,
    displayTags: Boolean = true,
    displayCopyButtons: Boolean = false,
    inputTestTag: String? = null,
    tagTestTag: String? = null,
    copyButtonTestTag: String? = null,
    onCopy: (ConsoleLine<TTag>, Int) -> Unit = { _, _ -> },
) where TTag : ConsoleTag, TTag : Enum<TTag> {
    val density = LocalDensity.current
    var textValue by remember { mutableStateOf(TextFieldValue(AnnotatedString(""))) }
    var sections by remember { mutableStateOf<List<Section<TTag>>>(emptyList()) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var xPosition by remember { mutableStateOf(0f) }
    var yPosition by remember { mutableStateOf(0f) }

    LaunchedEffect(lines, formatter, displayTags) {
        val newTags = mutableListOf<Section<TTag>>()
        val newTextValue = buildAnnotatedString {
            var prevTag: TTag? = null
            for ((index, line) in lines.withIndex()) {
                val prevLength = length

                appendLine(line.text)

                if (line.tag != prevTag) {
                    newTags += Section(line, prevLength, index)
                }

                prevTag = line.tag
            }
        }

        textValue = textValue.copy(annotatedString = newTextValue)
        sections = newTags
    }

    // auto scroll to the end of the selection if it is offscreen
    LaunchedEffect(textValue.selection) {
        val selection = textValue.selection.takeIf { !it.collapsed } ?: return@LaunchedEffect
        val width = consoleContainerSize.width
        val height = consoleContainerSize.height
        val rect = textLayout?.getCursorRect(selection.end) ?: return@LaunchedEffect
        val top = rect.top - verticalScrollState.value
        val bottom = rect.bottom - verticalScrollState.value
        val left = rect.left - horizontalScrollState.value
        val right = rect.right - horizontalScrollState.value
        val verticalScrollValue = when {
            top + yPosition < 0 -> rect.top.toInt() + yPosition.toInt()
            bottom + yPosition > height -> (rect.bottom.toInt() - height) + yPosition.toInt()
            else -> null
        }
        val horizontalScrollValue = when {
            left + xPosition < 0 -> rect.left.toInt() + xPosition.toInt()
            right + xPosition > width -> (rect.right.toInt() - width) + xPosition.toInt()
            else -> null
        }

        if (verticalScrollValue != null) {
            verticalScrollState.scrollTo(verticalScrollValue)
        }

        if (horizontalScrollValue != null) {
            horizontalScrollState.scrollTo(horizontalScrollValue)
        }
    }

    @Composable
    fun ConsoleInput(modifier: Modifier = Modifier) {
        BasicTextField(
            value = textValue,
            onValueChange = { newValue -> textValue = newValue },
            onTextLayout = { newTextLayout -> textLayout = newTextLayout },
            modifier =
                modifier
                    .padding(end = scrollbarContentSafePadding())
                    .thenIfNotNull(inputTestTag) { testTag(it) },
            readOnly = true,
            textStyle = JewelTheme.consoleTextStyle,
        )
    }

    Row(
        modifier =
            Modifier
                .onGloballyPositioned { coordinates ->
                    yPosition = coordinates.positionInParent().y
                }
                .widthIn(
                    min = with(density) { consoleContainerSize.width.toDp() },
                ),
    ) {
        Column(modifier = Modifier.padding(start = ConsoleStyling.LINE_START_PADDING)) {
            if (displayTags) {
                Box {
                    sections.composeForEachWithinLayout(textLayout) { line, yOffset, _ ->
                        Text(
                            text = formatter.colonTagString(line.tag),
                            modifier =
                                Modifier
                                    .offset(y = yOffset)
                                    .padding(end = ConsoleStyling.LINE_START_PADDING)
                                    .thenIfNotNull(tagTestTag) { testTag(it) },
                            style = JewelTheme.consoleTextStyle,
                            fontWeight = FontWeight.Thin,
                        )
                    }
                }
            }
        }

        if (wrapContent) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            xPosition = coordinates.positionInParent().x
                        },
            ) {
                ConsoleInput()
            }
        } else {
            ConsoleInput(
                modifier =
                    Modifier.onGloballyPositioned { coordinates ->
                        xPosition = coordinates.positionInParent().x
                    },
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Column(modifier = Modifier.padding(end = scrollbarContentSafePadding())) {
            if (displayCopyButtons) {
                Box {
                    sections.composeForEachWithinLayout(textLayout) { line, yOffset, index ->
                        Box(modifier = Modifier.offset(y = yOffset)) {
                            ActionIconButton(
                                modifier = Modifier
                                    .size(ConsoleStyling.COPY_SECTION_BUTTON_SPACE_SIZE)
                                    .thenIfNotNull(copyButtonTestTag) { testTag(it) },
                                iconKey = Icons.Keys.Copy,
                                tooltipText = message("process.output.output.copySection.tooltip"),
                                onClick = { onCopy(line, index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <TTag : ConsoleTag> List<Section<TTag>>.composeForEachWithinLayout(
    layout: TextLayoutResult?,
    callback: @Composable (ConsoleLine<TTag>, Dp, Int) -> Unit,
) {
    if (layout == null) {
        return
    }

    val density = LocalDensity.current
    val length = layout.layoutInput.text.length

    for ((tag, textOffset, index) in this) {
        if (textOffset !in 0..length) {
            continue
        }

        val yOffset = with(density) { layout.getCursorRect(textOffset).top.toDp() }

        callback(tag, yOffset, index)
    }
}

private data class Section<TTag : ConsoleTag>(
    val line: ConsoleLine<TTag>,
    val textOffset: Int,
    val index: Int,
)

private val NoScrollOnFocusBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        0f
}
