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

import com.intellij.javaee.ExternalResourceManagerEx;

/**
 * @author Eugene.Kudelevsky
 */
@SuppressWarnings({"JUnitTestClassNamingConvention"})
public class RngHtml5CompletionTest extends HighlightingTestBase {

  @Override
  public String getTestDataPath() {
    return "completion";
  }

  @Override
  protected void init() {
    super.init();

    final ExternalResourceManagerEx m = ExternalResourceManagerEx.getInstanceEx();
    m.addResource("http://www.w3.org/1999/xhtml/html5", toAbsolutePath("/highlighting/html5/html5.rnc"));
  }

  public void testHtml5_1() throws Throwable {
    doTestCompletion("html5_1");
  }

  public void testHtml5_2() throws Throwable {
    doTestCompletion("html5_2");
  }

  public void testHtml5_3() throws Throwable {
    doTestCompletion("html5_3");
  }

  public void testHtml5_4() throws Throwable {
    doTestCompletion("html5_4");
  }

  public void testHtml5_5() throws Throwable {
    doTestCompletion("html5_5");
  }

  public void testHtml5_6() throws Throwable {
    doTestCompletion("html5_6");
  }

  public void testHtml5_7() throws Throwable {
    doTestCompletion("html5_7");
  }

  public void testHtml5_8() throws Throwable {
    doTestCompletion("html5_8");
  }

  public void testHtml5_9() throws Throwable {
    doTestCompletion("html5_9");
  }

  public void testHtml5_10() throws Throwable {
    doTestCompletion("html5_10");
  }

  public void testHtml5_11() throws Throwable {
    doTestCompletion("html5_11");
  }

  public void testHtml5_12() throws Throwable {
    doTestCompletion("html5_12");
  }

  public void testHtml5_13() throws Throwable {
    doTestCompletion("html5_13");
  }

  public void testHtml5_14() throws Throwable {
    doTestCompletion("html5_14");
  }

  public void testHtml5_15() throws Throwable {
    doTestCompletion("html5_15");
  }

  public void testHtml5_16() throws Throwable {
    doTestCompletion("html5_16");
  }

  public void testHtml5_17() throws Throwable {
    doTestCompletion("html5_17");
  }
}
