// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import junit.framework.TestCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class MarkdownPreviewPageApiTest : TestCase() {
  fun `test content update payload includes markdown preview settings`() {
    val payload = Json.encodeToJsonElement(
      MarkdownContentChangedParams.serializer(),
      MarkdownContentChangedParams(
        markdown = "# Title",
        scrollLine = 3,
        settings = MarkdownPreviewSettingsParams(fontSize = 17),
      ),
    ).jsonObject

    assertEquals("17", payload.getValue("settings").jsonObject.getValue("fontSize").jsonPrimitive.content)
  }
}
