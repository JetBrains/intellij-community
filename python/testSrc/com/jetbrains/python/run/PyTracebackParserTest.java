/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.run;

import com.intellij.execution.filters.Filter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.allure.Subsystems;
import com.jetbrains.python.allure.Layers;
import com.jetbrains.python.testing.pytest.PyTestTracebackParser;
import com.jetbrains.python.traceBackParsers.LinkInTrace;
import junit.framework.TestCase;
import org.junit.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.util.List;

/**
 * Ensures we can parse python traces for links
 *
 * @author Ilya.Kazakevich
 */
@Subsystems.Run
@Layers.Functional
public class PyTracebackParserTest extends TestCase {


  /**
   * Ensures we find link in stack trace
   */
  public void testLineWithLink() {
    final LinkInTrace linkInTrace = new PyTracebackParser().findLinkInTrace("File \"foo/bar.py\", line 42 failed");
    Assert.assertNotNull("Failed to parse line", linkInTrace);
    Assert.assertEquals("Bad file name", "foo/bar.py", linkInTrace.getFileName());
    Assert.assertEquals("Bad line number", 42, linkInTrace.getLineNumber());
    Assert.assertEquals("Bad start pos", 6, linkInTrace.getStartPos());
    Assert.assertEquals("Bad end pos", 16, linkInTrace.getEndPos());
  }

  public void testLineWithLink2() {
    final LinkInTrace linkInTrace = new PyTracebackParser().findLinkInTrace(
      "File ~/.pyenv/versions/3.10.11/lib/python3.10/threading.py:324, in Condition.wait(self, timeout)");
    Assert.assertNotNull("Failed to parse line", linkInTrace);
    Assert.assertEquals("Bad file name", "~/.pyenv/versions/3.10.11/lib/python3.10/threading.py", linkInTrace.getFileName());
    Assert.assertEquals("Bad line number", 324, linkInTrace.getLineNumber());
    Assert.assertEquals("Bad start pos", 5, linkInTrace.getStartPos());
    Assert.assertEquals("Bad end pos", 62, linkInTrace.getEndPos());
  }

  public void testLinkAfterHttpUrl() {
    final LinkInTrace linkInTrace = new PyTestTracebackParser().findLinkInTrace("http://localhost:8080 tests/foo.py:42");
    Assert.assertNotNull("Failed to parse link after URL", linkInTrace);
    Assert.assertEquals("Bad file name", "tests/foo.py", linkInTrace.getFileName());
    Assert.assertEquals("Bad line number", 42, linkInTrace.getLineNumber());
    Assert.assertEquals("Bad start pos", 22, linkInTrace.getStartPos());
    Assert.assertEquals("Bad end pos", 37, linkInTrace.getEndPos());
  }

  public void testSeveralLinksInOneLine() {
    final String line = "first.py:10 http://localhost:8080 subdirectory/second.py:20 https://example.com third.py:30";
    final Filter.Result result = createTracebackFilter().applyFilter(line, line.length());

    Assert.assertNotNull("Failed to parse file links", result);
    final List<Filter.ResultItem> resultItems = result.getResultItems();
    Assert.assertEquals(3, resultItems.size());
    Assert.assertEquals("first.py:10", getHighlightedText(line, resultItems.get(0)));
    Assert.assertEquals("subdirectory/second.py:20", getHighlightedText(line, resultItems.get(1)));
    Assert.assertEquals("third.py:30", getHighlightedText(line, resultItems.get(2)));
    for (Filter.ResultItem resultItem : resultItems) {
      Assert.assertNotNull("Link is not clickable", resultItem.getHyperlinkInfo());
    }
  }

  public void testMixedTracebackFormatsInOneLine() {
    final String line = "File \"a.py\", line 1 ... b.py:2";
    final Filter.Result result = createTracebackFilter().applyFilter(line, line.length());

    Assert.assertNotNull("Failed to parse file links", result);
    final List<Filter.ResultItem> resultItems = result.getResultItems();
    Assert.assertEquals(2, resultItems.size());
    Assert.assertEquals("a.py", getHighlightedText(line, resultItems.get(0)));
    Assert.assertEquals("b.py:2", getHighlightedText(line, resultItems.get(1)));
    for (Filter.ResultItem resultItem : resultItems) {
      Assert.assertNotNull("Link is not clickable", resultItem.getHyperlinkInfo());
    }
  }

  public void testSeveralStandardTracebackLinksInOneLine() {
    final String line = "File \"a.py\", line 1; File \"b.py\", line 2";
    final Filter.Result result = createTracebackFilter().applyFilter(line, line.length());

    Assert.assertNotNull("Failed to parse file links", result);
    final List<Filter.ResultItem> resultItems = result.getResultItems();
    Assert.assertEquals(2, resultItems.size());
    Assert.assertEquals("a.py", getHighlightedText(line, resultItems.get(0)));
    Assert.assertEquals("b.py", getHighlightedText(line, resultItems.get(1)));
  }

  private static @NotNull PythonTracebackFilter createTracebackFilter() {
    return new PythonTracebackFilter(Mockito.mock(Project.class), null) {
      @Override
      protected @Nullable VirtualFile findFileByName(@NotNull String fileName) {
        return new LightVirtualFile(fileName);
      }
    };
  }

  private static @NotNull String getHighlightedText(@NotNull String line, @NotNull Filter.ResultItem resultItem) {
    return line.substring(resultItem.getHighlightStartOffset(), resultItem.getHighlightEndOffset());
  }

  /**
   * lines with out of file references should not have links
   */
  public void testLineNoLink() {
    Assert.assertNull("File with no lines should not work", new PyTracebackParser().findLinkInTrace("File \"foo/bar.py\""));
    Assert.assertNull("No file name provided, but link found", new PyTracebackParser().findLinkInTrace("line 42 failed"));
  }
}
