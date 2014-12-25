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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.inspections.quickfix.PySuppressInspectionFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public abstract class PyInspection extends LocalInspectionTool {
  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return PyBundle.message("INSP.GROUP.python");
  }

  @NotNull
  @Override
  public String getShortName() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public SuppressQuickFix[] getBatchSuppressActions(@Nullable PsiElement element) {
    List<SuppressQuickFix> result = new ArrayList<SuppressQuickFix>();
    result.add(new PySuppressInspectionFix(getSuppressId(), "Suppress for statement", PyStatement.class) {
      @Override
      public PsiElement getContainer(PsiElement context) {
        if (PsiTreeUtil.getParentOfType(context, PyStatementList.class, false, ScopeOwner.class) != null ||
            PsiTreeUtil.getParentOfType(context, PyFunction.class, PyClass.class) == null) {
          return super.getContainer(context);
        }
        return null;
      }
    });
    result.add(new PySuppressInspectionFix(getSuppressId(), "Suppress for function", PyFunction.class));
    result.add(new PySuppressInspectionFix(getSuppressId(), "Suppress for class", PyClass.class));
    return result.toArray(new SuppressQuickFix[result.size()]);
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    final PsiFile file = element.getContainingFile();
    boolean isAccepted = true;
    if (file instanceof PyFileImpl) {
      isAccepted = ((PyFileImpl)file).isAcceptedFor(this.getClass());
    }
    return !isAccepted || isSuppressedForParent(element, PyStatement.class) ||
           isSuppressedForParent(element, PyFunction.class) ||
           isSuppressedForParent(element, PyClass.class) ||
           isSuppressForCodeFragment(element);
  }

  private boolean isSuppressForCodeFragment(PsiElement element) {
    return isSuppressForCodeFragment() && PsiTreeUtil.getParentOfType(element, PyExpressionCodeFragment.class) != null;
  }

  protected boolean isSuppressForCodeFragment() {
    return false;
  }

  private boolean isSuppressedForParent(PsiElement element, final Class<? extends PyElement> parentClass) {
    PyElement parent = PsiTreeUtil.getParentOfType(element, parentClass, false);
    if (parent == null) {
      return false;
    }
    return isSuppressedForElement(parent);
  }

  private boolean isSuppressedForElement(PyElement stmt) {
    PsiElement prevSibling = stmt.getPrevSibling();
    if (prevSibling == null) {
      final PsiElement parent = stmt.getParent();
      if (parent != null) {
        prevSibling = parent.getPrevSibling();
      }
    }
    while (prevSibling instanceof PsiComment || prevSibling instanceof PsiWhiteSpace) {
      if (prevSibling instanceof PsiComment && isSuppressedInComment(prevSibling.getText().substring(1).trim())) {
        return true;
      }
      prevSibling = prevSibling.getPrevSibling();
    }
    return false;
  }

  private static final Pattern SUPPRESS_PATTERN = Pattern.compile(SuppressionUtil.COMMON_SUPPRESS_REGEXP);

  private boolean isSuppressedInComment(String commentText) {
    Matcher m = SUPPRESS_PATTERN.matcher(commentText);
    return m.matches() && SuppressionUtil.isInspectionToolIdMentioned(m.group(1), getSuppressId());
  }

  @NotNull
  protected String getSuppressId() {
    return getShortName().replace("Inspection", "");
  }
}
