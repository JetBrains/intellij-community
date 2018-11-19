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

package com.intellij.util.xml.ui;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class TooltipUtils {
  @NonNls private static final String MESSAGE_DELIMITER = "<hr size=1 noshade>";

  public static String getTooltipText(List<? extends DomElementProblemDescriptor> annotations) {
    if (annotations.size() == 0) return null;

    return getTooltipText(getMessages(annotations));
  }
  
  public static String getTooltipText(List<? extends DomElementProblemDescriptor> annotations, String[] messages) {
    return getTooltipText(ArrayUtil.mergeArrays(getMessages(annotations), messages));
  }

  private static String[] getMessages(final List<? extends DomElementProblemDescriptor> problems) {
    String[] messages = new String[problems.size()];
    for (int i = 0; i < problems.size(); i++) {
      messages[i] = problems.get(i).getDescriptionTemplate();
    }
    return messages;
  }

  public static String getTooltipText(String[] messages) {
    if (messages.length == 0) return null;

    StringBuilder text = new StringBuilder("<html><body><table><tr><td>&nbsp;</td><td>");
    int len = messages.length > 10 ? 10 : messages.length;
    for (int i = 0; i < len; i++) {
      if (i != 0) {
        text.append(MESSAGE_DELIMITER);
      }
      text.append(messages[i]);
    }
    if (messages.length > 10) {
      text.append(MESSAGE_DELIMITER);
      text.append("...");
    }
    text.append("</td><td>&nbsp;</td></tr></table></body></html>");
    return  text.toString();
  }

}
