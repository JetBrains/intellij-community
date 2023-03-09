// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.PackageUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.psi.TypeParametersVisitor;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.FixableUsagesRefactoringProcessor;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.ChangeReturnType;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.ReturnWrappedValue;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.UnwrapCall;
import com.intellij.refactoring.wrapreturnvalue.usageInfo.WrapReturnValue;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WrapReturnValueProcessor extends FixableUsagesRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(WrapReturnValueProcessor.class);

  private final MoveDestination myMoveDestination;
  private final PsiMethod myMethod;
  @NotNull
  private final String myClassName;
  private final String myPackageName;
  private final boolean myCreateInnerClass;
  private final PsiField myDelegateField;
  private final String myQualifiedName;
  private final boolean myUseExistingClass;
  private final List<PsiTypeParameter> myTypeParameters;
  @NonNls private final String myUnwrapMethodName;

  public WrapReturnValueProcessor(@NotNull String className,
                                  String packageName,
                                  MoveDestination moveDestination,
                                  PsiMethod method,
                                  boolean useExistingClass,
                                  final boolean createInnerClass,
                                  PsiField delegateField) {
    super(method.getProject());
    myMoveDestination = moveDestination;
    myMethod = method;
    myClassName = className;
    myPackageName = packageName;
    myCreateInnerClass = createInnerClass;
    myDelegateField = delegateField;
    myQualifiedName = StringUtil.getQualifiedName(packageName, className);
    myUseExistingClass = useExistingClass;

    final Set<PsiTypeParameter> typeParamSet = new HashSet<>();
    final TypeParametersVisitor visitor = new TypeParametersVisitor(typeParamSet);
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    assert returnTypeElement != null;
    returnTypeElement.accept(visitor);
    myTypeParameters = new ArrayList<>(typeParamSet);
    if (useExistingClass) {
      myUnwrapMethodName = calculateUnwrapMethodName();
    }
    else {
      myUnwrapMethodName = "getValue";
    }
  }

  @NonNls
  private String calculateUnwrapMethodName() {
    final PsiClass existingClass = JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
    if (existingClass != null) {
      if (TypeConversionUtil.isPrimitiveWrapper(myQualifiedName)) {
        final PsiPrimitiveType unboxedType =
          PsiPrimitiveType.getUnboxedType(JavaPsiFacade.getElementFactory(myProject).createType(existingClass));
        assert unboxedType != null;
        return unboxedType.getCanonicalText() + "Value()";
      }

      final PsiMethod getter = PropertyUtilBase.findGetterForField(myDelegateField);
      return getter != null ? getter.getName() : "";
    }
    return "";
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usageInfos) {
    return new WrapReturnValueUsageViewDescriptor(myMethod, usageInfos);
  }

  @Override
  public void findUsages(@NotNull List<? super FixableUsageInfo> usages) {
    findUsagesForMethod(myMethod, usages);
    for (PsiMethod overridingMethod : OverridingMethodsSearch.search(myMethod)) {
      findUsagesForMethod(overridingMethod, usages);
    }
  }

  private void findUsagesForMethod(PsiMethod psiMethod, List<? super FixableUsageInfo> usages) {
    for (PsiReference reference : ReferencesSearch.search(psiMethod, psiMethod.getUseScope())) {
      final PsiElement referenceElement = reference.getElement();
      final PsiElement parent = referenceElement.getParent();
      if (parent instanceof PsiCallExpression) {
        usages.add(new UnwrapCall((PsiCallExpression)parent, myUnwrapMethodName));
      }
      else if (referenceElement instanceof PsiMethodReferenceExpression) {
        usages.add(new UnwrapCall((PsiMethodReferenceExpression)referenceElement, myUnwrapMethodName));
      }
    }
    final String returnType = calculateReturnTypeString();
    usages.add(new ChangeReturnType(psiMethod, returnType));
    psiMethod.accept(new ReturnSearchVisitor(usages, returnType));
  }

  private String calculateReturnTypeString() {
    final String qualifiedName = StringUtil.getQualifiedName(myPackageName, myClassName);
    final StringBuilder returnTypeBuffer = new StringBuilder(qualifiedName);
    if (!myTypeParameters.isEmpty()) {
      returnTypeBuffer.append('<');
      returnTypeBuffer.append(StringUtil.join(myTypeParameters, typeParameter -> {
        final String paramName = typeParameter.getName();
        LOG.assertTrue(paramName != null);
        return paramName;
      }, ","));
      returnTypeBuffer.append('>');
    }
    else if (myDelegateField != null) {
      final PsiType type = myDelegateField.getType();
      final PsiType returnType = myMethod.getReturnType();
      final PsiClass containingClass = myDelegateField.getContainingClass();
      final PsiType inferredType = getInferredType(type, returnType, containingClass, myMethod);
      if (inferredType != null) {
        returnTypeBuffer.append("<").append(inferredType.getCanonicalText()).append(">");
      }
    }
    return returnTypeBuffer.toString();
  }

  protected static PsiType getInferredType(PsiType type, PsiType returnType, PsiClass containingClass, PsiMethod method) {
    if (containingClass != null && containingClass.getTypeParameters().length == 1) {
      final PsiSubstitutor substitutor = PsiResolveHelper.getInstance(method.getProject())
        .inferTypeArguments(containingClass.getTypeParameters(), new PsiType[]{type}, new PsiType[]{returnType}, PsiUtil.getLanguageLevel(
          method));
      final PsiTypeParameter typeParameter = containingClass.getTypeParameters()[0];
      final PsiType substituted = substitutor.substitute(typeParameter);
      if (substituted != null && !typeParameter.equals(PsiUtil.resolveClassInClassTypeOnly(substituted))) {
        return substituted;
      }
    }
    return null;
  }

  @Override
  protected boolean preprocessUsages(@NotNull final Ref<UsageInfo[]> refUsages) {
    MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    PsiClass existingClass = JavaPsiFacade.getInstance(myProject).findClass(myQualifiedName, GlobalSearchScope.allScope(myProject));
    if (myUseExistingClass) {
      if (existingClass == null) {
        conflicts.putValue(null, JavaRareRefactoringsBundle.message("could.not.find.selected.wrapping.class"));
      }
      else {
        PsiElement navigationElement = existingClass.getNavigationElement();
        if (navigationElement instanceof PsiClass) {
          existingClass = (PsiClass)navigationElement;
        }
        boolean foundConstructor = false;
        final Set<PsiType> returnTypes = new HashSet<>();
        returnTypes.add(myMethod.getReturnType());
        final PsiCodeBlock methodBody = myMethod.getBody();
        if (methodBody != null) {
          methodBody.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitReturnStatement(final @NotNull PsiReturnStatement statement) {
              super.visitReturnStatement(statement);
              final PsiExpression returnValue = statement.getReturnValue();
              if (returnValue != null) {
                returnTypes.add(returnValue.getType());
              }
            }

            @Override
            public void visitClass(@NotNull PsiClass aClass) {}
            @Override
            public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}
          });
        }

        final PsiMethod[] constructors = existingClass.getConstructors();
        constr: for (PsiMethod constructor : constructors) {
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();
          if (parameters.length == 1) {
            final PsiParameter parameter = parameters[0];
            final PsiType parameterType = parameter.getType();
            for (PsiType returnType : returnTypes) {
              if (getInferredType(parameterType, returnType, existingClass, myMethod) == null && !TypeConversionUtil.isAssignable(parameterType, returnType)) {
                continue constr;
              }
            }
            if (!PsiUtil.isAccessible(constructor, myMethod, null)) {
              continue constr;
            }
            final PsiCodeBlock body = constructor.getBody();
            if (body == null) continue constr;
            final boolean[] found = new boolean[1];
            body.accept(new JavaRecursiveElementWalkingVisitor() {
              @Override
              public void visitAssignmentExpression(final @NotNull PsiAssignmentExpression expression) {
                super.visitAssignmentExpression(expression);
                final PsiExpression lExpression = expression.getLExpression();
                if (lExpression instanceof PsiReferenceExpression && myDelegateField.isEquivalentTo(((PsiReferenceExpression)lExpression).resolve())) {
                  found[0] = true;
                }
              }
            });
            if (found[0]) {
              foundConstructor = true;
              break;
            }
          }
        }
        if (!foundConstructor) {
          conflicts.putValue(existingClass,
                             JavaBundle.message("wrap.return.value.existing.class.does.not.have.appropriate.constructor.conflict"));
        }
      }
      if (myUnwrapMethodName.length() == 0) {
        conflicts.putValue(existingClass,
                           JavaBundle.message("wrap.return.value.existing.class.does.not.have.getter.conflict"));
      }
    }
    else {
      if (existingClass != null) {
        conflicts.putValue(existingClass, JavaRareRefactoringsBundle.message("there.already.exists.a.class.with.the.selected.name"));
      }
      if (myMoveDestination != null && !myMoveDestination.isTargetAccessible(myProject, myMethod.getContainingFile().getVirtualFile())) {
        conflicts.putValue(myMethod, JavaBundle.message("wrap.return.value.created.class.not.accessible.conflict"));
      }
    }
    return showConflicts(conflicts, refUsages.get());
  }

  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usageInfos) {
    if (!myUseExistingClass && !buildClass()) return;
    super.performRefactoring(usageInfos);
  }

  private boolean buildClass() {
    final PsiManager manager = myMethod.getManager();
    final Project project = myMethod.getProject();
    final ReturnValueBeanBuilder beanClassBuilder = new ReturnValueBeanBuilder();
    beanClassBuilder.setProject(project);
    beanClassBuilder.setFile(myMethod.getContainingFile());
    beanClassBuilder.setTypeArguments(myTypeParameters);
    beanClassBuilder.setClassName(myClassName);
    beanClassBuilder.setPackageName(myPackageName);
    beanClassBuilder.setStatic(myCreateInnerClass && myMethod.hasModifierProperty(PsiModifier.STATIC));
    final PsiType returnType = myMethod.getReturnType();
    beanClassBuilder.setValueType(returnType);

    final String classString;
    try {
      classString = beanClassBuilder.buildBeanClass();
    }
    catch (IOException e) {
      LOG.error(e);
      return false;
    }

    try {
      final PsiFileFactory factory = PsiFileFactory.getInstance(project);
      final PsiJavaFile psiFile = (PsiJavaFile)factory.createFileFromText(myClassName + ".java", JavaFileType.INSTANCE, classString);
      final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
      if (myCreateInnerClass) {
        final PsiClass containingClass = myMethod.getContainingClass();
        final PsiElement innerClass = containingClass.add(psiFile.getClasses()[0]);
        JavaCodeStyleManager.getInstance(project).shortenClassReferences(innerClass);
      }
      else {
        final PsiFile containingFile = myMethod.getContainingFile();

        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        final PsiDirectory directory;
        if (myMoveDestination != null) {
          directory = myMoveDestination.getTargetDirectory(containingDirectory);
        }
        else {
          final Module module = ModuleUtilCore.findModuleForPsiElement(containingFile);
          directory = PackageUtil.findOrCreateDirectoryForPackage(module, myPackageName, containingDirectory, true, true);
        }

        if (directory != null) {
          final PsiElement shortenedFile = JavaCodeStyleManager.getInstance(project).shortenClassReferences(psiFile);
          final PsiElement reformattedFile = codeStyleManager.reformat(shortenedFile);
          directory.add(reformattedFile);
        }
        else {
          return false;
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.info(e);
      return false;
    }
    return true;
  }

  @Override
  @NotNull
  protected String getCommandName() {
    final PsiClass containingClass = myMethod.getContainingClass();
    return JavaRareRefactoringsBundle.message("wrapped.return.command.name", myClassName, StringUtil.getQualifiedName(containingClass.getName(), myMethod.getName()));
  }


  private class ReturnSearchVisitor extends JavaRecursiveElementWalkingVisitor {
    private final List<? super FixableUsageInfo> usages;
    private final String type;

    ReturnSearchVisitor(List<? super FixableUsageInfo> usages, String type) {
      super();
      this.usages = usages;
      this.type = type;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {}
    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);

      final PsiExpression returnValue = statement.getReturnValue();
      if (myUseExistingClass && returnValue instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)returnValue;
        if (callExpression.getArgumentList().isEmpty()) {
          final PsiReferenceExpression callMethodExpression = callExpression.getMethodExpression();
          final String methodName = callMethodExpression.getReferenceName();
          if (Comparing.strEqual(myUnwrapMethodName, methodName)) {
            final PsiExpression qualifier = callMethodExpression.getQualifierExpression();
            if (qualifier != null) {
              final PsiType qualifierType = qualifier.getType();
              if (qualifierType != null && qualifierType.getCanonicalText().equals(myQualifiedName)) {
                usages.add(new ReturnWrappedValue(statement));
                return;
              }
            }
          }
        }
      }
      usages.add(new WrapReturnValue(statement, type));
    }
  }
}
