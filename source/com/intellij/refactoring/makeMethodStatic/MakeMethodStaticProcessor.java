/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 16.04.2002
 * Time: 15:37:30
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.makeMethodStatic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

public class MakeMethodStaticProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.makeMethodStatic.MakeMethodStaticProcessor");

  private PsiMethod myMethod;
  private PsiClass myMethodClass;
  private boolean myPreviewUsages;
  private Settings mySettings;

  public MakeMethodStaticProcessor(Project project,
                                   PsiMethod method,
                                   boolean previewUsages, Settings settings, Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myMethod = method;
    mySettings = settings;
    myMethodClass = method.getContainingClass();
    myPreviewUsages = previewUsages;
  }


  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new MakeMethodStaticViewDescriptor(myMethod, usages, refreshCommand);
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      String[] conflicts = getConflictDescriptions(usages[0]);
      if (conflicts.length > 0) {
        ConflictsDialog conflictsDialog = new ConflictsDialog(conflicts, myProject);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) {
          return false;
        }
      }
      if(!mySettings.isChangeSignature()) {
        usages[0] = filterInternalUsages(usages[0]);
      }
    }
    usages[0] = filterOverriding(usages[0]);

    prepareSuccessful();
    return true;
  }

  private UsageInfo[] filterOverriding(UsageInfo[] usages) {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (!(usage instanceof OverridingMethodUsageInfo)) {
        result.add(usage);
      }
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  private UsageInfo[] filterInternalUsages(UsageInfo[] usage) {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    for (int i = 0; i < usage.length; i++) {
      UsageInfo usageInfo = usage[i];
      if(!(usageInfo instanceof InternalUsageInfo)) {
        result.add(usageInfo);
      }
    }
    return result.toArray(new UsageInfo[result.size()]);
  }

  private String[] getConflictDescriptions(UsageInfo[] usages) {
    ArrayList<String> conflicts = new ArrayList<String>();
    HashSet<PsiElement> processed = new HashSet<PsiElement>();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usageInfo = usages[i];

      if (usageInfo instanceof InternalUsageInfo && !(usageInfo instanceof SelfUsageInfo)) {
        PsiElement referencedElement = ((InternalUsageInfo) usageInfo).getReferencedElement();
        if (!mySettings.isMakeClassParameter()) {
          if (referencedElement instanceof PsiModifierListOwner) {
            if (((PsiModifierListOwner) referencedElement).hasModifierProperty(PsiModifier.STATIC)) {
              continue;
            }
          }

          if (processed.contains(referencedElement)) continue;
          processed.add(referencedElement);
          if (referencedElement instanceof PsiField) {
            PsiField field = (PsiField) referencedElement;

            if (mySettings.getNameForField(field) == null) {
              String message = "Method uses non-static " + ConflictsUtil.getDescription(field, true)
                      + ", which is not passed as a parameter";
              conflicts.add(message);
            }
          }
          else {
            String message = "Method uses " + ConflictsUtil.getDescription(referencedElement, true)
                    + ", which needs class instance.";
            conflicts.add(message);
          }
        }
      } if (usageInfo instanceof OverridingMethodUsageInfo) {
        final PsiMethod overridingMethod = ((PsiMethod) usageInfo.getElement());
        String message = "Method " + ConflictsUtil.getDescription(myMethod, false) + " is overriden by " +
                ConflictsUtil.getDescription(overridingMethod, true) + ".";
        conflicts.add(message);
      }
      else {
        PsiElement element = usageInfo.getElement();
        PsiElement container = ConflictsUtil.getContainer(element);
        if (processed.contains(container)) continue;
        processed.add(container);
        List fieldParameters = mySettings.getParameterOrderList();
        ArrayList<PsiField> inaccessible = new ArrayList<PsiField>();

        for (Iterator iterator = fieldParameters.iterator(); iterator.hasNext();) {
          Settings.FieldParameter fieldParameter = (Settings.FieldParameter) iterator.next();

          if (!PsiUtil.isAccessible(fieldParameter.field, element, null)) {
            inaccessible.add(fieldParameter.field);
          }
        }

        if (inaccessible.isEmpty()) continue;

        conflicts.add(createInaccessibleFieldsConflictDescription(inaccessible, container));
      }
    }
    return conflicts.toArray(new String[0]);
  }

  private String createInaccessibleFieldsConflictDescription(ArrayList<PsiField> inaccessible, PsiElement container) {
    StringBuffer buf = new StringBuffer("Field");
    if (inaccessible.size() == 1) {
      buf.append(" ");
    }
    else {
      buf.append("s ");
    }

    for (int j = 0; j < inaccessible.size(); j++) {
      PsiField field = inaccessible.get(j);

      if (j > 0) {
        if (j + 1 < inaccessible.size()) {
          buf.append(", ");
        }
        else {
          buf.append(" and ");
        }
      }
      buf.append(ConflictsUtil.htmlEmphasize(field.getName()));
    }
    buf.append(" is not accessible from ");
    buf.append(ConflictsUtil.getDescription(container, true));
    return buf.toString();
  }

  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    PsiManager manager = myMethod.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();

    if (mySettings.isReplaceUsages()) {
      PsiReference[] refs = helper.findReferences(myMethod, GlobalSearchScope.projectScope(myProject), true);
      for (int i = 0; i < refs.length; i++) {
        PsiElement ref = refs[i].getElement();
        PsiElement qualifier = null;
        if (ref instanceof PsiReferenceExpression) {
          qualifier = ((PsiReferenceExpression) ref).getQualifierExpression();
          if (qualifier instanceof PsiThisExpression) qualifier = null;
        }
        if (!PsiTreeUtil.isAncestor(myMethod, ref, true) || qualifier != null) {
          result.add(new UsageInfo(ref));
        }
      }
    }
    final PsiMethod[] overridingMethods = helper.findOverridingMethods(myMethod, GlobalSearchScope.allScope(myProject), false);
    for (int i = 0; i < overridingMethods.length; i++) {
      PsiMethod overridingMethod = overridingMethods[i];
      if (overridingMethod != myMethod) {
        result.add(new OverridingMethodUsageInfo(overridingMethod));
      }
    }

    UsageInfo[] externalUsages = result.toArray(new UsageInfo[0]);
    UsageInfo[] internalUsages;

