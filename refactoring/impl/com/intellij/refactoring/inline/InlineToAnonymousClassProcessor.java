package com.intellij.refactoring.inline;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.patterns.impl.MatchingContext;
import com.intellij.patterns.impl.Pattern;
import com.intellij.patterns.impl.StandardPatterns;
import static com.intellij.patterns.impl.StandardPatterns.psiElement;
import static com.intellij.patterns.impl.StandardPatterns.psiExpressionStatement;
import com.intellij.patterns.impl.TraverseContext;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
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

  private static Key<PsiAssignmentExpression> ourAssignmentKey = Key.create("assignment");
  private static Pattern ourNullPattern = StandardPatterns.psiExpression().type(PsiLiteralExpression.class).withText(PsiKeyword.NULL);
  private static Pattern ourAssignmentPattern = psiExpressionStatement().withChild(psiElement(PsiAssignmentExpression.class).save(ourAssignmentKey));
  private static Pattern ourSuperCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(psiElement().withText(PsiKeyword.SUPER)));
  private static Pattern ourThisCallPattern = psiExpressionStatement().withFirstChild(psiElement(PsiMethodCallExpression.class).withFirstChild(psiElement().withText(PsiKeyword.THIS)));

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
    PsiElementFactory factory = myClass.getManager().getElementFactory();
    PsiClassType superType = getSuperType();

    List<PsiElement> elementsToDelete = new ArrayList<PsiElement>();
    for(UsageInfo info: usages) {
      final PsiElement element = info.getElement();
      if (element.getParent() instanceof PsiNewExpression) {
        try {
          replaceNewExpression((PsiNewExpression) element.getParent(), superType);
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
            PsiClassType psiType = (PsiClassType) typeElement.getType();
            PsiClassType.ClassResolveResult classResolveResult = psiType.resolveGenerics();
            PsiType substType = classResolveResult.getSubstitutor().substitute(superType);
            assert classResolveResult.getElement() == myClass;
            try {
              typeElement.replace(factory.createTypeElement(substType));
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
        if (element.isValid()) {
          element.delete();
        }
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

  private PsiClassType getSuperType() {
    PsiElementFactory factory = myClass.getManager().getElementFactory();

    PsiClassType superType;
    final PsiClass[] interfaces = myClass.getInterfaces();
    if (interfaces.length > 0) {
      PsiClassType[] interfaceTypes = myClass.getImplementsListTypes();
      assert interfaces.length == 1;
      assert interfaceTypes.length == 1;
      superType = interfaceTypes [0];
    }
    else {
      PsiClass superClass = myClass.getSuperClass();
      PsiClassType[] classTypes = myClass.getExtendsListTypes();
      if (classTypes.length > 0) {
        superType = classTypes [0];
      }
      else {
        superType = factory.createType(superClass);
      }
    }
    return superType;
  }

  private void replaceNewExpression(final PsiNewExpression newExpression, final PsiClassType superType) throws IncorrectOperationException {
    JavaResolveResult classResolveResult = newExpression.getClassReference().advancedResolve(false);
    JavaResolveResult methodResolveResult = newExpression.resolveMethodGenerics();

    PsiSubstitutor classResolveSubstitutor = classResolveResult.getSubstitutor();
    PsiType substType = classResolveSubstitutor.substitute(superType);

    PsiTypeParameter[] typeParams = myClass.getTypeParameters();
    PsiType[] substitutedParameters = new PsiType[typeParams.length];
    for(int i=0; i< typeParams.length; i++) {
      substitutedParameters [i] = classResolveSubstitutor.substitute(typeParams [i]);
    }

    @NonNls StringBuilder builder = new StringBuilder("new ");
    builder.append(substType.getPresentableText());
    builder.append("() {}");

    PsiElementFactory factory = myClass.getManager().getElementFactory();
    final PsiMethod constructor = (PsiMethod) methodResolveResult.getElement();
    final PsiExpressionList constructorArguments = newExpression.getArgumentList();

    PsiNewExpression superNewExpressionTemplate = (PsiNewExpression) factory.createExpressionFromText(builder.toString(),
                                                                                                      newExpression.getContainingFile());
    Map<String, FieldInfo> fieldMap = new HashMap<String, FieldInfo>();
    PsiCodeBlock initializerBlock = factory.createCodeBlock();
    if (constructor != null) {
      final PsiExpressionList argumentList = superNewExpressionTemplate.getArgumentList();
      assert argumentList != null;

      fieldMap = analyzeConstructor(constructor, constructorArguments, initializerBlock);
      generateLocalsForFields(fieldMap, newExpression);

      addSuperConstructorArguments(constructor, argumentList, constructorArguments);
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
      if ((child instanceof PsiMethod && !((PsiMethod) child).isConstructor())) {
        if (fieldMap.size() > 0 || classResolveSubstitutor != PsiSubstitutor.EMPTY) {
          replaceReferences((PsiMember) child, fieldMap, substitutedParameters);
        }
        child = anonymousClass.addBefore(child, anonymousClass.getRBrace());
      }
      else if (child instanceof PsiClassInitializer || child instanceof PsiClass) {

        if (fieldMap.size() > 0 || classResolveSubstitutor != PsiSubstitutor.EMPTY) {
          replaceReferences((PsiMember) child, fieldMap, substitutedParameters);
        }
        child = anonymousClass.addBefore(child, anonymousClass.getRBrace());
      }
      else if (child instanceof PsiField) {
        PsiField field = (PsiField) child;
        FieldInfo info = fieldMap.get(field.getName());
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
    final PsiNewExpression superNewExpression = (PsiNewExpression) newExpression.replace(superNewExpressionTemplate);
    superNewExpression.getManager().getCodeStyleManager().shortenClassReferences(superNewExpression);
  }

  private Map<String, FieldInfo> analyzeConstructor(final PsiMethod constructor, final PsiExpressionList constructorArguments,
                                                    final PsiCodeBlock initializerBlock) throws IncorrectOperationException {
    final Map<String, FieldInfo> result = new HashMap<String, FieldInfo>();
    PsiCodeBlock body = constructor.getBody();
    assert body != null;
    for(PsiStatement stmt: body.getStatements()) {
      MatchingContext context = new MatchingContext();
      if (ourAssignmentPattern.accepts(stmt, context, new TraverseContext())) {
        PsiAssignmentExpression expression = context.get(ourAssignmentKey);
        processAssignmentInConstructor(constructor, constructorArguments, result, expression);
      }
      else if (!ourSuperCallPattern.accepts(stmt) && !ourThisCallPattern.accepts(stmt)) {
        initializerBlock.addBefore(stmt.copy(), initializerBlock.getRBrace());
      }
    }
    checkFieldWriteUsages(result);

    return result;
  }

  private void checkFieldWriteUsages(final Map<String, FieldInfo> result) {
    for(Map.Entry<String, FieldInfo> e: result.entrySet()) {
      FieldInfo info = e.getValue();
      if (info.generateLocal) {
        PsiField field = myClass.findFieldByName(e.getKey(), false);
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

  private void processAssignmentInConstructor(final PsiMethod constructor, final PsiExpressionList constructorArguments, final Map<String, FieldInfo> result,
                                              final PsiAssignmentExpression expression) {
    if (expression.getLExpression() instanceof PsiReferenceExpression) {
      PsiReferenceExpression lExpr = (PsiReferenceExpression) expression.getLExpression();
      final PsiExpression rExpr = expression.getRExpression();
      final PsiElement psiElement = lExpr.resolve();
      if (psiElement instanceof PsiField && rExpr != null) {
        PsiField field = (PsiField) psiElement;
        if (myClass.getManager().areElementsEquivalent(field.getContainingClass(), myClass)) {
          FieldInfo info = result.get(field.getName());
          if (info == null) {
            info = new FieldInfo(field.getType());
            result.put(field.getName(), info);
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
            info.generateLocal = true;
          }
          else {
            info.initializer = (PsiExpression) rExpr.copy();
          }
        }
      }
    }
  }

  private void generateLocalsForFields(final Map<String, FieldInfo> fieldMap, final PsiNewExpression newExpression) {
    for(Map.Entry<String, FieldInfo> e: fieldMap.entrySet()) {
      FieldInfo info = e.getValue();
      if (info.generateLocal) {
        final CodeStyleManager codeStyleManager = myClass.getManager().getCodeStyleManager();
        final String fieldName = e.getKey();
        String varName = codeStyleManager.variableNameToPropertyName(fieldName, VariableKind.FIELD);
        String localName = codeStyleManager.suggestUniqueVariableName(varName, newExpression, true);
        final PsiElementFactory factory = myClass.getManager().getElementFactory();
        try {
          final PsiDeclarationStatement declaration = factory.createVariableDeclarationStatement(localName, info.type, info.initializer);
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

  private void replaceReferences(final PsiMember method, final Map<String, FieldInfo> fieldMap,
                                 final PsiType[] substitutedParameters) throws  IncorrectOperationException {
    final PsiElementFactory factory = myClass.getManager().getElementFactory();
    final Map<PsiReferenceExpression, PsiVariable> referencesToReplace = new HashMap<PsiReferenceExpression, PsiVariable>();
    final Map<PsiTypeElement, PsiTypeElement> typesToReplace = new HashMap<PsiTypeElement, PsiTypeElement>();
    method.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(final PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final PsiElement element = expression.resolve();
        if (element instanceof PsiField) {
          PsiField field = (PsiField) element;
          if (field.getContainingClass() == method.getContainingClass()) {
            FieldInfo info = fieldMap.get(field.getName());
            if (info != null && info.replaceWithLocal) {
              referencesToReplace.put(expression, info.localVar);
            }
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
    for(Map.Entry<PsiReferenceExpression, PsiVariable> e: referencesToReplace.entrySet()) {
      final PsiExpression expression = factory.createExpressionFromText(e.getValue().getName(), method);
      e.getKey().replace(expression);
    }
    for(Map.Entry<PsiTypeElement, PsiTypeElement> e: typesToReplace.entrySet()) {
      e.getKey().replace(e.getValue());
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
      if (parentElement instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)parentElement;
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

  private static class FieldInfo {
    public FieldInfo(final PsiType type) {
      this.type = type;
    }

    PsiType type;
    PsiVariable localVar;
    PsiExpression initializer;
    boolean generateLocal;
    boolean replaceWithLocal;
  }
}
