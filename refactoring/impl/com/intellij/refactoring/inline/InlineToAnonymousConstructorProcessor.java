package com.intellij.refactoring.inline;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.patterns.impl.MatchingContext;
import com.intellij.patterns.impl.TraverseContext;
import com.intellij.patterns.impl.Pattern;
import com.intellij.patterns.impl.StandardPatterns;
import static com.intellij.patterns.impl.StandardPatterns.psiExpressionStatement;
import static com.intellij.patterns.impl.StandardPatterns.psiElement;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author yole
*/
class InlineToAnonymousConstructorProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inline.InlineToAnonymousConstructorProcessor");

  private static Key<PsiAssignmentExpression> ourAssignmentKey = Key.create("assignment");
  private static Pattern ourNullPattern = StandardPatterns.psiExpression().type(PsiLiteralExpression.class).withText(PsiKeyword.NULL);
  private static Pattern ourAssignmentPattern = psiExpressionStatement().withChild(psiElement(PsiAssignmentExpression.class).save(ourAssignmentKey));
  private static Pattern ourSuperCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(psiElement().withText(PsiKeyword.SUPER)));
  private static Pattern ourThisCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(psiElement().withText(PsiKeyword.THIS)));

  private PsiClass myClass;
  private PsiNewExpression myNewExpression;
  private PsiType mySuperType;

  public InlineToAnonymousConstructorProcessor(final PsiClass aClass, final PsiNewExpression psiNewExpression,
                                               final PsiType superType) {
    myClass = aClass;
    myNewExpression = psiNewExpression;
    mySuperType = superType;
  }

  public void run() throws IncorrectOperationException {
    checkInlineChainingConstructor(myNewExpression);
    JavaResolveResult classResolveResult = myNewExpression.getClassReference().advancedResolve(false);
    JavaResolveResult methodResolveResult = myNewExpression.resolveMethodGenerics();
    final PsiMethod constructor = (PsiMethod) methodResolveResult.getElement();

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

    PsiElementFactory factory = myClass.getManager().getElementFactory();
    final PsiExpressionList constructorArguments = myNewExpression.getArgumentList();

    PsiNewExpression superNewExpressionTemplate = (PsiNewExpression) factory.createExpressionFromText(builder.toString(),
                                                                                                      myNewExpression.getContainingFile());
    FieldInfoMap fieldMap = new FieldInfoMap();
    PsiCodeBlock initializerBlock = factory.createCodeBlock();
    PsiVariable outerClassLocal = null;
    if (myNewExpression.getQualifier() != null && myClass.getContainingClass() != null) {
      outerClassLocal = generateOuterClassLocal(myNewExpression);
    }
    if (constructor != null) {
      final PsiExpressionList argumentList = superNewExpressionTemplate.getArgumentList();
      assert argumentList != null;

      boolean isInMethod = (PsiTreeUtil.getParentOfType(myNewExpression, PsiStatement.class) != null);
      fieldMap = analyzeConstructor(constructor, constructorArguments, initializerBlock, isInMethod);
      addSuperConstructorArguments(constructor, argumentList, constructorArguments, fieldMap, isInMethod);
      generateLocalsForFields(fieldMap, myNewExpression);
    }

    ChangeContextUtil.encodeContextInfo(myClass.getNavigationElement(), true);
    PsiClass classCopy = (PsiClass) myClass.getNavigationElement().copy();
    ChangeContextUtil.clearContextInfo(myClass);
    final PsiClass anonymousClass = superNewExpressionTemplate.getAnonymousClass();
    assert anonymousClass != null;

    if (initializerBlock.getStatements().length > 0) {
      anonymousClass.addBefore(initializerBlock, anonymousClass.getRBrace());
    }

    for(PsiElement child: classCopy.getChildren()) {
      if ((child instanceof PsiMethod && !((PsiMethod) child).isConstructor()) ||
          child instanceof PsiClassInitializer || child instanceof PsiClass) {
        if (!fieldMap.isEmpty() || classResolveSubstitutor != PsiSubstitutor.EMPTY || outerClassLocal != null) {
          replaceReferences((PsiMember) child, fieldMap, substitutedParameters, outerClassLocal);
        }
        child = anonymousClass.addBefore(child, anonymousClass.getRBrace());
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField) child;
        FieldInfo info = fieldMap.getFieldInfo(field.getName());
        if (info == null || !info.replaceWithLocal) {
          boolean noInitializer = (field.getInitializer() == null);
          field = (PsiField) anonymousClass.addBefore(field, anonymousClass.getRBrace());
          if (info != null) {
            if (info.localVar != null) {
              field.setInitializer(factory.createExpressionFromText(info.localVar.getName(), field));
            }
            else if (info.initializer != null && noInitializer) {
              field.setInitializer(info.initializer);
            }
          }
        }
      }
    }
    ChangeContextUtil.decodeContextInfo(anonymousClass, anonymousClass, null);
    final PsiNewExpression superNewExpression = (PsiNewExpression) myNewExpression.replace(superNewExpressionTemplate);
    superNewExpression.getManager().getCodeStyleManager().shortenClassReferences(superNewExpression);
  }

  private static void checkInlineChainingConstructor(final PsiNewExpression newExpression) {
    while(true) {
      PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null || !InlineMethodHandler.isChainingConstructor(constructor)) break;
      InlineMethodProcessor.inlineConstructorCall(newExpression);
    }
  }

  private FieldInfoMap analyzeConstructor(final PsiMethod constructor, final PsiExpressionList constructorArguments,
                                          final PsiCodeBlock initializerBlock,
                                          final boolean isInMethod) throws IncorrectOperationException {
    final FieldInfoMap result = new FieldInfoMap();
    PsiCodeBlock body = constructor.getBody();
    assert body != null;
    for(PsiElement child: body.getChildren()) {
      if (child instanceof PsiStatement) {
        PsiStatement stmt = (PsiStatement) child;
        MatchingContext context = new MatchingContext();
        if (ourAssignmentPattern.accepts(stmt, context, new TraverseContext())) {
          PsiAssignmentExpression expression = context.get(ourAssignmentKey);
          if (!processAssignmentInConstructor(constructor, constructorArguments, result, expression, isInMethod)) {
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
                  replaceParameterReferences(constructor.getParameterList(),
                                             initializer,
                                             constructorArguments,
                                             new ArrayList<PsiReferenceExpression>(),
                                             result,
                                             isInMethod);
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
    checkFieldWriteUsages(result);

    return result;
  }

  private void checkFieldWriteUsages(final FieldInfoMap result) {
    for(FieldInfo info: result.getFields()) {
      if (info.generateLocal) {
        PsiField field = myClass.findFieldByName(info.name, false);
        assert field != null;
        boolean hasOnlyReadUsages = ReferencesSearch.search(field, new LocalSearchScope(myClass)).forEach(new Processor<PsiReference>() {
          public boolean process(final PsiReference psiReference) {
            PsiElement psiElement = psiReference.getElement();
            if (psiElement instanceof PsiExpression) {
              PsiMethod method = PsiTreeUtil.getParentOfType(psiElement, PsiMethod.class);
              if (method == null || !method.isConstructor()) {
                return !PsiUtil.isAccessedForWriting((PsiExpression)psiElement);
              }
            }

            return true;
          }
        });
        if (hasOnlyReadUsages) {
          info.replaceWithLocal = true;
        }
      }
    }
  }

  private boolean processAssignmentInConstructor(final PsiMethod constructor, final PsiExpressionList constructorArguments,
                                                 final FieldInfoMap fieldInfoMap,
                                                 final PsiAssignmentExpression expression,
                                                 final boolean isInMethod) {
    if (expression.getLExpression() instanceof PsiReferenceExpression) {
      PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
      final PsiExpression rExpr = expression.getRExpression();
      if (rExpr == null) return true;
      final PsiElement psiElement = lExpr.resolve();
      if (psiElement instanceof PsiField) {
        PsiField field = (PsiField) psiElement;
        if (myClass.getManager().areElementsEquivalent(field.getContainingClass(), myClass)) {
          FieldInfo info = fieldInfoMap.getNewFieldInfo(field);

          Object constantValue = myClass.getManager().getConstantEvaluationHelper().computeConstantExpression(rExpr);
          final boolean isConstantInitializer = constantValue != null || ourNullPattern.accepts(rExpr);
          if (!isConstantInitializer) {
            final List<PsiReferenceExpression> localVarRefs = new ArrayList<PsiReferenceExpression>();
            final PsiExpression initializer;
            try {
              initializer = replaceParameterReferences(constructor.getParameterList(),
                                                       (PsiExpression)rExpr.copy(),
                                                       constructorArguments,
                                                       localVarRefs,
                                                       fieldInfoMap,
                                                       isInMethod);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
              return true;
            }
            if (!localVarRefs.isEmpty()) {
              return false;
            }

            info.initializer = initializer;
            info.generateLocal = true;
          }
          else {
            info.initializer = (PsiExpression) rExpr.copy();
          }
        }
      }
      else if (psiElement instanceof PsiVariable) {
        try {
          replaceParameterReferences(constructor.getParameterList(),
                                   (PsiExpression)rExpr.copy(),
                                   constructorArguments,
                                   new ArrayList<PsiReferenceExpression>(),
                                     fieldInfoMap,
                                     isInMethod);
        }
        catch (IncorrectOperationException e) {
          LOG.error(e);
        }
      }
    }
    return true;
  }

  private PsiVariable generateOuterClassLocal(final PsiNewExpression newExpression) {
    final PsiElementFactory factory = myClass.getManager().getElementFactory();
    final CodeStyleManager codeStyleManager = myClass.getManager().getCodeStyleManager();

    String outerClassName = StringUtil.decapitalize(myClass.getContainingClass().getName());
    String localName = codeStyleManager.suggestUniqueVariableName(outerClassName, newExpression, true);
    try {
      final PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(localName,
                                                                                             factory.createType(myClass.getContainingClass()),
                                                                                             newExpression.getQualifier());
      PsiVariable variable = (PsiVariable)declaration.getDeclaredElements()[0];
      variable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      final PsiStatement newStatement = PsiTreeUtil.getParentOfType(newExpression, PsiStatement.class);
      assert newStatement != null;
      newStatement.getParent().addBefore(declaration, newStatement);
      return variable;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private void generateLocalsForFields(final FieldInfoMap fieldMap, final PsiNewExpression newExpression) {
    final PsiElementFactory factory = myClass.getManager().getElementFactory();
    final CodeStyleManager codeStyleManager = myClass.getManager().getCodeStyleManager();
    final PsiStatement newStatement = PsiTreeUtil.getParentOfType(newExpression, PsiStatement.class);

    for(FieldInfo info: fieldMap.getLocalsToGenerate()) {
      final String fieldName = info.name;
      String varName = codeStyleManager.variableNameToPropertyName(fieldName, VariableKind.FIELD);
      String localName = codeStyleManager.suggestUniqueVariableName(varName, newExpression, true);
      try {
        final PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(localName, info.type, info.initializer);
        PsiVariable variable = (PsiVariable)declaration.getDeclaredElements()[0];
        variable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        assert newStatement != null;
        newStatement.getParent().addBefore(declaration, newStatement);
        info.localVar = variable;
      }
      catch(IncorrectOperationException ex) {
        LOG.error(ex);
      }
    }
  }

  private static void addSuperConstructorArguments(PsiMethod constructor, PsiExpressionList argumentList,
                                                   PsiExpressionList constructorArguments,
                                                   FieldInfoMap fieldInfoMap,
                                                   boolean isInMethod) throws IncorrectOperationException {
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
        argumentList.add(replaceParameterReferences(constructor.getParameterList(), argument, constructorArguments,
                                                    new ArrayList<PsiReferenceExpression>(),
                                                    fieldInfoMap,
                                                    isInMethod));
      }
    }
  }

  private static PsiExpression replaceParameterReferences(final PsiParameterList constructorParameters,
                                                          PsiExpression argument,
                                                          final PsiExpressionList constructorArguments,
                                                          final List<PsiReferenceExpression> localVarRefs,
                                                          final FieldInfoMap fieldInfoMap,
                                                          final boolean isInMethod) throws IncorrectOperationException {
    if (argument instanceof PsiReferenceExpression) {
      PsiElement element = ((PsiReferenceExpression)argument).resolve();
      if (element instanceof PsiParameter) {
        int index = constructorParameters.getParameterIndex((PsiParameter) element);
        return (PsiExpression) argument.replace(constructorArguments.getExpressions() [index]);
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
      PsiParameter param = pair.second;
      int index = constructorParameters.getParameterIndex(param);
      if (isInMethod) {
        fieldInfoMap.addParameter(param, constructorArguments.getExpressions() [index]);
      }
      else {
        PsiReferenceExpression ref = pair.first;
        if (ref == argument) {
          argument = (PsiExpression)argument.replace(constructorArguments.getExpressions() [index]);
        }
        else {
          ref.replace(constructorArguments.getExpressions() [index]);
        }
      }
    }
    return argument;
  }

  private void replaceReferences(final PsiMember method, final FieldInfoMap fieldMap,
                                 final PsiType[] substitutedParameters, final PsiVariable outerClassLocal) throws IncorrectOperationException {
    final PsiElementFactory factory = myClass.getManager().getElementFactory();
    final Map<PsiReferenceExpression, PsiExpression> referencesToReplace = new HashMap<PsiReferenceExpression, PsiExpression>();
    final Map<PsiTypeElement, PsiTypeElement> typesToReplace = new HashMap<PsiTypeElement, PsiTypeElement>();
    method.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          try {
            PsiField field = (PsiField)element;
            if (field.getContainingClass() == method.getContainingClass()) {
              FieldInfo info = fieldMap.getFieldInfo(field.getName());
              if (info != null && info.replaceWithLocal) {
                final PsiExpression localRefExpr = factory.createExpressionFromText(info.localVar.getName(), method);
                referencesToReplace.put(expression, localRefExpr);
              }
            }
            else if (myClass.getContainingClass() != null && field.getContainingClass() == myClass.getContainingClass() &&
                     outerClassLocal != null) {
              PsiReferenceExpression expr = (PsiReferenceExpression)expression.copy();
              PsiExpression qualifier = factory.createExpressionFromText(outerClassLocal.getName(), field.getContainingClass());
              expr.setQualifierExpression(qualifier);
              referencesToReplace.put(expression, expr);
            }
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }

      public void visitTypeElement(final PsiTypeElement typeElement) {
        super.visitTypeElement(typeElement);
        if (typeElement.getType() instanceof PsiClassType) {
          PsiClassType classType = (PsiClassType) typeElement.getType();
          PsiClass psiClass = classType.resolve();
          if (psiClass instanceof PsiTypeParameter) {
            PsiClass containingClass = method.getContainingClass();
            PsiTypeParameter[] psiTypeParameters = containingClass.getTypeParameters();
            for(int i=0; i<psiTypeParameters.length; i++) {
              if (psiTypeParameters [i] == psiClass) {
                typesToReplace.put(typeElement, factory.createTypeElement(substitutedParameters [i]));
              }
            }
          }
        }
      }
    });
    for(Map.Entry<PsiReferenceExpression, PsiExpression> e: referencesToReplace.entrySet()) {
      e.getKey().replace(e.getValue());
    }
    for(Map.Entry<PsiTypeElement, PsiTypeElement> e: typesToReplace.entrySet()) {
      e.getKey().replace(e.getValue());
    }
  }

  private static class FieldInfo {
    public FieldInfo(final String name, final PsiType type) {
      this.name = name;
      this.type = type;
    }

    String name;
    PsiType type;
    PsiVariable localVar;
    PsiExpression initializer;
    boolean generateLocal;
    boolean replaceWithLocal;
  }

  private static class FieldInfoMap {
    private Map<String, FieldInfo> myFields = new HashMap<String, FieldInfo>();
    private Map<String, FieldInfo> myParameters = new HashMap<String, FieldInfo>();

    public FieldInfo getFieldInfo(String fieldName) {
      return myFields.get(fieldName);
    }

    public FieldInfo getNewFieldInfo(PsiField field) {
      FieldInfo info = myFields.get(field.getName());
      if (info == null) {
        info = new FieldInfo(field.getName(), field.getType());
        myFields.put(field.getName(), info);
      }
      return info;
    }

    public FieldInfo addParameter(PsiParameter param, PsiExpression initializer) {
      FieldInfo info = myParameters.get(param.getName());
      if (info == null) {
        info = new FieldInfo(param.getName(), param.getType());
        myParameters.put(param.getName(), info);
        info.initializer = initializer;
      }
      return info;
    }

    public Collection<FieldInfo> getFields() {
      return myFields.values();
    }

    public boolean isEmpty() {
      return myFields.size() == 0;
    }

    public Collection<FieldInfo> getLocalsToGenerate() {
      List<FieldInfo> fieldInfos = new ArrayList<FieldInfo>();
      for(FieldInfo info: myFields.values()) {
        if (info.generateLocal) {
          fieldInfos.add(info);
        }
      }
      fieldInfos.addAll(myParameters.values());
      return fieldInfos;
    }
  }
}