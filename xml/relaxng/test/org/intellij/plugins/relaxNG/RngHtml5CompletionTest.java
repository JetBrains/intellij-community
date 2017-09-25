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
package org.intellij.plugins.relaxNG;

import com.intellij.idea.Bombed;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings({"JUnitTestClassNamingConvention"})
public class RngHtml5CompletionTest extends HighlightingTestBase {

  @Override
  public String getTestDataPath() {
    return "completion";
  }

  public void testHtml5_1() {
    myTestFixture.testCompletionTyping("html5_1.xml", "\n", "html5_1_after.xml");
  }

  public void testHtml5_2() {
    doTestCompletion("html5_2");
  }

  public void testHtml5_3() {
    doTestCompletion("html5_3");
  }

  public void testHtml5_4() {
    doTestCompletion("html5_4");
  }

  public void testHtml5_5() {
    doTestCompletion("html5_5");
  }

  public void testHtml5_6() {
    doTestCompletion("html5_6");
  }

  public void testHtml5_7() {
    doTestCompletion("html5_7");
  }

  public void testHtml5_8() {
    doTestCompletion("html5_8");
  }

  public void testHtml5_9() {
    doTestCompletion("html5_9");
  }

  public void testHtml5_10() {
    doTestCompletion("html5_10");
  }

  public void testHtml5_11() {
    myTestFixture.testCompletionTyping("/html5_11.xml", "\n", "/html5_11_after.xml");
  }

  public void testHtml5_12() {
    doTestCompletion("html5_12");
  }

  public void testHtml5_13() {
    myTestFixture.testCompletionTyping("/html5_13.xml", "\n", "/html5_13_after.xml");
  }

  public void testHtml5_14() {
    doTestCompletion("html5_14");
  }

  public void testHtml5_15() {
    myTestFixture.testCompletionTyping("/html5_15.xml", "\n", "/html5_15_after.xml");
  }

  public void testHtml5_16() {
    myTestFixture.testCompletionTyping("html5_16.xml", "\n", "html5_16_after.xml");
  }

  @Bombed(year = 2018, month = 3, day = 1, user = "denofevil", description = "menu will be most likely removed from html 5.2")
  public void testHtml5_17() {
    doTestCompletion("html5_17");
  }
  
  public void testHtml5_overwritten_attributes() {
    myTestFixture.testCompletionTyping("html5_overwritten_attributes.xml", "a\n", "html5_overwritten_attributes_after.xml");
  }
}
