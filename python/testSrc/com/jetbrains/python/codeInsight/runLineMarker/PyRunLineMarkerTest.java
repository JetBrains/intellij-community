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
import com.intellij.execution.lineMarker.RunLineMarkerProvider;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import com.jetbrains.python.fixtures.PyTestCase;

import java.util.List;

public class PyRunLineMarkerTest extends PyTestCase {
  public void testRunnableMain() {
    List<LineMarkerInfo<?>> infos = getInfos("runnable.py");
    assertEquals("Wrong number of line markers", 1, infos.size());
    LineMarkerInfo<?> lineMarkerInfo = infos.get(0);
    PsiElement elementWithInfo = lineMarkerInfo.getElement();
    assertNotNull(elementWithInfo);
    assertTrue(elementWithInfo.getParent().getText().startsWith("if"));
    assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testWithManyIfs() {
    List<LineMarkerInfo<?>> infos = getInfos("runnable_with_ifs.py");
    assertEquals("There should be only one marker", 1, infos.size());
    assertEquals(ThreeState.YES, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testNotRunnable() {
    assertEquals(0, getInfos("not_runnable.py").size());
    assertEquals(ThreeState.UNSURE, RunLineMarkerProvider.hadAnythingRunnable(myFixture.getFile().getVirtualFile()));
  }

  public void testIncorrectName() {
    List<LineMarkerInfo<?>> infos = getInfos("wrong_spelling.py");
    assertEmpty("Should not be runnable", infos);
  }

  private List<LineMarkerInfo<?>> getInfos(String fileName) {
    myFixture.configureByFile(fileName);
    myFixture.doHighlighting();
    return DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.getEditor().getDocument(), myFixture.getProject());
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/codeInsight/runLineMarker/";
  }
}
