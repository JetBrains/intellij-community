// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.codeInspection;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.properties.PropertiesBundle;
import com.intellij.lang.properties.PropertiesInspectionBase;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class UseEllipsisInPropertyInspection extends PropertiesInspectionBase {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    final Charset charset = EncodingProjectManager.getInstance(holder.getProject()).getDefaultCharsetForPropertiesFiles(null);
    if (charset != StandardCharsets.UTF_8) return PsiElementVisitor.EMPTY_VISITOR;
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!(element instanceof PropertyValueImpl)) return;

        boolean found = getThreeDots(((PropertyValueImpl)element).getChars());
        if (found) {
          int length = element.getTextLength();
          holder.registerProblem(element, TextRange.create(length - 3, length),
                                 PropertiesBundle.message("inspection.use.ellipsis.in.property.description"),
                                 ReplaceThreeDotsWithEllipsisFix.getInstance());
        }
      }
    };
  }

  private static boolean getThreeDots(@NotNull CharSequence element) {
    int textLength = element.length();
    if (textLength <= 4) return false;
    // Ends with three dots…
    if (element.charAt(textLength - 3) != '.') return false;
    if (element.charAt(textLength - 2) != '.') return false;
    if (element.charAt(textLength - 1) != '.') return false;

    // But doesn't end with four dots and more
    if (element.charAt(textLength - 4) == '.') return false;
    return true;
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
      if (!(element instanceof PropertyValueImpl)) return;

      boolean found = getThreeDots(((PropertyValueImpl)element).getChars());
      if (found) {
        int length = element.getTextLength();
        StringBuilder newText = new StringBuilder(((PropertyValueImpl)element).getChars());
        newText.replace(length - 3, length, "…");

        ((PropertyValueImpl)element).replaceWithText(newText.toString());
      }
    }
  }
}
