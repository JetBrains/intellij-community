// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("DEPRECATION")
@TestApplication
class MarkdownSettingsCompatibilityTest {
  private val project by projectFixture(openProjectTask = createTestOpenProjectOptions(), openAfterCreation = true)
  private val otherProject by projectFixture(openProjectTask = createTestOpenProjectOptions(), openAfterCreation = true)
  private lateinit var originalState: MarkdownSettingsState

  @BeforeEach
  fun resetSettings() {
    val settings = MarkdownSettings.getInstance()
    originalState = settings.state
    settings.loadState(MarkdownSettingsState())
  }

  @AfterEach
  fun restoreSettings() {
    MarkdownSettings.getInstance().loadState(originalState)
  }

  @Test
  fun `deprecated accessors share application preferences`() {
    val settings = MarkdownSettings.getInstance()
    val adapter = MarkdownSettings.getInstance(project)
    assertSame(adapter, MarkdownSettings.getInstance(project))
    assertNotSame(adapter, MarkdownSettings.getInstance(otherProject))

    adapter.customStylesheetText = "body { color: red; }"
    assertEquals(adapter.customStylesheetText, settings.customStylesheetText)
    assertEquals(adapter.customStylesheetText, MarkdownSettings.getInstance(otherProject).customStylesheetText)

    settings.isAutoScrollEnabled = false
    settings.isVerticalSplit = false
    assertFalse(adapter.isAutoScrollEnabled)
    assertFalse(adapter.isVerticalSplit)
  }

  @Test
  fun `legacy synchronous JVM accessors remain available`() {
    val adapter = MarkdownSettings.getInstance(project)
    val staticAccessor = MarkdownSettings::class.java.getMethod("getInstance", Project::class.java)
    assertSame(adapter, staticAccessor.invoke(null, project))
    val companionAccessor = MarkdownSettings.Companion::class.java.getMethod("getInstance", Project::class.java)
    assertSame(adapter, companionAccessor.invoke(MarkdownSettings.Companion, project))
  }

  @Test
  fun `project adapters follow application state reloads`() {
    val adapter = MarkdownSettings.getInstance(project)
    MarkdownSettings.getInstance().loadState(MarkdownSettingsState().apply {
      isAutoScrollEnabled = false
      customStylesheetText = "body { color: red; }"
    })

    assertFalse(adapter.isAutoScrollEnabled)
    assertEquals("body { color: red; }", adapter.customStylesheetText)
    adapter.customStylesheetText = "body { color: blue; }"
    assertEquals(adapter.customStylesheetText, MarkdownSettings.getInstance().customStylesheetText)
  }

  @Test
  fun `deprecated stylesheet properties keep their project scope`() {
    val first = MarkdownSettings.getInstance(project)
    val second = MarkdownSettings.getInstance(otherProject)
    first.customStylesheetPath = "/first.css"
    first.useCustomStylesheetPath = true
    second.customStylesheetPath = "/second.css"
    second.useCustomStylesheetPath = true

    assertEquals("/first.css", MarkdownStylesheetSettings.getInstance(project).customStylesheetPath)
    assertEquals("/second.css", MarkdownStylesheetSettings.getInstance(otherProject).customStylesheetPath)
    assertTrue(MarkdownStylesheetSettings.getInstance(project).useCustomStylesheetPath)
    MarkdownStylesheetSettings.getInstance(project).customStylesheetPath = null
    assertNull(first.customStylesheetPath)
    assertEquals("/second.css", second.customStylesheetPath)
    assertNull(MarkdownSettings.getInstance().state.customStylesheetPath)
    assertFalse(MarkdownSettings.getInstance().state.useCustomStylesheetPath)
  }

  @Test
  fun `application and project listeners receive one event with the correct settings`(@TestDisposable disposable: Disposable): Unit =
    timeoutRunBlocking {
      val settings = MarkdownSettings.getInstance()
      val first = MarkdownSettings.getInstance(project)
      val second = MarkdownSettings.getInstance(otherProject)
      val applicationEvents = mutableListOf<Boolean>()
      val firstEvents = mutableListOf<Boolean>()
      val secondEvents = mutableListOf<Boolean>()
      fun listener(expected: MarkdownSettings, events: MutableList<Boolean>) = object : MarkdownSettings.ChangeListener {
        override fun beforeSettingsChanged(settings: MarkdownSettings) {
          assertSame(expected, settings)
          events.add(settings.useCustomStylesheetText)
        }

        override fun settingsChanged(settings: MarkdownSettings) {
          assertSame(expected, settings)
          events.add(settings.useCustomStylesheetText)
        }
      }
      application.messageBus.connect(disposable).subscribe(MarkdownSettings.ChangeListener.TOPIC, listener(settings, applicationEvents))
      project.messageBus.connect(disposable).subscribe(MarkdownSettings.ChangeListener.TOPIC, listener(first, firstEvents))
      otherProject.messageBus.connect(disposable).subscribe(MarkdownSettings.ChangeListener.TOPIC, listener(second, secondEvents))

      withContext(Dispatchers.EDT) {
        first.update {
          assertSame(first, it)
          it.useCustomStylesheetText = true
        }
      }

      assertEquals(listOf(false, true), applicationEvents)
      assertEquals(listOf(false, true), firstEvents)
      assertEquals(listOf(false, true), secondEvents)
    }

  @Test
  fun `preview service delegates state and follows reloads`() {
    val settings = MarkdownSettings.getInstance()
    settings.fontSize = 23
    val preview = service<MarkdownPreviewSettings>()
    val retainedState = preview.state
    assertEquals(23, retainedState.fontSize)
    retainedState.fontSize = 27
    assertEquals(27, settings.fontSize)

    val snapshot = MarkdownPreviewSettings.State().apply { fontSize = 31 }
    assertEquals(27, settings.fontSize)
    assertEquals(31, snapshot.fontSize)

    settings.loadState(MarkdownSettingsState().apply { fontSize = 21 })
    assertEquals(21, retainedState.fontSize)
    assertSame(retainedState, preview.state)
  }

  @Test
  fun `preview updates notify old and new listeners once`(@TestDisposable disposable: Disposable): Unit = timeoutRunBlocking {
    val settings = MarkdownSettings.getInstance()
    val preview = service<MarkdownPreviewSettings>()
    val currentEvents = mutableListOf<Int>()
    val legacyEvents = mutableListOf<Int>()
    val connection = application.messageBus.connect(disposable)
    connection.subscribe(MarkdownSettings.ChangeListener.TOPIC, object : MarkdownSettings.ChangeListener {
      override fun settingsChanged(settings: MarkdownSettings) {
        currentEvents.add(settings.fontSize)
      }
    })
    connection.subscribe(MarkdownPreviewSettings.ChangeListener.TOPIC, MarkdownPreviewSettings.ChangeListener {
      assertSame(preview, it)
      legacyEvents.add(it.state.fontSize)
    })

    withContext(Dispatchers.EDT) {
      preview.update { it.state.fontSize = 25 }
      settings.update { it.fontSize = 30 }
    }

    assertEquals(listOf(25, 30), currentEvents)
    assertEquals(listOf(25, 30), legacyEvents)
  }
}
