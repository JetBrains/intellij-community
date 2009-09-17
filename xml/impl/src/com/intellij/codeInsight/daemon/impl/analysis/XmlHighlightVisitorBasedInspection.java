package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
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
public class XmlHighlightVisitorBasedInspection extends GlobalInspectionTool {
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
  public void runInspection(AnalysisScope scope,
                            final InspectionManager manager,
                            final GlobalInspectionContext globalContext,
                            final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
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
            return super.add(info);
          }
        };
        myHolder.setWritable(true);
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