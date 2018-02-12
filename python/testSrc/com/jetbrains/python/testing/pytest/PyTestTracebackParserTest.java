/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.testing.pytest;

import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.traceBackParsers.LinkInTrace;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

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
    final LinkInTrace linkInTrace = new PyTestTracebackParser().findLinkInTrace("foo/bar.py:42 file ");
    Assert.assertNotNull("Failed to parse line", linkInTrace);
    Assert.assertEquals("Bad file name", "foo/bar.py", linkInTrace.getFileName());
    Assert.assertEquals("Bad line number", 42, linkInTrace.getLineNumber());
    Assert.assertEquals("Bad start pos", 0, linkInTrace.getStartPos());
    Assert.assertEquals("Bad end pos", 13, linkInTrace.getEndPos());
  }

  /**
   * lines with out of file references should not have links
   */
  @Test
  public void testLineNoLink() {
    Assert.assertNull("File with no lines should not work", new PyTestTracebackParser().findLinkInTrace("foo/bar.py file "));
    Assert.assertNull("No file name provided, but link found", new PyTestTracebackParser().findLinkInTrace(":12 file "));
  }

  @Test
  public void testLinks() throws java.io.IOException {
    final String s =
      StreamUtil.readText(PyTestTracebackParserTest.class.getResource("linksDataTest.txt").openStream(), Charset.defaultCharset());

    final Set<String> requiredStrings = new HashSet<>();
    requiredStrings.add("file:///c:/windows/system32/file.txt - 42");
    requiredStrings.add("file:///c:/windows/system32/file_spam.txt - 42");
    requiredStrings.add("c:\\documents and settings\\foo.txt - 43");
    requiredStrings.add("file:///c:/windows/system32/file.txt - 42");
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
        Assert.assertTrue(String.format("Unexpected file found %s line %s", trace.getFileName(), trace.getLineNumber()),
                          removeResult);
      }
    }
    Assert.assertThat("Some lines were not found", requiredStrings, Matchers.empty());
  }

    /**
     * Ensures
     * Regexp worst cases are limited to prevent freezing on very long lines
     */
    @Test(timeout = 5000)
    public void testLongLines () {
      Assert
        .assertNull("No link should be found in numbers list", new PyTestTracebackParser().findLinkInTrace(myStringJunk));
      Assert.assertNull("No link should be found in base64 list", new PyTestTracebackParser().findLinkInTrace(myBase64Junk));
      Assert.assertNull("No link should be found in junk", new PyTestTracebackParser().findLinkInTrace(myStringJunkWithSpaces));
      final LinkInTrace trace = new PyTestTracebackParser().findLinkInTrace(myStringJunkWithSpacesAndLine);
      Assert.assertNotNull("No link found in long line", trace);
    }
  }
