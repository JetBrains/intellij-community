package com.intellij.refactoring.invertBoolean;

import com.intellij.codeInsight.CodeInsightServicesUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author ven
 */
public class InvertBooleanProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.invertBoolean.InvertBooleanMethodProcessor");

  private PsiNamedElement myElement;
  private final String myNewName;

  public InvertBooleanProcessor(final PsiNamedElement method, final String name) {
    super(method.getProject());
    myElement = method;
    myNewName = name;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InvertBooleanUsageViewDescriptor(myElement);
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    if (!(myElement instanceof PsiMethod)) {
      prepareSuccessful();
      return true;
    }
    PsiMethod original = (PsiMethod)myElement;
    PsiMethod prototype = (PsiMethod)myElement.copy();
    try {
      prototype.setName(myNewName);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return false;
    }
    final ArrayList<String> conflicts = new ArrayList<String>();
    ConflictsUtil.checkMethodConflicts(
      original.getContainingClass(),
      original,
      prototype, conflicts);

    return showConflicts(conflicts);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final List<UsageInfo> result = new ArrayList<UsageInfo>();
    addRefs(result);

    if (myElement instanceof PsiMethod) {
      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search((PsiMethod)myElement).findAll();
      for (PsiMethod overrider : overriders) {
        result.add(new OverriderUsageInfo(overrider));
      }


      Collection<PsiMethod> allMethods = new HashSet<PsiMethod>(overriders);
      allMethods.add((PsiMethod)myElement);

      for (PsiMethod method : allMethods) {
        method.accept(new PsiRecursiveElementVisitor() {
          public void visitReturnStatement(PsiReturnStatement statement) {
            final PsiExpression returnValue = statement.getReturnValue();
            if (returnValue != null && PsiType.BOOLEAN.equals(returnValue.getType())) {
              result.add(new InvertAndChangeNameUsageInfo(returnValue, true, false));
            }
          }
        });
      }
    } else if (myElement instanceof PsiParameter && ((PsiParameter)myElement).getDeclarationScope() instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)((PsiParameter)myElement).getDeclarationScope();
      int index = method.getParameterList().getParameterIndex((PsiParameter)myElement);
      LOG.assertTrue(index >= 0);
      final Query<PsiReference> methodQuery = MethodReferencesSearch.search(method);
      final Collection<PsiReference> methodRefs = methodQuery.findAll();
      for (PsiReference ref : methodRefs) {
        if (ref.getElement().getParent() instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression call = (PsiMethodCallExpression)ref.getElement().getParent();
          final PsiReferenceExpression methodExpression = call.getMethodExpression();
          final PsiExpression[] args = call.getArgumentList().getExpressions();
          if (index < args.length) {
            if (methodExpression.getQualifier() == null || !"super".equals(methodExpression.getQualifierExpression().getText())) {
              result.add(new InvertAndChangeNameUsageInfo(args[index], true, false));
            } else {
              result.add(new InvertAndChangeNameUsageInfo(args[index], false, true));
            }
          }
        }
      }
      final Collection<PsiMethod> overriders = OverridingMethodsSearch.search(method).findAll();
      for (PsiMethod overrider : overriders) {
        result.add(new OverriderUsageInfo(overrider.getParameterList().getParameters()[index]));
      }
    } else {
      LOG.assertTrue(myElement instanceof PsiVariable);
      final PsiExpression initializer = ((PsiVariable)myElement).getInitializer();
      if (initializer != null) {
        result.add(new InvertAndChangeNameUsageInfo(initializer, true, false));
      }
    }

    return result.toArray(new UsageInfo[result.size()]);
  }

  private void addRefs(final List<UsageInfo> result) {
    final Query<PsiReference> query = myElement instanceof PsiMethod ?
                                      MethodReferencesSearch.search((PsiMethod)myElement) :
                                      ReferencesSearch.search(myElement);
    final Collection<PsiReference> refs = query.findAll();

    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression refExpr = ((PsiReferenceExpression)element);
        PsiElement parent = refExpr.getParent();
        if (parent instanceof PsiAssignmentExpression && refExpr.equals(((PsiAssignmentExpression)parent).getLExpression())) {
          result.add(new InvertAndChangeNameUsageInfo(((PsiAssignmentExpression)parent).getRExpression(), true, false));
          result.add(new InvertAndChangeNameUsageInfo(refExpr, false, true));
        }
        else {
          result.add(new InvertAndChangeNameUsageInfo(refExpr, true, true));
        }
      }
    }
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 1 && elements[0] instanceof PsiMethod);
    myElement = ((PsiMethod)elements[0]);
  }

  protected void performRefactoring(UsageInfo[] usages) {
    for (UsageInfo usage : usages) {
      if (usage instanceof InvertAndChangeNameUsageInfo) {
        final InvertAndChangeNameUsageInfo exprUsageInfo = (InvertAndChangeNameUsageInfo)usage;
        if (exprUsageInfo.isInvert()) {
          PsiExpression expression = exprUsageInfo.getElement();
          if (expression.getParent() instanceof PsiMethodCallExpression) expression = (PsiExpression)expression.getParent();
          if (expression == null) continue;
          try {
            while(expression.getParent() instanceof PsiPrefixExpression &&
                  ((PsiPrefixExpression)expression.getParent()).getOperationSign().getTokenType() == JavaTokenType.EXCL) {
              expression = (PsiExpression)expression.getParent();
            }
            expression.replace(CodeInsightServicesUtil.invertCondition(expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    try {
      PsiIdentifier newId = PsiManager.getInstance(myProject).getElementFactory().createIdentifier(myNewName);
      for (UsageInfo usage : usages) {
        if (usage instanceof InvertAndChangeNameUsageInfo) {
          final InvertAndChangeNameUsageInfo exprUsageInfo = (InvertAndChangeNameUsageInfo)usage;
          if (exprUsageInfo.isChangeName()) {
            final PsiExpression expr = exprUsageInfo.getElement();
            if (!(expr instanceof PsiReferenceExpression)) continue;
            final PsiElement referenceNameElement = ((PsiReferenceExpression)expr).getReferenceNameElement();
            assert referenceNameElement != null;
            referenceNameElement.replace(newId);
          }
        }
      }

      myElement.setName(myNewName);
      for (UsageInfo usage : usages) {
        if (usage instanceof OverriderUsageInfo) {
          final PsiNamedElement namedElement = ((OverriderUsageInfo)usage).getElement();
          if (namedElement != null) {
            namedElement.setName(myNewName);
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  protected String getCommandName() {
    return InvertBooleanHandler.REFACTORING_NAME;
  }
}
