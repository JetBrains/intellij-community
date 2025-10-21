// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.proofreading;

import com.intellij.grazie.text.ProblemFilter;
import com.intellij.grazie.text.TextContent;
import com.intellij.grazie.text.TextProblem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;

public class MavenProblemFilter extends ProblemFilter {
  @Override
  public boolean shouldIgnore(@NotNull TextProblem problem) {
    return false;
  }

  @Override
  public boolean shouldIgnore(@NotNull TextContent content) {
    if (!MavenDomUtil.isMavenFile(content.getContainingFile())) return false;

    TextContent.TextDomain domain = content.getDomain();
    if (domain == TextContent.TextDomain.LITERALS) return true;

    if (domain == TextContent.TextDomain.PLAIN_TEXT) {
      PsiElement parent = content.getCommonParent();
      return !(parent instanceof XmlTag tag && "description".equals(tag.getLocalName()));
    }

    return false;
  }
}
