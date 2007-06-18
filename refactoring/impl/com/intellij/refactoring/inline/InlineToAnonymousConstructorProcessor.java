package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.impl.MatchingContext;
import com.intellij.patterns.impl.Pattern;
import com.intellij.patterns.impl.StandardPatterns;
import static com.intellij.patterns.impl.StandardPatterns.psiElement;
import static com.intellij.patterns.impl.StandardPatterns.psiExpressionStatement;
import com.intellij.patterns.impl.TraverseContext;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
*/
class InlineToAnonymousConstructorProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousConstructorProcessor");

  private static final Key<PsiAssignmentExpression> ourAssignmentKey = Key.create("assignment");
  private static final Key<PsiCallExpression> ourCallKey = Key.create("call");
  private static final Pattern ourNullPattern = StandardPatterns.psiExpression().type(PsiLiteralExpression.class).withText(PsiKeyword.NULL);
  private static final Pattern ourAssignmentPattern = psiExpressionStatement().withChild(psiElement(PsiAssignmentExpression.class).save(ourAssignmentKey));
  private static final Pattern ourSuperCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).save(ourCallKey).withFirstChild(psiElement().withText(PsiKeyword.SUPER)));
  private static final Pattern ourThisCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(psiElement().withText(PsiKeyword.THIS)));

  private final PsiClass myClass;
  private final PsiNewExpression myNewExpression;
  private final PsiType mySuperType;
  private final Map<String, PsiExpression> myFieldInitializers = new HashMap<String, PsiExpression>();
  private final Map<PsiParameter, PsiVariable> myLocalsForParameters = new HashMap<PsiParameter, PsiVariable>();
  private final PsiStatement myNewStatement;
  private final PsiElementFactory myElementFactory;
  private PsiMethod myConstructor;
  private PsiExpressionList myConstructorArguments;
  private PsiParameterList myConstructorParameters;

  public InlineToAnonymousConstructorProcessor(final PsiClass aClass, final PsiNewExpression psiNewExpression,
                                               final PsiType superType) {
    myClass = aClass;
    myNewExpression = psiNewExpression;
    mySuperType = superType;
    myNewStatement = PsiTreeUtil.getParentOfType(myNewExpression, PsiStatement.class);
    myElementFactory = myClass.getManager().getElementFactory();
  }

  public void run() throws IncorrectOperationException {
    checkInlineChainingConstructor();
    JavaResolveResult classResolveResult = myNewExpression.getClassReference().advancedResolve(false);
    JavaResolveResult methodResolveResult = myNewExpression.resolveMethodGenerics();
    myConstructor = (PsiMethod) methodResolveResult.getElement();
    myConstructorArguments = myNewExpression.getArgumentList();

    PsiSubstitutor classResolveSubstitutor = classResolveResult.getSubstitutor();
    PsiType substType = classResolveSubstitutor.substitute(mySuperType);

    PsiTypeParameter[] typeParams = myClass.getTypeParameters();
    PsiType[] substitutedParameters = new PsiType[typeParams.length];
    for(int i=0; i< typeParams.length; i++) {
      substitutedParameters [i] = classResolveSubstitutor.substitute(typeParams [i]);
    }

    @NonNls StringBuilder builder = new StringBuilder("new ");
    builder.append(substType.getPresentableText());
    builder.append("() {}");

    PsiNewExpression superNewExpressionTemplate = (PsiNewExpression) myElementFactory.createExpressionFromText(builder.toString(),
                                                                                                      myNewExpression.getContainingFile());
    PsiCodeBlock initializerBlock = myElementFactory.createCodeBlock();
    PsiVariable outerClassLocal = null;
    if (myNewExpression.getQualifier() != null && myClass.getContainingClass() != null) {
      outerClassLocal = generateOuterClassLocal();
    }
    if (myConstructor != null) {
      myConstructorParameters = myConstructor.getParameterList();

      final PsiExpressionList argumentList = superNewExpressionTemplate.getArgumentList();
      assert argumentList != null;

      if (myNewStatement != null) {
        generateLocalsForArguments();
      }
      analyzeConstructor(initializerBlock);
      addSuperConstructorArguments(argumentList);
    }

    ChangeContextUtil.encodeContextInfo(myClass.getNavigationElement(), true);
    PsiClass classCopy = (PsiClass) myClass.getNavigationElement().copy();
    ChangeContextUtil.clearContextInfo(myClass);
    final PsiClass anonymousClass = superNewExpressionTemplate.getAnonymousClass();
    assert anonymousClass != null;

    int fieldCount = myClass.getFields().length;
    int processedFields = 0;
    if (initializerBlock.getStatements().length > 0 && fieldCount == 0) {
      anonymousClass.addBefore(initializerBlock, anonymousClass.getRBrace());
    }

    for(PsiElement child: classCopy.getChildren()) {
      if ((child instanceof PsiMethod && !((PsiMethod) child).isConstructor()) ||
          child instanceof PsiClassInitializer || child instanceof PsiClass) {
        if (!myFieldInitializers.isEmpty() || !myLocalsForParameters.isEmpty() || classResolveSubstitutor != PsiSubstitutor.EMPTY || outerClassLocal != null) {
          replaceReferences((PsiMember) child, substitutedParameters, outerClassLocal);
        }
        child = anonymousClass.addBefore(child, anonymousClass.getRBrace());
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField) child;
        replaceReferences(field, substitutedParameters, outerClassLocal);
        PsiExpression initializer = myFieldInitializers.get(field.getName());
        field = (PsiField) anonymousClass.addBefore(field, anonymousClass.getRBrace());
        if (initializer != null) {
          field.setInitializer(initializer);
        }
        processedFields++;
        if (processedFields == fieldCount && initializerBlock.getStatements().length > 0) {
          anonymousClass.addBefore(initializerBlock, anonymousClass.getRBrace());
        }
      }
    }
    ChangeContextUtil.decodeContextInfo(anonymousClass, anonymousClass, null);
    final PsiNewExpression superNewExpression = (PsiNewExpression) myNewExpression.replace(superNewExpressionTemplate);
    superNewExpression.getManager().getCodeStyleManager().shortenClassReferences(superNewExpression);
  }

  private void checkInlineChainingConstructor() {
    while(true) {
      PsiMethod constructor = myNewExpression.resolveConstructor();
      if (constructor == null || !InlineMethodHandler.isChainingConstructor(constructor)) break;
      InlineMethodProcessor.inlineConstructorCall(myNewExpression);
    }
  }

  private void analyzeConstructor(final PsiCodeBlock initializerBlock) throws IncorrectOperationException {
    PsiCodeBlock body = myConstructor.getBody();
    assert body != null;
    for(PsiElement child: body.getChildren()) {
      if (child instanceof PsiStatement) {
        PsiStatement stmt = (PsiStatement) child;
        MatchingContext context = new MatchingContext();
        if (ourAssignmentPattern.accepts(stmt, context, new TraverseContext())) {
          PsiAssignmentExpression expression = context.get(ourAssignmentKey);
          if (!processAssignmentInConstructor(expression)) {
            initializerBlock.addBefore(stmt, initializerBlock.getRBrace());
          }
        }
        else if (!ourSuperCallPattern.accepts(stmt) && !ourThisCallPattern.accepts(stmt)) {
          if (stmt instanceof PsiDeclarationStatement) {
            PsiElement[] declaredElements = ((PsiDeclarationStatement)stmt).getDeclaredElements();
            for(PsiElement declaredElement: declaredElements) {
              if (declaredElement instanceof PsiVariable) {
                PsiExpression initializer = ((PsiVariable)declaredElement).getInitializer();
                if (initializer != null) {
                  replaceParameterReferences(initializer, new ArrayList<PsiReferenceExpression>());
                }
              }
            }
          }
          initializerBlock.addBefore(stmt, initializerBlock.getRBrace());
        }
      }
      else if (child instanceof PsiComment) {
        if (child.getPrevSibling() instanceof PsiWhiteSpace) {
          initializerBlock.addBefore(child.getPrevSibling(), initializerBlock.getRBrace());
        }
        initializerBlock.addBefore(child, initializerBlock.getRBrace());
      }
    }
  }

  private boolean processAssignmentInConstructor(final PsiAssignmentExpression expression) {
    if (expression.getLExpression() instanceof PsiReferenceExpression) {
      PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
      final PsiExpression rExpr = expression.getRExpression();
      if (rExpr == null) return true;
      final PsiElement psiElement = lExpr.resolve();
      if (psiElement instanceof PsiField) {
        PsiField field = (PsiField) psiElement;
        if (myClass.getManager().areElementsEquivalent(field.getContainingClass(), myClass)) {
          final List<PsiReferenceExpression> localVarRefs = new ArrayList<PsiReferenceExpression>();
          final PsiExpression initializer;
          try {
            initializer = replaceParameterReferences((PsiExpression)rExpr.copy(), localVarRefs);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return true;
          }
          if (!localVarRefs.isEmpty()) {
            return false;
          }

          myFieldInitializers.put(field.getName(), initializer);
        }
      }
      else if (psiElement instanceof PsiVariable) {
        try {
          replaceParameterReferences((PsiExpression)rExpr.copy(), new ArrayList<PsiReferenceExpression>());
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    return true;
  }

  private boolean isConstant(final PsiExpression expr) {
    Object constantValue = myClass.getManager().getConstantEvaluationHelper().computeConstantExpression(expr);
    return constantValue != null || ourNullPattern.accepts(expr);
  }

  private PsiVariable generateOuterClassLocal() {
    PsiClass outerClass = myClass.getContainingClass();
    assert outerClass != null;
    return generateLocal(StringUtil.decapitalize(outerClass.getName()),
                         myElementFactory.createType(outerClass), myNewExpression.getQualifier());
  }

  private PsiVariable generateLocal(final String baseName, final PsiType type, final PsiExpression initializer) {
    final CodeStyleManager codeStyleManager = myClass.getManager().getCodeStyleManager();

    String baseNameForIndex = baseName;
    int index = 0;
    String localName;
    while(true) {
      localName = codeStyleManager.suggestUniqueVariableName(baseNameForIndex, myNewExpression, true);
      if (myClass.findFieldByName(localName, false) == null) {
        break;
      }
      index++;
      baseNameForIndex = baseName + index;
    }
    try {
      final PsiDeclarationStatement declaration = myElementFactory.createVariableDeclarationStatement(localName, type, initializer);
      PsiVariable variable = (PsiVariable)declaration.getDeclaredElements()[0];
      variable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      myNewStatement.getParent().addBefore(declaration, myNewStatement);
      return variable;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private void generateLocalsForArguments() {
    PsiExpression[] expressions = myConstructorArguments.getExpressions();
    for (int i = 0; i < expressions.length; i++) {
      PsiExpression expr = expressions[i];
      PsiParameter parameter = myConstructorParameters.getParameters()[i];
      if (parameter.isVarArgs()) {
        PsiEllipsisType ellipsisType = (PsiEllipsisType)parameter.getType();
        PsiType baseType = ellipsisType.getComponentType();
        StringBuilder exprBuilder = new StringBuilder("new ");
        exprBuilder.append(baseType.getCanonicalText());
        exprBuilder.append("[] { }");
        try {
          PsiNewExpression newExpr = (PsiNewExpression) myElementFactory.createExpressionFromText(exprBuilder.toString(), myClass);
          PsiArrayInitializerExpression arrayInitializer = newExpr.getArrayInitializer();
          assert arrayInitializer != null;
          for(int j=i; j < expressions.length; j++) {
            arrayInitializer.add(expressions [j]);
          }

          PsiVariable variable = generateLocal(parameter.getName(), ellipsisType.toArrayType(), newExpr);
          myLocalsForParameters.put(parameter, variable);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }

        break;
      }
      else if (!isConstant(expr)) {
        PsiVariable variable = generateLocal(parameter.getName(), parameter.getType(), expr);
        myLocalsForParameters.put(parameter, variable);
      }
    }
  }

  private void addSuperConstructorArguments(PsiExpressionList argumentList) throws IncorrectOperationException {
    final PsiCodeBlock body = myConstructor.getBody();
    assert body != null;
    PsiStatement[] statements = body.getStatements();
    if (statements.length == 0) {
      return;
    }
    MatchingContext context = new MatchingContext();
    if (!ourSuperCallPattern.accepts(statements [0], context, new TraverseContext())) {
      return;
    }
    PsiExpressionList superArguments = context.get(ourCallKey).getArgumentList();
    if (superArguments != null) {
      for(PsiExpression argument: superArguments.getExpressions()) {
        argumentList.add(replaceParameterReferences((PsiExpression) argument.copy(),
                                                    new ArrayList<PsiReferenceExpression>()));
      }
    }
  }

  private PsiExpression replaceParameterReferences(PsiExpression argument,
                                                   final List<PsiReferenceExpression> localVarRefs) throws IncorrectOperationException {
    if (argument instanceof PsiReferenceExpression) {
      PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (element instanceof PsiParameter) {
        PsiParameter parameter = (PsiParameter)element;
        if (myLocalsForParameters.containsKey(parameter)) {
          return (PsiExpression) argument.replace(getParameterReference(parameter));
        }
        int index = myConstructorParameters.getParameterIndex(parameter);
        return (PsiExpression) argument.replace(myConstructorArguments.getExpressions() [index]);
      }
    }

    final List<Pair<PsiReferenceExpression, PsiParameter>> parameterReferences = new ArrayList<Pair<PsiReferenceExpression, PsiParameter>>();
    argument.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement psiElement = expression.resolve();
        if (psiElement instanceof PsiParameter) {
          parameterReferences.add(new Pair<PsiReferenceExpression, PsiParameter>(expression, (PsiParameter) psiElement));
        }
        else if (psiElement instanceof PsiVariable) {
          localVarRefs.add(expression);
        }
      }
    });
    for (Pair<PsiReferenceExpression, PsiParameter> pair: parameterReferences) {
      PsiReferenceExpression ref = pair.first;
      PsiParameter param = pair.second;
      if (myLocalsForParameters.containsKey(param)) {
        ref.replace(getParameterReference(param));
      }
      else {
        int index = myConstructorParameters.getParameterIndex(param);
        if (ref == argument) {
          argument = (PsiExpression)argument.replace(myConstructorArguments.getExpressions() [index]);
        }
        else {
          ref.replace(myConstructorArguments.getExpressions() [index]);
        }
      }
    }
    return argument;
  }

  private PsiExpression getParameterReference(final PsiParameter parameter) throws IncorrectOperationException {
    PsiVariable variable = myLocalsForParameters.get(parameter);
    return myElementFactory.createExpressionFromText(variable.getName(), myClass);
  }

  private void replaceReferences(final PsiMember method,
                                 final PsiType[] substitutedParameters, final PsiVariable outerClassLocal) throws IncorrectOperationException {
    final Map<PsiElement, PsiElement> elementsToReplace = new HashMap<PsiElement, PsiElement>();
    method.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          try {
            PsiField field = (PsiField)element;
            if (myClass.getContainingClass() != null && field.getContainingClass() == myClass.getContainingClass() &&
                     outerClassLocal != null) {
              PsiReferenceExpression expr = (PsiReferenceExpression)expression.copy();
              PsiExpression qualifier = myElementFactory.createExpressionFromText(outerClassLocal.getName(), field.getContainingClass());
              expr.setQualifierExpression(qualifier);
              elementsToReplace.put(expression, expr);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

      public void visitTypeParameter(final PsiTypeParameter classParameter) {
        super.visitTypeParameter(classParameter);
        PsiReferenceList list = classParameter.getExtendsList();
        PsiJavaCodeReferenceElement[] referenceElements = list.getReferenceElements();
        for(PsiJavaCodeReferenceElement reference: referenceElements) {
          PsiElement psiElement = reference.resolve();
          if (psiElement instanceof PsiTypeParameter) {
            checkReplaceTypeParameter(reference, (PsiTypeParameter) psiElement);
          }
        }
      }

      public void visitTypeElement(final PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        if (typeElement.getType() instanceof PsiClassType) {
          PsiClassType classType = (PsiClassType) typeElement.getType();
          PsiClass psiClass = classType.resolve();
          if (psiClass instanceof PsiTypeParameter) {
            checkReplaceTypeParameter(typeElement, (PsiTypeParameter) psiClass);
          }
        }
      }

      private void checkReplaceTypeParameter(PsiElement element, PsiTypeParameter target) {
        PsiClass containingClass = method.getContainingClass();
        PsiTypeParameter[] psiTypeParameters = containingClass.getTypeParameters();
        for(int i=0; i<psiTypeParameters.length; i++) {
          if (psiTypeParameters [i] == target) {
            PsiType substType = substitutedParameters[i];
            if (substType == null) {
              substType = PsiType.getJavaLangObject(element.getManager(), element.getProject().getAllScope());
            }
            elementsToReplace.put(element, myElementFactory.createTypeElement(substType));
          }
        }
      }
    });
    for(Map.Entry<PsiElement, PsiElement> e: elementsToReplace.entrySet()) {
      e.getKey().replace(e.getValue());
    }
  }
}