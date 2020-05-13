package org.jetbrains.plugins.textmate.language.preferences;

import com.intellij.util.containers.Interner;
import com.intellij.util.containers.PathInterner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.textmate.bundles.Bundle;
import org.jetbrains.plugins.textmate.language.PreferencesReadUtil;
import org.jetbrains.plugins.textmate.plist.CompositePlistReader;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import static org.jetbrains.plugins.textmate.TestUtil.*;
import static org.junit.Assert.*;

public class TextMateSnippetsLoadingTest {
  @Test
  public void testLoadTextMateSnippets() throws IOException {
    SnippetsRegistry snippetsRegistry = loadSnippets(CHEF);
    Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet("log", "source.ruby.chef.something");
    assertEquals(1, snippets.size());
    TextMateSnippet snippet = snippets.iterator().next();
    assertNotNull(snippet);
    assertEquals("log", snippet.getKey());
    assertEquals("Chef::Log.${1:debug} \"${2:something to log}\"", snippet.getContent());
    assertEquals("chef_log", snippet.getName());
    assertEquals("18C24F07-1726-490B-84BA-4BF83263C6EE", snippet.getSettingsId());
  }

  @Test
  public void testLoadTextMateSnippetsWithInvalidSelector() throws IOException {
    SnippetsRegistry snippetsRegistry = loadSnippets(CHEF);
    Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet("log", "source");
    assertTrue(snippets.isEmpty());
  }
  
  @Test
  public void testLoadTextMateSnippetsFromPlist() throws IOException {
    SnippetsRegistry snippetsRegistry = loadSnippets(HTML);
    Collection<TextMateSnippet> snippets = snippetsRegistry.findSnippet("div", "text.html");
    assertEquals(1, snippets.size());
    TextMateSnippet snippet = snippets.iterator().next();
    assertNotNull(snippet);
    assertEquals("div", snippet.getKey());
    assertEquals("<div${1: id=\"${2:name}\"}>\n" +
                 "\t${0:$TM_SELECTED_TEXT}\n" +
                 "</div>", snippet.getContent());
    assertEquals("Div", snippet.getName());
    assertEquals("576036C0-A60E-11D9-ABD6-000D93C8BE28", snippet.getSettingsId());
  }

  @NotNull
  private static SnippetsRegistry loadSnippets(@NotNull String bundleName) throws IOException {
    final Bundle bundle = getBundle(bundleName);
    assertNotNull(bundle);
    final SnippetsRegistry snippetsRegistry = new SnippetsRegistry();
    Interner<CharSequence> interner = new PathInterner.PathEnumerator();
    for (File file : bundle.getSnippetFiles()) {
      final TextMateSnippet snippet = PreferencesReadUtil.loadSnippet(file, new CompositePlistReader().read(file), interner);
      if (snippet != null) {
        snippetsRegistry.register(snippet);
      }
    }
    return snippetsRegistry;
  }
}
