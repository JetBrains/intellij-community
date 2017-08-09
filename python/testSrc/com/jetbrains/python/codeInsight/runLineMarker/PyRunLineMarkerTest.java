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
package com.jetbrains.python.codeInsight.runLineMarker;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.List;

public class PyRunLineMarkerTest extends PyTestCase {
  public void testRunnableMain() {
    List<LineMarkerInfo> infos = getInfos("runnable.py");
    assertEquals("Wrong number of line markers", 1, infos.size());
    LineMarkerInfo lineMarkerInfo = infos.get(0);
    PsiElement elementWithInfo = lineMarkerInfo.getElement();
    assertNotNull(elementWithInfo);
    assertTrue(elementWithInfo.getParent().getText().startsWith("if __name__ == \"__main__\""));
  }

  public void testNotRunnable() {
    assertEquals(0, getInfos("not_runnable.py").size());
  }

  private List<LineMarkerInfo> getInfos(String fileName) {
    myFixture.configureByFile(fileName);
    myFixture.doHighlighting();
    return DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.getEditor().getDocument(), myFixture.getProject());
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/codeInsight/runLineMarker/";
  }
}
