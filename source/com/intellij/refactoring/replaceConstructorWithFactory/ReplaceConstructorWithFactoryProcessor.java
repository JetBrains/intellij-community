package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor");
  private final PsiMethod myConstructor;
  private final String myFactoryName;
  private final boolean myPreviewUsages;
  private final PsiElementFactory myFactory;
  private final PsiClass myOriginalClass;
  private final PsiClass myTargetClass;
  private PsiManager myManager;
  private boolean myIsInner;

  public ReplaceConstructorWithFactoryProcessor(Project project, PsiMethod constructor,
                                                PsiClass targetClass, String factoryName, boolean previewUsages,
                                                Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myOriginalClass = null;
    myConstructor = constructor;
    myFactoryName = factoryName;
    myPreviewUsages = previewUsages;
    myTargetClass = targetClass;
    LOG.assertTrue(myConstructor.isConstructor());
    myManager = PsiManager.getInstance(project);
    myFactory = PsiManager.getInstance(project).getElementFactory();

    myIsInner = isInner(myConstructor.getContainingClass());
  }

  public ReplaceConstructorWithFactoryProcessor(Project project, PsiClass aClass, PsiClass targetClass,
                                                String factoryName, boolean previewUsages,
                                                Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myOriginalClass = aClass;
    myConstructor = null;
    myTargetClass = targetClass;
    myFactoryName = factoryName;
    myPreviewUsages = previewUsages;
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

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    if (myConstructor != null) {
      return new ReplaceConstructorWithFactoryViewDescriptor(usages, refreshCommand, myConstructor);
    }
    else {
      return new ReplaceConstructorWithFactoryViewDescriptor(usages, refreshCommand, myOriginalClass);
    }
  }

  private List<PsiElement> myNonNewConstructorUsages;

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

    for (int i = 0; i < references.length; i++) {
      PsiElement element = references[i].getElement();

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
        public void visitConstructor(PsiMethod constructor) {
          myNonNewConstructorUsages.add(constructor);
        }

        public void visitClassWithoutConstructors(PsiClass aClass) {
          myNonNewConstructorUsages.add(aClass);
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  protected boolean preprocessUsages(UsageInfo[][] u) {
    UsageInfo[] usages = u[0];

    ArrayList<String> conflicts = new ArrayList<String>();
    if (!myManager.getResolveHelper().isAccessible(getConstructorContainingClass(), myTargetClass, null)) {
      String message = "Class " + ConflictsUtil.getDescription(getConstructorContainingClass(), true)
                       + " is not accessible from target " + ConflictsUtil.getDescription(myTargetClass, true) + ".";
      conflicts.add(message);
    }

    HashSet<PsiElement> reportedContainers = new HashSet<PsiElement>();
    final String targetClassDescription = ConflictsUtil.getDescription(myTargetClass, true);
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      final PsiElement container = ConflictsUtil.getContainer(usage.getElement());
      if (!reportedContainers.contains(container)) {
        reportedContainers.add(container);
        if (!myManager.getResolveHelper().isAccessible(myTargetClass, usage.getElement(), null)) {
          String message = "Target " + targetClassDescription + " is not accessible from "
                           + ConflictsUtil.getDescription(container, true) + ".";
          conflicts.add(message);
        }
      }
    }

    if (myIsInner) {
      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
        final PsiField field = PsiTreeUtil.getParentOfType(usage.getElement(), PsiField.class);
        if (field != null) {
          final PsiClass containingClass = field.getContainingClass();

          if (PsiTreeUtil.isAncestor(containingClass, myTargetClass, true)) {
            String message = "Constructor being refactored is used in initializer of " +
                             ConflictsUtil.getDescription(field, true)
                             + ". Non-static factory of inner class" +
                             ConflictsUtil.getDescription(getConstructorContainingClass(), false)
                             + " cannot be used in this context. Resulting code will not compile.";
            conflicts.add(message);
          }
        }
      }
    }


    if (myPrepareSuccessfulSwingThreadCallback != null && conflicts.size() > 0) {
      ConflictsDialog dialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]),
                                                   myProject);
      dialog.show();
      if (!dialog.isOK()) return false;
    }

    prepareSuccessful();
    return true;
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

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return super.isPreviewUsages(usages) || myPreviewUsages;
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
        for (Iterator<PsiElement> iterator = myNonNewConstructorUsages.iterator(); iterator.hasNext();) {
          PsiElement place = iterator.next();
          VisibilityUtil.escalateVisibility(myConstructor, place);
        }
      }

      if (myConstructor == null) {
        PsiMethod constructor = myFactory.createConstructor();
        constructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
        constructor = (PsiMethod)getConstructorContainingClass().add(constructor);
        VisibilityUtil.escalateVisibility(constructor, myTargetClass);
      }

      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
        PsiNewExpression newExpression = (PsiNewExpression)usage.getElement();

        VisibilityUtil.escalateVisibility(factoryMethod, newExpression);
        PsiMethodCallExpression factoryCall =
          (PsiMethodCallExpression)myFactory.createExpressionFromText(myFactoryName + "()", newExpression);
        factoryCall.getArgumentList().replace(newExpression.getArgumentList());

        boolean replaceMethodQualifier = false;
        PsiExpression newQualifier = newExpression.getQualifier();

        if (newQualifier != null) {
          newQualifier = (PsiExpression)newQualifier.copy();
        }

        PsiElement resolvedFactoryMethod = factoryCall.getMethodExpression().resolve();
        if (resolvedFactoryMethod != factoryMethod || newQualifier != null) {
          factoryCall.getMethodExpression().replace(qualifiedMethodReference);
          replaceMethodQualifier = true;
        }
        factoryCall = (PsiMethodCallExpression)newExpression.replace(factoryCall);

        if (replaceMethodQualifier) {
          if (newQualifier == null) {
            factoryCall.getMethodExpression().getQualifierExpression().replace(classReferenceExpression);
          }
          else {
            factoryCall.getMethodExpression().getQualifierExpression().replace(newQualifier);
          }
        }
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
      List<String> names = new ArrayList<String>();
      while(iterator.hasNext()) {
        PsiTypeParameter typeParameter = iterator.next();
        if (!names.contains(typeParameter.getName())) { //Otherwise type parameter is hidden in the constructor
          names.add(typeParameter.getName());
          factoryMethod.getTypeParameterList().add(typeParameter);
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

    for (int i = 0; i < params.length; i++) {
      PsiExpression paramRef = myFactory.createExpressionFromText(params[i].getName(), null);
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
      return "Replace constructor " + UsageViewUtil.getDescriptiveName(myConstructor) + " with a factory method";
    }
    else {
      return "Replace default constructor of " + UsageViewUtil.getDescriptiveName(myOriginalClass) +
             " with a factory method";
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
