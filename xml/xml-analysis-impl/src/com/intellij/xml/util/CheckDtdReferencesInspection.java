// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xml.util;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.XmlSuppressableInspectionTool;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlElementContentSpec;
import com.intellij.psi.xml.XmlEntityRef;
import org.jetbrains.annotations.NotNull;

/**
 * @author Maxim Mossienko
 */
public class CheckDtdReferencesInspection extends XmlSuppressableInspectionTool {
  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new XmlElementVisitor() {
      @Override
      public void visitXmlElement(final @NotNull XmlElement element) {
        if (element instanceof XmlElementContentSpec ||
            element instanceof XmlEntityRef
          ) {
          doCheckRefs(element, holder);
        }
      }
    };
  }

  private static void doCheckRefs(final XmlElement element, final ProblemsHolder holder) {
    for (PsiReference ref : element.getReferences()) {
      ProgressManager.checkCanceled();
      if (XmlHighlightVisitor.hasBadResolve(ref, true)) {
        if (ref.getElement() instanceof XmlElementContentSpec) {
          final String image = ref.getCanonicalText();
          if (image.equals("-") || image.equals("O")) continue;
        }
        holder.registerProblem(ref);
      }
    }
  }

  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }
}
