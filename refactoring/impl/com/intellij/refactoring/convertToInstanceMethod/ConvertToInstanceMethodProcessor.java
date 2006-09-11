package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.ResolveUtil;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.moveInstanceMethod.MoveInstanceMethodViewDescriptor;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.refactoring.util.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodProcessor");
  private PsiMethod myMethod;
  private PsiParameter myTargetParameter;
  private PsiClass myTargetClass;
  private Map<PsiTypeParameter, PsiTypeParameter> myTypeParameterReplacements;
  private static final Key<PsiTypeParameter> BIND_TO_TYPE_PARAMETER = Key.create("REPLACEMENT");
  private final String myOldVisibility;
  private final String myNewVisibility;


  public ConvertToInstanceMethodProcessor(final Project project,
                                          final PsiMethod method,
                                          final PsiParameter targetParameter,
                                          final String newVisibility) {
    super(project);
    myMethod = method;
    myTargetParameter = targetParameter;
    LOG.assertTrue(method.hasModifierProperty(PsiModifier.STATIC));
    LOG.assertTrue(myTargetParameter.getDeclarationScope() == myMethod);
    LOG.assertTrue(myTargetParameter.getType() instanceof PsiClassType);
    final PsiType type = myTargetParameter.getType();
    LOG.assertTrue(type instanceof PsiClassType);
    myTargetClass = ((PsiClassType) type).resolve();
    myOldVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList ());
    myNewVisibility = newVisibility;
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new MoveInstanceMethodViewDescriptor(myMethod, myTargetParameter, myTargetClass);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 3);
    myMethod = (PsiMethod) elements[0];
    myTargetParameter = (PsiParameter) elements[1];
    myTargetClass = (PsiClass) elements[2];
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    LOG.assertTrue(myTargetParameter.getDeclarationScope() == myMethod);
    final PsiManager manager = myMethod.getManager();
    final Project project = manager.getProject();
    PsiSearchHelper searchHelper = manager.getSearchHelper();

    final PsiReference[] methodReferences = searchHelper.findReferences(myMethod, GlobalSearchScope.projectScope(project), false);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (final PsiReference ref : methodReferences) {
      final PsiElement element = ref.getElement();
      if (element instanceof PsiReferenceExpression) {
        if (element.getParent() instanceof PsiMethodCallExpression) {
          result.add(new MethodCallUsageInfo((PsiMethodCallExpression)element.getParent()));
        }
      }
      else if (element instanceof PsiDocTagValue) {
        result.add(new JavaDocUsageInfo(ref)); //TODO:!!!
      }
    }

    PsiReference[] parameterReferences = searchHelper.findReferences(myTargetParameter, new LocalSearchScope(myMethod), false);
    for (final PsiReference ref : parameterReferences) {
      if (ref.getElement() instanceof PsiReferenceExpression) {
        result.add(new ParameterUsageInfo((PsiReferenceExpression)ref));
      }
    }

    if (myTargetClass.isInterface()) {
      PsiClass[] implementingClasses = RefactoringHierarchyUtil.findImplementingClasses(myTargetClass);
      for (final PsiClass implementingClass : implementingClasses) {
        result.add(new ImplementingClassUsageInfo(implementingClass));
      }
    }


    return result.toArray(new UsageInfo[result.size()]);
  }


  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    ArrayList<String> conflicts = new ArrayList<String>();
    final Set<PsiMember> methods = Collections.singleton(((PsiMember)myMethod));
    if (!myTargetClass.isInterface()) {
      final String original = VisibilityUtil.getVisibilityModifier(myMethod.getModifierList());
      conflicts.addAll(Arrays.asList(MoveMembersProcessor.analyzeAccessibilityConflicts(methods, myTargetClass, new LinkedHashSet<String>(), original)));
    }
    else {
      for (final UsageInfo usage : usagesIn) {
        if (usage instanceof ImplementingClassUsageInfo) {
          conflicts.addAll(Arrays.asList(MoveMembersProcessor.analyzeAccessibilityConflicts(
            methods, ((ImplementingClassUsageInfo)usage).getPsiClass(), new LinkedHashSet<String>(), PsiModifier.PUBLIC)));
        }
      }
    }

    for (final UsageInfo usageInfo : usagesIn) {
      if (usageInfo instanceof MethodCallUsageInfo) {
        final PsiMethodCallExpression methodCall = ((MethodCallUsageInfo)usageInfo).getMethodCall();
        final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
        final int index = myMethod.getParameterList().getParameterIndex(myTargetParameter);
        if (index < expressions.length) {
          PsiExpression instanceValue = expressions[index];
          instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
          if (instanceValue instanceof PsiLiteralExpression && ((PsiLiteralExpression)instanceValue).getValue() == null) {
            String message =
              RefactoringBundle.message("0.contains.call.with.null.argument.for.parameter.1",
                                        ConflictsUtil.getDescription(ConflictsUtil.getContainer(methodCall), true),
                                        CommonRefactoringUtil.htmlEmphasize(myTargetParameter.getName()));
            conflicts.add(message);
          }
        }
      }
    }

    try {
      addInaccessibilityConflicts(usagesIn, conflicts);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    return showConflicts(conflicts);
  }

  private void addInaccessibilityConflicts(final UsageInfo[] usages, final ArrayList<String> conflicts) throws IncorrectOperationException {
    final PsiModifierList copy = (PsiModifierList)myMethod.getModifierList().copy();
    if (myNewVisibility != null) {
      RefactoringUtil.setVisibility(copy, myNewVisibility);
    }

    for (UsageInfo usage : usages) {
      if (usage instanceof MethodCallUsageInfo) {
        final PsiMethodCallExpression call = ((MethodCallUsageInfo)usage).getMethodCall();
        PsiClass accessObjectClass = null;
        final PsiExpression[] arguments = call.getArgumentList().getExpressions();
        final int index = myMethod.getParameterList().getParameterIndex(myTargetParameter);
        LOG.assertTrue(index >= 0);
        if (index < arguments.length) {
          final PsiExpression argument = arguments[index];
          final PsiType argumentType = argument.getType();
          if (argumentType instanceof PsiClassType) accessObjectClass = ((PsiClassType)argumentType).resolve();
        }
        if (!ResolveUtil.isAccessible(myMethod, myTargetClass, copy, call, accessObjectClass, null)) {
          final String newVisibility = myNewVisibility == null ? VisibilityUtil.getVisibilityStringToDisplay(myMethod) : myNewVisibility;
          String message =
            RefactoringBundle.message("0.with.1.visibility.is.not.accesible.from.2",
                                      ConflictsUtil.getDescription(myMethod, true),
                                      newVisibility,
                                      ConflictsUtil.getDescription(ConflictsUtil.getContainer(call), true));
          conflicts.add(message);
        }
      }
    }
  }

  protected void performRefactoring(UsageInfo[] usages) {
    if (!CommonRefactoringUtil.checkReadOnlyStatus(myProject, myTargetClass)) return;
    final LvcsAction lvcsAction = LvcsIntegration.checkinFilesBeforeRefactoring(myProject, getCommandName());
    try {
      doRefactoring(usages);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
    finally{
      LvcsIntegration.checkinFilesAfterRefactoring(myProject, lvcsAction);
    }
  }

  private void doRefactoring(UsageInfo[] usages) throws IncorrectOperationException {
    myTypeParameterReplacements = buildTypeParameterReplacements();
    List<PsiClass> inheritors = new ArrayList<PsiClass>();

    RefactoringUtil.sortDepthFirstRightLeftOrder(usages);

    // Process usages
    for (final UsageInfo usage : usages) {
      if (usage instanceof MethodCallUsageInfo) {
        processMethodCall((MethodCallUsageInfo)usage);
      }
      else if (usage instanceof ParameterUsageInfo) {
        processParameterUsage((ParameterUsageInfo)usage);
      }
      else if (usage instanceof ImplementingClassUsageInfo) {
        inheritors.add(((ImplementingClassUsageInfo)usage).getPsiClass());
      }
    }

    prepareTypeParameterReplacement();
    myTargetParameter.delete();
    ChangeContextUtil.encodeContextInfo(myMethod, true);
    if (!myTargetClass.isInterface()) {
      addMethodToClass(myTargetClass);
    }
    else {
      final PsiMethod interfaceMethod = addMethodToClass(myTargetClass);
      RefactoringUtil.abstractizeMethod(myTargetClass, interfaceMethod);
      for (final PsiClass psiClass : inheritors) {
        final PsiMethod newMethod = addMethodToClass(psiClass);
        newMethod.getModifierList().setModifierProperty((myNewVisibility != null ? myNewVisibility : PsiModifier.PUBLIC), true);
      }
    }
    myMethod.delete();
  }

  private void prepareTypeParameterReplacement() throws IncorrectOperationException {
    if (myTypeParameterReplacements == null) return;
    final Collection<PsiTypeParameter> typeParameters = myTypeParameterReplacements.keySet();
    for (final PsiTypeParameter parameter : typeParameters) {
      final PsiReference[] references = myMethod.getManager().getSearchHelper()
        .findReferences(parameter, new LocalSearchScope(myMethod), false);
      for (final PsiReference reference : references) {
        if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
          reference.getElement().putCopyableUserData(BIND_TO_TYPE_PARAMETER, myTypeParameterReplacements.get(parameter));
        }
      }
    }
    final Set<PsiTypeParameter> methodTypeParameters = myTypeParameterReplacements.keySet();
    for (final PsiTypeParameter methodTypeParameter : methodTypeParameters) {
      methodTypeParameter.delete();
    }
  }

  private PsiMethod addMethodToClass(final PsiClass targetClass) throws IncorrectOperationException {
    final PsiMethod newMethod = (PsiMethod)targetClass.add(myMethod);
    final PsiModifierList modifierList = newMethod.getModifierList();
    modifierList.setModifierProperty(PsiModifier.STATIC, false);
    if (myNewVisibility != null && myNewVisibility != myOldVisibility) {
      modifierList.setModifierProperty(myNewVisibility, true);
    }
    ChangeContextUtil.decodeContextInfo(newMethod, null, null);
    if (myTypeParameterReplacements == null) return newMethod;
    final Map<PsiTypeParameter, PsiTypeParameter> additionalReplacements;
    if (targetClass != myTargetClass) {
      final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(myTargetClass, targetClass, PsiSubstitutor.EMPTY);
      final Map<PsiTypeParameter, PsiTypeParameter> map = calculateReplacementMap(superClassSubstitutor, myTargetClass, targetClass);
      if (map == null) return newMethod;
      additionalReplacements = new HashMap<PsiTypeParameter, PsiTypeParameter>();
      for (final Map.Entry<PsiTypeParameter, PsiTypeParameter> entry : map.entrySet()) {
        additionalReplacements.put((PsiTypeParameter)entry.getValue(), (PsiTypeParameter)entry.getKey());
      }
    }
    else {
      additionalReplacements = null;
    }
    newMethod.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        PsiTypeParameter typeParameterToBind = reference.getCopyableUserData(BIND_TO_TYPE_PARAMETER);
        if (typeParameterToBind != null) {
          reference.putCopyableUserData(BIND_TO_TYPE_PARAMETER, null);
          try {
            if (additionalReplacements != null) {
              typeParameterToBind = additionalReplacements.get(typeParameterToBind);
            }
            reference.bindToElement(typeParameterToBind);
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          visitElement(reference);
        }
      }
    });
    return newMethod;
  }

  private void processParameterUsage(ParameterUsageInfo usage) throws IncorrectOperationException {
    final PsiJavaCodeReferenceElement referenceExpression = usage.getReferenceExpression();
    if (referenceExpression.getParent() instanceof PsiReferenceExpression) {
      // todo: check for correctness
      referenceExpression.delete();
    }
    else {
      final PsiExpression expression = myMethod.getManager().getElementFactory().createExpressionFromText("this" , null);
      referenceExpression.replace(expression);
    }
  }

  private void processMethodCall(MethodCallUsageInfo usageInfo) throws IncorrectOperationException {
    PsiMethodCallExpression methodCall = usageInfo.getMethodCall();
    PsiParameterList parameterList = myMethod.getParameterList();
    PsiElementFactory factory = myMethod.getManager().getElementFactory();
    int parameterIndex = parameterList.getParameterIndex(myTargetParameter);
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    if (arguments.length <= parameterIndex) return;
    final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
    final PsiExpression qualifier;
    if (methodExpression.getQualifierExpression() != null) {
      qualifier = methodExpression.getQualifierExpression();
    }
    else {
      final PsiReferenceExpression newRefExpr = (PsiReferenceExpression)factory.createExpressionFromText("x." + myMethod.getName(), null);
      qualifier = ((PsiReferenceExpression)methodExpression.replace(newRefExpr)).getQualifierExpression();
    }
    qualifier.replace(arguments[parameterIndex]);
    arguments[parameterIndex].delete();
  }

  protected String getCommandName() {
    return ConvertToInstanceMethodHandler.REFACTORING_NAME;
  }

  public Map<PsiTypeParameter, PsiTypeParameter> buildTypeParameterReplacements() {
    final PsiClassType type = (PsiClassType)myTargetParameter.getType();
    final PsiSubstitutor substitutor = type.resolveGenerics().getSubstitutor();
    return calculateReplacementMap(substitutor, myTargetClass, myMethod);
  }

  private Map<PsiTypeParameter, PsiTypeParameter> calculateReplacementMap(final PsiSubstitutor substitutor,
                                                                          final PsiClass targetClass,
                                                                          final PsiElement containingElement) {
    final HashMap<PsiTypeParameter, PsiTypeParameter> result = new HashMap<PsiTypeParameter, PsiTypeParameter>();
    final Iterator<PsiTypeParameter> iterator = PsiUtil.typeParametersIterator(targetClass);
    while (iterator.hasNext()) {
      final PsiTypeParameter classTypeParameter = iterator.next();
      final PsiType substitution = substitutor.substitute(classTypeParameter);
      if (!(substitution instanceof PsiClassType)) return null;
      final PsiClass aClass = ((PsiClassType)substitution).resolve();
      if (!(aClass instanceof PsiTypeParameter)) return null;
      final PsiTypeParameter methodTypeParameter = ((PsiTypeParameter)aClass);
      if (methodTypeParameter.getOwner() != containingElement) return null;
      if (result.keySet().contains(methodTypeParameter)) return null;
      result.put(methodTypeParameter, classTypeParameter);
    }
    return result;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiParameter getTargetParameter() {
    return myTargetParameter;
  }
}
