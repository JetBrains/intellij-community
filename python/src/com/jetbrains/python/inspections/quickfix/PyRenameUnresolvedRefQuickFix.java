
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
package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * User : ktisha
 *
 * Quick fix to rename unresolved references
 */
public class PyRenameUnresolvedRefQuickFix implements LocalQuickFix {

  @NotNull
  @Override
  public String getName() {
    return PyBundle.message("QFIX.rename.unresolved.reference");
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.rename.unresolved.reference");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PyReferenceExpression)) {
      return;
    }
    final PyReferenceExpression referenceExpression = (PyReferenceExpression)element;

    ScopeOwner parentScope = ScopeUtil.getScopeOwner(referenceExpression);
    if (parentScope == null) return;

    List<PyReferenceExpression> refs = collectExpressionsToRename(referenceExpression, parentScope);

    LookupElement[] items = collectLookupItems(parentScope);
    final String name = referenceExpression.getReferencedName();

    ReferenceNameExpression refExpr = new ReferenceNameExpression(items, name);
    TemplateBuilderImpl builder = (TemplateBuilderImpl)TemplateBuilderFactory.getInstance().
                                          createTemplateBuilder(parentScope);
    for (PyReferenceExpression expr : refs) {
      if (!expr.equals(referenceExpression)) {
        builder.replaceElement(expr, name, name, false);
      }
      else {
        builder.replaceElement(expr, name, refExpr, true);
      }
    }

    Editor editor = getEditor(project, element.getContainingFile(), parentScope.getTextRange().getStartOffset());
    if (editor != null) {
      Template template = builder.buildInlineTemplate();
      TemplateManager.getInstance(project).startTemplate(editor, template);
    }
  }

  public static boolean isValidReference(final PsiReference reference) {
    if (!(reference instanceof PyReferenceImpl)) return false;
    ResolveResult[] results = ((PyReferenceImpl)reference).multiResolve(true);
    if(results.length == 0) return false;
    for (ResolveResult result : results) {
      if (!result.isValidResult()) return false;
    }
    return true;
  }


  private static List<PyReferenceExpression> collectExpressionsToRename(@NotNull final PyReferenceExpression expression,
                                                                        @NotNull final ScopeOwner parentScope) {

    final List<PyReferenceExpression> result = new ArrayList<>();
    PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        if (node.textMatches(expression) && !isValidReference(node.getReference())) {
          result.add(node);
        }
        super.visitPyReferenceExpression(node);
      }
    };

    parentScope.accept(visitor);
    return result;
  }

  @Nullable
  private static Editor getEditor(@NotNull final Project project, @NotNull final PsiFile file, int offset) {
    final VirtualFile virtualFile = file.getVirtualFile();
    return virtualFile != null ? FileEditorManager.getInstance(project).openTextEditor(
      new OpenFileDescriptor(project, virtualFile, offset), true
    ) : null;
  }

  private static LookupElement[] collectLookupItems(@NotNull final ScopeOwner parentScope) {
    Set<LookupElement> items = new LinkedHashSet<>();

    final Collection<String> usedNames = PyRefactoringUtil.collectUsedNames(parentScope);
    for (String name : usedNames) {
      if (name != null)
        items.add(LookupElementBuilder.create(name));
    }

    return items.toArray(new LookupElement[items.size()]);
  }

  private class ReferenceNameExpression extends Expression {
    class HammingComparator implements Comparator<LookupElement> {
      @Override
      public int compare(LookupElement lookupItem1, LookupElement lookupItem2) {
        String s1 = lookupItem1.getLookupString();
        String s2 = lookupItem2.getLookupString();
        int diff1 = 0;
        for (int i = 0; i < Math.min(s1.length(), myOldReferenceName.length()); i++) {
          if (s1.charAt(i) != myOldReferenceName.charAt(i)) diff1++;
        }
        int diff2 = 0;
        for (int i = 0; i < Math.min(s2.length(), myOldReferenceName.length()); i++) {
          if (s2.charAt(i) != myOldReferenceName.charAt(i)) diff2++;
        }
        return diff1 - diff2;
      }
    }

    ReferenceNameExpression(LookupElement[] items, String oldReferenceName) {
      myItems = items;
      myOldReferenceName = oldReferenceName;
      Arrays.sort(myItems, new HammingComparator());
    }

    LookupElement[] myItems;
    private final String myOldReferenceName;

    @Override
    public Result calculateResult(ExpressionContext context) {
      if (myItems == null || myItems.length == 0) {
        return new TextResult(myOldReferenceName);
      }
      return new TextResult(myItems[0].getLookupString());
    }

    @Override
    public Result calculateQuickResult(ExpressionContext context) {
      return null;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
      if (myItems == null || myItems.length == 1) return null;
      return myItems;
    }
  }
}
