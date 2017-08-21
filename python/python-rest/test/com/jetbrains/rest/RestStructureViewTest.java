/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.rest;

import com.jetbrains.rest.fixtures.RestFixtureTestCase;

import static com.intellij.testFramework.PlatformTestUtil.assertTreeEqual;

public class RestStructureViewTest extends RestFixtureTestCase {

  public void testPlain() {
    doTest("-plain.rst\n" +
           " Chapter 1 Title\n" +
           " Chapter 2 Title\n");
  }


  public void testFileBeginning() {
    doTest("-fileBeginning.rst\n" +
           " Chapter 1 Title\n" +
           " Chapter 2 Title\n");
  }

  public void testOneInnerSection() {
    doTest("-oneInnerSection.rst\n" +
           " -Chapter 1 Title\n" +
           "  -Section 1.1 Title\n" +
           "   Subsection 1.1.1 Title\n" +
           "  Section 1.2 Title\n" +
           " Chapter 2 Title\n");
  }

  public void testTree() {
    doTest("-tree.rst\n" +
           " -Hello, world\n" +
           "  -A section\n" +
           "   -A subsection\n" +
           "    A sub-subsection\n" +
           "    An other one\n" +
           "  -Back up\n" +
           "   And down\n" +
           "   -twice\n" +
           "    with feelings\n");
  }

  private void doTest(final String expected) {
    myFixture.configureByFile("/structureView/" + getTestName(true) + ".rst");
    myFixture.testStructureView(component -> assertTreeEqual(component.getTree(), expected));
  }
}
