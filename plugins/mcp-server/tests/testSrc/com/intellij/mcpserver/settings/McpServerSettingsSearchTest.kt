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
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox
import javax.swing.JComponent

/**
 * What Settings search can find on the MCP page.
 *
 * Both tests are disabled because the page composes only once it reaches a window, and the search traversal
 * builds pages off-screen and never shows them - so it walks an empty panel. They are written against the
 * behaviour that is wanted, not the behaviour there is, and they are what says so when the mount arrives.
 */
@TestApplication
class McpServerSettingsSearchTest {

  private fun optionsOf(configurable: McpServerSettingsConfigurable, component: JComponent): Set<OptionDescription> =
    mutableSetOf<OptionDescription>().also { SearchUtil.processComponent(configurable, it, component, false) }

  // TODO Enable once compose-swing-ui exposes a public mount-immediately entry point and
  //  ComposeSwingSearchableConfigurable.createComponent mounts synchronously. Until then the traversal sees
  //  no components and this finds nothing.
  @Disabled("The page composes when it reaches a window, so an off-screen traversal indexes nothing")
  @Test
  fun theOptionsOnThePageAreIndexed(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val configurable = McpServerSettingsConfigurable()
    try {
      val options = optionsOf(configurable, configurable.createComponent())
      val labels = options.mapNotNull { it.option }

      assertThat(labels).anyMatch { it.contains(McpServerBundle.message("settings.enable.mcp.server"), ignoreCase = true) }
      assertThat(labels).anyMatch { it.contains(McpServerBundle.message("settings.enable.brave.mode"), ignoreCase = true) }
    }
    finally {
      configurable.disposeUIResources()
    }
  }

  // TODO Enable together with the test above - spotlight walks the live Swing tree, so it has the same
  //  dependency on the page having composed.
  @Disabled("The page composes when it reaches a window, so there is nothing for spotlight to walk")
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
        SearchUtil.lightOptions(configurable, component, "brave")
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
