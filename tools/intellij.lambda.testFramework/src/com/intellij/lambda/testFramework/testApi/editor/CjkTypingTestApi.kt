package com.intellij.lambda.testFramework.testApi.editor

import com.intellij.ide.IdeEventQueue
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.editor.impl.EditorImpl
import kotlinx.coroutines.delay
import java.awt.event.InputMethodEvent
import java.awt.event.KeyEvent
import java.awt.font.TextHitInfo
import java.text.AttributedString
import javax.swing.JComponent
import kotlin.time.Duration

suspend fun EditorImpl.typeKorean(latency: Duration = defaultTestLatency, block: suspend KoreanTyping.() -> Unit) {
  KoreanTyping(contentComponent, latency).block()
}

class KoreanTyping(private val component: JComponent, private val latency: Duration) {
  private val eventQueue = IdeEventQueue.getInstance()

  private fun inputMethodTextChanged(text: String?, committedCharacterCount: Int = 0) {
    val event = InputMethodEvent(component, InputMethodEvent.INPUT_METHOD_TEXT_CHANGED, System.currentTimeMillis(),
                                 text?.let { AttributedString(it).iterator }, committedCharacterCount,
                                 TextHitInfo.afterOffset(0), TextHitInfo.afterOffset(0))
    eventQueue.postEvent(event)
  }

  private fun releaseKey(key: Char) {
    val event = KeyEvent(component, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), 0,
                         KeyEvent.getExtendedKeyCodeForChar(key.code), key)
    eventQueue.postEvent(event)
  }

  suspend fun type(text: String, key: Char, committedText: String? = null) {
    committedText?.let { inputMethodTextChanged(it, 1) }
    inputMethodTextChanged(text)
    releaseKey(key)
    delay(latency)
  }

  suspend fun finish(committedText: String) {
    inputMethodTextChanged(null)
    inputMethodTextChanged(committedText, 1)
    delay(latency)
  }
}