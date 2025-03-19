package com.intellij.performance.performancePlugin.commands

import com.intellij.codeInsight.completion.impl.CompletionServiceImpl.Companion.currentCompletionProgressIndicator
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.AlphaNumericTypeCommand
import com.jetbrains.performancePlugin.commands.PerformanceCommandCoroutineAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.NonNls
import kotlin.time.Duration.Companion.milliseconds

internal class TypingWithCompletionCommand(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {
    companion object {
        const val NAME: @NonNls String = "typingWithCompletion"
        const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
    }

    private fun splitInput(input: String): MutableList<String> {
        val regex = """\w+|[{}().,\n]|""".toRegex()

        return regex.findAll(input).map { it.value }.filter { it.isNotEmpty() }.toMutableList()
    }

    override suspend fun doExecute(context: PlaybackContext) {
        val input = """return file.declarations.flatMap { it.annotations }
    .flatMap { it.entries }
    .filter { it.valueArguments.isNotEmpty() }
    .mapNotNull { it.calleeExpression }
    .map { it.text }"""
        val tokens = splitInput(input)
        val delayDuration = 300.milliseconds
        val mutex = Mutex()
        val editor = (AlphaNumericTypeCommand.findTarget(context) as EditorComponentImpl?)?.editor
        if (editor == null) return
        val span = startSpan(NAME)
        var completionCount = 0L
        coroutineScope {
            //job to propagate typing each DELAY
            val tick = Channel<Unit>()
            val tickJob = launch {
                while (true) {
                    delay(delayDuration)
                    tick.send(Unit)
                }
            }

            //job for typing
            val currentTokenChannel = Channel<String>()
            val wasCompleted = Channel<Unit>(onBufferOverflow = BufferOverflow.SUSPEND)
            val typing = launch {
                var index = 0
                while (index < tokens.size) {
                    val token = tokens[index]
                    index++
                    if (token == "→") {
                        moveCaretRight(editor, mutex)
                        continue
                    }
                    currentTokenChannel.send(token)
                    for (char in token) {
                        mutex.withLock {
                            withContext(Dispatchers.EDT) {
                                editor.type(char.toString())
                            }
                        }
                        val completed = select {
                            wasCompleted.onReceive {
                                //add caret move right otherwise there will be double closing parentheses
                                if (tokens.getOrNull(index) == "{") {
                                    tokens.removeAt(index)
                                    val indexOfClosing = tokens.drop(index).indexOfFirst { s -> s == "}" } + index
                                    tokens.add(indexOfClosing, "→")
                                }
                                //skip parenthesis if method call was completed
                                if (tokens.getOrNull(index) == "(") {
                                    tokens.removeAt(index)
                                    tokens.removeAt(index)
                                }
                                true
                            }
                            tick.onReceive {
                                false
                            }
                        }
                        if (completed) {
                            break
                        }
                    }
                }
                currentTokenChannel.close()
                tickJob.cancel()
            }

            //job for completion
            launch {
                var currentToken: String? = null
                while (true) {
                    val tokenInChannel = currentTokenChannel.tryReceive()
                    if (tokenInChannel.isSuccess) {
                        currentToken = tokenInChannel.getOrNull()
                    } else if (tokenInChannel.isClosed) {
                        break
                    }
                    if (currentToken != null) {
                        if (completeTokenIfExists(currentToken, wasCompleted, mutex)) {
                            completionCount++
                        }
                    }
                }
            }
            typing.join()
            span.setAttribute("completionCount", completionCount)
            span.end()
        }
    }

    private suspend fun completeTokenIfExists(currentToken: String?, wasCompleted: Channel<Unit>, mutex: Mutex): Boolean {
        val numberOfTopElementsToSearch = 3
        val items = currentCompletionProgressIndicator?.lookup?.items
        val lookupElement = items?.take(numberOfTopElementsToSearch)?.firstOrNull { it.lookupString == currentToken }
        if (lookupElement != null) {
            val result = wasCompleted.trySend(Unit)
            if (!result.isSuccess) {
                return false
            }
            mutex.withLock {
                return withContext(Dispatchers.EDT) {
                    currentCompletionProgressIndicator?.lookup?.finishLookup(Lookup.NORMAL_SELECT_CHAR, lookupElement)
                    return@withContext true
                }
            }
        }
        return false
    }

    private suspend fun moveCaretRight(editor: EditorImpl, mutex: Mutex) {
        mutex.withLock {
            edtWriteAction {
                val caretModel = editor.caretModel
                val document = editor.document
                val currentCaretPosition = caretModel.offset

                if (currentCaretPosition < document.textLength) {
                    caretModel.moveToOffset(currentCaretPosition + 1)
                }
            }
        }
    }

    override fun getName(): String {
        return NAME
    }
}