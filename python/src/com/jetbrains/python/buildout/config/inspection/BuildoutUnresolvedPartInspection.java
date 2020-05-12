// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout.config.inspection;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReference;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.buildout.config.BuildoutCfgFileType;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgValueLine;
import com.jetbrains.python.buildout.config.ref.BuildoutPartReference;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class BuildoutUnresolvedPartInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("buildout");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "BuildoutUnresolvedPartInspection";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    List<ProblemDescriptor> problems = new ArrayList<>();
    if (file.getFileType().equals(BuildoutCfgFileType.INSTANCE)) {
      Visitor visitor = new Visitor();
      file.accept(visitor);

      for (BuildoutPartReference ref : visitor.getUnresolvedParts()) {
        ProblemDescriptor d = manager
          .createProblemDescriptor(ref.getElement(), ref.getRangeInElement(), PyBundle.message("buildout.unresolved.part.inspection.msg"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING, false);
        problems.add(d);
      }
    }
    return problems.toArray(ProblemDescriptor.EMPTY_ARRAY);
  }

  private static class Visitor extends PsiRecursiveElementVisitor {
    private final List<BuildoutPartReference> unresolvedParts = new ArrayList<>();

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (element instanceof BuildoutCfgValueLine) {
        PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ref instanceof BuildoutPartReference && ref.resolve() == null) {
            unresolvedParts.add((BuildoutPartReference)ref);
          }
        }

      }
      super.visitElement(element);
    }

    public List<BuildoutPartReference> getUnresolvedParts() {
      return unresolvedParts;
    }
  }


}
