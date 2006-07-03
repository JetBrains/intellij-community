package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.HashSet;

import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
class InlineConstantFieldProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineConstantFieldProcessor");
  private PsiField myField;
  private PsiReferenceExpression myRefExpr;
  private final boolean myInlineThisOnly;

  public InlineConstantFieldProcessor(PsiField field, Project project, PsiReferenceExpression ref, boolean isInlineThisOnly) {
    super(project);
    myField = field;
    myRefExpr = ref;
    myInlineThisOnly = isInlineThisOnly;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InlineViewDescriptor(myField);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    PsiManager manager = myField.getManager();
    if (myInlineThisOnly) return new UsageInfo[]{new UsageInfo(myRefExpr)};

    PsiSearchHelper helper = manager.getSearchHelper();
    PsiReference[] refs = helper.findReferences(myField, GlobalSearchScope.projectScope(myProject), false);
    UsageInfo[] infos = new UsageInfo[refs.length];
    for (int i = 0; i < refs.length; i++) {
      infos[i] = new UsageInfo(refs[i].getElement());
    }
    return infos;
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiField);
    myField = (PsiField)elements[0];
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiExpression initializer = myField.getInitializer();
    LOG.assertTrue(initializer != null);

    PsiConstantEvaluationHelper evalHelper = myField.getManager().getConstantEvaluationHelper();
    initializer = normalize ((PsiExpression)initializer.copy());
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      try {
        if (element instanceof PsiExpression) {
          inlineExpressionUsage(((PsiExpression)element), evalHelper, initializer);
        }
        else {
          PsiImportStaticStatement importStaticStatement = PsiTreeUtil.getParentOfType(element, PsiImportStaticStatement.class);
          LOG.assertTrue(importStaticStatement != null);
          importStaticStatement.delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (!myInlineThisOnly) {
      try {
        myField.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void inlineExpressionUsage(PsiExpression expr,
                                     final PsiConstantEvaluationHelper evalHelper,
                                     PsiExpression initializer1) throws IncorrectOperationException {
    while (expr.getParent() instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression arrayAccess = ((PsiArrayAccessExpression)expr.getParent());
      Object value = evalHelper.computeConstantExpression(arrayAccess.getIndexExpression());
      if (value instanceof Integer) {
        int intValue = ((Integer)value).intValue();
        if (initializer1 instanceof PsiNewExpression) {
          PsiExpression[] arrayInitializers = ((PsiNewExpression)initializer1).getArrayInitializer().getInitializers();
          if (0 <= intValue && intValue < arrayInitializers.length) {
            expr = (PsiExpression)expr.getParent();
            initializer1 = normalize(arrayInitializers[intValue]);
            continue;
          }
        }
      }

      break;
    }

    myField.normalizeDeclaration();
    ChangeContextUtil.encodeContextInfo(initializer1, true);
    PsiElement element = expr.replace(initializer1);
    ChangeContextUtil.decodeContextInfo(element, null, null);
  }

  private static PsiExpression normalize(PsiExpression expression) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiElementFactory factory = expression.getManager().getElementFactory();
      try {
        final PsiType type = expression.getType();
        if (type != null) {
          String typeString = type.getCanonicalText();
          PsiNewExpression result = (PsiNewExpression)factory.createExpressionFromText("new " + typeString + "{}", expression);
          result.getArrayInitializer().replace(expression);
          return result;
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return expression;
      }
    }

    return expression;
  }

  protected String getCommandName() {
    return RefactoringBundle.message("inline.field.command", UsageViewUtil.getDescriptiveName(myField));
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    ArrayList<String> conflicts = new ArrayList<String>();

    ReferencedElementsCollector collector = new ReferencedElementsCollector();
    PsiExpression initializer = myField.getInitializer();
    LOG.assertTrue(initializer != null);
    initializer.accept(collector);
    HashSet<PsiMember> referencedWithVisibility = collector.myReferencedMembers;

    PsiResolveHelper resolveHelper = myField.getManager().getResolveHelper();
    for (UsageInfo info : usagesIn) {
      PsiElement element = info.getElement();
      if (element instanceof PsiExpression && isAccessedForWriting((PsiExpression)element)) {
        String message = RefactoringBundle.message("0.is.used.for.writing.in.1", ConflictsUtil.getDescription(myField, true),
                                                   ConflictsUtil.getDescription(ConflictsUtil.getContainer(element), true));
        conflicts.add(message);
      }

      for (PsiMember member : referencedWithVisibility) {
        if (!resolveHelper.isAccessible(member, element, null)) {
          String message = RefactoringBundle.message("0.will.not.be.accessible.from.1.after.inlining", ConflictsUtil.getDescription(member, true),
                                                     ConflictsUtil.getDescription(ConflictsUtil.getContainer(element), true));
          conflicts.add(message);
        }
      }
    }

    return showConflicts(conflicts);
  }

  private static boolean isAccessedForWriting (PsiExpression expr) {
    while(expr.getParent() instanceof PsiArrayAccessExpression) {
      expr = (PsiExpression)expr.getParent();
    }

    return PsiUtil.isAccessedForWriting(expr);
  }
}
