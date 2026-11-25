// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.performanceTesting.lsp

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereManager
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.ui.playback.commands.PlaybackCommandCoroutineAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Searches for the given name in the shown Search Everywhere popup, and checks that the results have an item of that name.
 * The Symbols tab shows the workspace symbols of the server among its results. Open the popup on that tab first,
 * with `%searchEverywhere -tab symbol`: the first popup of a session takes longer to open than the search does.
 * The command puts the name into the search field at once, as a paste does, and ends when the search of all
 * contributors of the tab has finished. The popup stays open.
 *
 * Syntax: `%lspSearchEverywhere <the name of the item>`
 */
internal class LspSearchEverywhereCommand(text: String, line: Int) : PlaybackCommandCoroutineAdapter(text, line) {
  companion object {
    const val PREFIX: String = CMD_PREFIX + "lspSearchEverywhere"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val name = extractCommandArgument(PREFIX)
    require(name.isNotBlank()) { "Expected: $PREFIX <the name of the item>, got: '$text'" }
    val search = withContext(Dispatchers.EDT) {
      val popup = SearchEverywhereManager.getInstance(context.project).currentlyShownPopupInstance
                  ?: error("No Search Everywhere popup is shown")
      // the only API that gives the results of a search, which `%searchEverywhere` uses too
      @Suppress("TestOnlyProblems")
      val future = popup.findElementsForPattern(name)
      future
    }
    val items = try {
      withContext(Dispatchers.IO) { search.get(DEFAULT_TIMEOUT.inWholeMilliseconds, TimeUnit.MILLISECONDS) }
    }
    catch (e: TimeoutException) {
      throw IllegalStateException("Search Everywhere did not finish the search of '$name' in $DEFAULT_TIMEOUT", e)
    }
    val names = items.map { it.itemName() }
    check(name in names) { "Search Everywhere found no '$name', it found: $names" }
  }
}

private fun Any.itemName(): String {
  // a goto contributor gives its items in a wrapper with their presentation, as the test fixture of Search Everywhere unwraps them
  @Suppress("DEPRECATION")
  val item = PSIPresentationBgRendererWrapper.getItem(this)
  return (item as? NavigationItem)?.name ?: item.toString()
}
