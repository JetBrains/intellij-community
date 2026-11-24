// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.stats.completion.tracker

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.tracker.CompletionFactorsInitializer
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.pom.java.LanguageLevel
import com.intellij.stats.completion.Action
import com.intellij.stats.completion.events.CompletionStartedEvent
import com.intellij.stats.completion.events.LogEvent
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.javaCodeInsightFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.setUpJdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito

const val runnableInterface = "interface Runnable { void run(); void runFast(); }"
const val testText = """
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
  val completionEvents = filter { it.actionType != Action.CUSTOM }
  Assertions.assertThat(completionEvents.size).isEqualTo(actions.size)
  completionEvents.zip(actions).forEach { (event, action) ->
    Assertions.assertThat(event.actionType).isEqualTo(action)
  }
}

@TestApplication
abstract class CompletionLoggingTestBase {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    @BeforeAll
    @JvmStatic
    fun registerCompletionLogger() {
      val project = projectFixture.get()
      project.messageBus.connect(project).subscribe(LookupManagerListener.TOPIC, CompletionLoggerInitializer())
    }
  }

  private val tempDirFixture = tempPathFixture()
  private val moduleFixture = projectFixture.moduleFixture(tempDirFixture, addPathToSourceRoot = true)
  val myFixture: JavaCodeInsightTestFixture by javaCodeInsightFixture(projectFixture, tempDirFixture)

  @TestDisposable
  lateinit var testRootDisposable: Disposable

  val lookup: LookupImpl
    get() = myFixture.lookup as LookupImpl

  val trackedEvents = mutableListOf<LogEvent>()

  private lateinit var mockLoggerProvider: CompletionLoggerProvider

  val completionStartedEvent: CompletionStartedEvent
    get() = trackedEvents.first() as CompletionStartedEvent

  open fun completionFileLogger(shouldLogElementFeatures: Boolean): CompletionFileLogger {
    val eventLogger = object : CompletionEventLogger {
      override fun log(event: LogEvent) {
        trackedEvents.add(event)
      }
    }
    return CompletionFileLogger("installation-uid", "completion-uid", "0", Language.ANY.displayName, shouldLogElementFeatures, eventLogger)
  }

  @BeforeEach
  fun setUpCompletionLogging(): Unit = onEdt {
    val project = projectFixture.get()
    val module = moduleFixture.get()
    setUpJdk(LanguageLevel.JDK_1_6, project, module, testRootDisposable)
    IdeaTestUtil.setModuleLanguageLevel(module, LanguageLevel.JDK_1_6, testRootDisposable)

    trackedEvents.clear()

    mockLoggerProvider = Mockito.mock(CompletionLoggerProvider::class.java)
    Mockito.`when`(mockLoggerProvider.newCompletionLogger(any(), anyBoolean())).thenAnswer { completionFileLogger(it.arguments[1] as Boolean) }
    ApplicationManager.getApplication().replaceService(CompletionLoggerProvider::class.java, mockLoggerProvider, testRootDisposable)

    myFixture.addClass(runnableInterface)
    myFixture.configureByText(JavaFileType.INSTANCE, testText)

    CompletionFactorsInitializer.isEnabledInTests = true
  }

  @AfterEach
  fun tearDownCompletionLogging() {
    CompletionFactorsInitializer.isEnabledInTests = false
  }

  /**
   * Runs [block] on the EDT, as JUnit 3 did for each test.
   */
  fun onEdt(block: () -> Unit): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      block()
    }
  }

  private fun <T> any(): T = Mockito.any()
}
