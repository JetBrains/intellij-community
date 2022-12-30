// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.documentation.docstrings.EpydocString;
import com.jetbrains.python.toolbox.Substring;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.testFramework.UsefulTestCase.assertOrderedEquals;
import static com.intellij.testFramework.UsefulTestCase.assertSameElements;


public class EpydocStringTest extends TestCase {
  public void testTagValue() {
    EpydocString docString = createEpydocDocString("@rtype: C{str}");
    Substring s = docString.getTagValue("rtype");
    assertNotNull(s);
    assertEquals("C{str}", s.toString());
  }

  @NotNull
  private EpydocString createEpydocDocString(String s) {
    return new EpydocString(new Substring(s));
  }

  public void testTagWithParamValue() {
    EpydocString docString = createEpydocDocString("@type m: number");
    final Substring s = docString.getTagValue("type", "m");
    assertNotNull(s);
    assertEquals("number", s.toString());
  }

  public void testMultilineTag() {
    EpydocString docString = createEpydocDocString("    @param b: The y intercept of the line.  The X{y intercept} of a\n" +
                                                   "              line is the point at which it crosses the y axis (M{x=0}).");
    final Substring s = docString.getTagValue("param", "b");
    assertNotNull(s);
    assertEquals("The y intercept of the line.  The X{y intercept} of a line is the point at which it crosses the y axis (M{x=0}).",
                 s.concatTrimmedLines(" "));

  }

  public void testInlineMarkup() {
    assertEquals("The y intercept of the line.  The y intercept of a line is the point at which it crosses the y axis (x=0).",
                 EpydocString.removeInlineMarkup("The y intercept of the line.  The X{y intercept} of a line is the point at which it crosses the y axis (M{x=0})."));

  }

  public void testMultipleTags() {
    EpydocString docString = createEpydocDocString("""
                                                         Run the given function wrapped with seteuid/setegid calls.

                                                         This will try to minimize the number of seteuid/setegid calls, comparing
                                                         current and wanted permissions

                                                         @param euid: effective UID used to call the function.
                                                         @type  euid: C{int}

                                                         @param egid: effective GID used to call the function.
                                                         @type  egid: C{int}

                                                         @param function: the function run with the specific permission.
                                                         @type  function: any callable

                                                         @param *args: arguments passed to function
                                                         @param **kwargs: keyword arguments passed to C{function}
                                                        \s
                                                         @return: the return value of the function\
                                                     """);

    final List<String> params = docString.getParameters();
    assertOrderedEquals(params, "euid", "egid", "function", "*args", "**kwargs");
    assertEquals("effective UID used to call the function.", docString.getParamDescription("euid"));
    assertEquals("effective GID used to call the function.", docString.getParamDescription("egid"));
    assertEquals("arguments passed to function", docString.getParamDescription("args"));
    assertEquals("the return value of the function", docString.getReturnDescription());
  }

  public void testInlineMarkupToHTML() {
    assertEquals("can contain <i>inline markup</i> and <b>bold text</b>", EpydocString.inlineMarkupToHTML("can contain I{inline markup} and B{bold text}"));
  }

  public void testCodeToHTML() {
    assertEquals("<code>my_dict={1:2, 3:4}</code>", EpydocString.inlineMarkupToHTML("C{my_dict={1:2, 3:4}}"));
  }

  public void testUrlToHTML() {
    assertEquals("<a href=\"http://www.python.org\">www.python.org</a>", EpydocString.inlineMarkupToHTML("U{www.python.org}"));
    assertEquals("<a href=\"http://www.python.org\">www.python.org</a>", EpydocString.inlineMarkupToHTML("U{www.python.org}"));
    assertEquals("<a href=\"http://epydoc.sourceforge.net\">The epydoc homepage</a>",
                 EpydocString.inlineMarkupToHTML("U{The epydoc homepage<http://\n" +
                                                 "    epydoc.sourceforge.net>}"));
  }

  public void testNestedInlineMarkup() {
    assertEquals("<i><b>Inline markup</b> may be nested; and it may span</i> multiple lines.",
                 EpydocString.inlineMarkupToHTML("I{B{Inline markup} may be nested; and\n" +
                                                 "    it may span} multiple lines."));

  }

  public void testParagraph() {
    assertEquals("foo<p>bar", EpydocString.inlineMarkupToHTML("foo\n\nbar"));
  }

  public void testRemoveNestedInlineMarkup() {
    assertEquals("(ParsedDocstring, list of Field)",
                 EpydocString.removeInlineMarkup("C{(L{ParsedDocstring}, list of L{Field})}"));
  }

  public void testDifferentExceptionTags() {
    final EpydocString docstring = createEpydocDocString("@raises Exception1: foo\n" +
                                                         "@except Exception2: bar");
    assertSameElements(docstring.getRaisedExceptions(), "Exception1", "Exception2");
    assertEquals("foo", docstring.getRaisedExceptionDescription("Exception1"));
    assertEquals("bar", docstring.getRaisedExceptionDescription("Exception2"));
  }

  public void testFieldAliases() {
    final EpydocString docstring = createEpydocDocString("""
                                                           @param p1: p1 description
                                                           @parameter p2: p2 description
                                                           @arg p3: p3 description
                                                           @argument p4: p4 description

                                                           @kwarg key1: key1 description
                                                           @keyword key2: key2 description
                                                           @kwparam key3: key3 description

                                                           @raises Exc1: Exc1 description \s
                                                           @raise Exc2: Exc2 description\s
                                                           @except Exc3: Exc3 description\s
                                                           @exception Exc4: Exc4 description\s""");

    assertSameElements(docstring.getParameters(), "p1", "p2", "p3", "p4");
    assertEquals("p1 description", docstring.getParamDescription("p1"));
    assertEquals("p2 description", docstring.getParamDescription("p2"));
    assertEquals("p3 description", docstring.getParamDescription("p3"));
    assertEquals("p4 description", docstring.getParamDescription("p4"));

    assertSameElements(docstring.getKeywordArguments(), "key1", "key2", "key3");
    assertEquals("key1 description", docstring.getKeywordArgumentDescription("key1"));
    assertEquals("key2 description", docstring.getKeywordArgumentDescription("key2"));
    assertEquals("key3 description", docstring.getKeywordArgumentDescription("key3"));

    assertSameElements(docstring.getRaisedExceptions(), "Exc1", "Exc2", "Exc3", "Exc4");
    assertEquals("Exc1 description", docstring.getRaisedExceptionDescription("Exc1"));
    assertEquals("Exc2 description", docstring.getRaisedExceptionDescription("Exc2"));
    assertEquals("Exc3 description", docstring.getRaisedExceptionDescription("Exc3"));
    assertEquals("Exc4 description", docstring.getRaisedExceptionDescription("Exc4"));
  }
}
