/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.List;

public class SuggestionTest extends BasePlatformTestCase {

  public static final String TYPPPO = "Typppo";

  public void testSuggestions() { doTest("upgade", "upgrade"); }

  public void testFirstLetterUppercaseSuggestions() { doTest("Upgade", "Upgrade"); }

  public void testCamelCaseSuggestions() { doTest("TestUpgade", "TestUpgrade"); }

  private void doTest(String word, String expected) {
    List<String> result = SpellCheckerManager.getInstance(myFixture.getProject()).getSuggestions(word);
    assertEquals(expected, result.get(0));
  }

  public void testMaxSuggestions() {
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(myFixture.getProject());
    int oldCorrectionsLimit = Registry.intValue("spellchecker.corrections.limit");
    assertTrue(manager.getSuggestions(TYPPPO).size() <= oldCorrectionsLimit);
  }

  public void testMaxSuggestions1() {
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(myFixture.getProject());
    final RegistryValue registryValue = Registry.get("spellchecker.corrections.limit");
    final int oldCorrectionsLimit = registryValue.asInteger();

    registryValue.setValue((1));
    assertEquals(1, manager.getSuggestions(TYPPPO).size());

    registryValue.setValue((oldCorrectionsLimit));
  }

  public void testMaxSuggestions0() {
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(myFixture.getProject());
    final RegistryValue registryValue = Registry.get("spellchecker.corrections.limit");
    final int oldCorrectionsLimit = registryValue.asInteger();
    
    registryValue.setValue(0); // some incorrect value appeared
    assertEquals(0, manager.getSuggestions(TYPPPO).size());

    registryValue.setValue(oldCorrectionsLimit);
  }

  public void testMaxSuggestions1000() {
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(myFixture.getProject());
    final RegistryValue registryValue = Registry.get("spellchecker.corrections.limit");
    final int oldCorrectionsLimit = registryValue.asInteger();
    registryValue.setValue(1000);
    // because of quality threshold
    assertTrue(manager.getSuggestions("SomeVeryLongWordToReduceSuggestionsCount").size() < 1000);

    registryValue.setValue(oldCorrectionsLimit);
  }
}
