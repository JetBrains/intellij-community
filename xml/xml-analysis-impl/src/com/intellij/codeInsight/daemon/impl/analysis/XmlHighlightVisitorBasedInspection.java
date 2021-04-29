// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public void checkFile(@NotNull final PsiFile file,
                        @NotNull final InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull final GlobalInspectionContext globalContext,
                        @NotNull final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    HighlightInfoHolder myHolder = new HighlightInfoHolder(file) {
      @Override
      public boolean add(@Nullable HighlightInfo info) {
        if (info != null) {
          GlobalInspectionUtil.createProblem(
            file,
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
    highlightVisitor.analyze(file, true, myHolder, new Runnable() {
      @Override
      public void run() {
        file.accept(new XmlRecursiveElementVisitor() {
          @Override
          public void visitElement(@NotNull PsiElement element) {
            highlightVisitor.visit(element);
            super.visitElement(element);
          }
        });
      }
    });

  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return getGeneralGroupName();
  }

  @NotNull
  @Override
  public String getShortName() {
    return "XmlHighlighting";
  }
}
