/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnusedNamespaceInspection;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.QuickFix;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 *         Date: 11/7/11
 */
public class XmlImportOptimizer implements ImportOptimizer {
  
  private final XmlUnusedNamespaceInspection myInspection = new XmlUnusedNamespaceInspection();
  private final Condition<ProblemDescriptor> myCondition = descriptor -> {
    PsiElement element = descriptor.getPsiElement();
    PsiElement parent = element.getParent();
    return parent != null && !myInspection.isSuppressedFor(parent);
  };

  @Override
  public boolean supports(PsiFile file) {
    return file instanceof XmlFile;
  }

  @NotNull
  @Override
  public CollectingInfoRunnable processFile(final PsiFile file) {
    return new CollectingInfoRunnable() {
      int myRemovedNameSpaces = 0;

      @Override
      public void run() {
        XmlFile xmlFile = (XmlFile)file;
        Project project = xmlFile.getProject();
        HighlightDisplayKey key = HighlightDisplayKey.find(myInspection.getShortName());
        if (!InspectionProjectProfileManager.getInstance(project).getCurrentProfile().isToolEnabled(key, xmlFile)) return;
        ProblemsHolder holder = new ProblemsHolder(InspectionManager.getInstance(project), xmlFile, false);
        final XmlElementVisitor visitor = (XmlElementVisitor)myInspection.buildVisitor(holder, false);
        new PsiRecursiveElementVisitor() {
          @Override
          public void visitElement(PsiElement element) {
            if (element instanceof XmlAttribute) {
              visitor.visitXmlAttribute((XmlAttribute)element);
            }
            else {
              super.visitElement(element);
            }
          }
        }.visitFile(xmlFile);
        ProblemDescriptor[] results = holder.getResultsArray();
        ArrayUtil.reverseArray(results);
        List<ProblemDescriptor> list = ContainerUtil.filter(results, myCondition);

        Map<XmlUnusedNamespaceInspection.RemoveNamespaceDeclarationFix, ProblemDescriptor> fixes = new LinkedHashMap<>();
        for (ProblemDescriptor result : list) {
          for (QuickFix fix : result.getFixes()) {
            if (fix instanceof XmlUnusedNamespaceInspection.RemoveNamespaceDeclarationFix) {
              fixes.put((XmlUnusedNamespaceInspection.RemoveNamespaceDeclarationFix)fix, result);
            }
          }
        }

        SmartPsiElementPointer<XmlTag> pointer = null;
        for (Map.Entry<XmlUnusedNamespaceInspection.RemoveNamespaceDeclarationFix, ProblemDescriptor> fix : fixes.entrySet()) {
          pointer = fix.getKey().doFix(project, fix.getValue(), false);
          myRemovedNameSpaces++;
        }
        if (pointer != null) {
          XmlUnusedNamespaceInspection.RemoveNamespaceDeclarationFix.reformatStartTag(project, pointer);
        }
      }

      @Nullable
      @Override
      public String getUserNotificationInfo() {
        return myRemovedNameSpaces > 0
               ? "Removed " + myRemovedNameSpaces + " namespace" + (myRemovedNameSpaces > 1 ? "s" : "")
               : null;
      }
    };
  }
}
