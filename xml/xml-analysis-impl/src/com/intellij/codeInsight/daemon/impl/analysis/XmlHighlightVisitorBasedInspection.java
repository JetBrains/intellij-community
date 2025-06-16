// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class XmlHighlightVisitorBasedInspection extends GlobalSimpleInspectionTool {
  @Override
  public @NotNull HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void checkFile(final @NotNull PsiFile psiFile,
                        final @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        final @NotNull GlobalInspectionContext globalContext,
                        final @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    HighlightInfoHolder myHolder = new HighlightInfoHolder(psiFile) {
      @Override
      public boolean add(@Nullable HighlightInfo info) {
        if (info != null) {
          GlobalInspectionUtil.createProblem(
            psiFile,
            info,
            new TextRange(info.startOffset, info.endOffset),
            null,
            manager,
            problemDescriptionsProcessor,
            globalContext
          );
        }
        return true;
      }
    };
    final XmlHighlightVisitor highlightVisitor = new XmlHighlightVisitor();
    highlightVisitor.analyze(psiFile, true, myHolder, new Runnable() {
      @Override
      public void run() {
        psiFile.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            highlightVisitor.visit(element);
            super.visitElement(element);
          }
        });
      }
    });

  }

  @Override
  public @Nls @NotNull String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  @Override
  public @NotNull String getShortName() {
    return "XmlHighlighting";
  }
}
