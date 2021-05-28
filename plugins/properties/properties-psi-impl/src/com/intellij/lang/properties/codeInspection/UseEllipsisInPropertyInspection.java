// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.codeInspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesInspectionBase;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UseEllipsisInPropertyInspection extends PropertiesInspectionBase {
  @Override
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file,
                                                  @NotNull InspectionManager manager,
                                                  boolean isOnTheFly) {
    if (!(file instanceof PropertiesFile)) return null;
    final List<IProperty> properties = ((PropertiesFile)file).getProperties();
    final List<ProblemDescriptor> descriptors = new SmartList<>();
    for (IProperty property : properties) {
      ProgressManager.checkCanceled();
      final PropertyImpl propertyImpl = (PropertyImpl)property;
      ASTNode node = propertyImpl.getValueNode();
      if (node != null) {
        PsiElement value = node.getPsi();
        TextRange range = getThreeDots(value);
        if (range != null) {
          descriptors.add(manager.createProblemDescriptor(value, range,
                                                          PropertiesBundle.message("inspection.use.ellipsis.in.property.description"),
                                                          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true,
                                                          ReplaceThreeDotsWithEllipsisFix.getInstance()));
        }
      }
    }
    return descriptors.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  @Nullable
  private static TextRange getThreeDots(PsiElement element) {
    int textLength = element.getTextLength();
    if (textLength <= 4) return null;
    Project project = element.getProject();
    Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
    if (document != null) {
      CharSequence text = document.getCharsSequence();
      int endOffset = element.getTextRange().getEndOffset();
      if (text.charAt(endOffset - 1) != '.') return null;
      if (text.charAt(endOffset - 2) != '.') return null;
      if (text.charAt(endOffset - 3) != '.') return null;

      if (text.charAt(endOffset - 4) == '.') return null;
      return TextRange.create(textLength - 3, textLength);
    }
    return null;
  }

  private final static class ReplaceThreeDotsWithEllipsisFix implements LocalQuickFix {

    private static volatile ReplaceThreeDotsWithEllipsisFix instance;

    public static ReplaceThreeDotsWithEllipsisFix getInstance() {
      if (instance == null) {
        synchronized (ReplaceThreeDotsWithEllipsisFix.class) {
          if (instance == null) {
            instance = new ReplaceThreeDotsWithEllipsisFix();
          }
        }
      }
      return instance;
    }

    @Override
    public @NotNull String getFamilyName() {
      return PropertiesBundle.message("use.ellipsis.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      PsiElement parent = element == null ? null : element.getParent();
      if (!(parent instanceof PropertyImpl)) return;
      TextRange range = getThreeDots(element);
      if (range != null) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(element.getContainingFile());
        TextRange docRange = range.shiftRight(element.getTextRange().getStartOffset());
        if (document != null) {
          document.replaceString(docRange.getStartOffset(), docRange.getEndOffset(), "…");
        }
      }
    }
  }
}
