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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.XmlDefaultAttributeValueInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public class XmlInspectionsTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testDefaultAttributeValue() {
    myFixture.enableInspections(new XmlDefaultAttributeValueInspection());
    myFixture.configureByText(XmlFileType.INSTANCE, "<schema xmlns=\"http://www.w3.org/2001/XMLSchema\" elementFormDefault=<warning descr=\"Redundant default attribute value assignment\">\"unqua<caret>lified\"</warning>>\n" +
                                                    "</schema>");
    myFixture.checkHighlighting();
    IntentionAction action = myFixture.findSingleIntention(XmlErrorMessages.message("remove.attribute.quickfix.family"));
    myFixture.launchAction(action);
    myFixture.checkResult("<schema xmlns=\"http://www.w3.org/2001/XMLSchema\">\n" +
                          "</schema>");
  }
}
