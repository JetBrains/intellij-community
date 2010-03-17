/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker.inspection;

import com.intellij.spellchecker.inspections.SpellCheckerInspectionToolProvider;

/**
 * Created by IntelliJ IDEA.
 * User: Ekaterina Shliakhovetskaja
 */
public class CommentsWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {


  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/commentsWithMistakes";
  }

  public void testJava() throws Throwable {
    doTest("SPITest1.java", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testXml() throws Throwable {
    doTest("A.xml",SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testHtml() throws Throwable {
    doTest("test.html", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testTxt() throws Throwable {
    doTest("test.txt", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testTCTxt() throws Throwable {
    doTest("contents.txt", SpellCheckerInspectionToolProvider.getInspectionTools());
  }

  public void testTCTxt2() throws Throwable {
    doTest("crt.txt", SpellCheckerInspectionToolProvider.getInspectionTools());
  }
}
