package com.intellij.lambda.testFramework.testApi

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.remoteDev.tests.impl.utils.waitSuspending
import javax.swing.KeyStroke
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun getKeyStroke(action: String, timeout: Duration = 5.seconds): KeyStroke {
  return waitSuspending(
    "Wait for shortcut available",
    timeout = timeout,
    getter = { serviceAsync<ActionManager>().getKeyboardShortcut(action)?.firstKeyStroke },
    checker = { it != null },
    failMessageProducer = { "Have not found the needed shortcut for action $action" }
  )!!
}