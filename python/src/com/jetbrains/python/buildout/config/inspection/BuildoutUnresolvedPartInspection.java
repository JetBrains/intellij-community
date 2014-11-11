/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.buildout.config.inspection;

import com.google.common.collect.Lists;
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

import java.util.List;

/**
 * @author traff
 */
public class BuildoutUnresolvedPartInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("buildout");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("buildout.unresolved.part.inspection");
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
    List<ProblemDescriptor> problems = Lists.newArrayList();
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
    return problems.toArray(new ProblemDescriptor[problems.size()]);
  }

  private class Visitor extends PsiRecursiveElementVisitor {
    private final List<BuildoutPartReference> unresolvedParts = Lists.newArrayList();

    @Override
    public void visitElement(PsiElement element) {
      if (element instanceof BuildoutCfgValueLine) {
        PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ref instanceof BuildoutPartReference && ref.resolve() == null) {
            unresolvedParts.add((BuildoutPartReference)ref);
          }
        }

      }
      super.visitElement(element);    //To change body of overridden methods use File | Settings | File Templates.
    }

    public List<BuildoutPartReference> getUnresolvedParts() {
      return unresolvedParts;
    }
  }


}
