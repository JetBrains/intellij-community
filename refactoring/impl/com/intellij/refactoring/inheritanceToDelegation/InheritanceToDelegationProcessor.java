package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.inheritanceToDelegation.usageInfo.*;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.classMembers.ClassMemberReferencesVisitor;
import com.intellij.refactoring.util.classRefs.ClassInstanceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceScanner;
import com.intellij.refactoring.util.classRefs.ClassReferenceSearchingScanner;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.usages.UsageInfoToUsageConverter;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import com.intellij.codeInsight.generation.GenerateMembersUtil;

import java.util.*;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class InheritanceToDelegationProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.inheritanceToDelegation.InheritanceToDelegationProcessor");
  private final PsiClass myClass;
  private final String myInnerClassName;
  private boolean myIsDelegateOtherMembers;
  private final Set<PsiClass> myDelegatedInterfaces;
  private final Set<PsiMethod> myDelegatedMethods;
  private final HashMap<PsiMethod,String> myDelegatedMethodsVisibility;
  private final Set<PsiMethod> myOverridenMethods;

  private final PsiClass myBaseClass;
  private final Set<PsiMember> myBaseClassMembers;
  private final String myFieldName;
  private final String myGetterName;
  private final boolean myGenerateGetter;
  private Set<PsiClass> myBaseClassBases;
  private Set<PsiClass> myClassImplementedInterfaces;
  private PsiElementFactory myFactory;
  private final PsiClassType myBaseClassType;
  private final PsiManager myManager;
  private final boolean myIsInnerClassNeeded;
  private Set<PsiClass> myClassInheritors;
  private HashSet<PsiMethod> myAbstractDelegatedMethods;
  private Map<PsiClass, PsiSubstitutor> mySuperClassesToSubstitutors = new HashMap<PsiClass, PsiSubstitutor>();


  public InheritanceToDelegationProcessor(Project project,
                                          PsiClass aClass,
                                          PsiClass targetBaseClass,
                                          String fieldName,
                                          String innerClassName,
                                          PsiClass[] delegatedInterfaces,
                                          PsiMethod[] delegatedMethods,
                                          boolean delegateOtherMembers,
                                          boolean generateGetter) {
    super(project);

    myClass = aClass;
    myInnerClassName = innerClassName;
    myIsDelegateOtherMembers = delegateOtherMembers;
    myManager = myClass.getManager();
    myFactory = myManager.getElementFactory();

    myBaseClass = targetBaseClass;
    LOG.assertTrue(
            myBaseClass != null // && !myBaseClass.isInterface()
            && (myBaseClass.getQualifiedName() == null || !myBaseClass.getQualifiedName().equals("java.lang.Object"))
    );
    myBaseClassMembers = getAllBaseClassMembers();
    myBaseClassBases = getAllBases();
    myBaseClassType = myFactory.createType(myBaseClass, getSuperSubstitutor (myBaseClass));

    myIsInnerClassNeeded = InheritanceToDelegationUtil.isInnerClassNeeded(myClass, myBaseClass);


    myFieldName = fieldName;
    final String propertyName = CodeStyleManager.getInstance(myProject).variableNameToPropertyName(myFieldName, VariableKind.FIELD);
    myGetterName = PropertyUtil.suggestGetterName(propertyName, myBaseClassType);
    myGenerateGetter = generateGetter;

    myDelegatedInterfaces = new LinkedHashSet<PsiClass>();
    addAll(myDelegatedInterfaces, delegatedInterfaces);
    myDelegatedMethods = new LinkedHashSet<PsiMethod>();
    addAll(myDelegatedMethods, delegatedMethods);
    myDelegatedMethodsVisibility = new HashMap<PsiMethod, String>();
    for (PsiMethod method : myDelegatedMethods) {
      MethodSignature signature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));
      PsiMethod overridingMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, false);
      if (overridingMethod != null) {
        myDelegatedMethodsVisibility.put(method,
                                         VisibilityUtil.getVisibilityModifier(overridingMethod.getModifierList()));
      }
    }

    myOverridenMethods = getOverriddenMethods();
  }

  private PsiSubstitutor getSuperSubstitutor(final PsiClass superClass) {
    PsiSubstitutor result = mySuperClassesToSubstitutors.get(superClass);
    if (result == null) {
      result = TypeConversionUtil.getSuperClassSubstitutor(superClass, myClass, PsiSubstitutor.EMPTY);
      LOG.assertTrue(result != null);
      mySuperClassesToSubstitutors.put(superClass, result);
    }
    return result;
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new InheritanceToDelegationViewDescriptor(myClass);
  }

  @NotNull
  protected UsageInfo[] findUsages() {
    ArrayList<UsageInfo> usages = new ArrayList<UsageInfo>();
    PsiSearchHelper searchHelper = myManager.getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    final PsiClass[] inheritors = searchHelper.findInheritors(myClass, projectScope, true);
    myClassInheritors = new HashSet<PsiClass>();
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
    for (PsiClass inheritor : inheritors) {
      processClass(inheritor, usages);
    }

    return usages.toArray(new UsageInfo[usages.size()]);
  }

  private FieldAccessibility getFieldAccessibility(PsiElement element) {
    for (PsiClass aClass : myClassInheritors) {
      if (PsiTreeUtil.isAncestor(aClass, element, false)) {
        return new FieldAccessibility(true, aClass);
      }
    }
    return FieldAccessibility.INVISIBLE;
  }

  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    UsageInfo[] usagesIn = refUsages.get();
    ArrayList<UsageInfo> oldUsages = new ArrayList<UsageInfo>();
    addAll(oldUsages, usagesIn);
    final ObjectUpcastedUsageInfo[] objectUpcastedUsageInfos = objectUpcastedUsages(usagesIn);
    if (myPrepareSuccessfulSwingThreadCallback != null) {
      ArrayList<String> conflicts = new ArrayList<String>();
      if (objectUpcastedUsageInfos.length > 0) {
        final String message = RefactoringBundle.message("instances.of.0.upcasted.to.1.were.found",
                                                         ConflictsUtil.getDescription(myClass, true), CommonRefactoringUtil.htmlEmphasize("java.lang.Object"));

        conflicts.add(message);
      }

      analyzeConflicts(usagesIn, conflicts);
      if (!conflicts.isEmpty()) {
        ConflictsDialog conflictsDialog =
                new ConflictsDialog(myProject, conflicts);
        conflictsDialog.show();
        if (!conflictsDialog.isOK()) return false;
      }

      if (objectUpcastedUsageInfos.length > 0) {
        showObjectUpcastedUsageView(objectUpcastedUsageInfos);
        setPreviewUsages(true);
      }
    }
    ArrayList<UsageInfo> filteredUsages = filterUsages(oldUsages);
    refUsages.set(filteredUsages.toArray(new UsageInfo[filteredUsages.size()]));
    prepareSuccessful();
    return true;
  }

  private void analyzeConflicts(UsageInfo[] usage, ArrayList<String> conflicts) {
    HashMap<PsiElement,HashSet<PsiElement>> reportedNonDelegatedUsages = new HashMap<PsiElement, HashSet<PsiElement>>();
    HashMap<PsiClass,HashSet<PsiElement>> reportedUpcasts = new HashMap<PsiClass, HashSet<PsiElement>>();
//    HashSet reportedObjectUpcasts = new HashSet();

//    final String nameJavaLangObject = ConflictsUtil.htmlEmphasize("java.lang.Object");
    final String classDescription = ConflictsUtil.getDescription(myClass, false);

    for (UsageInfo aUsage : usage) {
      final PsiElement element = aUsage.getElement();
      if (aUsage instanceof InheritanceToDelegationUsageInfo) {
        InheritanceToDelegationUsageInfo usageInfo = (InheritanceToDelegationUsageInfo)aUsage;
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
            final PsiElement nonDelegatedMember = ((NonDelegatedMemberUsageInfo)usageInfo).nonDelegatedMember;
            HashSet<PsiElement> reportedContainers = reportedNonDelegatedUsages.get(nonDelegatedMember);
            if (reportedContainers == null) {
              reportedContainers = new HashSet<PsiElement>();
              reportedNonDelegatedUsages.put(nonDelegatedMember, reportedContainers);
            }
            final PsiElement container = ConflictsUtil.getContainer(element);
            if (!reportedContainers.contains(container)) {
              String message = RefactoringBundle.message("0.uses.1.of.an.instance.of.a.2", ConflictsUtil.getDescription(container, true),
                                                         ConflictsUtil.getDescription(nonDelegatedMember, true), classDescription);
              conflicts.add(ConflictsUtil.capitalize(message));
              reportedContainers.add(container);
            }
          }
          else if (usageInfo instanceof UpcastedUsageInfo) {
            final PsiClass upcastedTo = ((UpcastedUsageInfo)usageInfo).upcastedTo;
            HashSet<PsiElement> reportedContainers = reportedUpcasts.get(upcastedTo);
            if (reportedContainers == null) {
              reportedContainers = new HashSet<PsiElement>();
              reportedUpcasts.put(upcastedTo, reportedContainers);
            }
            final PsiElement container = ConflictsUtil.getContainer(element);
            if (container != null && !reportedContainers.contains(container)) {
              String message = RefactoringBundle.message("0.upcasts.an.instance.of.1.to.2",
                                                         ConflictsUtil.getDescription(container, true), classDescription,
                                                         ConflictsUtil.getDescription(upcastedTo, false));
              conflicts.add(ConflictsUtil.capitalize(message));
              reportedContainers.add(container);
            }
          }
        }
      }
      else if (aUsage instanceof NoLongerOverridingSubClassMethodUsageInfo) {
        NoLongerOverridingSubClassMethodUsageInfo info = (NoLongerOverridingSubClassMethodUsageInfo)aUsage;
        String message = RefactoringBundle.message("0.will.no.longer.override.1",
                                                   ConflictsUtil.getDescription(info.getSubClassMethod(), true),
                                                   ConflictsUtil.getDescription(info.getOverridenMethod(), true));
        conflicts.add(message);
      }
    }
  }

  private ObjectUpcastedUsageInfo[] objectUpcastedUsages(UsageInfo[] usages) {
    ArrayList<ObjectUpcastedUsageInfo> result = new ArrayList<ObjectUpcastedUsageInfo>();
    for (UsageInfo usage : usages) {
      if (usage instanceof ObjectUpcastedUsageInfo) {
        result.add(((ObjectUpcastedUsageInfo)usage));
      }
    }
    return result.toArray(new ObjectUpcastedUsageInfo[result.size()]);
  }

  private ArrayList<UsageInfo> filterUsages(ArrayList<UsageInfo> usages) {
    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();

    for (int i = 0; i < usages.size(); i++) {
      UsageInfo usageInfo = usages.get(i);

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

  private void processClass(PsiClass inheritor, ArrayList<UsageInfo> usages) {
    ClassReferenceScanner scanner = new ClassReferenceSearchingScanner(inheritor);
    final MyClassInstanceReferenceVisitor instanceVisitor = new MyClassInstanceReferenceVisitor(inheritor, usages);
    scanner.processReferences(
            new ClassInstanceScanner(inheritor,
                                     instanceVisitor)
    );
    MyClassInheritorMemberReferencesVisitor classMemberVisitor = new MyClassInheritorMemberReferencesVisitor(inheritor, usages, instanceVisitor);
    inheritor.accept(classMemberVisitor);
    PsiSubstitutor inheritorSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(myClass, inheritor, PsiSubstitutor.EMPTY);

    PsiMethod[] methods = inheritor.getMethods();
    for (PsiMethod method : methods) {
      final PsiMethod baseMethod = findSuperMethodInBaseClass(method);

      if (baseMethod != null) {
        if (!baseMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
          usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
        }
        else {
          final PsiMethod[] methodsByName = myClass.findMethodsByName(method.getName(), false);
          for (final PsiMethod classMethod : methodsByName) {
            final MethodSignature signature = classMethod.getSignature(inheritorSubstitutor);
            if (signature.equals(method.getSignature(PsiSubstitutor.EMPTY))) {
              if (!classMethod.hasModifierProperty(PsiModifier.ABSTRACT)) {
                usages.add(new NoLongerOverridingSubClassMethodUsageInfo(method, baseMethod));
                break;
              }
            }
          }
        }
      }
    }
  }

  protected void refreshElements(PsiElement[] elements) {
  }

  protected void performRefactoring(UsageInfo[] usages) {
    try {
      for (UsageInfo aUsage : usages) {
        InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo)aUsage;


        if (usage instanceof UnqualifiedNonDelegatedMemberUsageInfo) {
          delegateUsageFromClass(usage.getElement(), ((NonDelegatedMemberUsageInfo)usage).nonDelegatedMember,
                                 usage.getDelegateFieldAccessible());
        }
        else {
          upcastToDelegation(usage.getElement(), usage.getDelegateFieldAccessible());
        }
      }

      myAbstractDelegatedMethods = new HashSet<PsiMethod>();
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

    List<InnerClassMethod> innerClassMethods = getInnerClassMethods();
    for (InnerClassMethod innerClassMethod : innerClassMethods) {
      innerClassMethod.createMethod(innerClass);
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
          newExpr.replace(myFactory.createExpressionFromText(qualifier + "." + newExpr.getText(), parent));
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
      return method.hasModifierProperty (PsiModifier.STATIC);
    }
    return false;
  }

  private void upcastToDelegation(PsiElement element, FieldAccessibility fieldAccessibility) throws IncorrectOperationException {
    final PsiExpression expression = (PsiExpression) element;

    final PsiExpression newExpr;
    final PsiReferenceExpression ref;
    @NonNls final String delegateQualifier;
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
    for (PsiMethod method : myDelegatedMethods) {
      if (!myAbstractDelegatedMethods.contains(method)) {
        PsiMethod methodToAdd = delegateMethod(myFieldName, method, getSuperSubstitutor(method.getContainingClass()));

        String visibility = myDelegatedMethodsVisibility.get(method);
        if (visibility != null) {
          methodToAdd.getModifierList().setModifierProperty(visibility, true);
        }

        myClass.add(methodToAdd);
      }
    }
  }

  private PsiMethod delegateMethod(@NonNls String delegationTarget,
                                   PsiMethod method,
                                   PsiSubstitutor substitutor) throws IncorrectOperationException {
    substitutor = GenerateMembersUtil.correctSubstitutor(method, substitutor);
    PsiMethod methodToAdd = GenerateMembersUtil.substituteGenericMethod(method, substitutor);

    methodToAdd.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);

    final String delegationBody = getDelegationBody(methodToAdd, delegationTarget);
    PsiCodeBlock newBody = myFactory.createCodeBlockFromText(delegationBody, method);

    PsiCodeBlock oldBody = methodToAdd.getBody();
    if (oldBody != null) {
      oldBody.replace(newBody);
    }
    else {
      methodToAdd.addBefore(newBody, null);
    }

    if (methodToAdd.getDocComment() != null) methodToAdd.getDocComment().delete();
    methodToAdd = (PsiMethod)CodeStyleManager.getInstance(myProject).reformat(methodToAdd);
    return methodToAdd;
  }

  private String getDelegationBody(PsiMethod methodToAdd, String delegationTarget) {
    @NonNls final StringBuffer buffer = new StringBuffer();
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
    return buffer.toString();
  }

  private void addImplementingInterfaces() throws IncorrectOperationException {
    final PsiReferenceList implementsList = myClass.getImplementsList();
    for (PsiClass delegatedInterface : myDelegatedInterfaces) {
      if (!myClassImplementedInterfaces.contains(delegatedInterface)) {
        implementsList.add(myFactory.createClassReferenceElement(delegatedInterface));
      }
    }

    if (!myBaseClass.isInterface()) {
      final PsiReferenceList extendsList = myClass.getExtendsList();
      extendsList.getReferenceElements()[0].delete();
    } else {
      final PsiJavaCodeReferenceElement[] interfaceRefs = implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement interfaceRef : interfaceRefs) {
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
      field.getTypeElement().replace(myFactory.createTypeElement(myBaseClassType));
      if (fieldInitializerNeeded) {
        final PsiJavaCodeReferenceElement classReferenceElement = myFactory.createReferenceElementByType(myBaseClassType);
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
      @NonNls StringBuffer getterBuffer = new StringBuffer();
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

    for (UsageInfo aUsage : usages) {
      InheritanceToDelegationUsageInfo usage = (InheritanceToDelegationUsageInfo)aUsage;
      final FieldAccessibility delegateFieldAccessible = usage.getDelegateFieldAccessible();
      if (delegateFieldAccessible.isAccessible() && delegateFieldAccessible.getContainingClass() != myClass) {
        return PsiModifier.PROTECTED;
      }
    }
    return PsiModifier.PRIVATE;
  }

  private @NonNls String defaultClassFieldType() {
    return (myIsInnerClassNeeded ? myInnerClassName : "Object");
  }

  private PsiField createField(final String fieldVisibility, final boolean fieldInitializerNeeded, String defaultTypeName) throws IncorrectOperationException {
    @NonNls StringBuffer buffer = new StringBuffer();
    buffer.append(fieldVisibility);
    buffer.append(" final " + defaultTypeName + "  ");
    buffer.append(myFieldName);
    if (fieldInitializerNeeded) {
      buffer.append(" = new " + defaultTypeName + "()");
    }
    buffer.append(";");
    return myFactory.createFieldFromText(buffer.toString(), myClass);
  }

  private void fixConstructors() throws IncorrectOperationException {
    if (myBaseClass.isInterface()) return;
    final PsiJavaCodeReferenceElement baseClassReference = myFactory.createClassReferenceElement(myBaseClass);

    PsiMethod[] constructors = myClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      PsiCodeBlock body = constructor.getBody();
      final PsiStatement[] statements = body.getStatements();
      @NonNls String fieldQualifier = "";
      PsiParameter[] constructorParams = constructor.getParameterList().getParameters();
      for (PsiParameter constructorParam : constructorParams) {
        if (myFieldName.equals(constructorParam.getName())) {
          fieldQualifier = "this.";
          break;
        }
      }
      final @NonNls String assignmentText = fieldQualifier + myFieldName + "= new " + defaultClassFieldType() + "()";
      if (statements.length < 1 || !RefactoringUtil.isSuperOrThisCall(statements[0], true, true) || myBaseClass.isInterface()) {
        PsiExpressionStatement assignmentStatement =
          (PsiExpressionStatement)myFactory.createStatementFromText(
            assignmentText, body
          );
        if (!myIsInnerClassNeeded) {
          final PsiAssignmentExpression assignmentExpr = (PsiAssignmentExpression)assignmentStatement.getExpression();
          final PsiNewExpression newExpression = (PsiNewExpression)assignmentExpr.getRExpression();
          assert newExpression != null;
          final PsiJavaCodeReferenceElement classRef = newExpression.getClassReference();
          assert classRef != null;
          classRef.replace(baseClassReference);
        }

        assignmentStatement = (PsiExpressionStatement)CodeStyleManager.getInstance(myProject).reformat(assignmentStatement);
        if (statements.length > 0) {
          if (!RefactoringUtil.isSuperOrThisCall(statements[0], true, false)) {
            body.addBefore(assignmentStatement, statements[0]);
          }
          else {
            body.addAfter(assignmentStatement, statements[0]);
          }
        }
        else {
          body.add(assignmentStatement);
        }
      }
      else {
        final PsiExpressionStatement callStatement = ((PsiExpressionStatement)statements[0]);
        if (!RefactoringUtil.isSuperOrThisCall(callStatement, false, true)) {
          final PsiMethodCallExpression superConstructorCall =
            (PsiMethodCallExpression)callStatement.getExpression();
          PsiAssignmentExpression assignmentExpression =
            (PsiAssignmentExpression)myFactory.createExpressionFromText(
              assignmentText, superConstructorCall
            );
          PsiNewExpression newExpression =
            (PsiNewExpression)assignmentExpression.getRExpression();
          if (!myIsInnerClassNeeded) {
            newExpression.getClassReference().replace(baseClassReference);
          }
          assignmentExpression = (PsiAssignmentExpression)CodeStyleManager.getInstance(myProject).reformat(assignmentExpression);
          newExpression.getArgumentList().replace(superConstructorCall.getArgumentList());
          superConstructorCall.replace(assignmentExpression);
        }
      }
    }
  }

  private boolean isFieldInitializerNeeded() {
    if (myBaseClass.isInterface()) return true;
    PsiMethod[] constructors = myClass.getConstructors();
    for (PsiMethod constructor : constructors) {
      final PsiStatement[] statements = constructor.getBody().getStatements();
      if (statements.length > 0 && RefactoringUtil.isSuperOrThisCall(statements[0], true, false)) return false;
    }
    return true;
  }

  private List<InnerClassMethod> getInnerClassMethods() {
    ArrayList<InnerClassMethod> result = new ArrayList<InnerClassMethod>();

    // find all neccessary constructors
    if (!myBaseClass.isInterface()) {
      PsiMethod[] constructors = myClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiStatement[] statements = constructor.getBody().getStatements();
        if (statements.length > 0 && RefactoringUtil.isSuperOrThisCall(statements[0], true, false)) {
          final PsiMethodCallExpression superConstructorCall =
            (PsiMethodCallExpression)((PsiExpressionStatement)statements[0]).getExpression();
          PsiElement superConstructor = superConstructorCall.getMethodExpression().resolve();
          if (superConstructor instanceof PsiMethod && ((PsiMethod)superConstructor).isConstructor()) {
            result.add(new InnerClassConstructor((PsiMethod)superConstructor));
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

        public void createMethod(PsiClass innerClass)
                throws IncorrectOperationException {
          OverridenMethodClassMemberReferencesVisitor visitor = new OverridenMethodClassMemberReferencesVisitor();
          myMethod.accept(visitor);
          final List<PsiAction> actions = visitor.getPsiActions();
          for (PsiAction action : actions) {
            action.run();
          }
          innerClass.add(myMethod);
          myMethod.delete();
          // myMethod.replace(delegateMethod(myMethod));
        }
      }

      for (PsiMethod method : myOverridenMethods) {
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

        public void createMethod(PsiClass innerClass)
                throws IncorrectOperationException {
          PsiSubstitutor substitutor = getSuperSubstitutor(myMethod.getContainingClass());
          PsiMethod method = delegateMethod(myClass.getName() + ".this", myMethod, substitutor);
          final PsiClass containingClass = myMethod.getContainingClass();
          if (myBaseClass.isInterface() || containingClass.isInterface()) {
            method.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
          }
          innerClass.add(method);
          final MethodSignature signature = myMethod.getSignature(substitutor);
          PsiMethod outerMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, false);
          if (outerMethod == null) {
            final String visibility = checkOuterClassAbstractMethod(signature);
            PsiMethod newOuterMethod = (PsiMethod)myClass.add(myMethod);
            newOuterMethod.getModifierList().setModifierProperty(visibility, true);
            if (newOuterMethod.getDocComment() != null) {
              newOuterMethod.getDocComment().delete();
            }
          }
        }

      }
      PsiMethod[] methods = myBaseClass.getAllMethods();

      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final MethodSignature signature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));
          PsiMethod classMethod = MethodSignatureUtil.findMethodBySignature(myClass, signature, true);
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
    presentation.setTargetsNodeText(RefactoringBundle.message("replacing.inheritance.with.delegation"));
    presentation.setCodeUsagesString(RefactoringBundle.message("instances.casted.to.java.lang.object"));
    final String upcastedString = RefactoringBundle.message("instances.upcasted.to.object");
    presentation.setUsagesString(upcastedString);
    presentation.setTabText(upcastedString);

    UsageViewManager manager = myProject.getComponent(UsageViewManager.class);
    manager.showUsages(
      new UsageTarget[]{new PsiElement2UsageTargetAdapter(myClass)},
      UsageInfoToUsageConverter.convert(new UsageInfoToUsageConverter.TargetElementsDescriptor(myClass), usages),
      presentation
    );

    WindowManager.getInstance().getStatusBar(myProject).setInfo(RefactoringBundle.message("instances.upcasted.to.java.lang.object.found"));
  }

  /**
   *
   * @param methodSignature
   * @return Visibility
   */
  private String checkOuterClassAbstractMethod(MethodSignature methodSignature) {
    String visibility = PsiModifier.PROTECTED;
    for (PsiMethod method : myDelegatedMethods) {
      MethodSignature otherSignature = method.getSignature(getSuperSubstitutor(method.getContainingClass()));

      if (MethodSignatureUtil.areSignaturesEqual(otherSignature, methodSignature)) {
        visibility = VisibilityUtil.getHighestVisibility(visibility,
                                                         VisibilityUtil.getVisibilityModifier(method.getModifierList()));
        myAbstractDelegatedMethods.add(method);
      }
    }
    return visibility;
  }

  private Set<PsiMethod> getOverriddenMethods() {
    LinkedHashSet<PsiMethod> result = new LinkedHashSet<PsiMethod>();

    PsiMethod[] methods = myClass.getMethods();
    for (PsiMethod method : methods) {
      if (findSuperMethodInBaseClass(method) != null) result.add(method);
    }
    return result;
  }

  private PsiMethod findSuperMethodInBaseClass (PsiMethod method) {
    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      PsiClass containingClass = superMethod.getContainingClass();
      if (InheritanceUtil.isInheritorOrSelf(myBaseClass, containingClass, true)) {
        String qName = containingClass.getQualifiedName();
        if (qName == null || !"java.lang.Object".equals(qName)) {
          return superMethod;
        }
      }
    }
    return null;
  }


  protected String getCommandName() {
    return RefactoringBundle.message("replace.inheritance.with.delegation.command", UsageViewUtil.getDescriptiveName(myClass));
  }

  private Set<PsiMember> getAllBaseClassMembers() {
    HashSet<PsiMember> result = new HashSet<PsiMember>();
    addAll(result, myBaseClass.getAllFields());
    addAll(result, myBaseClass.getAllInnerClasses());
    addAll(result, myBaseClass.getAllMethods());

    //remove java.lang.Object members
    for (Iterator<PsiMember> iterator = result.iterator(); iterator.hasNext();) {
      PsiMember member = iterator.next();
      if ("java.lang.Object".equals(member.getContainingClass().getQualifiedName())) {
        iterator.remove();
      }
    }
    return Collections.unmodifiableSet(result);
  }

  private Set<PsiClass> getAllBases() {
    HashSet<PsiClass> temp = new HashSet<PsiClass>();
    RefactoringHierarchyUtil.getSuperClasses(myBaseClass, temp, true);
    temp.add(myBaseClass);
    return Collections.unmodifiableSet(temp);
  }

  private static <T> void addAll(Collection<T> collection, T[] objs) {
    for (T obj : objs) {
      collection.add(obj);
    }
  }

  private boolean isDelegated(PsiMember classMember) {
    if(!(classMember instanceof PsiMethod)) return false;
    final PsiMethod method = (PsiMethod) classMember;
    for (PsiMethod delegatedMethod : myDelegatedMethods) {
      //methods reside in base class, so no substitutor needed
      if (MethodSignatureUtil.areSignaturesEqual(method.getSignature(PsiSubstitutor.EMPTY),
                                                 delegatedMethod.getSignature(PsiSubstitutor.EMPTY))) {
        return true;
      }
    }
    return false;
  }

  private class MyClassInheritorMemberReferencesVisitor extends ClassMemberReferencesVisitor {
    private final List<UsageInfo> myUsageInfoStorage;
    private ClassInstanceScanner.ClassInstanceReferenceVisitor myInstanceVisitor;

    MyClassInheritorMemberReferencesVisitor(PsiClass aClass, List<UsageInfo> usageInfoStorage,
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
    MyClassMemberReferencesVisitor(List<UsageInfo> usageInfoStorage,
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
    private final ArrayList<PsiAction> myPsiActions;
    private final PsiThisExpression myQualifiedThis;

    OverridenMethodClassMemberReferencesVisitor() throws IncorrectOperationException {
      super(myClass);
      myPsiActions = new ArrayList<PsiAction>();
      final PsiJavaCodeReferenceElement classReferenceElement = myFactory.createClassReferenceElement(myClass);
      myQualifiedThis = (PsiThisExpression) myFactory.createExpressionFromText("A.this", null);
      myQualifiedThis.getQualifier().replace(classReferenceElement);
    }

    public List<PsiAction> getPsiActions() {
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
          final PsiMethod baseMethod = findSuperMethodInBaseClass(method);
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
    private final List<UsageInfo> myUsageInfoStorage;
    private final Set<PsiClass> myImplementedInterfaces;

    public MyClassInstanceReferenceVisitor(PsiClass aClass, List<UsageInfo> usageInfoStorage) {
      myClass = aClass;
      myUsageInfoStorage = usageInfoStorage;
      myImplementedInterfaces = getImplementedInterfaces();
    }

    public Set<PsiClass> getImplementedInterfaces() {
      PsiClass aClass = myClass;
      HashSet<PsiClass> result = new HashSet<PsiClass>();
      while (aClass != null && !myManager.areElementsEquivalent(aClass, myBaseClass)) {
        final PsiClassType[] implementsTypes = aClass.getImplementsListTypes();
        for (PsiClassType implementsType : implementsTypes) {
          PsiClass resolved = implementsType.resolve();
          if (resolved != null && !myManager.areElementsEquivalent(resolved, myBaseClass)) {
            result.add(resolved);
            RefactoringHierarchyUtil.getSuperClasses(resolved, result, true);
          }
        }

        aClass = aClass.getSuperClass();
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
          && !isDelegated((PsiMember)resolved)) {
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
      PsiClass aClass = PsiUtil.resolveClassInType(type);
      if (aClass == null) return;
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
