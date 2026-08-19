// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xmlb.XmlSerializer
import com.jetbrains.python.documentation.PyDocumentationSettings.ServiceState
import com.jetbrains.python.documentation.docstrings.DocStringFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression test for PY-91090: a stored docstring-format value that no valid build recognizes anymore
 * (e.g. `format="EPYTEXT"`, removed by PY-52574) must not break deserialization of [ServiceState].
 */
@TestApplication
internal class PyDocumentationSettingsSerializationTest {
  @Test
  fun `legacy state with removed docstring format deserializes to default instead of failing`() {
    // Component as written by an older build: the removed EPYTEXT enum constant leaked into the "format" option
    // (from the getFormat()/setFormat() accessor pair). Reading it used to call setFormat(null) and throw.
    val element = JDOMUtil.load(
      """
      <component name="PyDocumentationSettings">
        <option name="analyzeDoctest" value="false" />
        <option name="renderExternalDocumentation" value="true" />
        <option name="myDocStringFormat" value="Epytext" />
        <option name="format" value="EPYTEXT" />
      </component>
      """.trimIndent()
    )

    val state = XmlSerializer.deserialize(element, ServiceState::class.java)

    // The removed value falls back to PLAIN via setFormatName(); the redundant "format" option is ignored.
    assertEquals(DocStringFormat.PLAIN, state.format)
    assertFalse(state.myAnalyzeDoctest)
    assertTrue(state.myRenderExternalDocumentation)
  }

  @Test
  fun `format is no longer serialized redundantly`() {
    val state = ServiceState(DocStringFormat.NUMPY)

    val serialized = JDOMUtil.write(XmlSerializer.serialize(state))

    // Only the legacy "myDocStringFormat" option persists the format; the fragile "format" option is gone.
    assertTrue(serialized.contains("myDocStringFormat"), serialized)
    assertFalse(serialized.contains("name=\"format\""), serialized)
  }
}
