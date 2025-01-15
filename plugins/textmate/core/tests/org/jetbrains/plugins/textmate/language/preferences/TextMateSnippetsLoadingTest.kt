package org.jetbrains.plugins.textmate.language.preferences;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl;
import org.junit.Test;

import java.util.Collection;
import java.util.Iterator;

import static org.jetbrains.plugins.textmate.TestUtil.*;
import static org.junit.Assert.*;

public class TextMateSnippetsLoadingTest {
  @Test
  public void testLoadTextMateSnippets() {
    SnippetsRegistry snippetsRegistry = loadSnippets(CHEF);
    Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet("log", scopeFromString("source.ruby.chef.something"));
    assertEquals(1, snippets.size());
    TextMateSnippet snippet = snippets.iterator().next();
    assertNotNull(snippet);
    assertEquals("log", snippet.getKey());
    assertEquals("Chef::Log.${1:debug} \"${2:something to log}\"", snippet.getContent());
    assertEquals("chef_log", snippet.getName());
    assertEquals("18C24F07-1726-490B-84BA-4BF83263C6EE", snippet.getSettingsId());
  }

  @Test
  public void testLoadTextMateSnippetsWithInvalidSelector() {
    SnippetsRegistry snippetsRegistry = loadSnippets(CHEF);
    Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet("log", new TextMateScope("source", null));
    assertTrue(snippets.isEmpty());
  }
  
  @Test
  public void testLoadTextMateSnippetsFromPlist() {
    SnippetsRegistry snippetsRegistry = loadSnippets(HTML);
    Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet("div", new TextMateScope("text.html", null));
    assertEquals(1, snippets.size());
    TextMateSnippet snippet = snippets.iterator().next();
    assertNotNull(snippet);
    assertEquals("div", snippet.getKey());
    assertEquals("""
                   <div${1: id="${2:name}"}>
                   \t${0:$TM_SELECTED_TEXT}
                   </div>""", snippet.getContent());
    assertEquals("Div", snippet.getName());
    assertEquals("576036C0-A60E-11D9-ABD6-000D93C8BE28", snippet.getSettingsId());
  }

  @NotNull
  private static SnippetsRegistry loadSnippets(@NotNull String bundleName) {
    Iterator<TextMateSnippet> snippets = readBundle(bundleName).readSnippets().iterator();
    final SnippetsRegistryImpl snippetsRegistry = new SnippetsRegistryImpl(new TextMateSelectorWeigherImpl());
    while (snippets.hasNext()) {
      snippetsRegistry.register(snippets.next());
    }
    return snippetsRegistry;
  }
}
