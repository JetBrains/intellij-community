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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.JobDescriptor;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class XmlHighlightVisitorBasedInspection extends GlobalInspectionTool {
  private static final JobDescriptor XML_HIGHLIGHTER =
    new JobDescriptor(InspectionsBundle.message("inspection.processing.job.descriptor2"));

  @Override
  public boolean isGraphNeeded() {
    return false;
  }

  @NotNull
  @Override
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  public JobDescriptor[] getAdditionalJobs() {
    return new JobDescriptor[]{XML_HIGHLIGHTER};
  }

  @Override
  public void runInspection(AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    XML_HIGHLIGHTER.setTotalAmount(scope.getFileCount());
    scope.accept(new XmlRecursiveElementVisitor() {
      final XmlHighlightVisitor highlightVisitor = new XmlHighlightVisitor();

      HighlightInfoHolder myHolder;

      @Override
      public void visitFile(final PsiFile file) {
        myHolder = new HighlightInfoHolder(file, HighlightInfoFilter.EMPTY_ARRAY) {
          @Override
          public boolean add(@Nullable HighlightInfo info) {
            if (info != null) {
              ProblemHighlightType problemHighlightType = HighlightInfo.convertType(info.type);
              GlobalInspectionUtil.createProblem(
                file,
                info.description,
                problemHighlightType,
                new TextRange(info.startOffset, info.endOffset),
                manager,
                problemDescriptionsProcessor,
                globalContext
              );
            }
            return true;
          }
        };
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
          globalContext.incrementJobDoneAmount(XML_HIGHLIGHTER, ProjectUtil.calcRelativeToProjectPath(virtualFile, file.getProject()));
        }
        super.visitFile(file);
      }

      @Override
      public void visitElement(PsiElement element) {
        highlightVisitor.visit(element, myHolder);
        super.visitElement(element);
      }
    });
  }

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return "General";
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return "Xml Highlighting";
  }

  @NotNull
  @Override
  public String getShortName() {
    return "XmlHighlighting";
  }
}