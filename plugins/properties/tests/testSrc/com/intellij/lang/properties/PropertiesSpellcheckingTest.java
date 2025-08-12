/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.lang.properties;

import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class PropertiesSpellcheckingTest extends LightJavaCodeInsightFixtureTestCase {

  public void testPropertiesSpellcheckingStrategy() {
    myFixture.enableInspections(new SpellCheckingInspection());

    myFixture.configureByText("test.properties",
                              """
                                valid.key=value
                                # comment is <TYPO descr="Typo: In word 'cheked'">cheked</TYPO>
                                validWord<TYPO descr="Typo: In word 'Buuundary'">Buuundary</TYPO>=value
                                i3<TYPO descr="Typo: In word 'nvalid'">nvalid</TYPO>.key=i3<TYPO descr="Typo: In word 'nvalidValue'">nvalidValue</TYPO>""");
    myFixture.testHighlighting();
  }
}
