package com.jetbrains.python.codeInsight.intentions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.UsageView;
import com.intellij.usages.impl.FileStructureGroupRuleProvider;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.rules.UsageGroupingRule;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.patterns.ParentMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Usage view grouping by references. Usages are expected to return the element by which they are grouped,
 * User: dcheryasov
 * Date: Oct 7, 2009 6:08:18 PM
 */
public class PyRefGroupRuleProvider implements FileStructureGroupRuleProvider {
  public UsageGroupingRule getUsageGroupingRule(Project project) {
    return new UsageGroupingRule() {
      public UsageGroup groupUsage(Usage a_usage) {
        if (a_usage instanceof PsiElementUsage) {
          PsiElementUsage the_usage = (PsiElementUsage)a_usage;
          PsiElement elt = the_usage.getElement();
          if (elt != null && elt.getLanguage() instanceof PythonLanguage) {
            return new Group(elt, null);
          }
        }
        return null;
      }
    };
  }

  private static class Group extends PsiElementUsageGroupBase {
    Group(@NotNull PsiElement element, Icon icon) {
      super(element, icon);
    }

    @NotNull
    @Override
    public String getText(UsageView view) {
      StringBuilder sb = new StringBuilder();
      PsiElement element = getElement();
      if (element != null) {
        PsiFile f = element.getContainingFile();
        if (f != null) {
          Document doc = f.getViewProvider().getDocument();
          if (doc != null) {
            int line_number = doc.getLineNumber(element.getTextOffset());
            sb.append("(").append(line_number).append(") ");
          }
        }
      }
      PsiElement contextual;
      ParentMatcher finder = new ParentMatcher(PyStatement.class);
      List<? extends PsiElement> candidates = finder.search(element);
      if (candidates != null) contextual = candidates.get(0);
      else contextual = element;
      return sb.append(PyUtil.getReadableRepr(contextual, true)).toString();
    }
  }


}
