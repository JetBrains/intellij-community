package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor");
  private final PsiMethod myConstructor;
  private final String myFactoryName;
  private final PsiElementFactory myFactory;
  private final PsiClass myOriginalClass;
  private final PsiClass myTargetClass;
  private PsiManager myManager;
  private boolean myIsInner;

  public ReplaceConstructorWithFactoryProcessor(Project project,
                                                PsiMethod originalConstructor,
                                                PsiClass originalClass,
                                                PsiClass targetClass,
                                                String factoryName) {
    super(project);
    myOriginalClass = originalClass;
    myConstructor = originalConstructor;
    myTargetClass = targetClass;
    myFactoryName = factoryName;
    myManager = PsiManager.getInstance(project);
    myFactory = myManager.getElementFactory();

    myIsInner = isInner(myOriginalClass);
  }

  private boolean isInner(PsiClass originalClass) {
    final boolean result = PsiUtil.isInnerClass(originalClass);
    if (result) {
      LOG.assertTrue(PsiTreeUtil.isAncestor(myTargetClass, originalClass, false));
    }
    return result;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    if (myConstructor != null) {
      return new ReplaceConstructorWithFactoryViewDescriptor(myConstructor);
    }
    else {
      return new ReplaceConstructorWithFactoryViewDescriptor(myOriginalClass);
    }
  }

  private List<PsiElement> myNonNewConstructorUsages;

  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiSearchHelper searchHelper = myManager.getSearchHelper();
    final PsiReference[] references;
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    if (myConstructor != null) {
      references = searchHelper.findReferences(myConstructor, projectScope, false);
    }
    else {
      references = searchHelper.findReferences(myOriginalClass, projectScope, false);
    }

    ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
    myNonNewConstructorUsages = new ArrayList<PsiElement>();

    for (PsiReference reference : references) {
      PsiElement element = reference.getElement();

      if (element.getParent() instanceof PsiNewExpression) {
        usages.add(new UsageInfo(element.getParent()));
      }
      else {
        if ("super".equals(element.getText()) || "this".equals(element.getText())) {
          myNonNewConstructorUsages.add(element);
        }
      }
    }

    if (myConstructor != null && myConstructor.getParameterList().getParameters().length == 0) {
      RefactoringUtil.visitImplicitConstructorUsages(getConstructorContainingClass(), new RefactoringUtil.ImplicitConstructorUsageVisitor() {
        public void visitConstructor(PsiMethod constructor, PsiMethod baseConstructor) {
          myNonNewConstructorUsages.add(constructor);
        }

        public void visitClassWithoutConstructors(PsiClass aClass) {
          myNonNewConstructorUsages.add(aClass);
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usages = refUsages.get();

    ArrayList<String> conflicts = new ArrayList<String>();
    if (!myManager.getResolveHelper().isAccessible(getConstructorContainingClass(), myTargetClass, null)) {
      String message = RefactoringBundle.message("class.0.is.not.accessible.from.target.1",
                                                 ConflictsUtil.getDescription(getConstructorContainingClass(), true),
                                                 ConflictsUtil.getDescription(myTargetClass, true));
      conflicts.add(message);
    }

    HashSet<PsiElement> reportedContainers = new HashSet<PsiElement>();
    final String targetClassDescription = ConflictsUtil.getDescription(myTargetClass, true);
    for (UsageInfo usage : usages) {
      final PsiElement container = ConflictsUtil.getContainer(usage.getElement());
      if (!reportedContainers.contains(container)) {
        reportedContainers.add(container);
        if (!myManager.getResolveHelper().isAccessible(myTargetClass, usage.getElement(), null)) {
          String message = RefactoringBundle.message("target.0.is.not.accessible.from.1",
                                                     targetClassDescription,
                                                     ConflictsUtil.getDescription(container, true));
          conflicts.add(message);
        }
      }
    }

    if (myIsInner) {
      for (UsageInfo usage : usages) {
        final PsiField field = PsiTreeUtil.getParentOfType(usage.getElement(), PsiField.class);
        if (field != null) {
          final PsiClass containingClass = field.getContainingClass();

          if (PsiTreeUtil.isAncestor(containingClass, myTargetClass, true)) {
            String message = RefactoringBundle.message("constructor.being.refactored.is.used.in.initializer.of.0",
                                                       ConflictsUtil.getDescription(field, true), ConflictsUtil.getDescription(getConstructorContainingClass(), false));
            conflicts.add(message);
          }
        }
      }
    }


    return showConflicts(conflicts);
  }

  private PsiClass getConstructorContainingClass() {
    if (myConstructor != null) {
      return myConstructor.getContainingClass();
    }
    else {
      return myOriginalClass;
    }
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected void performRefactoring(UsageInfo[] usages) {

    try {
      PsiReferenceExpression classReferenceExpression =
        myFactory.createReferenceExpression(myTargetClass);
      PsiReferenceExpression qualifiedMethodReference =
        (PsiReferenceExpression)myFactory.createExpressionFromText("A." + myFactoryName, null);
      PsiMethod factoryMethod = (PsiMethod)myTargetClass.add(createFactoryMethod());
      if (myConstructor != null) {
        myConstructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
        VisibilityUtil.escalateVisibility(myConstructor, factoryMethod);
        for (PsiElement place : myNonNewConstructorUsages) {
          VisibilityUtil.escalateVisibility(myConstructor, place);
        }
      }

      if (myConstructor == null) {
        PsiMethod constructor = myFactory.createConstructor();
        constructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
        constructor = (PsiMethod)getConstructorContainingClass().add(constructor);
        VisibilityUtil.escalateVisibility(constructor, myTargetClass);
      }

      for (UsageInfo usage : usages) {
        PsiNewExpression newExpression = (PsiNewExpression)usage.getElement();
        if (newExpression == null) continue;

        VisibilityUtil.escalateVisibility(factoryMethod, newExpression);
        PsiMethodCallExpression factoryCall =
          (PsiMethodCallExpression)myFactory.createExpressionFromText(myFactoryName + "()", newExpression);
        factoryCall.getArgumentList().replace(newExpression.getArgumentList());

        boolean replaceMethodQualifier = false;
        PsiExpression newQualifier = newExpression.getQualifier();

        PsiElement resolvedFactoryMethod = factoryCall.getMethodExpression().resolve();
        if (resolvedFactoryMethod != factoryMethod || newQualifier != null) {
          factoryCall.getMethodExpression().replace(qualifiedMethodReference);
          replaceMethodQualifier = true;
        }

        if (replaceMethodQualifier) {
          if (newQualifier == null) {
            factoryCall.getMethodExpression().getQualifierExpression().replace(classReferenceExpression);
          }
          else {
            factoryCall.getMethodExpression().getQualifierExpression().replace(newQualifier);
          }
        }

        newExpression.replace(factoryCall);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiMethod createFactoryMethod() throws IncorrectOperationException {
    final PsiClass containingClass = getConstructorContainingClass();
    PsiClassType type = myFactory.createType(containingClass, PsiSubstitutor.EMPTY);
    final PsiMethod factoryMethod = myFactory.createMethod(myFactoryName, type);
    if (myConstructor != null) {
      factoryMethod.getParameterList().replace(myConstructor.getParameterList());
      factoryMethod.getThrowsList().replace(myConstructor.getThrowsList());

      Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(myConstructor);
      Collection<String> names = new HashSet<String>();
      while(iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        if (!names.contains(typeParameter.getName())) { //Otherwise type parameter is hidden in the constructor
          names.add(typeParameter.getName());
          factoryMethod.getTypeParameterList().addAfter(typeParameter, null);
        }
      }
    }
    PsiReturnStatement returnStatement =
      (PsiReturnStatement)myFactory.createStatementFromText("return new A();", null);
    PsiNewExpression newExpression = (PsiNewExpression)returnStatement.getReturnValue();
    PsiJavaCodeReferenceElement classRef = myFactory.createReferenceElementByType(type);
    newExpression.getClassReference().replace(classRef);
    final PsiExpressionList argumentList = newExpression.getArgumentList();

    PsiParameter[] params = factoryMethod.getParameterList().getParameters();

    for (PsiParameter parameter : params) {
      PsiExpression paramRef = myFactory.createExpressionFromText(parameter.getName(), null);
      argumentList.add(paramRef);
    }
    factoryMethod.getBody().add(returnStatement);

    factoryMethod.getModifierList().setModifierProperty(
      getDefaultFactoryVisibility(), true);

    if (!myIsInner) {
      factoryMethod.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
    }

    return (PsiMethod)CodeStyleManager.getInstance(myProject).reformat(factoryMethod);
  }

  private String getDefaultFactoryVisibility() {
    final PsiModifierList modifierList;
    if (myConstructor != null) {
      modifierList = myConstructor.getModifierList();
    }
    else {
      modifierList = myOriginalClass.getModifierList();
    }
    return VisibilityUtil.getVisibilityModifier(modifierList);
  }


  protected String getCommandName() {
    if (myConstructor != null) {
      return RefactoringBundle.message("replace.constructor.0.with.a.factory.method",
                                       UsageViewUtil.getDescriptiveName(myConstructor));
    }
    else {
      return RefactoringBundle.message("replace.default.constructor.of.0.with.a.factory.method",
                                       UsageViewUtil.getDescriptiveName(myOriginalClass));
    }
  }

  public PsiClass getOriginalClass() {
    return getConstructorContainingClass();
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  public PsiMethod getConstructor() {
    return myConstructor;
  }

  public String getFactoryName() {
    return myFactoryName;
  }
}
