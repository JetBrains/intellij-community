package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

import java.util.LinkedHashSet;

public class TypeExpression implements Expression {
  private final LookupItem[] myItems;
  protected SmartTypePointer myDefaultType;

  public TypeExpression(final Project project, PsiType[] types) {
    final LinkedHashSet<LookupItem> set = new LinkedHashSet<LookupItem>();
    for (int i = 0; i < types.length; i++) {
      PsiType type = types[i];
      LookupItemUtil.addLookupItem(set, type, "");
    }

    myItems = set.toArray(new LookupItem[set.size()]);
    final PsiType psiType = PsiUtil.convertAnonymousToBaseType(types[0]);
    myDefaultType = SmartPointerManager.getInstance(project).createSmartTypePointer(psiType);

  }

  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    return new PsiTypeResult(myDefaultType.getType(), PsiManager.getInstance(context.getProject()));
  }

  public Result calculateQuickResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    return new PsiTypeResult(myDefaultType.getType(), PsiManager.getInstance(context.getProject()));
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    if (myItems.length <= 1) return null;
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    return myItems;
  }

}
