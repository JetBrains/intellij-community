package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveInstanceMembersUtil;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.util.containers.HashSet;

import java.util.*;

/**
 * @author ven
 */
public class MoveInstanceMethodProcessor extends BaseRefactoringProcessor{
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodProcessor");

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiVariable getTargetVariable() {
    return myTargetVariable;
  }

  private PsiMethod myMethod;
  private PsiVariable myTargetVariable;
  private PsiClass myTargetClass;
  private final String myNewVisibility;
  private final Map<PsiClass, String> myOldClassParameterNames;

  public MoveInstanceMethodProcessor(final Project project,
                                     final PsiMethod method,
                                     final PsiVariable targetVariable,
                                     final String newVisibility,
                                     final Map<PsiClass, String> oldClassParameterNames) {
    super(project);
    myMethod = method;
    myTargetVariable = targetVariable;
    myOldClassParameterNames = oldClassParameterNames;
    LOG.assertTrue(myTargetVariable instanceof PsiParameter || myTargetVariable instanceof PsiField);
    LOG.assertTrue(myTargetVariable.getType() instanceof PsiClassType);
    final PsiType type = myTargetVariable.getType();
    LOG.assertTrue(type instanceof PsiClassType);
    myTargetClass = ((PsiClassType) type).resolve();
    myNewVisibility = newVisibility;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveInstanceMethodViewDescriptor(myMethod, myTargetVariable, myTargetClass);
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    final UsageInfo[] usages = refUsages.get();
    ArrayList<String> conflicts = new ArrayList<String>();
    final Set<PsiMember> members = new HashSet<PsiMember>();
    members.add(myMethod);
    if (myTargetVariable instanceof PsiField) members.add((PsiMember)myTargetVariable);
    if (!myTargetClass.isInterface()) {
      conflicts.addAll(Arrays.asList(MoveMembersProcessor.analyzeAccessibilityConflicts(members, myTargetClass, new LinkedHashSet<String>(), myNewVisibility)));
    }
    else {
      for (final UsageInfo usage : usages) {
        if (usage instanceof InheritorUsageInfo) {
          conflicts.addAll(Arrays.asList(MoveMembersProcessor.analyzeAccessibilityConflicts(
            members, ((InheritorUsageInfo)usage).getInheritor(), new LinkedHashSet<String>(), myNewVisibility)));
        }
      }
    }

    if (myTargetVariable instanceof PsiParameter) {
      PsiParameter parameter = (PsiParameter)myTargetVariable;
      for (final UsageInfo usageInfo : usages) {
        if (usageInfo instanceof MethodCallUsageInfo) {
          final PsiMethodCallExpression methodCall = ((MethodCallUsageInfo)usageInfo).getMethodCallExpression();
          final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
          final int index = myMethod.getParameterList().getParameterIndex(parameter);
          if (index < expressions.length) {
            PsiExpression instanceValue = expressions[index];
            instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
            if (instanceValue instanceof PsiLiteralExpression && ((PsiLiteralExpression)instanceValue).getValue() == null) {
              String message = RefactoringBundle.message("0.contains.call.with.null.argument.for.parameter.1",
                                                         ConflictsUtil.getDescription(ConflictsUtil.getContainer(methodCall), true),
                                                         CommonRefactoringUtil.htmlEmphasize(parameter.getName()));
              conflicts.add(message);
            }
          }
        }
      }
    }

    try {
      ConflictsUtil.checkMethodConflicts(myTargetClass, myMethod, getPatternMethod(), conflicts);
    }
    catch (IncorrectOperationException e) {}

    return showConflicts(conflicts);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    final PsiManager manager = myMethod.getManager();
    final GlobalSearchScope searchScope = GlobalSearchScope.allScope(manager.getProject());
    final PsiSearchHelper searchHelper = manager.getSearchHelper();
    final PsiReference[] refs = searchHelper.findReferences(myMethod, searchScope, false);
    final List<UsageInfo> usages = new ArrayList<UsageInfo>();
    for (PsiReference ref : refs) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        boolean isInternal = PsiTreeUtil.isAncestor(myMethod, element, true);
        usages.add(new MethodCallUsageInfo((PsiReferenceExpression)element, isInternal));
      }
      else if (element instanceof PsiDocTagValue) {
        usages.add(new JavadocUsageInfo(((PsiDocTagValue)element)));
      }
      else {
        LOG.assertTrue(false, "Unknown reference found");
      }
    }

    if (myTargetClass.isInterface()) {
      addInheritorUsages(myTargetClass, searchHelper, searchScope, usages);
    }

    final PsiCodeBlock body = myMethod.getBody();
    if (body != null) {
      body.accept(new PsiRecursiveElementVisitor() {
        public void visitNewExpression(PsiNewExpression expression) {
          if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
            usages.add(new InternalUsageInfo(expression));
          }
          super.visitNewExpression(expression);
        }

        public void visitReferenceExpression(PsiReferenceExpression expression) {
          if (MoveInstanceMembersUtil.getClassReferencedByThis(expression) != null) {
            usages.add(new InternalUsageInfo(expression));
          } else if (!expression.isQualified()) {
            final PsiElement resolved = expression.resolve();
            if (myTargetVariable.equals(resolved)) {
              usages.add(new InternalUsageInfo(expression));
            }
          }

          super.visitReferenceExpression(expression);
        }
      });
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private void addInheritorUsages(PsiClass aClass,
                                  final PsiSearchHelper searchHelper,
                                  final GlobalSearchScope searchScope,
                                  final List<UsageInfo> usages) {
    final PsiClass[] inheritors = searchHelper.findInheritors(aClass, searchScope, false);
    for (PsiClass inheritor : inheritors) {
      if (!inheritor.isInterface()) {
        usages.add(new InheritorUsageInfo(inheritor));
      }
      else {
        addInheritorUsages(inheritor, searchHelper, searchScope, usages);
      }
    }
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 3);
    myMethod = (PsiMethod) elements[0];
    myTargetVariable = (PsiVariable) elements[1];
    myTargetClass = (PsiClass) elements[2];
  }

  protected String getCommandName() {
    return RefactoringBundle.message("move.instance.method.command");
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myTargetClass)) return;

    PsiMethod patternMethod = createMethodToAdd();
    final List<PsiReference> docRefs = new ArrayList<PsiReference>();
    for (UsageInfo usage : usages) {
      if (usage instanceof InheritorUsageInfo) {
        final PsiClass inheritor = ((InheritorUsageInfo)usage).getInheritor();
        addMethodToClass(inheritor, patternMethod);
      }
      else if (usage instanceof MethodCallUsageInfo && !((MethodCallUsageInfo)usage).isInternal()) {
        correctMethodCall(((MethodCallUsageInfo)usage).getMethodCallExpression(), false);
      }
      else if (usage instanceof JavadocUsageInfo) {
        docRefs.add(usage.getElement().getReference());
      }
    }

    try {
      if (myTargetClass.isInterface()) patternMethod.getBody().delete();

      final PsiMethod method = addMethodToClass(myTargetClass, patternMethod);
      myMethod.delete();
      for (PsiReference reference : docRefs) {
        reference.bindToElement(method);
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void correctMethodCall(final PsiMethodCallExpression expression, final boolean isInternalCall) {
    try {
      final PsiManager manager = myMethod.getManager();
      PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (!methodExpression.isReferenceTo(myMethod)) return;
      final PsiExpression oldQualifier = methodExpression.getQualifierExpression();
      PsiExpression newQualifier = null;

      if (myTargetVariable instanceof PsiParameter) {
        final int index = myMethod.getParameterList().getParameterIndex((PsiParameter)myTargetVariable);
        final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
        if (index < arguments.length) {
          newQualifier = (PsiExpression)arguments[index].copy();
          arguments[index].delete();
        }
      }
      else {
        VisibilityUtil.escalateVisibility((PsiField)myTargetVariable, expression);
        newQualifier = manager.getElementFactory().createExpressionFromText(myTargetVariable.getName(), null);
      }

      PsiExpression newArgument = null;

      final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(methodExpression);
      if (classReferencedByThis != null) {
        @NonNls String thisArgumentText = null;
        if (manager.areElementsEquivalent(myMethod.getContainingClass(), classReferencedByThis)) {
          if (myOldClassParameterNames.containsKey(myMethod.getContainingClass())) {
            thisArgumentText = "this";
          }
        }
        else {
          thisArgumentText = classReferencedByThis.getName() + ".this";
        }

        if (thisArgumentText != null) {
          newArgument = manager.getElementFactory().createExpressionFromText(thisArgumentText, null);
        }
      } else {
        if (!isInternalCall && oldQualifier != null) {
          final PsiType type = oldQualifier.getType();
          if (type instanceof PsiClassType) {
            final PsiClass resolved = ((PsiClassType)type).resolve();
            if (resolved != null && getParameterNameToCreate(resolved) != null) {
              newArgument = replaceRefsToTargetVariable(oldQualifier);  //replace is needed in case old qualifier is e.g. the same as field as target variable
            }
          }
        }
      }


      if (newArgument != null) {
        expression.getArgumentList().add(newArgument);
      }

      if (newQualifier != null) {
        if (newQualifier instanceof PsiThisExpression && ((PsiThisExpression)newQualifier).getQualifier() == null) {
          //Remove now redundant 'this' qualifier
          if (oldQualifier != null) oldQualifier.delete();
        }
        else {
          final PsiReferenceExpression refExpr = (PsiReferenceExpression)manager.getElementFactory()
              .createExpressionFromText("q." + myMethod.getName(), null);
          refExpr.getQualifierExpression().replace(newQualifier);
          methodExpression.replace(refExpr);
        }
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private PsiExpression replaceRefsToTargetVariable(final PsiExpression expression) {
    final PsiManager manager = expression.getManager();
    if (expression instanceof PsiReferenceExpression &&
        ((PsiReferenceExpression)expression).isReferenceTo(myTargetVariable)) {
      return createThisExpr(manager);
    }

    expression.accept(new PsiRecursiveElementVisitor() {

      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.isReferenceTo(myTargetVariable)) {
          try {
            expression.replace(createThisExpr(manager));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
    });

    return expression;
  }

  private PsiExpression createThisExpr(final PsiManager manager)  {
    try {
      return manager.getElementFactory().createExpressionFromText("this", null);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return null;
    }
  }

  private PsiMethod addMethodToClass(final PsiClass aClass, final PsiMethod patternMethod) {
    try {
      final PsiMethod method = (PsiMethod)aClass.add(patternMethod);
      ChangeContextUtil.decodeContextInfo(method, null, null);
      return method;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return null;
  }

  private PsiMethod createMethodToAdd () {
    ChangeContextUtil.encodeContextInfo(myMethod, true);
    try {
      final PsiManager manager = myMethod.getManager();
      final PsiElementFactory factory = manager.getElementFactory();

      //correct internal references
      final PsiCodeBlock body = myMethod.getBody();
      if (body != null) {
        body.accept(new PsiRecursiveElementVisitor() {
          public void visitThisExpression(PsiThisExpression expression) {
            final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
            if (classReferencedByThis != null) {
              final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
              String paramName = getParameterNameToCreate(classReferencedByThis);
              try {
                final PsiExpression refExpression = factory.createExpressionFromText(paramName, null);
                expression.replace(refExpression);
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            }
          }

          public void visitReferenceExpression(PsiReferenceExpression expression) {
            try {
              final PsiExpression qualifier = expression.getQualifierExpression();
              if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).isReferenceTo(myTargetVariable)) {
                //Target is a field, replace target.m -> m
                qualifier.delete();
                return;
              } else {
                final PsiElement resolved = expression.resolve();
                if (myTargetVariable.equals(resolved)) {
                  PsiThisExpression thisExpression = (PsiThisExpression)factory.createExpressionFromText("this", null);
                  expression.replace(thisExpression);
                  return;
                } else if (myMethod.equals(resolved)) {
                } else {
                  PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
                  if (classReferencedByThis != null) {
                    final String paramName = getParameterNameToCreate(classReferencedByThis);
                    PsiReferenceExpression newQualifier = (PsiReferenceExpression)factory.createExpressionFromText(paramName, null);
                    expression.setQualifierExpression(newQualifier);
                    return;
                  }
                }
              }
              super.visitReferenceExpression(expression);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }

          public void visitNewExpression(PsiNewExpression expression) {
            try {
              final PsiExpression qualifier = expression.getQualifier();
              if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).isReferenceTo(myTargetVariable)) {
                //Target is a field, replace target.new A() -> new A()
                qualifier.delete();
              } else {
                final PsiClass classReferencedByThis = MoveInstanceMembersUtil.getClassReferencedByThis(expression);
                if (classReferencedByThis != null) {
                  if (qualifier != null) qualifier.delete();
                  final String paramName = getParameterNameToCreate(classReferencedByThis);
                  final PsiExpression newExpression = factory.createExpressionFromText(paramName + "." + expression.getText(), null);
                  expression = (PsiNewExpression)expression.replace(newExpression);
                }
              }
              super.visitNewExpression(expression);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }

          public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            correctMethodCall(expression, true);
          }
        });
      }

      final PsiMethod methodCopy = getPatternMethod();

      final List<PsiParameter> newParameters = Arrays.asList(methodCopy.getParameterList().getParameters());
      RefactoringUtil.fixJavadocsForParams(methodCopy, new HashSet<PsiParameter>(newParameters));
      return methodCopy;
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
      return myMethod;
    }
  }

  private PsiMethod getPatternMethod() throws IncorrectOperationException {
    final PsiMethod methodCopy = (PsiMethod)myMethod.copy();
    methodCopy.getModifierList().setModifierProperty(myTargetClass.isInterface() ? PsiModifier.PUBLIC : myNewVisibility, true);
    if (myTargetVariable instanceof PsiParameter) {
      final int index = myMethod.getParameterList().getParameterIndex((PsiParameter)myTargetVariable);
      methodCopy.getParameterList().getParameters()[index].delete();
    }

    addParameters(PsiManager.getInstance(myProject).getElementFactory(), methodCopy);
    return methodCopy;
  }

  private void addParameters(final PsiElementFactory factory, final PsiMethod methodCopy) throws IncorrectOperationException {
    final Set<Map.Entry<PsiClass, String>> entries = myOldClassParameterNames.entrySet();
    for (final Map.Entry<PsiClass, String> entry : entries) {
      final PsiClassType type = factory.createType(entry.getKey());
      final PsiParameter parameter = factory.createParameter(entry.getValue(), type);
      methodCopy.getParameterList().add(parameter);
    }
  }

  private String getParameterNameToCreate(PsiClass aClass) {
    LOG.assertTrue(aClass != null);
    final String paramName = myOldClassParameterNames.get(aClass);
    return paramName;
  }
}
