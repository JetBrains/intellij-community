/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.extractMethodObject;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
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
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.util.duplicates.Match;
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

public class ExtractMethodObjectProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#" + com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor.class.getName());
  @NonNls public static final String REFACTORING_NAME = "Extract Method Object";

  private final PsiElementFactory myElementFactory;

  private MyExtractMethodProcessor myExtractProcessor;
  private boolean myCreateInnerClass;
  private String myInnerClassName;

  public ExtractMethodObjectProcessor(Project project, Editor editor, PsiElement[] elements, final String innerClassName) {
    super(project);
    myInnerClassName = innerClassName;
    myExtractProcessor = new MyExtractMethodProcessor(project, editor, elements, null, REFACTORING_NAME, innerClassName, HelpID.EXTRACT_METHOD_OBJECT);
    myElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
  }

  protected UsageViewDescriptor createUsageViewDescriptor(final UsageInfo[] usages) {
    return new ExtractMethodObjectViewDescriptor(getMethod());
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    PsiReference[] refs =
        ReferencesSearch.search(getMethod(), GlobalSearchScope.projectScope(myProject), false).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (element != null && element.isValid()) {
        result.add(new UsageInfo(element));
      }
    }
    UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  protected void refreshElements(final PsiElement[] elements) {}

  protected void performRefactoring(final UsageInfo[] usages) {
    try {
      if (isCreateInnerClass()) {
        final PsiClass innerClass = (PsiClass)getMethod().getContainingClass().add(myElementFactory.createClass(getInnerClassName()));

        final boolean isStatic = copyMethodModifiers(innerClass);


        for (UsageInfo usage : usages) {
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
          if (methodCallExpression != null) {
            replaceMethodCallExpression(inferTypeArguments(methodCallExpression), methodCallExpression);
          }
        }

        final PsiParameter[] parameters = getMethod().getParameterList().getParameters();
        if (parameters.length > 0) {
          createInnerClassConstructor(innerClass, parameters);
        } else if (isStatic) {
          final PsiMethod copy = (PsiMethod)getMethod().copy();
          copy.setName("invoke");
          innerClass.add(copy);
          return;
        }

        copyMethodWithoutParameters(innerClass);
        copyMethodTypeParameters(innerClass);
      } else {
        for (UsageInfo usage : usages) {
          final PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(usage.getElement(), PsiMethodCallExpression.class);
          if (methodCallExpression != null) {
            methodCallExpression.replace(processMethodDeclaration( methodCallExpression.getArgumentList()));
          }
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public  PsiExpression processMethodDeclaration( PsiExpressionList expressionList) throws IncorrectOperationException {
    if (isCreateInnerClass()) {
      final String typeArguments = getMethod().getTypeParameters().length > 0 ? "<" +
                                                                             StringUtil.join(Arrays.asList(getMethod().getTypeParameters()),
                                                                                             new Function<PsiTypeParameter, String>() {
                                                                                               public String fun(final PsiTypeParameter typeParameter) {
                                                                                                 final String typeParameterName =
                                                                                                     typeParameter.getName();
                                                                                                 LOG.assertTrue(typeParameterName != null);
                                                                                                 return typeParameterName;
                                                                                               }
                                                                                             }, ", ") +
                                                                                                      ">" : "";
      final PsiMethodCallExpression methodCallExpression =
          (PsiMethodCallExpression)myElementFactory.createExpressionFromText("invoke" + expressionList.getText(), null);
      return replaceMethodCallExpression(typeArguments, methodCallExpression);
    }
    else {
      final String paramsDeclaration = getMethod().getParameterList().getText();
      final PsiType returnType = getMethod().getReturnType();
      LOG.assertTrue(returnType != null);

      final PsiCodeBlock methodBody = getMethod().getBody();
      LOG.assertTrue(methodBody != null);
      return myElementFactory.createExpressionFromText("new Object(){ \n" +
                                                       "private " +
                                                       returnType.getPresentableText() +
                                                       " " + myInnerClassName +
                                                       paramsDeclaration +
                                                       methodBody.getText() +
                                                       "}." + myInnerClassName +
                                                       expressionList.getText(), null);
    }
  }


  private PsiMethodCallExpression replaceMethodCallExpression(final String inferredTypeArguments,
                                                              final PsiMethodCallExpression methodCallExpression)
      throws IncorrectOperationException {
    @NonNls final String staticqualifier = getMethod().getModifierList().hasModifierProperty(PsiModifier.STATIC) ? getInnerClassName() : null;
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
      newReplacement = qualifier + "new " + getInnerClassName() + inferredTypeArguments + argumentList.getText()+ ".";
    }
    return (PsiMethodCallExpression)methodCallExpression.replace(myElementFactory.createExpressionFromText(newReplacement + "invoke()", null));
  }

  @NotNull
  private String inferTypeArguments(final PsiMethodCallExpression methodCallExpression) {
    final PsiReferenceParameterList list = methodCallExpression.getMethodExpression().getParameterList();

    if (list != null && list.getTypeArguments().length > 0) {
      return list.getText();
    } else {
      final PsiTypeParameter[] methodTypeParameters = getMethod().getTypeParameters();
      if (methodTypeParameters.length > 0) {
        List<String> typeSignature = new ArrayList<String>();
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(getMethod().getProject()).getResolveHelper();
        for (final PsiTypeParameter typeParameter : methodTypeParameters) {
          final PsiType type = resolveHelper.inferTypeForMethodTypeParameter(typeParameter, getMethod().getParameterList().getParameters(),
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


  private boolean copyMethodModifiers(final PsiClass innerClass) throws IncorrectOperationException {
    final PsiModifierList methodModifierList = getMethod().getModifierList();

    final PsiModifierList innerClassModifierList = innerClass.getModifierList();
    LOG.assertTrue(innerClassModifierList != null);
    innerClassModifierList.setModifierProperty(PsiModifier.PRIVATE, true);
    final boolean isStatic = methodModifierList.hasModifierProperty(PsiModifier.STATIC);
    innerClassModifierList.setModifierProperty(PsiModifier.STATIC, isStatic);
    return isStatic;
  }

  private void copyMethodTypeParameters(final PsiClass innerClass) throws IncorrectOperationException {
    final PsiTypeParameterList typeParameterList = innerClass.getTypeParameterList();
    LOG.assertTrue(typeParameterList != null);

    for (PsiTypeParameter parameter : getMethod().getTypeParameters()) {
      typeParameterList.add(parameter);
    }
  }

  private void copyMethodWithoutParameters(final PsiClass innerClass) throws IncorrectOperationException {
    final PsiMethod newMethod = myElementFactory.createMethod("invoke", getMethod().getReturnType());
    newMethod.getThrowsList().replace(getMethod().getThrowsList());

    final PsiCodeBlock replacedMethodBody = newMethod.getBody();
    LOG.assertTrue(replacedMethodBody != null);
    final PsiCodeBlock methodBody = getMethod().getBody();
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
      final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(getMethod().getProject());
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

  public PsiMethod getMethod() {
    return myExtractProcessor.getExtractedMethod();
  }

  public String getInnerClassName() {
    return myInnerClassName;
  }

  public void setCreateInnerClass(final boolean createInnerClass) {
    myCreateInnerClass = createInnerClass;
  }

  public boolean isCreateInnerClass() {
    return myCreateInnerClass;
  }


  public MyExtractMethodProcessor getExtractProcessor() {
    return myExtractProcessor;
  }

  public class MyExtractMethodProcessor extends ExtractMethodProcessor {

    public MyExtractMethodProcessor(Project project,
                                        Editor editor,
                                        PsiElement[] elements,
                                        PsiType forcedReturnType,
                                        String refactoringName,
                                        String initialMethodName,
                                        String helpId) {
      super(project, editor, elements, forcedReturnType, refactoringName, initialMethodName, helpId);

    }

    @Override
    protected void apply(final AbstractExtractDialog dialog) {
      super.apply(dialog);
      myCreateInnerClass = (((ExtractMethodObjectDialog)dialog).createInnerClass());
      myInnerClassName = myCreateInnerClass ? StringUtil.capitalize(dialog.getChosenMethodName()) : dialog.getChosenMethodName();
    }

    @Override
    protected AbstractExtractDialog createExtractMethodDialog(final boolean direct) {
      return new ExtractMethodObjectDialog(myProject, myTargetClass, myInputVariables, myReturnType, myTypeParameterList,
                                           myThrownExceptions, myStatic, myCanBeStatic,  myElements);
    }


    @Override
    public PsiElement processMatch(final Match match) throws IncorrectOperationException {
      PsiElement element = super.processMatch(match);
      PsiMethodCallExpression methodCallExpression;
      if (element instanceof PsiMethodCallExpression) {
        methodCallExpression = (PsiMethodCallExpression)element;
      }
      else if (element instanceof PsiExpressionStatement) {
        final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
        if (expression instanceof PsiMethodCallExpression) {
          methodCallExpression = (PsiMethodCallExpression)expression;
        }
        else {
          return element;
        }
      }
      else {
        return element;
      }
      PsiExpression expression = processMethodDeclaration(methodCallExpression.getArgumentList());

      return methodCallExpression.replace(expression);
    }
  }
}