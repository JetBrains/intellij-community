// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.documentation.docstrings.SphinxDocString;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.toolbox.Substring;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public class SphinxDocstringTest extends PyTestCase {
  private static SphinxDocString createSphinxDocstring(@Language("TEXT") @NotNull String unescapedContentWithoutQuotes) {
    return new SphinxDocString(new Substring(unescapedContentWithoutQuotes));
  }

  public void testFieldAliases() {
    final SphinxDocString docstring = createSphinxDocstring(":param p1: p1 description\n" +
                                                            ":parameter p2: p2 description\n" +
                                                            ":arg p3: p3 description\n" +
                                                            ":argument p4: p4 description\n" +
                                                            "\n" +
                                                            ":key key1: key1 description\n" +
                                                            ":keyword key2: key2 description\n" +
                                                            "\n" +
                                                            ":raises Exc1: Exc1 description  \n" +
                                                            ":raise Exc2: Exc2 description \n" +
                                                            ":except Exc3: Exc3 description \n" +
                                                            ":exception Exc4: Exc4 description ");

    assertSameElements(docstring.getParameters(), "p1", "p2", "p3", "p4");
    assertEquals("p1 description", docstring.getParamDescription("p1"));
    assertEquals("p2 description", docstring.getParamDescription("p2"));
    assertEquals("p3 description", docstring.getParamDescription("p3"));
    assertEquals("p4 description", docstring.getParamDescription("p4"));

    assertSameElements(docstring.getKeywordArguments(), "key1", "key2");
    assertEquals("key1 description", docstring.getKeywordArgumentDescription("key1"));
    assertEquals("key2 description", docstring.getKeywordArgumentDescription("key2"));

    assertSameElements(docstring.getRaisedExceptions(), "Exc1", "Exc2", "Exc3", "Exc4");
    assertEquals("Exc1 description", docstring.getRaisedExceptionDescription("Exc1"));
    assertEquals("Exc2 description", docstring.getRaisedExceptionDescription("Exc2"));
    assertEquals("Exc3 description", docstring.getRaisedExceptionDescription("Exc3"));
    assertEquals("Exc4 description", docstring.getRaisedExceptionDescription("Exc4"));
  }
}
