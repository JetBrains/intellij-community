/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInsight.daemon.XmlErrorMessages;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlBundle;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class HtmlExtraClosingTagInspection extends HtmlLocalInspectionTool {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return XmlBundle.message("html.inspection.extra.closing.tag");
  }

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return "HtmlExtraClosingTag";
  }

  @Override
  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final TextRange range = XmlTagUtil.getEndTagRange(tag);

    if (range != null && tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag.getName())) {
      holder.registerProblem(tag, XmlErrorMessages.message("extra.closing.tag.for.empty.element"),
                             ProblemHighlightType.LIKE_UNUSED_SYMBOL, range.shiftRight(-tag.getTextRange().getStartOffset()), new RemoveExtraClosingTagIntentionAction());
    }
  }
}
