/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.replaceMethodWithMethodObject;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ReplaceMethodWithMethodObjectProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + ReplaceMethodWithMethodObjectProcessor.class.getName());
  @NonNls public static final String REFACTORING_NAME = "Replace Method with Method Object";
  private final PsiMethod myMethod;
  private final PsiElementFactory myElementFactory;
  private final String myInnerClassName;
  private final boolean myDeleteOriginalMethod;

  public ReplaceMethodWithMethodObjectProcessor(PsiMethod method, @NonNls final String innerClassName, final boolean deleteOriginalMethod) {
    super(method.getProject());
    myMethod = method;
    myInnerClassName = innerClassName;
    myDeleteOriginalMethod = deleteOriginalMethod;
    myElementFactory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new ReplaceMethodWithMethodObjectViewDescriptor(myMethod);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    if (myDeleteOriginalMethod) {
      PsiReference[] refs =
          ReferencesSearch.search(myMethod, GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
      for (PsiReference ref : refs) {
        final PsiElement element = ref.getElement();
        if (element != null && element.isValid()) {
          result.add(new UsageInfo(element));
        }
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(final PsiElement[] elements) {}

  protected void performRefactoring(final UsageInfo[] usages) {
    try {
      final PsiClass innerClass = createInnerClass();

      final PsiModifierList classModifierList = innerClass.getModifierList();
      LOG.assertTrue(classModifierList != null);

      @NonNls String staticqualifier = null;
      if (classModifierList.hasModifierProperty(PsiModifier.STATIC)) {
        final int packageNameLength = ((PsiClassOwner)innerClass.getContainingFile()).getPackageName().length();
        final String innerClassName = innerClass.getQualifiedName();
        LOG.assertTrue(innerClassName != null);
        staticqualifier = packageNameLength > 0 ? innerClassName.substring(packageNameLength + 1) : innerClassName;
      }

      final String innerClassName = innerClass.getName();
      for (UsageInfo usage : usages) {
        final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
        if (methodCallExpression != null) {
          replaceMethodCallExpression(innerClassName, staticqualifier, inferTypeArguments(methodCallExpression), methodCallExpression);
        }
      }
      processMethodDeclaration(innerClassName, staticqualifier);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void processMethodDeclaration(final String innerClass, final String staticqualifier) throws IncorrectOperationException {
    if (myDeleteOriginalMethod) {
      myMethod.delete();
    }
    else {
      final PsiCodeBlock body = myMethod.getBody();
      LOG.assertTrue(body != null);
      final PsiCodeBlock block = myElementFactory.createCodeBlock();
      final String parametersList =
          StringUtil.join(Arrays.asList(myMethod.getParameterList().getParameters()), new Function<PsiParameter, String>() {
            public String fun(final PsiParameter psiParameter) {
              final String parameterName = psiParameter.getName();
              LOG.assertTrue(parameterName != null);
              return parameterName;
            }
          }, ", ");
      PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)myElementFactory.createExpressionFromText(myMethod.getName() + "(" + parametersList + ")", null);
      final String typeArguments = myMethod.getTypeParameters().length > 0 ?
                                   "<" + StringUtil.join(Arrays.asList(myMethod.getTypeParameters()),
                                                         new Function<PsiTypeParameter, String>() {
                                                           public String fun(final PsiTypeParameter typeParameter) {
                                                             final String typeParameterName =
                                                                 typeParameter.getName();
                                                             LOG.assertTrue(typeParameterName != null);
                                                             return typeParameterName;
                                                           }
                                                         }, ", ") + ">"
                                   : "";
      methodCallExpression = replaceMethodCallExpression(innerClass, staticqualifier, typeArguments, methodCallExpression);
      final PsiStatement innerClassMethodCallStatement = myElementFactory
          .createStatementFromText((myMethod.getReturnType() == PsiType.VOID ? "" : "return ") + methodCallExpression.getText() + ";", null);
      block.add(innerClassMethodCallStatement);
      body.replace(block);
    }
  }

  private PsiMethodCallExpression replaceMethodCallExpression(final String innerClassName,
                                                              final String staticqualifier,
                                                              final String inferredTypeArguments,
                                                              final PsiMethodCallExpression methodCallExpression)
      throws IncorrectOperationException {
    @NonNls String newReplacement;
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
    if (staticqualifier != null) {
      newReplacement = argumentList.getExpressions().length > 0
                       ? "new " + staticqualifier + inferredTypeArguments + argumentList.getText() + "."
                       : staticqualifier + ".";
    } else {
      final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
      final String qualifier = qualifierExpression != null ? qualifierExpression.getText() + "." : "";
      newReplacement = qualifier + "new " + innerClassName + inferredTypeArguments + argumentList.getText()+ ".";
    }
    return (PsiMethodCallExpression)methodCallExpression.replace(myElementFactory.createExpressionFromText(newReplacement + myMethod.getName() + "()", null));
  }

  @NotNull
  private String inferTypeArguments(final PsiMethodCallExpression methodCallExpression) {
    final PsiReferenceParameterList list = methodCallExpression.getMethodExpression().getParameterList();

    if (list != null && list.getTypeArguments().length > 0) {
      return list.getText();
    } else {
      final PsiTypeParameter[] methodTypeParameters = myMethod.getTypeParameters();
      if (methodTypeParameters.length > 0) {
        List<String> typeSignature = new ArrayList<String>();
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myMethod.getProject()).getResolveHelper();
        for (final PsiTypeParameter typeParameter : methodTypeParameters) {
          final PsiType type = resolveHelper.inferTypeForMethodTypeParameter(typeParameter, myMethod.getParameterList().getParameters(),
                                                                             methodCallExpression.getArgumentList().getExpressions(),
                                                                             PsiSubstitutor.EMPTY, methodCallExpression, false);
          if (type == null || type == PsiType.NULL) {
            return "";
          }
          typeSignature.add(type.getPresentableText());
        }
        return "<" + StringUtil.join(typeSignature, ", ") + ">";

      }
    }
    return "";
  }

  protected String getCommandName() {
    return REFACTORING_NAME;
  }

  private PsiClass createInnerClass() throws IncorrectOperationException {
    PsiClass innerClass = myElementFactory.createClass(myInnerClassName);

    final boolean isStatic = copyMethodModifiers(innerClass);

    innerClass = (PsiClass)myMethod.getContainingClass().add(innerClass);

    final PsiParameter[] parameters = myMethod.getParameterList().getParameters();
    if (parameters.length > 0) {
      createInnerClassConstructor(innerClass, parameters);
    } else if (isStatic) {
      innerClass.add(myMethod);
      return innerClass;
    }

    createMethodCopyWithoutParameters(innerClass);
    copyMethodTypeParameters(innerClass);

    return innerClass;
  }

  private boolean copyMethodModifiers(final PsiClass innerClass) throws IncorrectOperationException {
    final PsiModifierList methodModifierList = myMethod.getModifierList();

    final PsiModifierList innerClassModifierList = innerClass.getModifierList();
    LOG.assertTrue(innerClassModifierList != null);

    if (!myDeleteOriginalMethod) {
      innerClassModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    } else {
      innerClassModifierList.setModifierProperty(VisibilityUtil.getVisibilityModifier(methodModifierList), true);
    }
    final boolean isStatic = methodModifierList.hasModifierProperty(PsiModifier.STATIC);
    innerClassModifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
    return isStatic;
  }

  private void copyMethodTypeParameters(final PsiClass innerClass) throws IncorrectOperationException {
    final PsiTypeParameterList typeParameterList = innerClass.getTypeParameterList();
    LOG.assertTrue(typeParameterList != null);

    for (PsiTypeParameter parameter : myMethod.getTypeParameters()) {
      typeParameterList.add(parameter);
    }
  }

  private void createMethodCopyWithoutParameters(final PsiClass innerClass) throws IncorrectOperationException {
    final PsiMethod newMethod = myElementFactory.createMethod(myMethod.getName(), myMethod.getReturnType());
    newMethod.getThrowsList().replace(myMethod.getThrowsList());

    final PsiCodeBlock replacedMethodBody = newMethod.getBody();
    LOG.assertTrue(replacedMethodBody != null);
    final PsiCodeBlock methodBody = myMethod.getBody();
    LOG.assertTrue(methodBody != null);
    replacedMethodBody.replace(methodBody);
    innerClass.add(newMethod);
  }

  private void createInnerClassConstructor(final PsiClass innerClass, final PsiParameter[] parameters) throws IncorrectOperationException {
    final PsiMethod constructor = myElementFactory.createConstructor();
    final PsiParameterList parameterList = constructor.getParameterList();
    for (PsiParameter parameter : parameters) {
      final PsiModifierList parameterModifierList = parameter.getModifierList();
      LOG.assertTrue(parameterModifierList != null);
      final String parameterName = parameter.getName();
      LOG.assertTrue(parameterName != null);
      PsiParameter parm = myElementFactory.createParameter(parameterName, parameter.getType());
      if (CodeStyleSettingsManager.getSettings(myProject).GENERATE_FINAL_PARAMETERS) {
        final PsiModifierList modifierList = parm.getModifierList();
        LOG.assertTrue(modifierList != null);
        modifierList.setModifierProperty(PsiModifier.FINAL, true);
      }
      parameterList.add(parm);

      final PsiField field = createField(parm, constructor, innerClass, parameterModifierList.hasModifierProperty(PsiModifier.FINAL));
      for (PsiReference reference : ReferencesSearch.search(parameter)) {
        reference.handleElementRename(field.getName());
      }
    }
    innerClass.add(constructor);
  }

  private PsiField createField(PsiParameter parameter, PsiMethod constructor, PsiClass innerClass, boolean isFinal) {
    final String parameterName = parameter.getName();
    PsiType type = parameter.getType();
    if (type instanceof PsiEllipsisType) type = ((PsiEllipsisType)type).toArrayType();
    try {
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(myMethod.getProject());
      final String fieldName = styleManager.suggestVariableName(VariableKind.FIELD, parameterName, null, type).names[0];
      PsiField field = myElementFactory.createField(fieldName, type);

      final PsiModifierList modifierList = field.getModifierList();
      LOG.assertTrue(modifierList != null);
      if (AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NULLABLE, false)) {
        modifierList.addAfter(myElementFactory.createAnnotationFromText("@" + AnnotationUtil.NULLABLE, field), null);
      }
      modifierList.setModifierProperty(PsiModifier.FINAL, isFinal);

      final PsiCodeBlock methodBody = constructor.getBody();

      LOG.assertTrue(methodBody != null);

      @NonNls final  String stmtText;
      if (Comparing.strEqual(parameterName, fieldName)) {
        stmtText = "this." + fieldName + " = " + parameterName + ";";
      } else {
        stmtText = fieldName + " = " + parameterName + ";";
      }
      PsiStatement assignmentStmt = myElementFactory.createStatementFromText(stmtText, methodBody);
      assignmentStmt = (PsiStatement)CodeStyleManager.getInstance(constructor.getProject()).reformat(assignmentStmt);
      methodBody.add(assignmentStmt);

      field = (PsiField)innerClass.add(field);
      return field;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    return null;
  }
}