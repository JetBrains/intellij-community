// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.webview.preview

import junit.framework.TestCase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
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

  fun `test default content update payload inherits WebView font size`() {
    val payload = Json.encodeToJsonElement(
      MarkdownContentChangedParams.serializer(),
      MarkdownContentChangedParams(
        markdown = "# Title",
        scrollLine = 3,
        settings = MarkdownPreviewSettingsParams(fontSize = null),
      ),
    ).jsonObject

    assertEquals(JsonNull, payload.getValue("settings").jsonObject.getValue("fontSize"))
  }

  fun `test content update payload includes content version`() {
    val payload = Json.encodeToJsonElement(
      MarkdownContentChangedParams.serializer(),
      MarkdownContentChangedParams(
        markdown = "```shell\npwd\n```",
        scrollLine = 0,
        settings = MarkdownPreviewSettingsParams(fontSize = 14),
        contentVersion = 42,
      ),
    ).jsonObject

    assertEquals("42", payload.getValue("contentVersion").jsonPrimitive.content)
  }

  fun `test resolve run commands payload includes command candidates`() {
    val payload = Json.encodeToJsonElement(
      MarkdownResolveRunCommandsParams.serializer(),
      MarkdownResolveRunCommandsParams(
        contentVersion = 42,
        candidates = listOf(
          MarkdownCommandCandidate(
            id = "command-id",
            kind = MarkdownPreviewCommandKind.BLOCK,
            startLine = 1,
            startColumn = 1,
            endLine = 3,
            endColumn = 4,
            rawCommand = "pwd\n",
            language = "shell",
          ),
        ),
      ),
    ).jsonObject

    val command = payload.getValue("candidates").jsonArray.single().jsonObject
    assertEquals("command-id", command.getValue("id").jsonPrimitive.content)
    assertEquals("BLOCK", command.getValue("kind").jsonPrimitive.content)
    assertEquals("pwd\n", command.getValue("rawCommand").jsonPrimitive.content)
  }

  fun `test resolved run commands payload includes command descriptors`() {
    val payload = Json.encodeToJsonElement(
      MarkdownResolvedRunCommandsParams.serializer(),
      MarkdownResolvedRunCommandsParams(
        commands = listOf(
          MarkdownCommandDescriptor(
            id = "command-id",
            kind = MarkdownPreviewCommandKind.BLOCK,
            startLine = 1,
            startColumn = 1,
            endLine = 3,
            endColumn = 4,
            title = "Run block",
          ),
        ),
      ),
    ).jsonObject

    val command = payload.getValue("commands").jsonArray.single().jsonObject
    assertEquals("command-id", command.getValue("id").jsonPrimitive.content)
  }
}
