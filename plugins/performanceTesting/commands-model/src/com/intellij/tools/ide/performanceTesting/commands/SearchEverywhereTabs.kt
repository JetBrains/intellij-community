// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.ide.performanceTesting.commands

enum class CommonSearchEverywhereTabs(val tabId: String, val providerClassName: String) {
  ALL("all", "SearchEverywhereContributor.All"),
  CLASS("class", "ClassSearchEverywhereContributor"),
  FILE("file", "FileSearchEverywhereContributor"),
  SYMBOL("symbol", "SymbolSearchEverywhereContributor"),
  ACTION("action", "ActionSearchEverywhereContributor"),
  TEXT("text", "TextSearchContributor"),
}