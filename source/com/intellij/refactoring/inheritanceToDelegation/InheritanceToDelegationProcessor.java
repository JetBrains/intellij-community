package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.inheritanceToDelegation.usageInfo.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.ConflictsUtil;
import com.intellij.refactoring.util.RefactoringHierarchyUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.VisibilityUtil;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceSearchingScanner;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * @author dsl
 */
public class InheritanceToDelegationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationProcessor");
  private final PsiClass myClass;
  private final String myInnerClassName;
  private boolean myIsDelegateOtherMembers;
  private final LinkedHashSet myDelegatedInterfaces;
  private final LinkedHashSet myDelegatedMethods;
  private final com.intellij.util.containers.HashMap myDelegatedMethodsVisibility;
  private final LinkedHashSet myOverridenMethods;

  private boolean myPreviewUsages;

  private final PsiClass myBaseClass;
  private final Set myBaseClassMembers;
  private final String myFieldName;
  private final String myGetterName;
  private final boolean myGenerateGetter;
  private Set myBaseClassBases;
  private Set myClassImplementedInterfaces;
  private PsiElementFactory myFactory;
  private final PsiType myBaseClassType;
  private final PsiManager myManager;
  private final boolean myIsInnerClassNeeded;
  private Set myClassInheritors;
  private HashSet myAbstractDelegatedMethods;


  public InheritanceToDelegationProcessor(Project project,
                                          PsiClass aClass,
                                          PsiClass targetBaseClass, String fieldName, String innerClassName,
                                          PsiClass[] delegatedInterfaces, PsiMethod[] delegatedMethods,
                                          boolean delegateOtherMembers, boolean generateGetter,
                                          boolean previewUsages,
                                          Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);

    myClass = aClass;
    myInnerClassName = innerClassName;
    myIsDelegateOtherMembers = delegateOtherMembers;
    myManager = myClass.getManager();
    myFactory = myManager.getElementFactory();
    myPreviewUsages = previewUsages;

    myBaseClass = targetBaseClass;
    LOG.assertTrue(
            myBaseClass != null // && !myBaseClass.isInterface()
            && (myBaseClass.getQualifiedName() == null || !myBaseClass.getQualifiedName().equals("java.lang.Object"))
    );
    myBaseClassMembers = getAllBaseClassMembers();
    myBaseClassBases = getAllBases();
    myBaseClassType = myFactory.createType(myBaseClass);

    myIsInnerClassNeeded = InheritanceToDelegationUtil.isInnerClassNeeded(myClass, myBaseClass);


    myFieldName = fieldName;
    final String propertyName = CodeStyleManager.getInstance(myProject).variableNameToPropertyName(myFieldName, VariableKind.FIELD);
    myGetterName = PropertyUtil.suggestGetterName(propertyName, myBaseClassType);
    myGenerateGetter = generateGetter;

    myDelegatedInterfaces = new LinkedHashSet();
    addAll(myDelegatedInterfaces, delegatedInterfaces);
    myDelegatedMethods = new LinkedHashSet();
    addAll(myDelegatedMethods, delegatedMethods);
    myDelegatedMethodsVisibility = new com.intellij.util.containers.HashMap();
    for (Iterator iterator = myDelegatedMethods.iterator(); iterator.hasNext();) {
      PsiMethod method = (PsiMethod) iterator.next();
      PsiMethod overridingMethod = myClass.findMethodBySignature(method, false);
      if (overridingMethod != null) {
        myDelegatedMethodsVisibility.put(method,
                VisibilityUtil.getVisibilityModifier(overridingMethod.getModifierList()));
      }
    }

    myOverridenMethods = getOverriddenMethods();
  }


  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return super.isPreviewUsages(usages) || myPreviewUsages;
  }


  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new InheritanceToDelegationViewDescriptor(myClass, usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    ArrayList usages = new ArrayList();
    PsiSearchHelper searchHelper = myManager.getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    final PsiClass[] inheritors = searchHelper.findInheritors(myClass, projectScope, true);
    myClassInheritors = new HashSet();
    myClassInheritors.add(myClass);
    addAll(myClassInheritors, inheritors);

    {
      ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(myClass);
      final MyClassInstanceReferenceVisitor instanceReferenceVisitor = new MyClassInstanceReferenceVisitor(myClass, usages);
      scanner.processReferences(new ClassInstanceScanner(myClass, instanceReferenceVisitor));

      MyClassMemberReferencesVisitor visitor = new MyClassMemberReferencesVisitor(usages, instanceReferenceVisitor);
      myClass.accept(visitor);

      myClassImplementedInterfaces = instanceReferenceVisitor.getImplementedInterfaces();
    }
    for (int i = 0; i < inheritors.length; i++) {
      processClass(inheritors[i], usages);
    }

    return (UsageInfo[]) usages.toArray(new UsageInfo[usages.size()]);
  }

  private FieldAccessibility getFieldAccessibility(PsiElement element) {
    for (Iterator iterator = myClassInheritors.iterator(); iterator.hasNext();) {
      PsiClass aClass = (PsiClass) iterator.next();
      if (PsiTreeUtil.isAncestor(aClass, element, false)) {
        return new FieldAccessibility(true, aClass);
      }
    }
    return FieldAccessibility.INVISIBLE;
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    ArrayList oldUsages = new ArrayList();
    addAll(oldUsages, usages[0]);
    final ObjectUpcastedUsageInfo[] objectUpcastedUsageInfos = objectUpcastedUsages(usages[0]);
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      ArrayList conflicts = new ArrayList();
      if (objectUpcastedUsageInfos.length > 0) {
        final String message = "Instances of " + ConflictsUtil.getDescription(myClass, true) + " upcasted to " +
                ConflictsUtil.htmlEmphasize("java.lang.Object") + " were found. If you continue, they will be shown " +
                "in a separate Find tab.";

        conflicts.add(message);
      }

      analyzeConflicts(usages[0], conflicts);
      if (!conflicts.isEmpty()) {
        ConflictsDialog conflictsDialog =
                new ConflictsDialog((String[]) conflicts.toArray(new String[conflicts.size()]), myProject);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) return false;
      }

      if (objectUpcastedUsageInfos.length > 0) {
        showObjectUpcastedUsageView(objectUpcastedUsageInfos);
        myPreviewUsages = true;
      }
    }
    ArrayList filteredUsages = filterUsages(oldUsages);
    usages[0] = (UsageInfo[]) filteredUsages.toArray(new UsageInfo[filteredUsages.size()]);
    prepareSuccessful();
    return true;
  }

  private void analyzeConflicts(UsageInfo[] usage, ArrayList conflicts) {
    com.intellij.util.containers.HashMap reportedNonDelegatedUsages = new com.intellij.util.containers.HashMap();
    com.intellij.util.containers.HashMap reportedUpcasts = new com.intellij.util.containers.HashMap();
//    HashSet reportedObjectUpcasts = new HashSet();

//    final String nameJavaLangObject = ConflictsUtil.htmlEmphasize("java.lang.Object");
    final String classDescription = ConflictsUtil.getDescription(myClass, false);

    for (int i = 0; i < usage.length; i++) {
      if (usage[i] instanceof InheritanceToDelegationUsageInfo) {
        InheritanceToDelegationUsageInfo usageInfo = (InheritanceToDelegationUsageInfo) usage[i];
        /*if (usageInfo instanceof ObjectUpcastedUsageInfo) {
          PsiElement container = ConflictsUtil.getContainer(usageInfo.element);
          if (!reportedObjectUpcasts.contains(container)) {
            String message = "An instance of " + classDescription + " is upcasted to "
                    + nameJavaLangObject + " in " + ConflictsUtil.getDescription(container, true) + ".";
            conflicts.add(message);
            reportedObjectUpcasts.add(container);
          }
        } else*/
        if (!myIsDelegateOtherMembers && !usageInfo.getDelegateFieldAccessible().isAccessible()) {
          if (usageInfo instanceof NonDelegatedMemberUsageInfo) {
            final PsiElement nonDelegatedMember = ((NonDelegatedMemberUsageInfo) usageInfo).nonDelegatedMember;
            HashSet reportedContainers = (HashSet) reportedNonDelegatedUsages.get(nonDelegatedMember);
            if (reportedContainers == null) {
              reportedContainers = new HashSet();
              reportedNonDelegatedUsages.put(nonDelegatedMember, reportedContainers);
            }
            final PsiElement container = ConflictsUtil.getContainer(usageInfo.getElement());
            if (!reportedContainers.contains(container)) {
              String message = ConflictsUtil.getDescription(container, true) + " uses "
                      + ConflictsUtil.getDescription(nonDelegatedMember, true) + " of an instance of a "
                      + classDescription + ".";
              conflicts.add(ConflictsUtil.capitalize(message));
              reportedContainers.add(container);
            }
          } else if (usageInfo instanceof UpcastedUsageInfo) {
            final PsiClass upcastedTo = ((UpcastedUsageInfo) usageInfo).upcastedTo;
            HashSet reportedContainers = (HashSet) reportedUpcasts.get(upcastedTo);
            if (reportedContainers == null) {
              reportedContainers = new HashSet();
              reportedUpcasts.put(upcastedTo, reportedContainers);
            }
            final PsiElement container = ConflictsUtil.getContainer(usageInfo.getElement());
            if (!reportedContainers.contains(container)) {
              String message = ConflictsUtil.getDescription(container, true) + " upcasts an instance of "
                      + classDescription + " to " + ConflictsUtil.getDescription(upcastedTo, false) + ".";
              conflicts.add(ConflictsUtil.capitalize(message));
              reportedContainers.add(container);
            }
          }
        }
      } else if (usage[i] instanceof NoLongerOverridingSubClassMethodUsageInfo) {
        NoLongerOverridingSubClassMethodUsageInfo info = (NoLongerOverridingSubClassMethodUsageInfo) usage[i];
        String message = ConflictsUtil.getDescription(info.getSubClassMethod(), true) + " will no longer override "
                + ConflictsUtil.getDescription(info.getOverridenMethod(), true);
        conflicts.add(message);
      }
    }
  }

  private ObjectUpcastedUsageInfo[] objectUpcastedUsages(UsageInfo[] usages) {
    ArrayList result = new ArrayList();
    for (int i = 0; i < usages.length; i++) {
      UsageInfo usage = usages[i];
      if (usage instanceof ObjectUpcastedUsageInfo) {
        result.add(((ObjectUpcastedUsageInfo) usage));
      }
    }
    return (ObjectUpcastedUsageInfo[]) result.toArray(new ObjectUpcastedUsageInfo[result.size()]);
  }

  private ArrayList filterUsages(ArrayList usages) {
    ArrayList result = new ArrayList();

    for (int i = 0; i < usages.size(); i++) {
      UsageInfo usageInfo = (UsageInfo) usages.get(i);

      if (!(usageInfo instanceof InheritanceToDelegationUsageInfo)) {
        continue;
      }
      if (usageInfo instanceof ObjectUpcastedUsageInfo) {
        continue;
      }

      if (!myIsDelegateOtherMembers) {
        final FieldAccessibility delegateFieldAccessible = ((InheritanceToDelegationUsageInfo) usageInfo).getDelegateFieldAccessible();
        if (!delegateFieldAccessible.isAccessible()) continue;
      }

      result.add(usageInfo);
    }
    return result;
  }

  private void processClass(PsiClass inheritor, ArrayList usages) {
    ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(inheritor);
    final MyClassInstanceReferenceVisitor instanceVisitor = new MyClassInstanceReferenceVisitor(inheritor, usages);
    scanner.processReferences(
            new ClassInstanceScanner(inheritor,
                    instanceVisitor)
    );
    MyClassInheritorMemberReferencesVisitor classMemberVisitor = new MyClassInheritorMemberReferencesVisitor(inheritor, usages, instanceVisitor);
    inheritor.accept(classMemberVisitor);

    PsiMethod[] methods = inheritor.getMethods();
    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];

      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE)) return;
      final PsiMethod baseMethod = myBaseClass.findMethodBySignature(method, true);
      if (baseMethod != null) {
        final PsiMethod classMethod = myClass.findMethodBySignature(method, false);
        if (!baseMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
        } else {
          if (classMethod != null && !classMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
          }
        }
      }
    }
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      for (int i = 0; i < usages.length; i++) {
        InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo) usages[i];


        if (usage instanceof UnqualifiedNonDelegatedMemberUsageInfo) {
          delegateUsageFromClass(usage.getElement(), ((NonDelegatedMemberUsageInfo) usage).nonDelegatedMember,
                  usage.getDelegateFieldAccessible());
        } else {
          upcastToDelegation(usage.getElement(), usage.getDelegateFieldAccessible());
        }
      }

      myAbstractDelegatedMethods = new HashSet();
      addInnerClass();
      addField(usages);
      delegateMethods();
      addImplementingInterfaces();
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void addInnerClass() throws IncorrectOperationException {
    if (!myIsInnerClassNeeded) return;

    PsiClass innerClass = myFactory.createClass(myInnerClassName);
    final PsiJavaCodeReferenceElement baseClassReferenceElement = myFactory.createClassReferenceElement(myBaseClass);
    if (!myBaseClass.isInterface()) {
      innerClass.getExtendsList().add(baseClassReferenceElement);
    } else {
      innerClass.getImplementsList().add(baseClassReferenceElement);
    }
    innerClass.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);
    innerClass = (PsiClass) myClass.add(innerClass);

    List innerClassMethods = getInnerClassMethods();
    for (Iterator iterator = innerClassMethods.iterator(); iterator.hasNext();) {
      InnerClassMethod innerClassMethod = (InnerClassMethod) iterator.next();
      innerClassMethod.createMethod(innerClass, myClass);
    }
  }

  private void delegateUsageFromClass(PsiElement element, PsiElement nonDelegatedMember,
                                      FieldAccessibility fieldAccessibility) throws IncorrectOperationException {
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression referenceExpression = (PsiReferenceExpression) element;
      if (referenceExpression.getQualifierExpression() != null) {
        upcastToDelegation(referenceExpression.getQualifierExpression(), fieldAccessibility);
      } else {
        final String name = ((PsiNamedElement) nonDelegatedMember).getName();
        final String qualifier;
        if (isStatic (nonDelegatedMember)) {
          qualifier = myBaseClass.getName();
        }
        else if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
          qualifier = myGetterName + "()";
        }
        else {
          qualifier = myFieldName;
        }

        PsiExpression newExpr = myFactory.createExpressionFromText(qualifier + "." + name, element);
        newExpr = (PsiExpression) CodeStyleManager.getInstance(myProject).reformat(newExpr);
        element.replace(newExpr);
      }
    }
    else if (element instanceof PsiJavaCodeReferenceElement) {
        final String name = ((PsiNamedElement) nonDelegatedMember).getName();

      PsiElement parent = element.getParent ();
      if (!isStatic (nonDelegatedMember) && parent instanceof PsiNewExpression) {
        final PsiNewExpression newExpr = (PsiNewExpression) parent;
        if (newExpr.getQualifier() != null) {
          upcastToDelegation(newExpr.getQualifier(), fieldAccessibility);
        } else {
          final String qualifier;
          if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
            qualifier = myGetterName + "()";
          }
          else {
            qualifier = myFieldName;
          }
          final PsiExpression template = myFactory.createExpressionFromText(qualifier + ".new C()", parent);
          final PsiElement qual = template.getFirstChild ();
          final PsiElement dot  = qual.getNextSibling();
          parent.addBefore(dot,  parent.getFirstChild());
          parent.addBefore(qual, parent.getFirstChild());
        }
      }
      else {
        final String qualifier = myBaseClass.getName();
        PsiJavaCodeReferenceElement newRef = myFactory.createFQClassNameReferenceElement(qualifier + "." + name, element.getResolveScope ());
        //newRef = (PsiJavaCodeReferenceElement) CodeStyleManager.getInstance(myProject).reformat(newRef);
        element.replace(newRef);
      }
    } else {
      LOG.assertTrue(false);
    }
  }

  private boolean isStatic(PsiElement member) {
    if (member instanceof PsiModifierListOwner) {
      final PsiModifierListOwner method = (PsiModifierListOwner) member;
      return method.hasModifierProperty ("static");
    }
    return false;
  }

  private void upcastToDelegation(PsiElement element, FieldAccessibility fieldAccessibility) throws IncorrectOperationException {
    final PsiExpression expression = (PsiExpression) element;

    final PsiExpression newExpr;
    final PsiReferenceExpression ref;
    final String delegateQualifier;
    if (!(expression instanceof PsiThisExpression || expression instanceof PsiSuperExpression)) {
      delegateQualifier = "a.";
    } else {
      PsiResolveHelper resolveHelper = myManager.getResolveHelper();
      final PsiVariable psiVariable = resolveHelper.resolveReferencedVariable(myFieldName, element);
      if (psiVariable == null) {
        delegateQualifier = "";
      } else {
        delegateQualifier = "a.";
      }
    }
    if (!fieldAccessibility.isAccessible() && myGenerateGetter) {
      newExpr = myFactory.createExpressionFromText(delegateQualifier + myGetterName + "()", expression);
      ref = (PsiReferenceExpression) ((PsiMethodCallExpression) newExpr).getMethodExpression().getQualifierExpression();
    } else {
      newExpr = myFactory.createExpressionFromText(delegateQualifier + myFieldName, expression);
      ref = (PsiReferenceExpression) ((PsiReferenceExpression) newExpr).getQualifierExpression();
    }
//    LOG.debug("upcastToDelegation:" + element + ":newExpr = " + newExpr);
//    LOG.debug("upcastToDelegation:" + element + ":ref = " + ref);
    if (ref != null) {
      ref.replace(expression);
    }
    expression.replace(newExpr);
//    LOG.debug("upcastToDelegation:" + element + ":replaced = " + replaced);
  }

  private void delegateMethods() throws IncorrectOperationException {
    for (Iterator iterator = myDelegatedMethods.iterator(); iterator.hasNext();) {
      PsiMethod method = (PsiMethod) iterator.next();

      if (!myAbstractDelegatedMethods.contains(method)) {
        PsiMethod methodToAdd = delegateMethod(myFieldName, method);

        String visibility = (String) myDelegatedMethodsVisibility.get(method);
        if (visibility != null) {
          methodToAdd.getModifierList().setModifierProperty(visibility, true);
        }

        myClass.add(methodToAdd);
      }
    }
  }

  private String getMethodHeaderText(PsiMethod method) {
    StringBuffer buf = new StringBuffer();
    buf.append("public ");
    PsiTypeElement typeElt = method.getReturnTypeElement();
    if (typeElt != null) {
      buf.append(typeElt.getText());
      buf.append(' ');
    }
    buf.append(method.getName());
    buf.append('(');
    PsiParameter[] params = method.getParameterList().getParameters();
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      if (i > 0) buf.append(',');
      buf.append(param.getTypeElement().getText());
      buf.append(' ');
      buf.append(param.getName());
    }
    buf.append(')');
    buf.append(method.getThrowsList().getText());
    return buf.toString();
  }

  private PsiMethod delegateMethod(String delegationTarget, PsiMethod method) throws IncorrectOperationException {
    PsiMethod methodToAdd = (PsiMethod) method.copy();

    PsiCodeBlock oldBody = methodToAdd.getBody();
    StringBuffer buffer = new StringBuffer();
    if (oldBody == null) {
      buffer.append(getMethodHeaderText(method));
    }

    appendDelegationBody(buffer, methodToAdd, method, delegationTarget);


    if (oldBody != null) {
      PsiCodeBlock newBody;
      newBody = myFactory.createCodeBlockFromText(buffer.toString(), method);
      oldBody.replace(newBody);
    } else {
      methodToAdd = myFactory.createMethodFromText(buffer.toString(), method);
    }

    if (methodToAdd.getDocComment() != null) {
      methodToAdd.getDocComment().delete();  // todo: maintain JavaDoc
    }
    methodToAdd.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);
    methodToAdd = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(methodToAdd);
    return methodToAdd;
  }

  private void appendDelegationBody(StringBuffer buffer, PsiMethod methodToAdd, PsiMethod originalMethod, String delegationTarget) {
    buffer.append("{\n");

    if (methodToAdd.getReturnType() != PsiType.VOID) {
      buffer.append("return ");
    }

    buffer.append(delegationTarget);
    buffer.append(".");
    buffer.append(methodToAdd.getName());
    buffer.append("(");
    PsiParameter[] params = methodToAdd.getParameterList().getParameters();
    for (int i = 0; i < params.length; i++) {
      PsiParameter param = params[i];
      if (i > 0) {
        buffer.append(",");
      }
      buffer.append(param.getName());
    }
    buffer.append(");\n}");
  }

  private void addImplementingInterfaces() throws IncorrectOperationException {
    final PsiReferenceList implementsList = myClass.getImplementsList();
    for (Iterator iterator = myDelegatedInterfaces.iterator(); iterator.hasNext();) {
      PsiClass delegatedInterface = (PsiClass) iterator.next();

      if (!myClassImplementedInterfaces.contains(delegatedInterface)) {
        implementsList.add(myFactory.createClassReferenceElement(delegatedInterface));
      }
    }

    if (!myBaseClass.isInterface()) {
      final PsiReferenceList extendsList = myClass.getExtendsList();
      extendsList.getReferenceElements()[0].delete();
    } else {
      final PsiJavaCodeReferenceElement[] interfaceRefs = implementsList.getReferenceElements();
      for (int i = 0; i < interfaceRefs.length; i++) {
        PsiJavaCodeReferenceElement interfaceRef = interfaceRefs[i];
        final PsiElement resolved = interfaceRef.resolve();
        if (myManager.areElementsEquivalent(myBaseClass, resolved)) {
          interfaceRef.delete();
          break;
        }
      }
    }
  }

  private void addField(UsageInfo[] usages) throws IncorrectOperationException {
    final String fieldVisibility = getFieldVisibility(usages);

    final boolean fieldInitializerNeeded = isFieldInitializerNeeded();

    PsiField field = createField(fieldVisibility, fieldInitializerNeeded, defaultClassFieldType());

    if (!myIsInnerClassNeeded) {
      PsiType fieldType = myBaseClassType;
      field.getTypeElement().replace(myFactory.createTypeElement(fieldType));
      if (fieldInitializerNeeded) {
        final PsiJavaCodeReferenceElement classReferenceElement = myFactory.createClassReferenceElement(myBaseClass);
        PsiNewExpression newExpression = (PsiNewExpression) field.getInitializer();
        newExpression.getClassReference().replace(classReferenceElement);
      }
    }

    field = (PsiField) CodeStyleManager.getInstance(myProject).reformat(field);
    myClass.add(field);
    if (!fieldInitializerNeeded) {
      fixConstructors();
    }

    if (myGenerateGetter) {
      final String getterVisibility = PsiModifier.PUBLIC;
      StringBuffer getterBuffer = new StringBuffer();
      getterBuffer.append(getterVisibility);
      getterBuffer.append(" Object ");
      getterBuffer.append(myGetterName);
      getterBuffer.append("() {\n return ");
      getterBuffer.append(myFieldName);
      getterBuffer.append(";\n}");
      PsiMethod getter = myFactory.createMethodFromText(getterBuffer.toString(), myClass);
      getter.getReturnTypeElement().replace(myFactory.createTypeElement(myBaseClassType));
      getter = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(getter);
      myClass.add(getter);
    }
  }

  private String getFieldVisibility(UsageInfo[] usages) {
    if (myIsDelegateOtherMembers && !myGenerateGetter) {
      return PsiModifier.PUBLIC;
    }

    for (int i = 0; i < usages.length; i++) {
      InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo) usages[i];
      final FieldAccessibility delegateFieldAccessible = usage.getDelegateFieldAccessible();
      if (delegateFieldAccessible.isAccessible() && delegateFieldAccessible.getContainingClass() != myClass) {
        return PsiModifier.PROTECTED;
      }
    }
    return PsiModifier.PRIVATE;
  }

  private String defaultClassFieldType() {
    return (myIsInnerClassNeeded ? myInnerClassName : "Object");
  }

  private PsiField createField(final String fieldVisibility, final boolean fieldInitializerNeeded, String defaultTypeName) throws IncorrectOperationException {
    StringBuffer buffer = new StringBuffer();
    buffer.append(fieldVisibility);
    buffer.append(" final " + defaultTypeName + "  ");
    buffer.append(myFieldName);
    if (fieldInitializerNeeded) {
      buffer.append(" = new " + defaultTypeName + "()");
    }
    buffer.append(";");
    PsiField field = myFactory.createFieldFromText(buffer.toString(), myClass);
    return field;
  }

  private void fixConstructors() throws IncorrectOperationException {
    if (myBaseClass.isInterface()) return;
    final PsiJavaCodeReferenceElement baseClassReference = myFactory.createClassReferenceElement(myBaseClass);

    PsiMethod[] constructors = myClass.getConstructors();
    for (int i = 0; i < constructors.length; i++) {
      PsiMethod constructor = constructors[i];
      PsiCodeBlock body = constructor.getBody();
      final PsiStatement[] statements = body.getStatements();
      String fieldQualifier = "";
      PsiParameter[] constructorParams = constructor.getParameterList().getParameters();
      for (int j = 0; j < constructorParams.length; j++) {
        PsiParameter constructorParam = constructorParams[j];
        if (myFieldName.equals(constructorParam.getName())) {
          fieldQualifier = "this.";
          break;
        }
      }
      final String assignmentText = fieldQualifier + myFieldName + "= new " + defaultClassFieldType() + "()";
      if (statements.length < 1 || !RefactoringUtil.isSuperOrThisCall(statements[0], true, true) || myBaseClass.isInterface()) {
        PsiExpressionStatement assignmentStatement =
                (PsiExpressionStatement) myFactory.createStatementFromText(
                        assignmentText, body
                );
        if (!myIsInnerClassNeeded) {
          ((PsiNewExpression) assignmentStatement.getExpression()).getClassReference().replace(baseClassReference);
        }

        assignmentStatement = (PsiExpressionStatement) CodeStyleManager.getInstance(myProject).reformat(assignmentStatement);
        if (statements.length > 0) {
          if (!RefactoringUtil.isSuperOrThisCall(statements[0], true, false)) {
            body.addBefore(assignmentStatement, statements[0]);
          } else {
            body.addAfter(assignmentStatement, statements[0]);
          }
        } else {
          body.add(assignmentStatement);
        }
      } else {
        final PsiExpressionStatement callStatement = ((PsiExpressionStatement) statements[0]);
        if (!RefactoringUtil.isSuperOrThisCall(callStatement, false, true)) {
          final PsiMethodCallExpression superConstructorCall =
                  (PsiMethodCallExpression) callStatement.getExpression();
          PsiAssignmentExpression assignmentExpression =
                  (PsiAssignmentExpression) myFactory.createExpressionFromText(
                          assignmentText, superConstructorCall
                  );
          PsiNewExpression newExpression =
                  (PsiNewExpression) assignmentExpression.getRExpression();
          if (!myIsInnerClassNeeded) {
            newExpression.getClassReference().replace(baseClassReference);
          }
          assignmentExpression = (PsiAssignmentExpression) CodeStyleManager.getInstance(myProject).reformat(assignmentExpression);
          newExpression.getArgumentList().replace(superConstructorCall.getArgumentList());
          superConstructorCall.replace(assignmentExpression);
        }
      }
    }
  }

  private boolean isFieldInitializerNeeded() {
    if (myBaseClass.isInterface()) return true;
    PsiMethod[] constructors = myClass.getConstructors();
    for (int i = 0; i < constructors.length; i++) {
      PsiMethod constructor = constructors[i];
      final PsiStatement[] statements = constructor.getBody().getStatements();
      if (statements.length > 0 && RefactoringUtil.isSuperOrThisCall(statements[0], true, false)) return false;
    }
    return true;
  }

  private List getInnerClassMethods() {
    ArrayList result = new ArrayList();

    // find all neccessary constructors
    if (!myBaseClass.isInterface()) {
      PsiMethod[] constructors = myClass.getConstructors();
      for (int i = 0; i < constructors.length; i++) {
        PsiMethod constructor = constructors[i];
        final PsiStatement[] statements = constructor.getBody().getStatements();
        if (statements.length > 0 && RefactoringUtil.isSuperOrThisCall(statements[0], true, false)) {
          final PsiMethodCallExpression superConstructorCall =
                  (PsiMethodCallExpression) ((PsiExpressionStatement) statements[0]).getExpression();
          PsiElement superConstructor = superConstructorCall.getMethodExpression().resolve();
          if (superConstructor instanceof PsiMethod && ((PsiMethod) superConstructor).isConstructor()) {
            result.add(new InnerClassConstructor((PsiMethod) superConstructor));
          }
        }
      }
    }

    // find overriding/implementing method
    {
      class InnerClassOverridingMethod extends InnerClassMethod {
        public InnerClassOverridingMethod(PsiMethod method) {
          super(method);
        }

        public void createMethod(PsiClass innerClass, PsiClass outerClass)
                throws IncorrectOperationException {
          OverridenMethodClassMemberReferencesVisitor visitor = new OverridenMethodClassMemberReferencesVisitor();
          myMethod.accept(visitor);
          final List actions = visitor.getPsiActions();
          for (Iterator iterator = actions.iterator(); iterator.hasNext();) {
            PsiAction action = (PsiAction) iterator.next();
            action.run();
          }
          innerClass.add(myMethod);
          myMethod.delete();
          // myMethod.replace(delegateMethod(myMethod));
        }
      }

      for (Iterator iterator = myOverridenMethods.iterator(); iterator.hasNext();) {
        PsiMethod method = (PsiMethod) iterator.next();
        result.add(new InnerClassOverridingMethod(method));
      }
    }

    // fix abstract methods
    {
      class InnerClassAbstractMethod extends InnerClassMethod {
        public InnerClassAbstractMethod(PsiMethod method) {
          super(method);
          LOG.assertTrue(method.hasModifierProperty(PsiModifier.ABSTRACT));
        }

        public void createMethod(PsiClass innerClass, PsiClass outerClass)
                throws IncorrectOperationException {
          PsiMethod method = delegateMethod(outerClass.getName() + ".this", myMethod);
          final PsiClass containingClass = myMethod.getContainingClass();
          if (myBaseClass.isInterface() || containingClass.isInterface()) {
            method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
          }
          innerClass.add(method);
          PsiMethod outerMethod = outerClass.findMethodBySignature(myMethod, false);
          if (outerMethod == null) {
            final String visibility = checkOuterClassAbstractMethod(myMethod);
            PsiMethod newOuterMethod = (PsiMethod) outerClass.add(myMethod);
            newOuterMethod.getModifierList().setModifierProperty(visibility, true);
            if (newOuterMethod.getDocComment() != null) {
              newOuterMethod.getDocComment().delete();
            }
          }
        }

      }
      PsiMethod[] methods = myBaseClass.getAllMethods();

      for (int i = 0; i < methods.length; i++) {
        PsiMethod method = methods[i];
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          PsiMethod classMethod = myClass.findMethodBySignature(method, true);
          if (classMethod == null || classMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
            result.add(new InnerClassAbstractMethod(method));
          }
        }
      }
    }


    return result;
  }

  private void showObjectUpcastedUsageView(final ObjectUpcastedUsageInfo[] usages) {
    UsageViewPresentation presentation = new UsageViewPresentation();
    presentation.setTargetsNodeText("Replacing inheritance with delegation");
    presentation.setCodeUsagesString("Instances casted to java.lang.Object");
    presentation.setUsagesString("Instances upcasted to Object");
    presentation.setTabText("Instances upcasted to Object");

    UsageViewManager manager = myProject.getComponent(UsageViewManager.class);
    manager.showUsages(new UsageTarget[]{new PsiElement2UsageTargetAdapter(myClass)},
                       UsageInfo2UsageAdapter.convert(usages),
                       presentation);

    WindowManager.getInstance().getStatusBar(myProject).setInfo("Instances upcasted to java.lang.Object found");
  }

  /**
   *
   * @param methodSignature
   * @return Visibility
   */
  private String checkOuterClassAbstractMethod(PsiMethod methodSignature) {
    String visibility = PsiModifier.PROTECTED;
    for (Iterator iterator = myDelegatedMethods.iterator(); iterator.hasNext();) {
      PsiMethod method = (PsiMethod) iterator.next();

      if (MethodSignatureUtil.areSignaturesEqual(method, methodSignature)) {
        visibility = VisibilityUtil.getHighestVisibility(visibility,
                VisibilityUtil.getVisibilityModifier(method.getModifierList()));
        myAbstractDelegatedMethods.add(method);
      }
    }
    return visibility;
  }

  private LinkedHashSet getOverriddenMethods() {
    LinkedHashSet result = new LinkedHashSet();

    PsiMethod[] methods = myClass.getMethods();

    for (int i = 0; i < methods.length; i++) {
      PsiMethod method = methods[i];

      if (method.isConstructor() || method.hasModifierProperty(PsiModifier.PRIVATE)) continue;
      PsiMethod baseMethod = myBaseClass.findMethodBySignature(method, true);
      if (baseMethod != null) {
        PsiClass containingClass = baseMethod.getContainingClass();
        String qName = containingClass.getQualifiedName();
        if (qName == null || !"java.lang.Object".equals(qName)) {
          result.add(method);
        }
      }
    }
    return result;
  }


  protected String getCommandName() {
    return "Replacing inheritance with delegation in " + UsageViewUtil.getDescriptiveName(myClass);
  }

  private Set getAllBaseClassMembers() {
    HashSet result = new HashSet();
    addAll(result, myBaseClass.getAllFields());
    addAll(result, myBaseClass.getAllInnerClasses());
    addAll(result, myBaseClass.getAllMethods());

    ArrayList javaLangObjectMembers = new ArrayList();

    for (Iterator iterator = result.iterator(); iterator.hasNext();) {
      PsiElement element = (PsiElement) iterator.next();
      String qName = null;
      if (element instanceof PsiField) {
        qName = ((PsiField) element).getContainingClass().getQualifiedName();
      } else if (element instanceof PsiMethod) {
        qName = ((PsiMethod) element).getContainingClass().getQualifiedName();
      }
      if (qName != null && qName.equals("java.lang.Object")) {
        javaLangObjectMembers.add(element);
      }
    }
    result.removeAll(javaLangObjectMembers);
    return Collections.unmodifiableSet(result);
  }

  private Set getAllBases() {
    HashSet temp = new HashSet();
    RefactoringHierarchyUtil.getSuperClasses(myBaseClass, temp, true);
    temp.add(myBaseClass);
    return Collections.unmodifiableSet(temp);
  }

  private static void addAll(Collection collection, Object[] objs) {
    for (int i = 0; i < objs.length; i++) {
      collection.add(objs[i]);
    }
  }

  private boolean isDelegated(PsiElement classMember) {
    if(!(classMember instanceof PsiMethod)) return false;
    final PsiMethod method = (PsiMethod) classMember;
    for (Iterator iterator = myDelegatedMethods.iterator(); iterator.hasNext();) {
      PsiMethod delegatedMethod = (PsiMethod) iterator.next();
      if (MethodSignatureUtil.areSignaturesEqual(method, delegatedMethod)) return true;
    }
    return false;
  }

  private class MyClassInheritorMemberReferencesVisitor extends ClassMemberReferencesVisitor {
    private final List myUsageInfoStorage;
    private ClassInstanceScanner.ClassInstanceReferenceVisitor myInstanceVisitor;

    MyClassInheritorMemberReferencesVisitor(PsiClass aClass, List usageInfoStorage,
                                            ClassInstanceScanner.ClassInstanceReferenceVisitor instanceScanner) {
      super(aClass);

      myUsageInfoStorage = usageInfoStorage;
      myInstanceVisitor = instanceScanner;
    }

    public void visitTypeElement(PsiTypeElement type) {
      super.visitTypeElement (type);
    }

    public void visitReferenceElement(PsiJavaCodeReferenceElement element) {
      super.visitReferenceElement (element);
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
      if ("super".equals(classMemberReference.getText()) && classMemberReference.getParent() instanceof PsiMethodCallExpression) {
        return;
      }

      if (classMember != null && myBaseClassMembers.contains(classMember) && !isDelegated(classMember)) {
        final FieldAccessibility delegateFieldVisibility = new FieldAccessibility(true, getPsiClass());
        final InheritanceToDelegationUsageInfo usageInfo;
        if (classMemberReference instanceof PsiReferenceExpression) {
          if (((PsiReferenceExpression) classMemberReference).getQualifierExpression() == null) {
            usageInfo = new UnqualifiedNonDelegatedMemberUsageInfo(classMemberReference, classMember,
                    delegateFieldVisibility);
          } else {
            usageInfo = new NonDelegatedMemberUsageInfo(
                    ((PsiReferenceExpression) classMemberReference).getQualifierExpression(),
                    classMember, delegateFieldVisibility
            );
          }
          myUsageInfoStorage.add(usageInfo);
        }
        else /*if (classMemberReference instanceof PsiJavaCodeReferenceElement)*/ {
            usageInfo = new UnqualifiedNonDelegatedMemberUsageInfo(classMemberReference, classMember,
                    delegateFieldVisibility);
            myUsageInfoStorage.add(usageInfo);

        }
      }
    }

    public void visitThisExpression(PsiThisExpression expression) {
      ClassInstanceScanner.processNonArrayExpression(myInstanceVisitor, expression, null);
    }
  }

  private class MyClassMemberReferencesVisitor extends MyClassInheritorMemberReferencesVisitor {
    MyClassMemberReferencesVisitor(List usageInfoStorage,
                                   ClassInstanceScanner.ClassInstanceReferenceVisitor instanceScanner) {
      super(InheritanceToDelegationProcessor.this.myClass, usageInfoStorage, instanceScanner);
    }

    public void visitMethod(PsiMethod method) {
      if (!myOverridenMethods.contains(method)) {
        super.visitMethod(method);
      }
    }
  }

  interface PsiAction {
    void run() throws IncorrectOperationException;
  }

  /**
   * This visitor should be called for overriden methods before they are moved to an inner class
   */
  private class OverridenMethodClassMemberReferencesVisitor extends ClassMemberReferencesVisitor {
    private final ArrayList myPsiActions;
    private final PsiThisExpression myQualifiedThis;
    private final PsiJavaCodeReferenceElement myClassReferenceElement;

    OverridenMethodClassMemberReferencesVisitor() throws IncorrectOperationException {
      super(myClass);
      myPsiActions = new ArrayList();
      myClassReferenceElement = myFactory.createClassReferenceElement(myClass);
      myQualifiedThis = (PsiThisExpression) myFactory.createExpressionFromText("A.this", null);
      myQualifiedThis.getQualifier().replace(myClassReferenceElement);
    }

    public List getPsiActions() {
      return myPsiActions;
    }

    class QualifyThis implements PsiAction {
      private final PsiThisExpression myThisExpression;

      QualifyThis(PsiThisExpression thisExpression) {
        myThisExpression = thisExpression;
      }

      public void run() throws IncorrectOperationException {
        myThisExpression.replace(myQualifiedThis);
      }
    }

    class QualifyName implements PsiAction {
      private final PsiReferenceExpression myRef;
      private final String myReferencedName;

      QualifyName(PsiReferenceExpression ref, String name) {
        myRef = ref;
        myReferencedName = name;
      }

      public void run() throws IncorrectOperationException {
        PsiReferenceExpression newRef =
                (PsiReferenceExpression) myFactory.createExpressionFromText("a." + myReferencedName, null);
        newRef.getQualifierExpression().replace(myQualifiedThis);
        myRef.replace(newRef);
      }
    }

    protected void visitClassMemberReferenceExpression(PsiMember classMember,
                                                       PsiReferenceExpression classMemberReference) {
      if (classMember instanceof PsiField) {
        final PsiField field = (PsiField) classMember;

        if (field.getContainingClass().equals(myClass)) {
          final String name = field.getName();
          final PsiField baseField = myBaseClass.findFieldByName(name, true);
          if (baseField != null) {
            myPsiActions.add(new QualifyName(classMemberReference, name));
          } else if (classMemberReference.getQualifierExpression() instanceof PsiThisExpression) {
            myPsiActions.add(new QualifyThis((PsiThisExpression) classMemberReference.getQualifierExpression()));
          }
        }
      } else if (classMember instanceof PsiMethod && !myOverridenMethods.contains(classMember)) {
        final PsiMethod method = (PsiMethod) classMember;

        if (method.getContainingClass().equals(myClass)) {
          final PsiMethod baseMethod = myBaseClass.findMethodBySignature(method, true);
          if (baseMethod != null) {
            myPsiActions.add(new QualifyName(classMemberReference, baseMethod.getName()));
          } else if (classMemberReference.getQualifierExpression() instanceof PsiThisExpression) {
            myPsiActions.add(new QualifyThis((PsiThisExpression) classMemberReference.getQualifierExpression()));
          }
        }
      }
    }

    public void visitThisExpression(final PsiThisExpression expression) {
      class Visitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
        public void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance) {
          LOG.assertTrue(false);
        }

        public void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance) {
          processType(typeCastExpression.getCastType().getType());
        }

        public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
          processType(expectedType);
        }

        public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
          LOG.assertTrue(false);
        }

        private void processType(PsiType type) {
          if (type == null || type instanceof PsiPrimitiveType) return;
          final PsiClass resolved = PsiUtil.resolveClassInType(type);
          if (resolved != null && !myBaseClassBases.contains(resolved)) {
            myPsiActions.add(new QualifyThis(expression));
          }
        }
      }
      Visitor visitor = new Visitor();
      ClassInstanceScanner.processNonArrayExpression(visitor, expression, null);
    }

    protected void visitClassMemberReferenceElement(PsiMember classMember, PsiJavaCodeReferenceElement classMemberReference) {
    }

  }


  private final class MyClassInstanceReferenceVisitor implements ClassInstanceScanner.ClassInstanceReferenceVisitor {
    private final PsiClass myClass;
    private final List myUsageInfoStorage;
    private final Set myImplementedInterfaces;

    public MyClassInstanceReferenceVisitor(PsiClass aClass, List usageInfoStorage) {
      myClass = aClass;
      myUsageInfoStorage = usageInfoStorage;
      myImplementedInterfaces = getImplementedInterfaces();
    }

    public Set getImplementedInterfaces() {
      PsiClass aClass = myClass;
      HashSet result = new HashSet();
      while (aClass != null && !myManager.areElementsEquivalent(aClass, myBaseClass)) {
        final PsiReferenceList referenceList = aClass.getImplementsList();
        if (referenceList != null) {
          final PsiClassType[] implementsList = referenceList.getReferencedTypes();
          for (int i = 0; i < implementsList.length; i++) {
            PsiClassType superType = implementsList[i];
            PsiElement resolved = superType.resolve();
            if (resolved instanceof PsiClass && !myManager.areElementsEquivalent(resolved, myBaseClass)) {
              result.add(resolved);
              RefactoringHierarchyUtil.getSuperClasses((PsiClass) resolved, result, true);
            }
          }
        }
        if (aClass.getExtendsList() != null) {
          final PsiClassType[] extendsList = aClass.getExtendsList().getReferencedTypes();
          aClass = null;
          if (extendsList.length > 0) {
            PsiElement resolved = extendsList[0].resolve();
            if (resolved instanceof PsiClass) {
              aClass = (PsiClass) resolved;
            }
          }
        }
      }
      return result;
    }


    public void visitQualifier(PsiReferenceExpression qualified, PsiExpression instanceRef, PsiElement referencedInstance) {
      final PsiExpression qualifierExpression = qualified.getQualifierExpression();

      // do not add usages inside a class
      if (qualifierExpression == null
              || qualifierExpression instanceof PsiThisExpression
              || qualifierExpression instanceof PsiSuperExpression) {
        return;
      }

      PsiElement resolved = qualified.resolve();
      if (resolved != null && (myBaseClassMembers.contains(resolved) || myOverridenMethods.contains(resolved))
              && !isDelegated(resolved)) {
        myUsageInfoStorage.add(new NonDelegatedMemberUsageInfo(instanceRef, resolved, getFieldAccessibility(instanceRef)));
      }
    }

    public void visitTypeCast(PsiTypeCastExpression typeCastExpression, PsiExpression instanceRef, PsiElement referencedInstance) {
      processTypedUsage(typeCastExpression.getCastType().getType(), instanceRef);
    }


    public void visitReadUsage(PsiExpression instanceRef, PsiType expectedType, PsiElement referencedInstance) {
      processTypedUsage(expectedType, instanceRef);
    }

    public void visitWriteUsage(PsiExpression instanceRef, PsiType assignedType, PsiElement referencedInstance) {
    }

    private void processTypedUsage(PsiType type, PsiExpression instanceRef) {
      if (type == null || type instanceof PsiPrimitiveType) return;
      PsiClass aClass = PsiUtil.resolveClassInType(type);
      String qName = aClass.getQualifiedName();
      if (qName != null && "java.lang.Object".equals(qName)) {
        myUsageInfoStorage.add(new ObjectUpcastedUsageInfo(instanceRef, getFieldAccessibility(instanceRef)));
      } else {
        if (myBaseClassBases.contains(aClass)
                && !myImplementedInterfaces.contains(aClass) && !myDelegatedInterfaces.contains(aClass)) {
          myUsageInfoStorage.add(new UpcastedUsageInfo(instanceRef, aClass, getFieldAccessibility(instanceRef)));
        }
      }
    }
  }
}
