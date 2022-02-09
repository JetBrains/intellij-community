/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import com.intellij.maven.testFramework.MavenTestCase;

import java.io.IOException;

public class MavenJDOMUtilTest extends MavenTestCase {
  public void testReadingValuesWithComments() throws Exception {
    assertEquals("aaa", readValue("<root><foo>aaa<!--a--></foo></root>", "foo"));
    assertEquals("aaa", readValue("<root><foo>\n" +
                                  "aaa<!--a--></foo></root>", "foo"));
    assertEquals("aaa", readValue("<root><foo>aaa<!--a-->\n" +
                                  "</foo></root>", "foo"));
    assertEquals("aaa", readValue("<root><foo>\n" +
                                  "aaa\n" +
                                  "<!--a-->\n" +
                                  "</foo></root>", "foo"));
  }

  private String readValue(String xml, String valuePath) throws IOException {
    VirtualFile f = createProjectSubFile("foo.xml", xml);

    Element el = MavenJDOMUtil.read(f, new MavenJDOMUtil.ErrorHandler() {
      @Override
      public void onReadError(IOException e) {
        throw new RuntimeException(e);
      }

      @Override
      public void onSyntaxError() {
        fail("syntax error");
      }
    });

    return MavenJDOMUtil.findChildValueByPath(el, valuePath);
  }
}
