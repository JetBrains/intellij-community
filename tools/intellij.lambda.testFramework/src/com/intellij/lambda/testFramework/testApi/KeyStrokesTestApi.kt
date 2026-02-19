package com.intellij.lambda.testFramework.testApi

import com.intellij.ide.IdeEventQueue
import com.intellij.lambda.testFramework.frameworkLogger
import com.intellij.lambda.testFramework.testApi.utils.defaultTestLatency
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.wm.WindowManager
import com.intellij.remoteDev.tests.LambdaIdeContext
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import java.awt.Component
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import kotlin.time.Duration

/**
 * Emulating a real user adding Key Events, without specifying the exact component, so focus resolution is also triggered
 */

context(lambdaIdeContext: LambdaIdeContext)
private suspend fun pressKeyStrokes(keyStrokes: List<KeyStrokeAdapter>, repeat: Int = 1, latency: Duration = defaultTestLatency) {
  assertThat(ApplicationManager.getApplication().isHeadlessEnvironment)
    .describedAs { "pressKeyStrokes can be used on in non haedless IDE" }
    .isFalse()

  val component = serviceAsync<WindowManager>().getIdeFrame(getProject())?.component
  val ideEventQueue = IdeEventQueue.getInstance()
  repeat(repeat) {
    for (keyStroke in keyStrokes) {
      delay(latency)
      frameworkLogger.info("Pressing $keyStroke")

      ideEventQueue.postEvent(createKeyEvent(source = component, id = KeyEvent.KEY_PRESSED, keyStroke = keyStroke))
      if (keyStroke.keyChar != KeyEvent.CHAR_UNDEFINED) {
        ideEventQueue.postEvent(createKeyEvent(source = component,
                                                   id = KeyEvent.KEY_TYPED,
                                                   keyStroke = keyStroke,
                                                   keyCode = KeyEvent.VK_UNDEFINED,
                                                   keyLocation = KeyEvent.KEY_LOCATION_UNKNOWN))
      }
      ideEventQueue.postEvent(
        createKeyEvent(source = component, id = KeyEvent.KEY_RELEASED, keyStroke = keyStroke))
    }
  }
}

context(lambdaIdeContext: LambdaIdeContext)
private suspend fun pressKeyStrokesDirectly(component: Component, keyStrokes: List<KeyStrokeAdapter>, repeat: Int = 1, latency: Duration = defaultTestLatency) {
  repeat(repeat) {
    for (keyStroke in keyStrokes) {
      delay(latency)
      frameworkLogger.info("Pressing $keyStroke directly to $component")

      component.dispatchEvent(createKeyEvent(source = component, id = KeyEvent.KEY_PRESSED, keyStroke = keyStroke))
      if (keyStroke.keyChar != KeyEvent.CHAR_UNDEFINED) {
        component.dispatchEvent(createKeyEvent(source = component,
                                                   id = KeyEvent.KEY_TYPED,
                                                   keyStroke = keyStroke,
                                                   keyCode = KeyEvent.VK_UNDEFINED,
                                                   keyLocation = KeyEvent.KEY_LOCATION_UNKNOWN))
      }
      component.dispatchEvent(
        createKeyEvent(source = component, id = KeyEvent.KEY_RELEASED, keyStroke = keyStroke))
    }
  }
}

private fun createKeyEvent(source: Component?,
                           id: Int,
                           keyStroke: KeyStrokeAdapter,
                           keyCode: Int = keyStroke.keyCode,
                           keyLocation: Int = KeyEvent.KEY_LOCATION_STANDARD): KeyEvent {
  return KeyEvent(/* source = */ source,
                  /* id = */ id,
                  /* when = */ 0,
                  /* modifiers = */ keyStroke.modifiers,
                  /* keyCode = */ keyCode,
                  /* keyChar = */ keyStroke.keyChar,
                  /* keyLocation = */ keyLocation)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressKeyStroke(keyStroke: KeyStroke, repeat: Int = 1, latency: Duration = defaultTestLatency) {
  pressKeyStrokes(listOf(KeyStrokeAdapter(keyStroke)), repeat, latency)
}

context(lambdaIdeContext: LambdaIdeContext)
suspend fun pressKeyStrokesDirectly(component: Component, keyStroke: KeyStroke, repeat: Int = 1, latency: Duration = defaultTestLatency) {
  pressKeyStrokesDirectly(component, listOf(KeyStrokeAdapter(keyStroke)), repeat, latency)
}

/**
 * Type text through the event queue rather than direct typing to the editor.
 */
context(lambdaIdeContext: LambdaIdeContext)
suspend fun typeWithEventQueue(string: CharSequence, latency: Duration = defaultTestLatency) {
  assertThat(ApplicationManager.getApplication().isHeadlessEnvironment)
    .describedAs { "typeWithEventQueue can be used on in non haedless IDE" }
    .isFalse()

  frameworkLogger.info("Type with latency ($latency) through event queue: '$string'")
  val keyStrokes = string.map { c ->
    val modifier = if (c.isUpperCase()) KeyEvent.SHIFT_DOWN_MASK else 0
    val keyCode = KeyEvent.getExtendedKeyCodeForChar(c.code)
    KeyStrokeAdapter(keyCode, c, modifier)
  }
  pressKeyStrokes(keyStrokes, repeat = 1, latency)
}

/**
 * Unlike KeyStroke, which is unique, cached and can express only on of press/release/type events,
 * this class allows to store info for all types of keyboard events.
 * This is needed to properly support [typeWithEventQueue], because without [keyCode] it can't call
 * actions by shortcuts (which is the main goal of the test - to check that typing symbols doesn't trigger actions)
 */
private data class KeyStrokeAdapter(val keyCode: Int, val keyChar: Char, val modifiers: Int) {
    constructor(keyStroke: KeyStroke) : this(keyStroke.keyCode, keyStroke.keyChar, keyStroke.modifiers)
}
