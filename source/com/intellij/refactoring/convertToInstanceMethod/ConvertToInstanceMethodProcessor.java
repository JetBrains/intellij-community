package com.intellij.refactoring.convertToInstanceMethod;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.localVcs.LvcsAction;
import com.intellij.openapi.localVcs.impl.LvcsIntegration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.move.moveMembers.MoveMembersProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;

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
    final PsiClass targetClass = ((PsiClassType) type).resolve();
    myTargetClass = targetClass;
    myOldVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList ());
    myNewVisibility = newVisibility;
  }

  public PsiClass getTargetClass() {
    return myTargetClass;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new ConvertToInstanceMethodViewDescriptor(usages, refreshCommand, myMethod, myTargetParameter, myTargetClass);
  }

  protected void refreshElements(PsiElement[] elements) {
    LOG.assertTrue(elements.length == 3);
    myMethod = (PsiMethod) elements[0];
    myTargetParameter = (PsiParameter) elements[1];
    myTargetClass = (PsiClass) elements[2];
  }

  protected UsageInfo[] findUsages() {
    LOG.assertTrue(myTargetParameter.getDeclarationScope() == myMethod);
    final PsiManager manager = myMethod.getManager();
    final Project project = manager.getProject();
    PsiSearchHelper searchHelper = manager.getSearchHelper();

    final PsiReference[] methodReferences = searchHelper.findReferences(myMethod, GlobalSearchScope.projectScope(project), false);
    List<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < methodReferences.length; i++) {
      final PsiReference ref = methodReferences[i];
      if (ref.getElement() instanceof PsiReferenceExpression) {
        if (ref.getElement().getParent() instanceof PsiMethodCallExpression) {
          result.add(new MethodCallUsageInfo((PsiMethodCallExpression) ref.getElement().getParent()));
        }
      }
      else if (ref.getElement() instanceof PsiDocTagValue) {
        result.add(new JavaDocUsageInfo(ref));
      }
    }

    PsiReference[] parameterReferences = searchHelper.findReferences(myTargetParameter, new LocalSearchScope(myMethod), false);
    for (int i = 0; i < parameterReferences.length; i++) {
      final PsiReference ref = parameterReferences[i];
      if (ref.getElement() instanceof PsiReferenceExpression) {
        result.add(new ParameterUsageInfo((PsiReferenceExpression) ref));
      }
    }

    if (myTargetClass.isInterface()) {
      PsiClass[] implementingClasses = RefactoringHierarchyUtil.findImplementingClasses(myTargetClass);
      for (int i = 0; i < implementingClasses.length; i++) {
        final PsiClass implementingClass = implementingClasses[i];
        result.add(new ImplementingClassUsageInfo(implementingClass));
      }
    }


    return result.toArray(new UsageInfo[result.size()]);
  }


  protected boolean preprocessUsages(UsageInfo[][] usages) {
    final UsageInfo[] usageList = usages[0];
    ArrayList<String> conflicts = new ArrayList<String>();
    final Set<PsiMember> methods = Collections.singleton(((PsiMember)myMethod));
    if (!myTargetClass.isInterface()) {
      final String original = VisibilityUtil.getVisibilityModifier(myMethod.getModifierList());
      conflicts.addAll(Arrays.asList(MoveMembersProcessor.analyzeAccessibilityConflicts(methods, myTargetClass, new LinkedHashSet(), original)));
    }
    else {
      for (int i = 0; i < usageList.length; i++) {
        final UsageInfo usage = usageList[i];
        if (usage instanceof ImplementingClassUsageInfo) {
          conflicts.addAll(Arrays.asList(MoveMembersProcessor.analyzeAccessibilityConflicts(
            methods, ((ImplementingClassUsageInfo)usage).getPsiClass(), new LinkedHashSet(), PsiModifier.PUBLIC)));
        }
      }
    }

    for (int i = 0; i < usageList.length; i++) {
      final UsageInfo usageInfo = usageList[i];
      if (usageInfo instanceof MethodCallUsageInfo) {
        final PsiMethodCallExpression methodCall = ((MethodCallUsageInfo)usageInfo).getMethodCall();
        final PsiExpression[] expressions = methodCall.getArgumentList().getExpressions();
        final int index = myMethod.getParameterList().getParameterIndex(myTargetParameter);
        if (index < expressions.length) {
          PsiExpression instanceValue = expressions[index];
          instanceValue = RefactoringUtil.unparenthesizeExpression(instanceValue);
          if (instanceValue instanceof PsiLiteralExpression && ((PsiLiteralExpression)instanceValue).getValue() == null) {
            String message =
              ConflictsUtil.getDescription(ConflictsUtil.getContainer(methodCall), true) + " contains call with null argument for parameter " +
              ConflictsUtil.htmlEmphasize(myTargetParameter.getName());
            conflicts.add(message);
          }
        }
      }
    }

    if (conflicts.size() != 0) {
      final ConflictsDialog conflictsDialog = new ConflictsDialog(conflicts.toArray(new String[conflicts.size()]), myProject);
      conflictsDialog.show();
      if (!conflictsDialog.isOK()) return false;
    }
    return super.preprocessUsages(usages);
  }

  protected void performRefactoring(UsageInfo[] usages) {
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
    // Process usages
    for (int i = 0; i < usages.length; i++) {
      final UsageInfo usage = usages[i];
      if (usage instanceof MethodCallUsageInfo) {
        processMethodCall((MethodCallUsageInfo) usage);
      }
      else if (usage instanceof ParameterUsageInfo) {
        processParameterUsage((ParameterUsageInfo) usage);
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
      for (Iterator<PsiClass> iterator = inheritors.iterator(); iterator.hasNext();) {
        final PsiClass psiClass = iterator.next();
        final PsiMethod newMethod = addMethodToClass(psiClass);
        newMethod.getModifierList().setModifierProperty((myNewVisibility != null ? myNewVisibility : PsiModifier.PUBLIC), true);
      }
    }
    myMethod.delete();
  }

  private void prepareTypeParameterReplacement() throws IncorrectOperationException {
    if (myTypeParameterReplacements == null) return;
    final Collection<PsiTypeParameter> typeParameters = myTypeParameterReplacements.keySet();
    for (Iterator<PsiTypeParameter> iterator = typeParameters.iterator(); iterator.hasNext();) {
      final PsiTypeParameter parameter = iterator.next();
      final PsiReference[] references = myMethod.getManager().getSearchHelper().findReferences(parameter, new LocalSearchScope(myMethod), false);
      for (int i = 0; i < references.length; i++) {
        final PsiReference reference = references[i];
        if (reference.getElement() instanceof PsiJavaCodeReferenceElement) {
          reference.getElement().putCopyableUserData(BIND_TO_TYPE_PARAMETER, myTypeParameterReplacements.get(parameter));
        }
      }
    }
    final Set<PsiTypeParameter> methodTypeParameters = myTypeParameterReplacements.keySet();
    for (Iterator<PsiTypeParameter> iterator = methodTypeParameters.iterator(); iterator.hasNext();) {
      final PsiTypeParameter methodTypeParameter = iterator.next();
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
      additionalReplacements = new com.intellij.util.containers.HashMap<PsiTypeParameter, PsiTypeParameter>();
      final Set entries = map.entrySet();
      for (Iterator<Map.Entry> iterator = entries.iterator(); iterator.hasNext();) {
        final Map.Entry entry = iterator.next();
        additionalReplacements.put((PsiTypeParameter)entry.getValue(), (PsiTypeParameter)entry.getKey());
      }
    }
    else {
      additionalReplacements = null;
    }
    newMethod.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        visitReferenceElement(expression);
      }

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
    return "Convert To Instance Method";
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
