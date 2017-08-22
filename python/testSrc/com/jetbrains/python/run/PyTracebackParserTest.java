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

import com.jetbrains.python.traceBackParsers.LinkInTrace;
import junit.framework.TestCase;
import org.junit.Assert;

/**
 * Ensures we can parse python traces for links
 *
 * @author Ilya.Kazakevich
 */
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

  /**
   * lines with out of file references should not have links
   */
  public void testLineNoLink() {
    Assert.assertNull("File with no lines should not work", new PyTracebackParser().findLinkInTrace("File \"foo/bar.py\""));
    Assert.assertNull("No file name provided, but link found", new PyTracebackParser().findLinkInTrace("line 42 failed"));
  }
}
