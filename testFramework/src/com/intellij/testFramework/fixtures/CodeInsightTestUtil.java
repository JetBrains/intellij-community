/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.intention.IntentionAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class CodeInsightTestUtil {
  
  private CodeInsightTestUtil() {
  }

  @Nullable
  public static IntentionAction findIntentionByText(List<IntentionAction> actions, @NonNls String text) {
    for (IntentionAction action : actions) {
      final String s = action.getText();
      if (s.equals(text)) {
        return action;
      }
    }
    return null;
  }

  public static void doIntentionTest(CodeInsightTestFixture fixture, @NonNls String file, @NonNls String actionText) throws Throwable {
    final List<IntentionAction> list = fixture.getAvailableIntentions(file + ".xml");
    assert list.size() > 0;
    final IntentionAction intentionAction = findIntentionByText(list, actionText);
    assert intentionAction != null : "Action not found: " + actionText;
    fixture.launchAction(intentionAction);
    fixture.checkResultByFile(file + "_after.xml");
  }
}
