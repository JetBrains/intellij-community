// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.preview.jcef

import com.intellij.find.SearchReplaceComponent
import com.intellij.idea.TestFor
import com.intellij.markdown.jcef.preview.JCEFHtmlPanelProvider
import com.intellij.markdown.jcef.preview.MarkdownJCEFHtmlPanel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
class MarkdownPreviewSearchTest {
  private val project by projectFixture()

  @Test
  @TestFor(issues = ["IJPL-263048"])
  fun `typed query replaces the previous query after find opens the search bar again`(): Unit = timeoutRunBlocking(JCEF_STARTUP_TIMEOUT) {
    assumeTrue(JCEFHtmlPanelProvider.canBeUsed(), "The preview needs JCEF")
    val panel = withContext(Dispatchers.EDT) {
      MarkdownJCEFHtmlPanel(project, LightVirtualFile("some.md", "alpha beta"))
    }
    try {
      val searchComponent = panel.awaitSearchComponent()
      val query = withContext(Dispatchers.EDT) {
        val field = searchComponent.searchTextComponent
        panel.showSearchBar()
        field.text = "alpha"
        panel.showSearchBar()
        field.replaceSelection("beta")
        field.text
      }
      assertThat(query).isEqualTo("beta")
    }
    finally {
      withContext(NonCancellable + Dispatchers.EDT) {
        Disposer.dispose(panel)
      }
    }
  }

  private suspend fun MarkdownJCEFHtmlPanel.awaitSearchComponent(): SearchReplaceComponent {
    var searchComponent: SearchReplaceComponent? = null
    waitUntil("The preview did not create the search bar", JCEF_STARTUP_TIMEOUT) {
      searchComponent = withContext(Dispatchers.EDT) {
        UIUtil.findComponentOfType(component, SearchReplaceComponent::class.java)
      }
      searchComponent != null
    }
    return searchComponent!!
  }

  private companion object {
    val JCEF_STARTUP_TIMEOUT = 60.seconds
  }
}
