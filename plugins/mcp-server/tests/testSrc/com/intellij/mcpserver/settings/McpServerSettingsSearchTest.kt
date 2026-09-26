// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mcpserver.settings

import com.intellij.ide.ui.search.ComponentHighlightingListener
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.ui.search.SearchUtil
import com.intellij.mcpserver.McpServerBundle
import com.intellij.mcpserver.frontend.settings.McpServerSettingsConfigurable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * What Settings search can find on the MCP page.
 *
 * The traversal builds a page off-screen and never shows it, so what it reads is whatever the page holds
 * before it reaches a window - which for a Compose page is everything, because the composition runs on the
 * call that builds it.
 *
 * What it reads is the components the page holds, so an option the page does not compose is not indexed. The
 * page composes the groups behind the server being enabled, and these run against a page with it off.
 */
@TestApplication
class McpServerSettingsSearchTest {

  private fun optionsOf(configurable: McpServerSettingsConfigurable, component: JComponent): Set<OptionDescription> =
    mutableSetOf<OptionDescription>().also { SearchUtil.processComponent(configurable, it, component, false) }

  @Test
  fun theOptionsOnThePageAreIndexed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = McpServerSettingsConfigurable()
    try {
      // An option is indexed once per word of its text, and every one of them carries the whole text as
      // the hit, which is what the search results show.
      val hits = optionsOf(configurable, configurable.createComponent()).map { it.hit }

      assertThat(hits).contains(McpServerBundle.message("enable.mcp.server"))
      assertThat(hits).contains(McpServerBundle.message("settings.terminal.promotion.show"))
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  @Test
  fun spotlightHighlightsARealCheckBox(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = McpServerSettingsConfigurable()
    try {
      val component = configurable.createComponent()
      val highlighted = mutableListOf<JComponent>()
      val connection = ApplicationManager.getApplication().messageBus.connect()
      connection.subscribe(
        ComponentHighlightingListener.TOPIC,
        ComponentHighlightingListener { component, _ -> highlighted += component },
      )
      try {
        SearchUtil.lightOptions(configurable, component, "terminal")
      }
      finally {
        connection.disconnect()
      }

      assertThat(highlighted).anyMatch { it is JCheckBox }
    }
    finally {
      configurable.disposeUIResources()
    }
  }
}
