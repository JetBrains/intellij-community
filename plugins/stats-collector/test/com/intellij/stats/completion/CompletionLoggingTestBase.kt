/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.completion


import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.stats.events.completion.Action
import com.intellij.stats.events.completion.CompletionStartedEvent
import com.intellij.stats.events.completion.LogEvent
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.picocontainer.MutablePicoContainer


val runnableInterface = "interface Runnable { void run(); void runFast(); }"
val testText = """
class Test {
    public void run() {
        Runnable r = new Runnable() {
            public void run() {}
        };
        r<caret>
    }
}
"""

fun List<LogEvent>.assertOrder(vararg actions: Action) {
  Assertions.assertThat(size).isEqualTo(actions.size)
  zip(actions).forEach {
    val event = it.first
    val action = it.second
    Assertions.assertThat(event.actionType).isEqualTo(action)
  }
}


abstract class CompletionLoggingTestBase : LightFixtureCompletionTestCase() {

  val trackedEvents = mutableListOf<LogEvent>()

  lateinit var realLoggerProvider: CompletionLoggerProvider
  lateinit var mockLoggerProvider: CompletionLoggerProvider

  lateinit var container: MutablePicoContainer


  val completionStartedEvent: CompletionStartedEvent
    get() = trackedEvents.first() as CompletionStartedEvent


  open fun completionFileLogger(): CompletionFileLogger {
    val eventLogger = object : CompletionEventLogger {
      override fun log(event: LogEvent) {
        trackedEvents.add(event)
      }
    }
    return CompletionFileLogger("installation-uid", "completion-uid", eventLogger)
  }

  override fun setUp() {
    super.setUp()
    trackedEvents.clear()

    container = ApplicationManager.getApplication().picoContainer as MutablePicoContainer
    mockLoggerProvider = Mockito.mock(CompletionLoggerProvider::class.java)

    Mockito.`when`(mockLoggerProvider.newCompletionLogger()).thenReturn(completionFileLogger())

    val name = CompletionLoggerProvider::class.java.name
    realLoggerProvider = container.getComponentInstance(name) as CompletionLoggerProvider

    container.unregisterComponent(name)
    container.registerComponentInstance(name, mockLoggerProvider)

    myFixture.addClass(runnableInterface)
    myFixture.configureByText(JavaFileType.INSTANCE, testText)

    CompletionTrackerInitializer.isEnabledInTests = true
  }

  override fun tearDown() {
    CompletionTrackerInitializer.isEnabledInTests = false

    val name = CompletionLoggerProvider::class.java.name
    container.unregisterComponent(name)
    container.registerComponentInstance(name, realLoggerProvider)
    super.tearDown()
  }

}

