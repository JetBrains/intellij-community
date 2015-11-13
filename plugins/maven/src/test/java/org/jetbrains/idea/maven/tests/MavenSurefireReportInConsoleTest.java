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
package org.jetbrains.idea.maven.tests;

import com.intellij.execution.filters.Filter;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.project.MavenTestConsoleFilter;
import org.jetbrains.idea.maven.server.MavenServerManager;

import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class MavenSurefireReportInConsoleTest extends LightCodeInsightFixtureTestCase {

  private Filter myFilter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFilter = new MavenTestConsoleFilter();
  }

  @Override
  protected void tearDown() throws Exception {
    MavenServerManager.getInstance().shutdown(true);
    super.tearDown();
  }

  private List<String> passLine(String line) {
    if (!line.endsWith("\n")) {
      line += '\n';
    }

    Filter.Result result = myFilter.applyFilter(line, line.length());
    if (result == null) return Collections.emptyList();

    List<String> res = ContainerUtil.newArrayList();

    for (Filter.ResultItem item : result.getResultItems()) {
      res.add(line.substring(item.getHighlightStartOffset(), item.getHighlightEndOffset()));
    }

    return res;
  }

  public void testSurefire2_14() {
    myFixture.addClass("public class CccTest {\n" +
                       "  public void testTtt() {}\n" +
                       "  public void testTtt2() {}\n" +
                       "}");

    String tempDirPath = myFixture.getTempDirPath();

    assertEquals(passLine("[INFO] Scanning for projects..."), Collections.emptyList());
    assertEquals(passLine("[INFO] Surefire report directory: " + tempDirPath), Collections.singletonList(tempDirPath));
    assertEquals(passLine("[ERROR] Please refer to " + tempDirPath + " for the individual test results."), Collections.singletonList(
      tempDirPath));
  }
}
