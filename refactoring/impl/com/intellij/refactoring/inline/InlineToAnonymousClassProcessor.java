package com.intellij.refactoring.inline;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.impl.Pattern;
import com.intellij.patterns.impl.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class InlineToAnonymousClassProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousClassProcessor");

  private PsiClass myClass;
  private boolean myInlineThisOnly;

  private static Pattern ourNullPattern = StandardPatterns.psiExpression().type(PsiLiteralExpression.class).withText(PsiKeyword.NULL);

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

  protected boolean preprocessUsages(final Ref<UsageInfo[]> refUsages) {
    String s = getPreprocessUsagesMessage(refUsages);
    if (s != null) {
      CommonRefactoringUtil.showErrorMessage(RefactoringBundle.message("inline.to.anonymous.refactoring"), s, null, myClass.getProject());
      return false;
    }
    return super.preprocessUsages(refUsages);
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

    List<PsiElement> elementsToDelete = new ArrayList<PsiElement>();
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
        PsiImportStatement statement = PsiTreeUtil.getParentOfType(element, PsiImportStatement.class);
        if (statement != null) {
          elementsToDelete.add(statement);
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
    }

    for(PsiElement element: elementsToDelete) {
      try {
        element.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
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

    Map<PsiField, FieldInfo> fieldMap = new HashMap<PsiField, FieldInfo>();
    if (constructor != null) {
      final PsiExpressionList argumentList = superNewExpressionTemplate.getArgumentList();
      assert argumentList != null;

      fieldMap = analyzeConstructorFieldAssignments(constructor, constructorArguments);
      generateLocalsForFields(fieldMap, newExpression);

      addSuperConstructorArguments(constructor, argumentList, constructorArguments);
    }

    final PsiNewExpression superNewExpression = (PsiNewExpression) newExpression.replace(superNewExpressionTemplate);
    final PsiClass anonymousClass = superNewExpression.getAnonymousClass();
    assert anonymousClass != null;
    for(PsiElement child: myClass.getChildren()) {
      if ((child instanceof PsiMethod && !((PsiMethod) child).isConstructor()) ||
          child instanceof PsiClassInitializer) {
        if (fieldMap.size() > 0) {
          replaceFieldReferences(child, fieldMap);
        }
        anonymousClass.addBefore(child, anonymousClass.getRBrace());
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField) child;
        FieldInfo info = fieldMap.get(field);
        if (info == null || !info.replaceWithLocal) {
          boolean noInitializer = (field.getInitializer() == null);
          field = (PsiField) anonymousClass.addBefore(field, anonymousClass.getRBrace());
          if (info != null && info.initializer != null && noInitializer) {
            field.setInitializer(info.initializer);
          }
        }
      }
    }
    superNewExpression.getManager().getCodeStyleManager().shortenClassReferences(superNewExpression);
  }

  private Map<PsiField, FieldInfo> analyzeConstructorFieldAssignments(final PsiMethod constructor, final PsiExpressionList constructorArguments) {
    final Map<PsiField, FieldInfo> result = new HashMap<PsiField, FieldInfo>();
    constructor.accept(new PsiRecursiveElementVisitor() {
      public void visitAssignmentExpression(final PsiAssignmentExpression expression) {
        super.visitAssignmentExpression(expression);
        if (expression.getLExpression() instanceof PsiReferenceExpression) {
          PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
          final PsiExpression rExpr = expression.getRExpression();
          final PsiElement psiElement = lExpr.resolve();
          if (psiElement instanceof PsiField && rExpr != null) {
            PsiField field = (PsiField) psiElement;
            if (myClass.getManager().areElementsEquivalent(field.getContainingClass(), myClass)) {
              FieldInfo info = result.get(field);
              if (info == null) {
                info = new FieldInfo();
                result.put(field, info);
              }

              Object constantValue = myClass.getManager().getConstantEvaluationHelper().computeConstantExpression(rExpr);
              final boolean isConstantInitializer = constantValue != null || ourNullPattern.accepts(rExpr);
              if (!isConstantInitializer) {
                final PsiExpression initializer;
                try {
                  initializer = replaceParameterReferences(constructor.getParameterList(),
                                                           (PsiExpression)rExpr.copy(),
                                                           constructorArguments);
                }
                catch (IncorrectOperationException e) {
                  LOG.error(e);
                  return;
                }

                info.initializer = initializer;
                info.replaceWithLocal = true;
              }
              else {
                info.initializer = (PsiExpression) rExpr.copy();
              }
            }
          }
        }
      }
    });
    return result;
  }

  private void generateLocalsForFields(final Map<PsiField, FieldInfo> fieldMap, final PsiNewExpression newExpression) {
    for(Map.Entry<PsiField, FieldInfo> e: fieldMap.entrySet()) {
      FieldInfo info = e.getValue();
      if (info.replaceWithLocal) {
        final CodeStyleManager codeStyleManager = myClass.getManager().getCodeStyleManager();
        final PsiField field = e.getKey();
        String varName = codeStyleManager.variableNameToPropertyName(field.getName(), VariableKind.FIELD);
        String localName = codeStyleManager.suggestUniqueVariableName(varName, newExpression, true);
        final PsiElementFactory factory = myClass.getManager().getElementFactory();
        try {
          final PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(localName, field.getType(), info.initializer);
          PsiVariable variable = (PsiVariable)declaration.getDeclaredElements()[0];
          variable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
          final PsiStatement newStatement = PsiTreeUtil.getParentOfType(newExpression, PsiStatement.class);
          if (newStatement != null) {
            newStatement.getParent().addBefore(declaration, newStatement);
          }
          info.localVar = variable;
        }
        catch(IncorrectOperationException ex) {
          LOG.error(ex);
        }
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

  private static PsiExpression replaceParameterReferences(final PsiParameterList constructorParameters, PsiExpression argument,
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

  private void replaceFieldReferences(final PsiElement method, final Map<PsiField, FieldInfo> fieldMap) throws  IncorrectOperationException {
    final Map<PsiReferenceExpression, PsiVariable> referencesToReplace = new HashMap<PsiReferenceExpression, PsiVariable>();
    method.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          //noinspection RedundantCast
          FieldInfo info = fieldMap.get((PsiField) element);
          if (info != null && info.replaceWithLocal) {
            referencesToReplace.put(expression, info.localVar);
          }
        }
      }
    });
    for(Map.Entry<PsiReferenceExpression, PsiVariable> e: referencesToReplace.entrySet()) {
      final PsiExpression expression = myClass.getManager().getElementFactory().createExpressionFromText(e.getValue().getName(), method);
      e.getKey().replace(expression);
    }
  }

  protected String getCommandName() {
    return "Inline class " + myClass;
  }

  @Nullable
  public String getPreprocessUsagesMessage(final Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    for(UsageInfo usage: usages) {
      final PsiElement element = usage.getElement();
      final PsiElement parentElement = element.getParent();
      if (parentElement != null && parentElement.getParent() instanceof PsiClassObjectAccessExpression) {
        return "Class cannot be inlined because it has usages of its class literal";
      }
      final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class);
      if (newExpression != null) {
        final PsiMethod[] constructors = myClass.getConstructors();
        if (constructors.length == 0) {
          if (newExpression.getArgumentList().getExpressions().length > 0) {
            return "Class cannot be inlined because a call to its constructor is unresolved";
          }
        }
        else {
          final JavaResolveResult resolveResult = newExpression.resolveMethodGenerics();
          if (!resolveResult.isValidResult()) {
            return "Class cannot be inlined because a call to its constructor is unresolved";
          }
        }
      }
    }
    return null;
  }

  private class FieldInfo {
    PsiVariable localVar;
    PsiExpression initializer;
    boolean replaceWithLocal;
  }
}
