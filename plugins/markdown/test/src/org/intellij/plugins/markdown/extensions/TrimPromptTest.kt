// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.extensions

import org.intellij.plugins.markdown.extensions.jcef.commandRunner.CommandRunnerExtension
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TrimPromptTest {

  private fun strip(line: String): String = CommandRunnerExtension.trimPrompt(line)

  @Test fun `plain command is unchanged`() {
    assertEquals("npm run dev", strip("npm run dev"))
  }

  @Test fun `trailing hash comment is stripped along with its leading whitespace`() {
    assertEquals("npm run dev", strip("npm run dev       # start Vite dev server (HMR, localhost:5173)"))
  }

  @Test fun `trailing hash comment with single leading space is stripped`() {
    assertEquals("npm run dev", strip("npm run dev # comment"))
  }

  @Test fun `pure comment line collapses to empty`() {
    assertEquals("", strip("# just a comment"))
  }

  @Test fun `hash inside a word is not a comment`() {
    assertEquals("echo hello#world", strip("echo hello#world"))
  }

  @Test fun `hash inside double quotes is preserved`() {
    assertEquals("""curl -H "X-Frag: #foo"""", strip("""curl -H "X-Frag: #foo""""))
  }

  @Test fun `hash inside single quotes is preserved`() {
    assertEquals("echo 'hi # not comment'", strip("echo 'hi # not comment'"))
  }

  @Test fun `hash after closed quote is treated as comment`() {
    assertEquals("""echo "hi"""", strip("""echo "hi" # trailing"""))
  }

  @Test fun `backslash-escaped hash is preserved`() {
    assertEquals("""echo hello \# world""", strip("""echo hello \# world"""))
  }

  @Test fun `leading dollar prompt is trimmed`() {
    assertEquals(" npm install", strip("\$ npm install"))
  }

  @Test fun `leading dollar prompt and trailing comment are both trimmed`() {
    assertEquals(" npm install", strip("\$ npm install # deps"))
  }

  @Test fun `multiline preserves ordering and strips per line`() {
    val input = """
      npm install # deps
      npm run dev # start
    """.trimIndent()
    assertEquals("npm install\nnpm run dev", strip(input))
  }

  @Test fun `multiline with a pure comment line drops that line`() {
    val input = """
      # header
      npm run dev
    """.trimIndent()
    assertEquals("npm run dev", strip(input))
  }

  @Test fun `double-quoted string containing single quote does not open single-quote mode`() {
    assertEquals("""echo "it's fine"""", strip("""echo "it's fine" # yes"""))
  }

  @Test fun `single-quoted string containing double quote does not open double-quote mode`() {
    assertEquals("""echo 'say "hi"'""", strip("""echo 'say "hi"' # yes"""))
  }

  @Test fun `empty input returns empty`() {
    assertEquals("", strip(""))
  }
}
