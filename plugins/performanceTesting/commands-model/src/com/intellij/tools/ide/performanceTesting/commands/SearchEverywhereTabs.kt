package com.intellij.tools.ide.performanceTesting.commands

enum class CommonSearchEverywhereTabs(val tabId: String, val providerClassName: String) {
  ALL("all", "SearchEverywhereContributor.All"),
  CLASS("class", "ClassSearchEverywhereContributor"),
  FILE("file", "FileSearchEverywhereContributor"),
  SYMBOL("symbol", "SymbolSearchEverywhereContributor"),
  ACTION("action", "ActionSearchEverywhereContributor"),
  TEXT("text", "TextSearchContributor"),
}