/**
 * created at Sep 17, 2001
 * @author Jeka
 */
package com.intellij.refactoring.changeSignature;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbDeclMethodRole;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.scope.processor.VariablesProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.*;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.*;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorImplicitUsageInfo;
import com.intellij.refactoring.util.usageInfo.DefaultConstructorUsageCollector;
import com.intellij.refactoring.util.usageInfo.NoConstructorClassUsageInfo;
import com.intellij.usageView.FindUsagesCommand;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashSet;

import java.util.*;

public class ChangeSignatureProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ChangeSignatureProcessor");

  private final String myNewVisibility;
  private String myNewName;
  private CanonicalTypes.Type myNewType;
  private ParameterInfo[] myParameterInfo;
  private boolean myToPreviewUsages;
  private ChangeInfo myChangeInfo;
  private PsiManager myManager;
  private PsiElementFactory myFactory;
  private static final Class[] NORMALIZED_RESOLUTION_CONTEXT_CLASSES =
          new Class[]{PsiStatement.class, PsiClass.class, PsiFile.class};
  private HashSet<PsiMethod> myMethodsToBeChanged;
  private final boolean myGenerateDelegate;

  public ChangeSignatureProcessor(Project project, PsiMethod method,
                                  final boolean generateDelegate, String newVisibility,
                                  String newName, PsiType newType,
                                  ParameterInfo[] parameterInfo, boolean toPreviewUsages,
                                  Runnable prepareSuccessfulCallback) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, null, toPreviewUsages, prepareSuccessfulCallback);
  }

  public ChangeSignatureProcessor(Project project, PsiMethod method,
                                  final boolean generateDelegate, String newVisibility,
                                  String newName, PsiType newType,
                                  ParameterInfo[] parameterInfo,
                                  ThrownExceptionInfo[] exceptionInfos,
                                  boolean toPreviewUsages,
                                  Runnable prepareSuccessfulCallback) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, exceptionInfos, toPreviewUsages, prepareSuccessfulCallback);
  }

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  boolean generateDelegate,
                                  String newVisibility,
                                  String newName,
                                  CanonicalTypes.Type newType,
                                  ParameterInfo[] parameterInfo,
                                  ThrownExceptionInfo[] thrownExceptions,
                                  boolean toPreviewUsages,
                                  Runnable prepareSuccessfulCallback) {
    super(project, prepareSuccessfulCallback);
    myGenerateDelegate = generateDelegate;
    LOG.assertTrue(method.isValid());
    if (newVisibility == null) {
      myNewVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    } else {
      myNewVisibility = newVisibility;
    }
    myNewName = newName;
    myNewType = newType;
    myParameterInfo = parameterInfo;
    myToPreviewUsages = toPreviewUsages;

    myChangeInfo = new ChangeInfo(myNewVisibility, method, myNewName, myNewType, myParameterInfo, thrownExceptions);
    LOG.assertTrue(myChangeInfo.getMethod().isValid());
  }

  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages, FindUsagesCommand refreshCommand) {
    return new ChangeSignatureViewDescriptor(myChangeInfo.getMethod(), usages, refreshCommand);
  }

  protected UsageInfo[] findUsages() {
    myManager = PsiManager.getInstance(myProject);
    myFactory = myManager.getElementFactory();

    ArrayList<UsageInfo> result = new ArrayList<UsageInfo>();
    final PsiMethod method = myChangeInfo.getMethod();

    findSimpleUsages(method, result);
    findEjbUsages(result);


    final UsageInfo[] usageInfos = result.toArray(new UsageInfo[result.size()]);
    return UsageViewUtil.removeDuplicatedUsages(usageInfos);
  }

  private void findSimpleUsages(final PsiMethod method, final ArrayList<UsageInfo> result) {
    PsiManager manager = method.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();

    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(myProject);
    PsiMethod[] overridingMethods = helper.findOverridingMethods(method, projectScope, true);

    for (int i = 0; i < overridingMethods.length; i++) {
      PsiMethod overridingMethod = overridingMethods[i];
        result.add(new UsageInfo(overridingMethod));
    }

    boolean needToChangeCalls = !myGenerateDelegate && (needToChangeCalls() || myChangeInfo.isVisibilityChanged/*for checking inaccessible*/);
    if (needToChangeCalls) {
      List<PsiElement> l = new ArrayList<PsiElement>();
      PsiReference[] refs = helper.findReferencesIncludingOverriding(method, projectScope, true);
      for (int i = 0; i < refs.length; i++) {
        PsiReference reference = refs[i];
        l.add(reference.getElement());
      }

      int parameterCount = method.getParameterList().getParameters().length;
      for (Iterator<PsiElement> iterator = l.iterator(); iterator.hasNext();) {
        PsiElement ref = iterator.next();
        if (myChangeInfo.isParameterSetOrOrderChanged) {
          if (RefactoringUtil.isMethodUsage(ref)) {
            PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference((PsiJavaCodeReferenceElement)ref);
            if (!method.isVarArgs() && list.getExpressions().length != parameterCount) continue;
          }
        }
        result.add(new UsageInfo(ref));
      }

      if (method.isConstructor() && parameterCount == 0) {
          RefactoringUtil.visitImplicitConstructorUsages(method.getContainingClass(),
                                                         new DefaultConstructorUsageCollector(result));
      }
    } else if (myChangeInfo.isParameterTypesChanged) {
      PsiReference[] refs = helper.findReferencesIncludingOverriding(method, projectScope, true);
      for (int i = 0; i < refs.length; i++) {
        PsiReference reference = refs[i];
        if (reference.getElement() instanceof PsiDocTagValue) { //types are mentioned in e.g @link, see SCR 40895
          result.add(new UsageInfo(reference.getElement()));
        }
      }
    }

    findParametersUsage(method, result, overridingMethods);

    // Conflicts
    detectLocalsCollisionsInMethod(method, result);
    for (int j = 0; j < overridingMethods.length; j++) {
      final PsiMethod overridingMethod = overridingMethods[j];
      detectLocalsCollisionsInMethod(overridingMethod, result);
    }
  }

  private boolean needToChangeCalls() {
    return myChangeInfo.isNameChanged || myChangeInfo.isParameterSetOrOrderChanged || myChangeInfo.isExceptionSetChanged;
  }

  private void detectLocalsCollisionsInMethod(final PsiMethod method,
                                              final ArrayList<UsageInfo> result) {
    final PsiParameter[] overridingParameters = method.getParameterList().getParameters();
    final Set<PsiParameter> deletedParameters =
      new HashSet<PsiParameter>(Arrays.asList(overridingParameters));

    for (int i = 0; i < myParameterInfo.length; i++) {
      ParameterInfo parameterInfo = myParameterInfo[i];
      if (parameterInfo.oldParameterIndex >= 0 &&
          parameterInfo.getName().equals(overridingParameters[parameterInfo.oldParameterIndex].getName())) {
        deletedParameters.remove(overridingParameters[parameterInfo.oldParameterIndex]);
      }
    }
    for (int i = 0; i < myParameterInfo.length; i++) {
      ParameterInfo parameterInfo = myParameterInfo[i];
      final int oldParameterIndex = parameterInfo.oldParameterIndex;
      final String newName = parameterInfo.getName();
      if (oldParameterIndex >= 0) {
        final PsiParameter parameter = overridingParameters[oldParameterIndex];
        if (!newName.equals(parameter.getName())) {
          RenameUtil.visitLocalsCollisions(parameter, newName, method.getBody(), null, new RenameUtil.CollidingVariableVisitor() {
            public void visitCollidingElement(final PsiVariable collidingVariable) {
              if (!(collidingVariable instanceof PsiField) && !deletedParameters.contains(collidingVariable)) {
                result.add(new RenamedParameterCollidesWithLocalUsageInfo(parameter, collidingVariable, method));
              }
            }
          });
        }
      } else {
        RenameUtil.visitLocalsCollisions(method, newName, method.getBody(), null, new RenameUtil.CollidingVariableVisitor() {
          public void visitCollidingElement(PsiVariable collidingVariable) {
            if (!(collidingVariable instanceof PsiField) && !deletedParameters.contains(collidingVariable)) {
              result.add(new NewParameterCollidesWithLocalUsageInfo(collidingVariable, collidingVariable, method));
            }
          }
        });
      }
    }
  }

  private void findParametersUsage(final PsiMethod method, ArrayList<UsageInfo> result, PsiMethod[] overriders) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < myChangeInfo.newParms.length; i++) {
      ParameterInfo info = myChangeInfo.newParms[i];
      if (info.oldParameterIndex >= 0) {
        PsiParameter parameter = parameters[info.oldParameterIndex];
        if (!info.getName().equals(parameter.getName())) {
          addParameterUsages(parameter, result, info);

          for (int j = 0; j < overriders.length; j++) {
            PsiParameter parameter1 = overriders[j].getParameterList().getParameters()[info.oldParameterIndex];
            if (parameter.getName().equals(parameter1.getName())) {
              addParameterUsages(parameter1, result, info);
            }
          }
        }
      }
    }
  }

  private void findEjbUsages(ArrayList<UsageInfo> result) {
    if (myChangeInfo.ejbRole == null) return;
    if (!(myChangeInfo.ejbRole instanceof EjbDeclMethodRole)) return;

    final PsiMethod[] implementations = EjbUtil.findEjbImplementations(myChangeInfo.getMethod());
    for (int i = 0; i < implementations.length; i++) {
      PsiMethod implementation = implementations[i];
      result.add(new EjbUsageInfo(implementation));
      findSimpleUsages(implementation, result);
    }
  }

  protected void refreshElements(PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    myChangeInfo.updateMethod((PsiMethod) elements[0]);
  }

  protected boolean isPreviewUsages(UsageInfo[] usages) {
    return super.isPreviewUsages(usages) || myToPreviewUsages;
  }

  protected boolean preprocessUsages(UsageInfo[][] usages) {
    Set<String> conflictDescriptions = new HashSet<String>();
    conflictDescriptions.addAll(Arrays.asList(RenameUtil.getConflictDescriptions(usages[0])));
    Set<UsageInfo> usagesSet = new HashSet<UsageInfo>(Arrays.asList(usages[0]));
    RenameUtil.removeConflictUsages(usagesSet);
    if (myChangeInfo.isVisibilityChanged) {
      try {
        addInaccessibilityDescriptions(usagesSet, conflictDescriptions);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    if (myPrepareSuccessfulSwingThreadCallback != null && conflictDescriptions.size() > 0) {
      ConflictsDialog dialog = new ConflictsDialog(conflictDescriptions.toArray(new String[conflictDescriptions.size()]), myProject);
      dialog.show();
      if (!dialog.isOK()) return false;
    }

    if (myChangeInfo.isReturnTypeChanged) {
      askToRemoveCovariantOverriders (usagesSet);
    }

    usages[0] = usagesSet.toArray(new UsageInfo[usagesSet.size()]);
    prepareSuccessful();
    return true;
  }

  private void addInaccessibilityDescriptions(Set<UsageInfo> usages, Set<String> conflictDescriptions) throws IncorrectOperationException {
    PsiMethod method = myChangeInfo.getMethod();
    PsiModifierList modifierList = (PsiModifierList)method.getModifierList().copy();
    RefactoringUtil.setVisibility(modifierList, myNewVisibility);

    for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
      UsageInfo usageInfo = iterator.next();
      PsiElement element = usageInfo.getElement();
      if (element != null) {
        PsiClass accessObjectClass = null;
        if (element instanceof PsiReferenceExpression) {
          PsiExpression qualifier = ((PsiReferenceExpression)element).getQualifierExpression();
          if (qualifier != null) {
            accessObjectClass = (PsiClass)PsiUtil.getAccessObjectClass(qualifier).getElement();
          }

          if (!element.getManager().getResolveHelper().isAccessible(method, modifierList, element, accessObjectClass)) {
            String message =
              ConflictsUtil.getDescription(method, true) + " with " + myNewVisibility + " visibility is not accesible from " +
              ConflictsUtil.getDescription(ConflictsUtil.getContainer(element), true);
            conflictDescriptions.add(message);
            if (!needToChangeCalls()) {
              iterator.remove();
            }
          }
        }
      }
    }
  }

  private void askToRemoveCovariantOverriders(Set<UsageInfo> usages) {
    if (myManager.getEffectiveLanguageLevel().compareTo(LanguageLevel.JDK_1_5) >= 0) {
      List<UsageInfo> covariantOverriderInfos = new ArrayList<UsageInfo>();
      PsiSubstitutor substitutor = calculateSubstitutor(myChangeInfo.getMethod());
      PsiType type;
      try {
        type = substitutor.substitute(myChangeInfo.newReturnType.getType(myChangeInfo.getMethod()));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return;
      }

      for (Iterator<UsageInfo> iterator = usages.iterator(); iterator.hasNext();) {
        UsageInfo usageInfo = iterator.next();
        if (usageInfo.getElement() instanceof PsiMethod) {
          PsiMethod overrider = (PsiMethod)usageInfo.getElement();
          if (type.isAssignableFrom(overrider.getReturnType())) {
            covariantOverriderInfos.add(usageInfo);
          }
        }
      }

      if (covariantOverriderInfos.size() > 0) {
        if (ApplicationManager.getApplication().isUnitTestMode() ||
        Messages.showYesNoDialog(myProject, "Do you want to process overriding methods\n" +
                                            "with covariant return type?",
                                 "Change Method Signature", Messages.getQuestionIcon())
        != DialogWrapper.OK_EXIT_CODE) {
          for (Iterator<UsageInfo> iterator = covariantOverriderInfos.iterator(); iterator.hasNext();) {
            usages.remove(iterator.next());
          }
        }
      }
    }
  }

  protected void performRefactoring(UsageInfo[] usages) {
    PsiElementFactory factory = myManager.getElementFactory();


    List<UsageInfo> postponedUsages = new ArrayList<UsageInfo>();

    try {
      if (myChangeInfo.isNameChanged) {
        myChangeInfo.newNameIdentifier = factory.createIdentifier(myChangeInfo.newName);
      }

      if (myChangeInfo.isReturnTypeChanged) {
        myChangeInfo.newTypeElement = myChangeInfo.newReturnType.getType(myChangeInfo.getMethod());
      }

      myMethodsToBeChanged = new HashSet<PsiMethod>();

      if (myGenerateDelegate) {
        generateDelegate();
      }

      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
        if (!(usage instanceof DefaultConstructorImplicitUsageInfo) &&  usage.getElement() instanceof PsiMethod) {
          myMethodsToBeChanged.add((PsiMethod) usage.getElement());
        }
      }

      if (myChangeInfo.isExceptionSetOrOrderChanged) {
          fixThrowsLists(getChangedExceptionInfo(myChangeInfo));
      }

      for (int i = 0; i < usages.length; i++) {
        UsageInfo usage = usages[i];
        if (!usage.getElement().isValid()) continue;

        if (usage instanceof DefaultConstructorImplicitUsageInfo) {
          addSuperCall(((DefaultConstructorImplicitUsageInfo) usage).getConstructor());
        } else if (usage instanceof NoConstructorClassUsageInfo) {
          addDefaultConstructor(((NoConstructorClassUsageInfo) usage).getPsiClass());
        } else if (usage.getElement() instanceof PsiMethod) {
          processMethod((PsiMethod) usage.getElement(), false);
        } else if (usage.getElement() instanceof PsiJavaCodeReferenceElement) {
          if (RefactoringUtil.isMethodUsage(usage.getElement())) {
            processMethodUsage((PsiJavaCodeReferenceElement) usage.getElement(), myChangeInfo);
          } else {
            String newName = ((MyParameterUsageInfo) usage).newParameterName;
            String oldName = ((MyParameterUsageInfo) usage).oldParameterName;
            processParameterUsage((PsiReferenceExpression) usage.getElement(), oldName, newName);
          }
        } else if (usage.getElement() instanceof PsiEnumConstant) {
          fixActualArgumentsList(((PsiEnumConstant)usage.getElement()).getArgumentList(), myChangeInfo, false);
        } else {
          postponedUsages.add(usage);
        }
      }

      LOG.assertTrue(myChangeInfo.getMethod().isValid());

      processMethod(myChangeInfo.getMethod(), true);

      for (Iterator<UsageInfo> i = postponedUsages.iterator(); i.hasNext();) {
        UsageInfo usageInfo = i.next();
        PsiReference reference = usageInfo.getElement().getReference();
        if (reference != null) {
          PsiElement target = null;
          if (usageInfo instanceof MyParameterUsageInfo) {
            String newParameterName = ((MyParameterUsageInfo) usageInfo).newParameterName;
            PsiParameter[] newParams = myChangeInfo.getMethod().getParameterList().getParameters();
            for (int j = 0; j < newParams.length; j++) {
              PsiParameter newParam = newParams[j];
              if (newParam.getName().equals(newParameterName)) {
                target = newParam;
                break;
              }
            }
          } else {
            target = myChangeInfo.getMethod();
          }
          if (target != null) {
            reference.bindToElement(target);
          }
        }
      }

      fixJavadocsForChangedMethod(myChangeInfo.getMethod());
      for (Iterator<PsiMethod> iterator = myMethodsToBeChanged.iterator(); iterator.hasNext();) {
        PsiMethod method = iterator.next();
        fixJavadocsForChangedMethod(method);
      }
      LOG.assertTrue(myChangeInfo.getMethod().isValid());
    } catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  private void generateDelegate() throws IncorrectOperationException {
    final PsiMethod delegate = (PsiMethod)myChangeInfo.getMethod().copy();
    final PsiClass targetClass = myChangeInfo.getMethod().getContainingClass();
    LOG.assertTrue(!targetClass.isInterface());
    makeEmptyBody(delegate);
    final PsiCallExpression callExpression = addDelegatingCallTemplate(delegate);
    addDelegateArguments(callExpression);
    targetClass.addBefore(delegate, myChangeInfo.getMethod());
  }

  private void addDelegateArguments(final PsiCallExpression callExpression) throws IncorrectOperationException {
    final ParameterInfo[] newParms = myChangeInfo.newParms;
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfo newParm = newParms[i];
      final PsiExpression actualArg;
      if (newParm.oldParameterIndex >= 0) {
        actualArg = myFactory.createExpressionFromText(myChangeInfo.oldParameterNames[newParm.oldParameterIndex], callExpression);
      }
      else {
        actualArg = myChangeInfo.defaultValues[i];
      }
      callExpression.getArgumentList().add(actualArg);
    }
  }

  private void makeEmptyBody(final PsiMethod delegate) throws IncorrectOperationException {
    if (delegate.getBody() != null) {
      delegate.getBody().replace(myFactory.createCodeBlock());
    }
    else {
      delegate.add(myFactory.createCodeBlock());
    }
    delegate.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);
  }

  private PsiCallExpression addDelegatingCallTemplate(final PsiMethod delegate) throws IncorrectOperationException {
    final PsiCallExpression callExpression;
    if (delegate.isConstructor()) {
      PsiElement callStatement = myFactory.createStatementFromText("this();", null);
      callStatement = CodeStyleManager.getInstance(myProject).reformat(callStatement);
      callStatement = delegate.getBody().add(callStatement);
      callExpression = (PsiCallExpression)((PsiExpressionStatement) callStatement).getExpression();
    } else {
      if (PsiType.VOID.equals(delegate.getReturnType())) {
        PsiElement callStatement = myFactory.createStatementFromText(myChangeInfo.newName + "();", null);
        callStatement = CodeStyleManager.getInstance(myProject).reformat(callStatement);
        callStatement = delegate.getBody().add(callStatement);
        callExpression = (PsiCallExpression)((PsiExpressionStatement) callStatement).getExpression();
      }
      else {
        PsiElement callStatement = myFactory.createStatementFromText("return " + myChangeInfo.newName + "();", null);
        callStatement = CodeStyleManager.getInstance(myProject).reformat(callStatement);
        callStatement = delegate.getBody().add(callStatement);
        callExpression = (PsiCallExpression)((PsiReturnStatement) callStatement).getReturnValue();
      }
    }
    return callExpression;
  }

  private void addDefaultConstructor(PsiClass aClass) throws IncorrectOperationException {
    if (!(aClass instanceof PsiAnonymousClass)) {
      PsiMethod defaultConstructor = myFactory.createMethodFromText(aClass.getName() + "(){}", aClass);
      defaultConstructor = (PsiMethod) CodeStyleManager.getInstance(myProject).reformat(defaultConstructor);
      defaultConstructor = (PsiMethod) aClass.add(defaultConstructor);
      defaultConstructor.getModifierList().setModifierProperty(VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
      addSuperCall(defaultConstructor);
    } else {
      final PsiElement parent = aClass.getParent();
      if (parent instanceof PsiNewExpression) {
        final PsiExpressionList argumentList = ((PsiNewExpression) parent).getArgumentList();
        fixActualArgumentsList(argumentList, myChangeInfo, false);
      }
    }
  }

  private void addSuperCall(PsiMethod constructor) throws IncorrectOperationException {
    PsiExpressionStatement superCall = (PsiExpressionStatement) myFactory.createStatementFromText("super();", constructor);
    PsiStatement[] statements = constructor.getBody().getStatements();
    if (statements.length > 0) {
      superCall = (PsiExpressionStatement) constructor.getBody().addBefore(superCall, statements[0]);
    } else {
      superCall = (PsiExpressionStatement) constructor.getBody().add(superCall);
    }
    PsiMethodCallExpression callExpression = (PsiMethodCallExpression) superCall.getExpression();
    processMethodUsage(callExpression.getMethodExpression(), myChangeInfo);
  }

  private PsiParameter createNewParameter(ParameterInfo newParm, PsiSubstitutor substitutor) throws IncorrectOperationException {
    final PsiElementFactory factory = PsiManager.getInstance(myProject).getElementFactory();
    final PsiParameter newParameter = factory.createParameterFromText("X " + newParm.getName(), null);
    final PsiType type = substitutor.substitute(newParm.createType(myChangeInfo.getMethod().getParameterList()));
    newParameter.getTypeElement().replace(factory.createTypeElement(type));
    return newParameter;
  }

  protected String getCommandName() {
    return "Changing signature of " + UsageViewUtil.getDescriptiveName(myChangeInfo.getMethod());
  }

  private void processMethodUsage(PsiJavaCodeReferenceElement ref, ChangeInfo changeInfo)
          throws IncorrectOperationException {

    if (changeInfo.isNameChanged) {
      PsiElement last = ref.getReferenceNameElement();
      if (last instanceof PsiIdentifier && last.getText().equals(changeInfo.oldName)) {
        last.replace(changeInfo.newNameIdentifier);
      }
    }
    final PsiExpressionList list = RefactoringUtil.getArgumentListByMethodReference(ref);

    boolean isSuperCall = false;
    if (ref instanceof PsiReferenceExpression) {
      final PsiExpression qualifierExpression = ((PsiReferenceExpression) ref).getQualifierExpression();
      if (qualifierExpression instanceof PsiSuperExpression) {

        for (Iterator iterator = myMethodsToBeChanged.iterator(); iterator.hasNext();) {
          PsiElement element = (PsiElement) iterator.next();
          if (PsiTreeUtil.isAncestor(element, ref, false)) {
            isSuperCall = true;
            break;
          }
        }
      }
    }

    fixActualArgumentsList(list, changeInfo, isSuperCall);

    if (changeInfo.isExceptionSetChanged) {
      PsiClassType[] newExceptions = getChangedExceptionInfo(changeInfo);
      fixExceptions(ref, newExceptions);
    }
  }

  private PsiClassType[] getChangedExceptionInfo(ChangeInfo changeInfo) throws IncorrectOperationException {
    PsiClassType[] newExceptions = new PsiClassType[changeInfo.newExceptions.length];
    for (int i = 0; i < newExceptions.length; i++) {
      newExceptions[i] = (PsiClassType)changeInfo.newExceptions[i].myType.getType(myChangeInfo.getMethod()); //context really does not matter here
    }
    return newExceptions;
  }

  private void fixExceptions(PsiJavaCodeReferenceElement ref, PsiClassType[] newExceptions) throws IncorrectOperationException {

    fixThrowsLists(newExceptions);

    newExceptions = filterCheckedExceptions(newExceptions);
    //Now that methods' throws lists are modified, may use ExceptionUtil.collectUnhandledExceptions

    PsiElement context = PsiTreeUtil.getParentOfType(ref, new Class[]{PsiTryStatement.class, PsiMethod.class});
    if (context instanceof PsiTryStatement) {
      PsiTryStatement tryStatement = (PsiTryStatement)context;
      PsiCodeBlock tryBlock = tryStatement.getTryBlock();

      //Remove unused catches
      PsiClassType[] classes = ExceptionUtil.collectUnhandledExceptions(tryBlock, tryBlock);
      PsiParameter[] catchParameters = tryStatement.getCatchBlockParameters();
      for (int i = 0; i < catchParameters.length; i++) {
        PsiParameter parameter = catchParameters[i];
        final PsiType caughtType = parameter.getType();

        if (!(caughtType instanceof PsiClassType)) continue;
        if (ExceptionUtil.isUncheckedExceptionOrSuperclass((PsiClassType)caughtType)) continue;

        if (!isCatchParameterRedundant((PsiClassType)caughtType, classes)) continue;
        parameter.getParent().delete(); //delete catch section
      }

      PsiClassType[] exceptionsToAdd  = filterUnhandledExceptions(newExceptions, tryBlock);
      addExceptions(exceptionsToAdd, tryStatement);

      adjustPossibleEmptyTryStatement(tryStatement);
    }
    else {
      newExceptions = filterUnhandledExceptions(newExceptions, ref);
      if (newExceptions.length > 0) {
        //Add new try statement
        PsiElementFactory elementFactory = myManager.getElementFactory();
        PsiTryStatement tryStatement = (PsiTryStatement)elementFactory.createStatementFromText("try {} catch (Exception e) {}", null);
        PsiStatement anchor = PsiTreeUtil.getParentOfType(ref, PsiStatement.class);
        LOG.assertTrue(anchor != null);
        tryStatement.getTryBlock().add(anchor);
        tryStatement = (PsiTryStatement)anchor.getParent().addAfter(tryStatement, anchor);

        addExceptions(newExceptions, tryStatement);
        anchor.delete();
        tryStatement.getCatchSections()[0].delete(); //Delete dummy catch section
      }
    }
  }

  private PsiClassType[] filterCheckedExceptions(PsiClassType[] exceptions) {
    List<PsiClassType> result = new ArrayList<PsiClassType>();
    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exceptionType = exceptions[i];
      if (!ExceptionUtil.isUncheckedException(exceptionType)) result.add(exceptionType);
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  private void adjustPossibleEmptyTryStatement(PsiTryStatement tryStatement) throws IncorrectOperationException {
    PsiCodeBlock tryBlock = tryStatement.getTryBlock();
    if (tryBlock != null) {
      if (tryStatement.getCatchSections().length == 0 &&
          tryStatement.getFinallyBlock() == null) {
        if (tryBlock.getLBrace() != null && tryBlock.getRBrace() != null) {
          tryStatement.getParent().addRangeAfter(tryBlock.getLBrace().getNextSibling(), tryBlock.getRBrace().getPrevSibling(), tryStatement);
          tryStatement.delete();
        }
      }
    }
  }

  private void addExceptions(PsiClassType[] exceptionsToAdd, PsiTryStatement tryStatement) throws IncorrectOperationException {
    for (int i = 0; i < exceptionsToAdd.length; i++) {
      PsiClassType type = exceptionsToAdd[i];
      final CodeStyleManager styleManager = tryStatement.getManager().getCodeStyleManager();
      String name = styleManager.suggestVariableName(VariableKind.PARAMETER, null, null, type).names[0];
      name = styleManager.suggestUniqueVariableName(name, tryStatement, false);

      PsiCatchSection catchSection = tryStatement.getManager().getElementFactory().createCatchSection(type, name, tryStatement);
      tryStatement.add(catchSection);
    }
  }

  private void fixThrowsLists(PsiClassType[] newExceptions) throws IncorrectOperationException {
    PsiElementFactory elementFactory = myManager.getElementFactory();
    PsiJavaCodeReferenceElement[] refs = new PsiJavaCodeReferenceElement[newExceptions.length];
    for (int i = 0; i < refs.length; i++) {
      refs[i] = elementFactory.createReferenceElementByType(newExceptions[i]);
    }
    PsiReferenceList throwsList = elementFactory.createReferenceList(refs);

    replaceThrowsList(myChangeInfo.getMethod(), throwsList);
    for (Iterator<PsiMethod> iterator = myMethodsToBeChanged.iterator(); iterator.hasNext();) {
      PsiMethod method = iterator.next();
      replaceThrowsList(method, throwsList);
    }
  }

  private void replaceThrowsList(PsiMethod method, PsiReferenceList throwsList) throws IncorrectOperationException {
    PsiReferenceList methodThrowsList = (PsiReferenceList)method.getThrowsList().replace(throwsList);
    methodThrowsList = (PsiReferenceList)myManager.getCodeStyleManager().shortenClassReferences(methodThrowsList);
    myManager.getCodeStyleManager().reformatRange(method, method.getParameterList().getTextRange().getEndOffset(),
                                                  methodThrowsList.getTextRange().getEndOffset());
  }

  private PsiClassType[] filterUnhandledExceptions(PsiClassType[] exceptions, PsiElement place) {
    List<PsiClassType> result = new ArrayList<PsiClassType>();
    for (int i = 0; i < exceptions.length; i++) {
      PsiClassType exception = exceptions[i];
      if (!ExceptionUtil.isHandled(exception, place)) result.add(exception);
    }
    return result.toArray(new PsiClassType[result.size()]);
  }

  private boolean isCatchParameterRedundant (PsiClassType catchParamType, PsiType[] thrownTypes) {
    for (int j = 0; j < thrownTypes.length; j++) {
      PsiType exceptionType = thrownTypes[j];
      if (exceptionType.isConvertibleFrom(catchParamType)) return false;
    }
    return true;
  }

  private int getNonVarargCount(ChangeInfo changeInfo, PsiExpression[] args) {
    if (!changeInfo.wasVararg) return args.length;
    return changeInfo.getMethod().getParameterList().getParameters().length - 1;
  }

  private void fixActualArgumentsList(PsiExpressionList list, ChangeInfo changeInfo, boolean isSuperCall) throws IncorrectOperationException {
    final PsiElementFactory factory = list.getManager().getElementFactory();
    if (changeInfo.isParameterSetOrOrderChanged) {
      final PsiExpression[] args = list.getExpressions();
      final int nonVarargCount = getNonVarargCount(changeInfo, args);
      final int varargCount = args.length - nonVarargCount;

      final int newArgsLength;
      final int newNonVarargCount;
      if (changeInfo.retainsVarargs) {
        newNonVarargCount = changeInfo.newParms.length - 1;
        newArgsLength =  newNonVarargCount + varargCount;
      }
      else if (changeInfo.obtainsVarags) {
        newNonVarargCount = changeInfo.newParms.length - 1;
        newArgsLength = newNonVarargCount;
      }
      else {
        newNonVarargCount = changeInfo.newParms.length;
        newArgsLength = changeInfo.newParms.length;
      }
      final PsiExpression[] newArgs = new PsiExpression[newArgsLength];
      for (int i = 0; i < newNonVarargCount; i++) {
        final ParameterInfo info = changeInfo.newParms[i];
        final int index = info.oldParameterIndex;
        if (index >= 0) {
          newArgs[i] = args[index];
        } else {
          if (!isSuperCall) {
            newArgs[i] = createDefaultValue(factory, info, list);
          } else {
            newArgs[i] = factory.createExpressionFromText(info.getName(), list);
          }
        }
      }
      final int newVarargCount = newArgsLength - newNonVarargCount;
      LOG.assertTrue(newVarargCount == 0 || newVarargCount == varargCount);
      for (int i = 0; i < newVarargCount; i++) {
        newArgs[newNonVarargCount + i] = args[nonVarargCount + i];
      }
      ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newArgs), ExpressionList.INSTANCE, changeInfo.toRemoveParm);
    }
  }

  private PsiExpression createDefaultValue(final PsiElementFactory factory, final ParameterInfo info, final PsiExpressionList list)
    throws IncorrectOperationException {
    if (info.useAnySingleVariable) {
      final PsiResolveHelper resolveHelper = list.getManager().getResolveHelper();
      final PsiType type = info.getTypeWrapper().getType(myChangeInfo.getMethod());
      final VariablesProcessor processor = new VariablesProcessor(false) {
              protected boolean check(PsiVariable var, PsiSubstitutor substitutor) {
                if (var instanceof PsiField && !resolveHelper.isAccessible((PsiField)var, list, null)) return false;
                final PsiType varType = substitutor.substitute(var.getType());
                return type.isAssignableFrom(varType);
              }

              public boolean execute(PsiElement pe, PsiSubstitutor substitutor) {
                super.execute(pe, substitutor);
                return size() < 2;
              }
            };
      PsiScopesUtil.treeWalkUp(processor, list, null);
      if (processor.size() == 1) {
        final PsiVariable result = processor.getResult(0);
        return factory.createExpressionFromText(result.getName(), list);
      }
    }
    return factory.createExpressionFromText(info.defaultValue, list);
  }

  private void addParameterUsages(PsiParameter parameter,
                                       ArrayList<UsageInfo> results,
                                       ParameterInfo info) {
    PsiManager manager = parameter.getManager();
    PsiSearchHelper helper = manager.getSearchHelper();
    GlobalSearchScope projectScope = GlobalSearchScope.projectScope(manager.getProject());
    PsiReference[] parmRefs = helper.findReferences(parameter, projectScope, false);
    for (int k = 0; k < parmRefs.length; k++) {
      PsiElement parmRef = parmRefs[k].getElement();
      UsageInfo usageInfo = new MyParameterUsageInfo(parmRef, parameter.getName(), info.getName());
      results.add(usageInfo);
    }
  }

  private void processMethod(PsiMethod method, boolean isOriginal) throws IncorrectOperationException {
    PsiElementFactory factory = method.getManager().getElementFactory();

    if (myChangeInfo.isVisibilityChanged) {
      PsiModifierList modifierList = method.getModifierList();
      final String highestVisibility =
              (isOriginal ? myNewVisibility :
              VisibilityUtil.getHighestVisibility(myNewVisibility,
                      VisibilityUtil.getVisibilityModifier(modifierList)));
      RefactoringUtil.setVisibility(modifierList, highestVisibility);
    }

    final PsiSubstitutor substitutor = calculateSubstitutor(method);

    if (myChangeInfo.isNameChanged) {
      final EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
      if (role instanceof EjbImplMethodRole && myChangeInfo.ejbRole instanceof EjbDeclMethodRole) {
        EjbDeclMethodRole declRole = (EjbDeclMethodRole) myChangeInfo.ejbRole;

        String newName = myChangeInfo.newName;
        final PsiMethod[] oldImpl = declRole.suggestImplementations();
        for (int i = 0; i < oldImpl.length; i++) {
          PsiMethod oldMethod = oldImpl[i];
          if (oldMethod.getName().equals(method.getName())) {
            PsiMethod newDeclMethod = (PsiMethod) method.copy();
            newDeclMethod.getNameIdentifier().replace(myChangeInfo.newNameIdentifier);
            EjbDeclMethodRole newDeclRole = new EjbDeclMethodRole(newDeclMethod, declRole.getEjb(), declRole.getType());

            newName = newDeclRole.suggestImplementations()[i].getName();
            break;
          }
        }
        method.getNameIdentifier().replace(factory.createIdentifier(newName));
      } else {
        method.getNameIdentifier().replace(myChangeInfo.newNameIdentifier);
      }
    }

    if (myChangeInfo.isReturnTypeChanged) {
      //TODO : normalize brackets!

      final PsiType returnType = substitutor.substitute(myChangeInfo.newTypeElement);
      method.getReturnTypeElement().replace(factory.createTypeElement(returnType));
    }

    PsiParameterList list = method.getParameterList();
    PsiParameter[] parameters = list.getParameters();

    PsiParameter[] newParms = new PsiParameter[myChangeInfo.newParms.length];
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfo info = myChangeInfo.newParms[i];
      int index = info.oldParameterIndex;
      if (index >= 0) {
        PsiParameter parameter = parameters[index];
        newParms[i] = parameter;

        String oldName = myChangeInfo.oldParameterNames[index];
        if (!oldName.equals(info.getName()) && oldName.equals(parameter.getName())) {
          PsiIdentifier newIdentifier = factory.createIdentifier(info.getName());
          parameter.getNameIdentifier().replace(newIdentifier);
        }

        String oldType = myChangeInfo.oldParameterTypes[index];
        if (!oldType.equals(info.getTypeText())) {
          parameter.normalizeDeclaration();
          PsiType newType = substitutor.substitute(info.createType(myChangeInfo.getMethod().getParameterList()));

          parameter.getTypeElement().replace(factory.createTypeElement(newType));
        }
      } else {
        newParms[i] = createNewParameter(info, substitutor);
//        newParms[i] = factory.createParameterFromText(info.type + " " + info.name, myResolutionContext);
//        newParms[i].getTypeElement().replace(getTypeFromName(type));
      }
    }

    List<FieldConflictsResolver> conflictResolvers = new ArrayList<FieldConflictsResolver>();
    final ParameterInfo[] newParmsInfo = myChangeInfo.newParms;
    for (int i = 0; i < newParmsInfo.length; i++) {
      ParameterInfo parameterInfo = newParmsInfo[i];
      conflictResolvers.add(new FieldConflictsResolver(parameterInfo.getName(), method.getBody()));
    }
    ChangeSignatureUtil.synchronizeList(list, Arrays.asList(newParms), ParameterList.INSTANCE, myChangeInfo.toRemoveParm);
    for (int i = 0; i < conflictResolvers.size(); i++) {
      FieldConflictsResolver fieldConflictsResolver = conflictResolvers.get(i);
      fieldConflictsResolver.fix();
    }
  }

  private PsiSubstitutor calculateSubstitutor(PsiMethod method) {
    PsiSubstitutor substitutor;
    if (method.getManager().areElementsEquivalent(method, myChangeInfo.getMethod())) {
      substitutor = PsiSubstitutor.EMPTY;
    } else {
      final PsiClass sourceClass = myChangeInfo.getMethod().getContainingClass();
      final PsiClass containingClass = method.getContainingClass();
      if(sourceClass != null && containingClass != null && InheritanceUtil.isInheritorOrSelf(containingClass, sourceClass, true)) {
        final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(sourceClass, containingClass, PsiSubstitutor.EMPTY);
        final MethodSignature superMethodSignature = myChangeInfo.getMethod().getSignature(superClassSubstitutor);
        final MethodSignature methodSignature = method.getSignature(PsiSubstitutor.EMPTY);
        final PsiSubstitutor superMethodSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
        if (superMethodSubstitutor != null) {
          substitutor = superMethodSubstitutor;
        }
        else {
          substitutor = superClassSubstitutor;
        }
      } else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return substitutor;
  }

  private static void processParameterUsage(PsiReferenceExpression ref, String oldName, String newName)
          throws IncorrectOperationException {

    PsiElement last = ref.getReferenceNameElement();
    if (last instanceof PsiIdentifier && last.getText().equals(oldName)) {
      PsiElementFactory factory = ref.getManager().getElementFactory();
      PsiIdentifier newNameIdentifier = factory.createIdentifier(newName);
      last.replace(newNameIdentifier);
    }
  }

  private static class MyParameterUsageInfo extends UsageInfo {
    final String oldParameterName;
    final String newParameterName;

    public MyParameterUsageInfo(PsiElement element, String oldParameterName, String newParameterName) {
      super(element);
      this.oldParameterName = oldParameterName;
      this.newParameterName = newParameterName;
    }
  }

  public static class EjbUsageInfo extends UsageInfo {
    public EjbUsageInfo(PsiMethod implementation) {
      super(implementation);
    }
  }

  public static PsiElement normalizeResolutionContext(PsiElement resolutionContext) {
    PsiElement result = PsiTreeUtil.getParentOfType(resolutionContext, NORMALIZED_RESOLUTION_CONTEXT_CLASSES, false);
    if (result != null) {
      return result;
    } else {
      return resolutionContext;
    }
  }

  private static class RenamedParameterCollidesWithLocalUsageInfo extends UnresolvableCollisionUsageInfo {
    private final PsiElement myCollidingElement;
    private final PsiMethod myMethod;

    public RenamedParameterCollidesWithLocalUsageInfo(PsiParameter parameter, PsiElement collidingElement, PsiMethod method) {
      super(parameter, collidingElement);
      myCollidingElement = collidingElement;
      myMethod = method;
    }

    public String getDescription() {
      StringBuffer buffer = new StringBuffer();
      buffer.append("There is already a ");
      buffer.append(ConflictsUtil.getDescription(myCollidingElement, true));
      buffer.append(" in the ");
      buffer.append(ConflictsUtil.getDescription(myMethod, true));
      buffer.append(". It will conflict with the renamed parameter.");
      return buffer.toString();
    }
  }

  private void fixJavadocsForChangedMethod(PsiMethod method) throws IncorrectOperationException {
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final ParameterInfo[] newParms = myChangeInfo.newParms;
    LOG.assertTrue(parameters.length == newParms.length);
    final Set<PsiParameter> newParameters = new HashSet<PsiParameter>();
    for (int i = 0; i < newParms.length; i++) {
      ParameterInfo newParm = newParms[i];
      if (newParm.oldParameterIndex < 0) {
        newParameters.add(parameters[i]);
      }
    }
    RefactoringUtil.fixJavadocsForParams(method, newParameters);
  }

  private static class ExpressionList implements ChangeSignatureUtil.ChildrenGenerator<PsiExpressionList, PsiExpression> {
    public static final ExpressionList INSTANCE = new ExpressionList();
    private ExpressionList() {}
    public List<PsiExpression> getChildren(PsiExpressionList psiExpressionList) {
      return Arrays.asList(psiExpressionList.getExpressions());
    }
  }

  private static class ParameterList implements ChangeSignatureUtil.ChildrenGenerator<PsiParameterList, PsiParameter> {
    public static final ParameterList INSTANCE = new ParameterList();
    private ParameterList() {}
    public List<PsiParameter> getChildren(PsiParameterList psiParameterList) {
      return Arrays.asList(psiParameterList.getParameters());
    }
  }

}
