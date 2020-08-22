// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.traceBackParsers.LinkInTrace;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Ensures we can parse pytest traces for links.
 * This test is JUnit4-based (hence it uses annotations) to support timeout
 *
 * @author Ilya.Kazakevich
 */
public final class PyTestTracebackParserTest {
  private String myStringJunk;
  private String myStringJunkWithSpaces;
  private String myStringJunkWithSpacesAndLine;
  private String myBase64Junk;

  @Before
  public void setUp() {
    // Generate junk to text regexp speed
    final int junkSize = 50000;
    final byte[] junk = new byte[junkSize];
    for (int i = 0; i < junkSize; i++) {
      // We do not care about precision nor security, that junk for tests
      //noinspection NumericCastThatLosesPrecision,UnsecureRandomNumberGeneration
      junk[i] = (byte)(Math.random() * 10);
    }
    final String longString = StringUtil.repeat("1", junkSize);
    myStringJunk = String.format("%s:%s", longString, longString);
    myBase64Junk = Base64.getEncoder().encodeToString(junk);
    myStringJunkWithSpaces = StringUtil.repeat("dd ddddddddd", junkSize);
    myStringJunkWithSpacesAndLine = myStringJunkWithSpaces + " c:/file:12";
  }

  /**
   * Ensures we find link in stack trace
   */
  @Test
  public void testLineWithLink() {
    ensureLinkIsCorrect(" foo/bar.py:42 file ", "foo/bar.py:42", "foo/bar.py", 42);
    ensureLinkIsCorrect("         foo/bar.py:42 file ", "foo/bar.py:42", "foo/bar.py", 42);
    ensureLinkIsCorrect("foo/bar.py:42 file ", "foo/bar.py:42", "foo/bar.py", 42);
    ensureLinkIsCorrect("foo foo/bar.py:42:1 file ", "foo/bar.py:42", "foo/bar.py", 42);
    ensureLinkIsCorrect("Error in file c:/users/foo.py:22", "c:/users/foo.py:22", "c:/users/foo.py", 22);
  }

  @Test
  public void testNoLink() {
    assertNull(new PyTestTracebackParser().findLinkInTrace("    "));
    assertNull(new PyTestTracebackParser().findLinkInTrace("asdasd"));
    assertNull(new PyTestTracebackParser().findLinkInTrace("a/b/c/d"));
  }

  private static void ensureLinkIsCorrect(@NotNull String text,
                                          @NotNull String linkSubText,
                                          @NotNull String fileName,
                                          int lineNumber) {
    LinkInTrace linkInTrace = new PyTestTracebackParser().findLinkInTrace(text);
    assertNotNull("Failed to parse line", linkInTrace);
    assertEquals("Bad file name", fileName, linkInTrace.getFileName());
    assertEquals("Bad line number", lineNumber, linkInTrace.getLineNumber());
    assertEquals(linkSubText, text.substring(linkInTrace.getStartPos(), linkInTrace.getEndPos()));
  }

  /**
   * lines with out of file references should not have links
   */
  @Test
  public void testLineNoLink() {
    assertNull("File with no lines should not work", new PyTestTracebackParser().findLinkInTrace("foo/bar.py file "));
    assertNull("No file name provided, but link found", new PyTestTracebackParser().findLinkInTrace(":12 file "));
  }

  @Test
  public void testLinks() throws java.io.IOException {
    final String s;
    try (Reader reader = new InputStreamReader(PyTestTracebackParserTest.class.getResourceAsStream("linksDataTest.txt"), StandardCharsets.UTF_8)) {
      s = StreamUtil.readText(reader);
    }

    final Set<String> requiredStrings = new HashSet<>();
    requiredStrings.add("file:///c:/windows/system32/file.txt - 42");
    requiredStrings.add("file:///c:/windows/system32/file_spam.txt - 42");
    requiredStrings.add("c:\\documents and settings\\foo.txt - 43");
    requiredStrings.add("/file.py - 42");
    requiredStrings.add("c:\\folder55\\file.py - 12");
    requiredStrings.add("C:\\temp\\untitled55\\test_sample.py - 5");
    requiredStrings.add("C:\\temp\\untitled55\\test_sample.py - 6");
    requiredStrings.add("C:\\temp\\untitled55\\test_sample.py - 7");
    requiredStrings.add("C:\\temp\\untitled55\\test_sample.py - 89999");
    requiredStrings.add("C:\\temp\\untitled55\\test_sample.py - 99999");
    requiredStrings.add("../../../files/files.py - 100");
    requiredStrings.add("/Users/Mac Hipster/Applications/PyCharm 4.0 .app/helpers/lala.py - 12");
    requiredStrings.add("C:\\Users\\ilya.kazakevich\\virtenvs\\spammy\\lib\\site-packages\\django_cron\\models.py - 4");
    final String[] strings = StringUtil.splitByLines(s);
    for (final String line : strings) {
      final LinkInTrace trace = new PyTestTracebackParser().findLinkInTrace(line);
      if (trace != null) {
        final boolean removeResult = requiredStrings.remove(trace.getFileName() + " - " + trace.getLineNumber());
        assertTrue(String.format("Unexpected file found %s line %s", trace.getFileName(), trace.getLineNumber()),
                   removeResult);
      }
    }
    assertThat("Some lines were not found", requiredStrings, Matchers.empty());
  }

  /**
   * Ensures
   * Regexp worst cases are limited to prevent freezing on very long lines
   */
  @Test(timeout = 5000)
  public void testLongLines() {
    assertNull("No link should be found in numbers list", new PyTestTracebackParser().findLinkInTrace(myStringJunk));
    assertNull("No link should be found in base64 list", new PyTestTracebackParser().findLinkInTrace(myBase64Junk));
    assertNull("No link should be found in junk", new PyTestTracebackParser().findLinkInTrace(myStringJunkWithSpaces));
    final LinkInTrace trace = new PyTestTracebackParser().findLinkInTrace(myStringJunkWithSpacesAndLine);
    assertNotNull("No link found in long line", trace);
  }
}
