// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.editor

import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.intellij.plugins.markdown.settings.MarkdownCodeInsightSettings
import org.jetbrains.annotations.TestOnly

/**
 * Applies [transform] to the state of [MarkdownCodeInsightSettings] before each test, and resets the settings after each test.
 * Declare the result as an instance field, so that each test gets its own fixture.
 */
@TestOnly
fun markdownCodeInsightSettingsFixture(transform: (MarkdownCodeInsightSettings.State) -> Unit): TestFixture<MarkdownCodeInsightSettings> =
  testFixture("markdownCodeInsightSettingsFixture") {
    val settings = MarkdownCodeInsightSettings.getInstance()
    transform(settings.state)
    initialized(settings) {
      settings.reset()
    }
  }
