package com.intellij.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author yole
 */
public class InlineToAnonymousClassProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousClassProcessor");

  private PsiClass myClass;
  private boolean myInlineThisOnly;

  protected InlineToAnonymousClassProcessor(Project project, PsiClass psiClass, boolean inlineThisOnly) {
    super(project);
    myClass = psiClass;
    myInlineThisOnly = inlineThisOnly;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InlineViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final Collection<PsiReference> refCollection = ReferencesSearch.search(myClass).findAll();
    Set<UsageInfo> usages = new HashSet<UsageInfo>();
    for (PsiReference reference : refCollection) {
      usages.add(new UsageInfo(reference.getElement()));
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected void refreshElements(PsiElement[] elements) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiClass superClass;
    final PsiClass[] interfaces = myClass.getInterfaces();
    if (interfaces.length > 0) {
      assert interfaces.length == 1;
      superClass = interfaces [0];
    }
    else {
      superClass = myClass.getSuperClass();
    }

    PsiElementFactory factory = myClass.getManager().getElementFactory();

    for(UsageInfo info: usages) {
      final PsiElement element = info.getElement();
      final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
      if (newExpression != null) {
        try {
          replaceNewExpression(newExpression, superClass);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
      else {
        PsiTypeElement typeElement = PsiTreeUtil.getParentOfType(element, PsiTypeElement.class);
        if (typeElement != null) {
          PsiType psiType = typeElement.getType();
          LOG.assertTrue(psiType instanceof PsiClassType && ((PsiClassType) psiType).resolve() == myClass);
          try {
            typeElement.replace(factory.createTypeElement(factory.createType(superClass)));
          }
          catch(IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    }

    if (!myInlineThisOnly) {
      try {
        myClass.delete();
      }
      catch(IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  private void replaceNewExpression(final PsiNewExpression newExpression, final PsiClass superClass) throws IncorrectOperationException {
    @NonNls StringBuilder builder = new StringBuilder("new ");
    builder.append(superClass.getQualifiedName());
    builder.append("() {}");

    PsiElementFactory factory = myClass.getManager().getElementFactory();
    final PsiMethod constructor = newExpression.resolveConstructor();
    final PsiExpressionList constructorArguments = newExpression.getArgumentList();

    PsiNewExpression superNewExpressionTemplate = (PsiNewExpression) factory.createExpressionFromText(builder.toString(),
                                                                                                      newExpression.getContainingFile());

    if (constructor != null) {
      final PsiExpressionList argumentList = superNewExpressionTemplate.getArgumentList();
      assert argumentList != null;
      addSuperConstructorArguments(constructor, argumentList, constructorArguments);
    }

    final PsiNewExpression superNewExpression = (PsiNewExpression) newExpression.replace(superNewExpressionTemplate);
    final PsiClass anonymousClass = superNewExpression.getAnonymousClass();
    assert anonymousClass != null;
    for(PsiElement child: myClass.getChildren()) {
      if ((child instanceof PsiMethod && !((PsiMethod) child).isConstructor()) ||
          child instanceof PsiClassInitializer) {
        anonymousClass.addBefore(child, anonymousClass.getRBrace());
      }
    }
  }

  private static void addSuperConstructorArguments(PsiMethod constructor, PsiExpressionList argumentList,
                                                   PsiExpressionList constructorArguments) throws IncorrectOperationException {
    final PsiCodeBlock body = constructor.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0 || !(statements [0] instanceof PsiExpressionStatement)) {
      return;
    }
    PsiExpressionStatement stmt = (PsiExpressionStatement) statements [0];
    if (!(stmt.getExpression() instanceof PsiCallExpression)) {
      return;
    }
    PsiCallExpression expr = (PsiCallExpression) stmt.getExpression();
    final PsiElement superKeyword = expr.getFirstChild();
    if (superKeyword == null || !superKeyword.getText().equals(PsiKeyword.SUPER)) {
      return;
    }
    PsiExpressionList superArguments = expr.getArgumentList();
    if (superArguments != null) {
      for(PsiExpression argument: superArguments.getExpressions()) {
        argumentList.add(replaceParameterReferences(constructor.getParameterList(), argument, constructorArguments));
      }
    }
  }

  private static PsiElement replaceParameterReferences(final PsiParameterList constructorParameters, PsiExpression argument,
                                                       final PsiExpressionList constructorArguments) throws IncorrectOperationException {
    final List<Pair<PsiReferenceExpression, PsiParameter>> parameterReferences = new ArrayList<Pair<PsiReferenceExpression, PsiParameter>>();
    argument.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement psiElement = expression.resolve();
        if (psiElement instanceof PsiParameter) {
          parameterReferences.add(new Pair<PsiReferenceExpression, PsiParameter>(expression, (PsiParameter) psiElement));
        }
      }
    });
    for (Pair<PsiReferenceExpression, PsiParameter> pair: parameterReferences) {
      PsiReferenceExpression ref = pair.first;
      PsiParameter param = pair.second;
      int index = constructorParameters.getParameterIndex(param);
      if (ref == argument) {
        argument = (PsiExpression)argument.replace(constructorArguments.getExpressions() [index]);
      }
      else {
        ref.replace(constructorArguments.getExpressions() [index]);
      }
    }
    return argument;
  }

  protected String getCommandName() {
    return "Inline class " + myClass;
  }
}
