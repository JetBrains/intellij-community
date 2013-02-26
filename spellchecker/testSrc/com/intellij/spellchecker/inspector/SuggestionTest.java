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
package com.intellij.spellchecker.inspector;

import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

import java.util.List;


public class SuggestionTest extends CodeInsightFixtureTestCase {

  private SpellCheckerManager spManager;
  private SpellCheckerManager getManager(){
    if (spManager==null){
      spManager = SpellCheckerManager.getInstance(myFixture.getProject());
    }
    assert spManager!=null;
    return spManager;
  }

  public void testSuggestions(){
    List<String> result = getManager().getSuggestions("upgade");
    assertEquals("upgrade",result.get(0));
  }


  public void testFirstLetterUppercaseSuggestions(){
    List<String> result = getManager().getSuggestions("Upgade");
    assertEquals("Upgrade",result.get(0));
  }

  public void testCamelCaseSuggestions(){
    SpellCheckerManager manager = SpellCheckerManager.getInstance(myFixture.getProject());
    assert manager!=null;
    List<String> result = manager.getSuggestions("TestUpgade");
    assertEquals("TestUpgrade",result.get(0));
  }

}
