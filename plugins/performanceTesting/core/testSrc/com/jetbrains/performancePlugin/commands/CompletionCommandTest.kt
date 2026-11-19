// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionPhase
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.StageInfo
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.registerExtension
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.StatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Runs `%doComplete` while an autopopup computes its items, as after a script types `slices.`. The command must measure its own completion,
 * not the autopopup it cancels.
 */
@TestApplication
internal class CompletionCommandTest {
  private val project = projectFixture(openAfterCreation = true)
  private val fixture by codeInsightModuleFixture(project)

  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `a completion that cancels an autopopup records its own items and the first shown element`(): Unit = timeoutRunBlocking(60.seconds) {
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true, disposable)
    val contributor = CompletionContributorEP("any", AutopopupBlockingContributor::class.java.name, DefaultPluginDescriptor("completionCommandTest"))
    ApplicationManager.getApplication().registerExtension(CompletionContributor.EP, contributor, disposable)
    AutopopupBlockingContributor.autopopupStarted = CountDownLatch(1)

    withContext(Dispatchers.EDT) {
      fixture.configureByText("a.txt", "<caret>")
      // invocation count 0 makes CompletionParameters.isAutoPopup true, as for the autopopup that typing starts
      CodeCompletionHandlerBase(CompletionType.BASIC, false, true, false).invokeCompletion(fixture.project, fixture.editor, 0)
    }
    assertTrue(AutopopupBlockingContributor.autopopupStarted.await(30, TimeUnit.SECONDS), "the autopopup did not start")
    while (withContext(Dispatchers.EDT) { CompletionServiceImpl.completionPhase !is CompletionPhase.BgCalculation }) {
      delay(10)
    }

    val span = RecordingSpan()
    val command = object : CompletionCommand("%doComplete BASIC", 1) {
      override fun startSpan(name: String): Span = span
    }
    try {
      command.execute(TestPlaybackContext(fixture.project)).await()
    }
    finally {
      withContext(Dispatchers.EDT) { LookupManager.hideActiveLookup(fixture.project) }
    }

    assertTrue(span.ended, "the span did not end")
    assertEquals(2L, span.attributes[AttributeKey.longKey("number")])
    assertNotNull(span.attributes[AttributeKey.longKey("firstElementShown")], "firstElementShown was not recorded before the span ended")
  }
}

/**
 * Keeps an autopopup computing until a newer completion cancels it and offers two items to an explicit completion.
 */
internal class AutopopupBlockingContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    if (parameters.isAutoPopup) {
      autopopupStarted.countDown()
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
      while (System.nanoTime() < deadline) {
        ProgressManager.checkCanceled()
        Thread.sleep(10)
      }
      return
    }
    result.addElement(LookupElementBuilder.create("alpha"))
    result.addElement(LookupElementBuilder.create("beta"))
  }

  companion object {
    @Volatile
    var autopopupStarted: CountDownLatch = CountDownLatch(1)
  }
}

/**
 * Span that keeps its attributes and, like an OpenTelemetry span, drops those set after [end].
 */
private class RecordingSpan : Span {
  val attributes: MutableMap<AttributeKey<*>, Any> = ConcurrentHashMap()

  @Volatile
  var ended: Boolean = false

  override fun <T : Any?> setAttribute(key: AttributeKey<T>, value: T?): Span {
    if (!ended && value != null) attributes[key] = value
    return this
  }

  override fun addEvent(name: String, attributes: Attributes): Span = this
  override fun addEvent(name: String, attributes: Attributes, timestamp: Long, unit: TimeUnit): Span = this
  override fun setStatus(statusCode: StatusCode, description: String): Span = this
  override fun recordException(exception: Throwable, additionalAttributes: Attributes): Span = this
  override fun updateName(name: String): Span = this

  override fun end() {
    ended = true
  }

  override fun end(timestamp: Long, unit: TimeUnit) {
    ended = true
  }

  override fun getSpanContext(): SpanContext = SpanContext.getInvalid()
  override fun isRecording(): Boolean = !ended
}

private class TestPlaybackContext(private val project: Project) :
  PlaybackContext(null, { _, _, _ -> }, 1, null, false, false, null, null, emptySet()) {
  override fun pushStage(info: StageInfo) {}
  override fun popStage(): StageInfo? = null
  override fun getCurrentStageDepth(): Int = 0
  override fun addPassed(stage: StageInfo) {}
  override fun isDisposed(): Boolean = false
  override fun storeRegistryValue(key: String) {}
  override fun setProject(project: Project?) {}
  override fun getProject(): Project = project
}
