/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.Locale;

/**
 * @author Ekaterina Shliakhovetskaja
 */
public class CommentsWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  protected String getBasePath() {
    return getSpellcheckerTestDataPath() + "/inspection/commentsWithMistakes";
  }

  public void testJava() {
    doTest("SPITest1.java");
  }

  public void testJavaWithTurkishLocale() {
    Locale locale = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      doTest("SPITest2.java");
    }
    finally {
      Locale.setDefault(locale);
    }
  }

  public void testXml() {
    doTest("A.xml");
  }

  public void testHtml() {
    doTest("test.html");
  }

  public void testTxt() {
    doTest("test.txt");
  }

  public void testLongTxt() {
    doTest("longTest.txt");
  }
}
