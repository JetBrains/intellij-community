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

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.ArrayUtil;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import org.jetbrains.annotations.NonNls;

import java.util.List;

public class TooltipUtils {
  @NonNls private static final HtmlChunk MESSAGE_DELIMITER = HtmlChunk.hr().attr("size", "1")
    .attr("noshade", "noshade");

  public static @NlsContexts.Tooltip String getTooltipText(List<? extends DomElementProblemDescriptor> annotations) {
    if (annotations.size() == 0) return null;

    return getTooltipText(getMessages(annotations));
  }

  public static @NlsContexts.Tooltip String getTooltipText(List<? extends DomElementProblemDescriptor> annotations,
                                                           @InspectionMessage String[] messages) {
    return getTooltipText(ArrayUtil.mergeArrays(getMessages(annotations), messages));
  }

  private static @InspectionMessage String[] getMessages(final List<? extends DomElementProblemDescriptor> problems) {
    String[] messages = new String[problems.size()];
    for (int i = 0; i < problems.size(); i++) {
      messages[i] = problems.get(i).getDescriptionTemplate();
    }
    return messages;
  }

  public static @NlsContexts.Tooltip String getTooltipText(@InspectionMessage String[] messages) {
    if (messages.length == 0) return null;

    HtmlChunk.Element contents = HtmlChunk.tag("td");
    int len = Math.min(messages.length, 10);
    for (int i = 0; i < len; i++) {
      if (i != 0) {
        contents = contents.child(MESSAGE_DELIMITER);
      }
      contents = contents.addText(messages[i]);
    }
    if (messages.length > 10) {
      contents = contents.child(MESSAGE_DELIMITER)
        .addText("...");
    }
    HtmlChunk emptyCell = HtmlChunk.tag("td").addText("&nbsp;");
    return new HtmlBuilder().append(
      HtmlChunk.tag("table").child(
        HtmlChunk.tag("tr").children(emptyCell, contents, emptyCell)))
      .wrapWithHtmlBody().toString();
  }
}
