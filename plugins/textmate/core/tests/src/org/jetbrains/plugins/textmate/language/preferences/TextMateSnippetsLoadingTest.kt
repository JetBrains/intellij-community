package org.jetbrains.plugins.textmate.language.preferences

import org.jetbrains.plugins.textmate.TestUtil.CHEF
import org.jetbrains.plugins.textmate.TestUtil.HTML
import org.jetbrains.plugins.textmate.TestUtil.readBundle
import org.jetbrains.plugins.textmate.TestUtil.scopeFromString
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.plist.XmlPlistReaderForTests
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TextMateSnippetsLoadingTest {
  @Test
  fun testLoadTextMateSnippets() {
    val snippetsRegistry: SnippetsRegistry = loadSnippets(CHEF)
    val snippets = snippetsRegistry.findSnippet("log", scopeFromString("source.ruby.chef.something"))
    assertEquals(1, snippets.size)
    val snippet = snippets.iterator().next()
    assertNotNull(snippet)
    assertEquals("log", snippet.key)
    assertEquals("Chef::Log.\${1:debug} \"\${2:something to log}\"", snippet.content)
    assertEquals("chef_log", snippet.name)
    assertEquals("18C24F07-1726-490B-84BA-4BF83263C6EE", snippet.settingsId)
  }

  @Test
  fun testLoadTextMateSnippetsWithInvalidSelector() {
    val snippetsRegistry: SnippetsRegistry = loadSnippets(CHEF)
    val snippets = snippetsRegistry.findSnippet("log", TextMateScope("source", null))
    assertTrue(snippets.isEmpty())
  }

  @Test
  fun testLoadTextMateSnippetsFromPlist() {
    val snippetsRegistry: SnippetsRegistry = loadSnippets(HTML)
    val snippets = snippetsRegistry.findSnippet("div", TextMateScope("text.html", null))
    assertEquals(1, snippets.size)
    val snippet = snippets.iterator().next()
    assertNotNull(snippet)
    assertEquals("div", snippet.key)
    assertEquals("""
                   <div${'$'}{1: id="${'$'}{2:name}"}>
                   ${'\t'}${'$'}{0:${'$'}TM_SELECTED_TEXT}
                   </div>
                   """.trimIndent(), snippet.content)
    assertEquals("Div", snippet.name)
    assertEquals("576036C0-A60E-11D9-ABD6-000D93C8BE28", snippet.settingsId)
  }

  companion object {
    private fun loadSnippets(bundleName: String): SnippetsRegistry {
      val snippets = readBundle(bundleName, XmlPlistReaderForTests()).readSnippets().iterator()
      val snippetsRegistryBuilder = SnippetsRegistryBuilder(TextMateSelectorWeigherImpl())
      while (snippets.hasNext()) {
        snippetsRegistryBuilder.register(snippets.next())
      }
      return snippetsRegistryBuilder.build()
    }
  }
}
