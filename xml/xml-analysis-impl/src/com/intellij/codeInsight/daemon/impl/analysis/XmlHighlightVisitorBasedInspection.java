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

/**
 * @author yole
 */
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
          public void visitElement(PsiElement element) {
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
    return InspectionProfileEntry.GENERAL_GROUP_NAME;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "XML highlighting";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "XmlHighlighting";
  }
}
