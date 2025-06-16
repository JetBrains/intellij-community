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
package com.intellij.spellchecker.inspection.quickfixes;

import com.intellij.testFramework.UsefulTestCase;
import org.junit.Assume;

public class XmlSpellCheckerFixesTest extends AbstractSpellCheckerFixesTest {
  @Override
  protected String getExtension() {
    return ".xml";
  }

  public void testSimpleWordChangeTo() {
    doChangeToTest();
  }

  public void testInjectionChangeTo() {
    Assume.assumeFalse("This test is skipped on TeamCity", UsefulTestCase.IS_UNDER_TEAMCITY);
    doChangeToTest();
  }
}
