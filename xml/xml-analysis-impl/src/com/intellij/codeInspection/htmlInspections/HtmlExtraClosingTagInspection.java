// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.htmlInspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import com.intellij.xml.util.HtmlUtil;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class HtmlExtraClosingTagInspection extends HtmlLocalInspectionTool {
  @NonNls
  public static final String TOOL_ID = "HtmlExtraClosingTag";

  @Override
  @NonNls
  @NotNull
  public String getShortName() {
    return TOOL_ID;
  }

  @Override
  protected void checkTag(@NotNull final XmlTag tag, @NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    final TextRange range = XmlTagUtil.getEndTagRange(tag);

    if (range != null && tag instanceof HtmlTag && HtmlUtil.isSingleHtmlTag(tag, true)
        && tag.getLanguage().isKindOf(HTMLLanguage.INSTANCE)) {
      holder.registerProblem(tag, XmlAnalysisBundle.message("html.inspections.extra.closing.tag.for.empty.element"),
                             ProblemHighlightType.LIKE_UNUSED_SYMBOL, range.shiftRight(-tag.getTextRange().getStartOffset()), new RemoveExtraClosingTagIntentionAction());
    }
  }
}