//    if (mySettings.isChangeSignature()) {
    internalUsages = MakeMethodStaticUtil.findClassRefsInMethod(myMethod, true);
//    }
//    else {
//      internalUsages = new UsageInfo[0];
//    }

    UsageInfo[] resultArray = ArrayUtil.mergeArrays(internalUsages, externalUsages, UsageInfo.class);
    return resultArray;
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return super.isPreviewUsages(usages) || myPreviewUsages;
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiManager manager = myMethod.getManager();
    PsiElementFactory factory = manager.getElementFactory();

    try {
      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
        if(usage instanceof SelfUsageInfo) {
          changeSelfUsage((SelfUsageInfo) usage);
        }
        else if (usage instanceof InternalUsageInfo) {
          changeInternalUsage((InternalUsageInfo) usage, factory);
        }
        else {
          changeExternalUsage(usage, factory);
        }
      }
      changeMethodSignature(factory, usages);
    }
    catch (IncorrectOperationException ex) {
      LOG.assertTrue(false);
    }
  }

  private void changeSelfUsage(SelfUsageInfo usageInfo) throws IncorrectOperationException {
    PsiElement parent = usageInfo.getElement().getParent();
    LOG.assertTrue(parent instanceof PsiMethodCallExpression);
    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
    PsiElementFactory factory = methodCall.getManager().getElementFactory();
    PsiExpressionList args = methodCall.getArgumentList();
    PsiElement addParameterAfter = null;

    if(mySettings.isMakeClassParameter()) {
      PsiElement arg = factory.createExpressionFromText(mySettings.getClassParameterName(), null);
      addParameterAfter = args.addAfter(arg, null);
    }

    if(mySettings.isMakeFieldParameters()) {
      List parameters = mySettings.getParameterOrderList();
      for (Iterator iterator = parameters.iterator(); iterator.hasNext();) {
        Settings.FieldParameter fieldParameter = (Settings.FieldParameter) iterator.next();
        PsiElement arg = factory.createExpressionFromText(fieldParameter.name, null);
        if(addParameterAfter == null) {
          addParameterAfter = args.addAfter(arg, null);
        }
        else {
          addParameterAfter = args.addAfter(arg, addParameterAfter);
        }
      }
    }
  }

  private void changeMethodSignature(PsiElementFactory factory, UsageInfo[] usages)
          throws IncorrectOperationException {
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(myMethod);
    PsiParameterList paramList = myMethod.getParameterList();
    PsiElement addParameterAfter = null;
    PsiDocTag anchor = null;
    List<PsiType> addedTypes = new ArrayList<PsiType>();

    if (mySettings.isMakeClassParameter()) {
      // Add parameter for object
      PsiType parameterType = factory.createType(myMethodClass, PsiSubstitutor.EMPTY);
      addedTypes.add(parameterType);

      final String classParameterName = mySettings.getClassParameterName();
      PsiParameter parameter = factory.createParameter(classParameterName, parameterType);
      if(makeClassParameterFinal(usages)) {
        parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
      }
      addParameterAfter = paramList.addAfter(parameter, null);
      anchor = javaDocHelper.addParameterAfter(classParameterName, anchor);
    }

    if (mySettings.isMakeFieldParameters()) {
      List parameters = mySettings.getParameterOrderList();

      for (Iterator iterator = parameters.iterator(); iterator.hasNext();) {
        Settings.FieldParameter fieldParameter = (Settings.FieldParameter) iterator.next();
        final PsiType fieldParameterType = fieldParameter.field.getType();
        final PsiParameter parameter = factory.createParameter(fieldParameter.name, fieldParameterType);
        addedTypes.add(fieldParameterType);
        if (makeFieldParameterFinal(fieldParameter.field, usages)) {
          parameter.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }
        addParameterAfter = paramList.addAfter(parameter, addParameterAfter);
        anchor = javaDocHelper.addParameterAfter(fieldParameter.name, anchor);
      }
    }
    addTypeParameters(addedTypes);
    // Add static modifier
    final PsiModifierList modifierList = myMethod.getModifierList();
    modifierList.setModifierProperty(PsiModifier.STATIC, true);
    modifierList.setModifierProperty(PsiModifier.FINAL, false);
  }

  private void addTypeParameters(List<PsiType> addedTypes) throws IncorrectOperationException {
    final PsiManager manager = myMethod.getManager();
    final PsiElementFactory factory = manager.getElementFactory();
    final List<PsiTypeParameter> typeParametersToAdd = new ArrayList<PsiTypeParameter>();

    final PsiMethod methodFromText = factory.createMethodFromText("void utterGarbage();", myMethod);
    for (Iterator<PsiType> iterator = addedTypes.iterator(); iterator.hasNext();) {
      final PsiType type = iterator.next();
      methodFromText.getParameterList().add(factory.createParameter("p", type));
    }
    final LocalSearchScope searchScope = new LocalSearchScope(methodFromText);    
    final Iterator<PsiTypeParameter> tpIterator = PsiUtil.typeParametersIterator(myMethodClass);
    while (tpIterator.hasNext()) {
      final PsiTypeParameter psiTypeParameter = tpIterator.next();
      if (manager.getSearchHelper().findReferences(psiTypeParameter, searchScope, false).length > 0) {
        typeParametersToAdd.add(psiTypeParameter);
      }
    }
    Collections.reverse(typeParametersToAdd);
    for (Iterator<PsiTypeParameter> iterator = typeParametersToAdd.iterator(); iterator.hasNext();) {
      final PsiTypeParameter typeParameter = iterator.next();
      myMethod.getTypeParameterList().add(typeParameter);
    }
  }

  private boolean makeClassParameterFinal(UsageInfo[] usages) {
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if(usage instanceof InternalUsageInfo) {
        final InternalUsageInfo internalUsageInfo = (InternalUsageInfo) usage;
        PsiElement referencedElement = internalUsageInfo.getReferencedElement();
        if(!(referencedElement instanceof PsiField)
                || mySettings.getNameForField((PsiField) referencedElement) == null) {
          if(internalUsageInfo.isInsideAnonymous()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean makeFieldParameterFinal(PsiField field, UsageInfo[] usages) {
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (usage instanceof InternalUsageInfo) {
        final InternalUsageInfo internalUsageInfo = (InternalUsageInfo) usage;
        PsiElement referencedElement = internalUsageInfo.getReferencedElement();
        if (referencedElement instanceof PsiField && field.equals(referencedElement)) {
          if (internalUsageInfo.isInsideAnonymous()) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private void changeInternalUsage(InternalUsageInfo usage, PsiElementFactory factory)
          throws IncorrectOperationException {
    if (!mySettings.isChangeSignature()) return;

    PsiElement element = usage.getElement();

    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression newRef = null;

      if (mySettings.isMakeFieldParameters()) {
        PsiElement resolved = ((PsiReferenceExpression) element).resolve();
        if (resolved instanceof PsiField) {
          String name = mySettings.getNameForField((PsiField) resolved);
          if (name != null) {
            newRef = (PsiReferenceExpression) factory.createExpressionFromText(name, null);
          }
        }
      }

      if (newRef == null && mySettings.isMakeClassParameter()) {
        newRef =
                (PsiReferenceExpression) factory.createExpressionFromText(
                        mySettings.getClassParameterName() + "." + element.getText(), null);
      }

      if (newRef != null) {
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(myProject);
        newRef = (PsiReferenceExpression) codeStyleManager.reformat(newRef);
        element.replace(newRef);
      }
    }
    else if (element instanceof PsiThisExpression && mySettings.isMakeClassParameter()) {
      element.replace(factory.createExpressionFromText(mySettings.getClassParameterName(), null));
    }
    else if (element instanceof PsiSuperExpression && mySettings.isMakeClassParameter()) {
      element.replace(factory.createExpressionFromText(mySettings.getClassParameterName(), null));
    }
  }

  private void changeExternalUsage(UsageInfo usage, PsiElementFactory factory)
          throws IncorrectOperationException {
    if (!(usage.getElement() instanceof PsiReferenceExpression)) return;

    PsiReferenceExpression methodRef = (PsiReferenceExpression) usage.getElement();
    PsiElement parent = methodRef.getParent();
    LOG.assertTrue(parent instanceof PsiMethodCallExpression);

    PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
    PsiExpression instanceRef;

    instanceRef = methodRef.getQualifierExpression();
    PsiElement newQualifier;

    if (instanceRef == null || instanceRef instanceof PsiSuperExpression) {
      instanceRef = factory.createExpressionFromText("this", null);
      newQualifier = null;
    }
    else {
      newQualifier = factory.createReferenceExpression(myMethodClass);
    }

    if (mySettings.getNewParametersNumber() > 1) {
      int copyingSafetyLevel = RefactoringUtil.verifySafeCopyExpression(instanceRef);
      if (copyingSafetyLevel == RefactoringUtil.EXPR_COPY_PROHIBITED) {
        String tempVar = RefactoringUtil.createTempVar(instanceRef, methodCall, true);
        instanceRef = factory.createExpressionFromText(tempVar, null);
      }
    }


    PsiElement anchor = null;
    PsiExpressionList argList = methodCall.getArgumentList();
    PsiExpression[] exprs = argList.getExpressions();
    if (mySettings.isMakeClassParameter()) {
      if (exprs.length > 0) {
        anchor = argList.addBefore(instanceRef, exprs[0]);
      }
      else {
        anchor = argList.add(instanceRef);
      }
    }


    if (mySettings.isMakeFieldParameters()) {
      List parameters = mySettings.getParameterOrderList();

      for (Iterator iterator = parameters.iterator(); iterator.hasNext();) {
        Settings.FieldParameter fieldParameter = (Settings.FieldParameter) iterator.next();

        PsiReferenceExpression fieldRef;
        if (newQualifier != null) {
          fieldRef = (PsiReferenceExpression) factory.createExpressionFromText(
                  "a." + fieldParameter.field.getName(), null);
          fieldRef.getQualifierExpression().replace(instanceRef);
        }
        else {
          fieldRef = (PsiReferenceExpression) factory.createExpressionFromText(fieldParameter.field.getName(), null);
        }

        if (anchor != null) {
          anchor = argList.addAfter(fieldRef, anchor);
        }
        else {
          if (exprs.length > 0) {
            anchor = argList.addBefore(fieldRef, exprs[0]);
          }
          else {
            anchor = argList.add(fieldRef);
          }
        }
      }
    }

    if (newQualifier != null) {
      methodRef.getQualifierExpression().replace(newQualifier);
    }

  }

  protected String getCommandName() {
    return "Making " + UsageViewUtil.getDescriptiveName(myMethod) + " static";
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public Settings getSettings() {
    return mySettings;
  }
}
