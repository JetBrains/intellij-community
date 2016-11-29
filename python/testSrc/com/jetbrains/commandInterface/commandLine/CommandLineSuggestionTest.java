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
package com.jetbrains.commandInterface.commandLine;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.fixtures.PyTestCase;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.Set;


/**
 * Tests references provide correct suggestions for commands, options and argument in command line
 *
 * @author Ilya.Kazakevich
 */
public final class CommandLineSuggestionTest extends PyTestCase {
  /**
   * Ensures suggestions are correct
   */
  public void testSuggestions() throws Exception {
    CommandTestTools.initFileType();
    CommandTestTools.createFileByText(myFixture, "command positional_ar --a");

    ensureSuggestions("command", "command");
    ensureSuggestions("positional_ar", "positional_argument", "--option-no-argument", "--available-option");
    ensureSuggestions("--a", "--option-no-argument", "--available-option");
  }

  /**
   * @param initialPositionText place to move cusor to
   * @param expectedSuggestions expected suggestions
   */
  private void ensureSuggestions(@NotNull final String initialPositionText, @NotNull final String... expectedSuggestions) {
    moveByText(initialPositionText);
    final Set<String> completions = new HashSet<>();
    for (final LookupElement element : myFixture.completeBasic()) {
      completions.add(element.getLookupString());
    }

    Assert.assertThat("Bad suggestions", completions,
                      Matchers.containsInAnyOrder(expectedSuggestions));
  }


  @Override
  protected String getTestDataPath() {
    return CommandTestTools.TEST_PATH;
  }
}
