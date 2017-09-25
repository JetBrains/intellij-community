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

/**
 * @author Ekaterina Shliakhovetskaja
 */
public class XmlWithMistakesInspectionTest extends SpellcheckerInspectionTestCase {
  @Override
  protected String getBasePath() {
    return SpellcheckerInspectionTestCase.getSpellcheckerTestDataPath() + "inspection/xmlWithMistakes";
  }

  public void testXml() {
    doTest("test.xml");
  }

  public void testCharacterData() {
    doTest("test.html");
  }

  public void testKnownAttributes() {
    doTest("attributes.html");
  }
}
