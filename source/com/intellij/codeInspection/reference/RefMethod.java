/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:30 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.EjbUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.j2ee.ejb.role.EjbDeclMethodRole;
import com.intellij.j2ee.ejb.role.EjbImplMethodRole;
import com.intellij.j2ee.ejb.role.EjbMethodRole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiSuperMethodUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class RefMethod extends RefElement {
  private static final int IS_APPMAIN_MASK = 0x10000;
  private static final int IS_LIBRARY_OVERRIDE_MASK = 0x20000;
  private static final int IS_CONSTRUCTOR_MASK = 0x40000;
  private static final int IS_ABSTRACT_MASK = 0x80000;
  private static final int IS_BODY_EMPTY_MASK = 0x100000;
  private static final int IS_ONLY_CALLS_SUPER_MASK = 0x200000;
  private static final int IS_RETURN_VALUE_USED_MASK = 0x400000;
  private static final int IS_EJB_DECLARATION_MASK = 0x800000;
  private static final int IS_EJB_IMPLEMENTATION_MASK = 0x1000000;
  private static final int IS_OVERRIDES_DEPRECATED_MASK = 0x2000000;

  private static final String RETURN_VALUE_UNDEFINED = "#";

  private final ArrayList<RefMethod> mySuperMethods;
  private final ArrayList<RefMethod> myDerivedMethods;
  private ArrayList<PsiClassType> myUnThrownExceptions;

  private final RefParameter[] myParameters;
  private String myReturnValueTemplate;

  public RefMethod(PsiMethod method, RefManager manager) {
      this((RefClass) manager.getReference(method.getContainingClass()), method,  manager);
  }

  public RefMethod(RefClass ownerClass, PsiMethod method, RefManager manager) {
    super(method, manager);

    ownerClass.add(this);

    myDerivedMethods = new ArrayList<RefMethod>(0);
    setConstructor(method.isConstructor());
    setFlag(method.getReturnType() == null || PsiType.VOID == method.getReturnType(), IS_RETURN_VALUE_USED_MASK);

    if (!isReturnValueUsed()) {
      myReturnValueTemplate = RETURN_VALUE_UNDEFINED;
    }

    if (isConstructor()) {
      addReference(getOwnerClass(), getOwnerClass().getElement(), method, false, true, null);
      setCanBeStatic(false);
    }

    if (getOwnerClass().isInterface() || !(getOwnerClass().getOwner() instanceof RefPackage)) {
      setCanBeStatic(false);
    }

    if (getOwnerClass().isInterface()) {
      setAbstract(false);
    } else {
      setAbstract(method.hasModifierProperty(PsiModifier.ABSTRACT));
    }


    setAppMain(RefUtil.isAppMain(method, this));
    setLibraryOverride(method.hasModifierProperty(PsiModifier.NATIVE));

    mySuperMethods = new ArrayList<RefMethod>(0);

    initializeSuperMethods(method);
    if (isLibraryOverride()) {
      getOwnerClass().addLibraryOverrideMethod(this);
      setCanBeStatic(false);
    }

    if (getSuperMethods().size() > 0 || isAbstract()) {
      setCanBeStatic(false);
    }

    if (getOwnerClass().isTestCase() && method.getName().startsWith("test")) {
      setCanBeStatic(false);
    }

    PsiParameter[] paramList = method.getParameterList().getParameters();
    myParameters = new RefParameter[paramList.length];
    for (int i = 0; i < paramList.length; i++) {
      PsiParameter parameter = paramList[i];
        myParameters[i] = getRefManager().getParameterReference(parameter, i);
    }

    if (isConstructor() || isAbstract() || isStatic() || getAccessModifier() == PsiModifier.PRIVATE || ownerClass.isAnonymous() || ownerClass.isInterface()) {
      setCanBeFinal(false);
    }

    if (method.hasModifierProperty(PsiModifier.NATIVE)) {
      updateReturnValueTemplate(null);
      updateThrowsList(null);
    }

    if (getAccessModifier() == PsiModifier.PRIVATE && !(getOwnerClass().getOwner() instanceof RefElement)) {
      setCanBeFinal(false);
    }

    collectUncaughtExceptions(method);
  }

  private void checkForSuperCall(PsiMethod method) {
    if (isConstructor()) {
      boolean isBaseExplicitlyCalled = false;
      PsiCodeBlock body = method.getBody();
      if (body == null) return;
      PsiStatement[] statements = body.getStatements();
      if (statements.length > 0) {
        PsiStatement first = statements[0];
        if (first instanceof PsiExpressionStatement) {
          PsiExpression firstExpression = ((PsiExpressionStatement) first).getExpression();
          if (firstExpression instanceof PsiMethodCallExpression) {
            PsiExpression qualifierExpression = ((PsiMethodCallExpression)firstExpression).getMethodExpression().getQualifierExpression();
            if (qualifierExpression instanceof PsiReferenceExpression) {
              String text = qualifierExpression.getText();
              if ("super".equals(text) || text.equals(this)) {
                isBaseExplicitlyCalled = true;
              }
            }
          }
        }
      }

      if (!isBaseExplicitlyCalled) {
        for (Iterator<RefClass> iterator = getOwnerClass().getBaseClasses().iterator(); iterator.hasNext();) {
          RefClass superClass = iterator.next();
          RefMethod superDefaultConstructor = superClass.getDefaultConstructor();

          if (superDefaultConstructor != null) {
            superDefaultConstructor.addInReference(this);
            addOutReference(superDefaultConstructor);
          }
        }
      }
    }
  }

  // To be used only from RefImplicitConstructor.
  protected RefMethod(String name, RefClass ownerClass) {
    super(name, ownerClass);

    ownerClass.add(this);

    myDerivedMethods = new ArrayList<RefMethod>(0);
    mySuperMethods = new ArrayList<RefMethod>(0);

    addOutReference(getOwnerClass());
    getOwnerClass().addInReference(this);

    setConstructor(true);

    myParameters = new RefParameter[0];
  }

  public Collection<RefMethod> getSuperMethods() {
    return mySuperMethods;
  }

  public Collection<RefMethod> getDerivedMethods() {
    return myDerivedMethods;
  }

  public boolean isBodyEmpty() {
    return checkFlag(IS_BODY_EMPTY_MASK);
  }

  public boolean isOnlyCallsSuper() {
    return checkFlag(IS_ONLY_CALLS_SUPER_MASK);
  }

  public boolean hasBody() {
    return !isAbstract() && !getOwnerClass().isInterface() || !isBodyEmpty();
  }

  private void initializeSuperMethods(PsiMethod method) {
    PsiMethod[] superMethods = PsiSuperMethodUtil.findSuperMethods(method);
    for (int i = 0; i < superMethods.length; i++) {
      PsiMethod psiSuperMethod = superMethods[i];
      if (RefUtil.isDeprecated(psiSuperMethod) && !psiSuperMethod.hasModifierProperty(PsiModifier.ABSTRACT)) setOverridesDeprecated(true);
      if (RefUtil.belongsToScope(psiSuperMethod, getRefManager())) {
          RefMethod refSuperMethod = (RefMethod) getRefManager().getReference(psiSuperMethod);
        if (refSuperMethod != null) {
          addSuperMethod(refSuperMethod);
          refSuperMethod.markExtended(this);
        }
      } else {
        setLibraryOverride(true);
      }
    }
  }

  private void addSuperMethod(RefMethod refSuperMethod) {
    if (!getSuperMethods().contains(refSuperMethod)) {
      getSuperMethods().add(refSuperMethod);
    }
  }

  public void addReference(RefElement refWhat, PsiElement psiWhat, PsiElement psiFrom, boolean forWriting, boolean forReading, PsiReferenceExpression expression) {
    if (refWhat instanceof RefParameter) {
      if (forWriting) {
        ((RefParameter)refWhat).parameterReferenced(true);
      }
      if (forReading) {
        ((RefParameter)refWhat).parameterReferenced(false);
      }
    } else {
      super.addReference(refWhat, psiWhat, psiFrom, forWriting, forReading, expression);
    }
  }

  private void markExtended(RefMethod method) {
    setCanBeStatic(false);
    setCanBeFinal(false);
    if (!myDerivedMethods.contains(method)) {
      myDerivedMethods.add(method);
    }
  }

  public RefParameter[] getParameters() {
    return myParameters;
  }

  public void buildReferences() {
    // Work on code block to find what we're referencing...
    PsiMethod method = (PsiMethod) getElement();
    if (method != null) {
      PsiCodeBlock body = method.getBody();
      RefUtil.addReferences(method, this, body);
      checkForSuperCall(method);
      setOnlyCallsSuper(RefUtil.isMethodOnlyCallsSuper(method));

      setBodyEmpty(isOnlyCallsSuper() || !isLibraryOverride() && (body == null || body.getStatements().length == 0));

      EjbClassRole classRole = J2EERolesUtil.getEjbRole(method.getContainingClass());
      if (classRole != null) {
        if (!getSuperMethods().isEmpty() || isLibraryOverride()) {
          setCanBeFinal(false);
        }

        EjbMethodRole role = J2EERolesUtil.getEjbRole(method);
        if (role != null) {
          setCanBeStatic(false);
          int roleType = role.getType();
          if (role instanceof EjbDeclMethodRole) {
            setEjbDeclaration(true);
            setCanBeFinal(false);

            if (roleType == EjbDeclMethodRole.EJB_METHOD_ROLE_FINDER_DECL ||
                roleType == EjbDeclMethodRole.EJB_METHOD_ROLE_CMP_SETTER_DECL ||
                roleType == EjbDeclMethodRole.EJB_METHOD_ROLE_CMR_SETTER_DECL ||
                roleType == EjbDeclMethodRole.EJB_METHOD_ROLE_CMP_GETTER_DECL ||
                roleType == EjbDeclMethodRole.EJB_METHOD_ROLE_CMR_GETTER_DECL) {
              for (int i = 0; i < myParameters.length; i++) {
                RefParameter refParameter = myParameters[i];
                refParameter.parameterReferenced(false);
                refParameter.parameterReferenced(true);
              }
            }
          } else if (role instanceof EjbImplMethodRole) {
            PsiMethod[] declarations = EjbUtil.findEjbDeclarations(method);
            if (declarations.length != 0) {
              for (int i = 0; i < declarations.length; i++) {
                PsiMethod psiDeclaration = declarations[i];
                if (RefUtil.belongsToScope(psiDeclaration, getRefManager())) {
                    RefMethod refDeclaration = (RefMethod) getRefManager().getReference(psiDeclaration);

                  if (refDeclaration != null) {
                    addSuperMethod(refDeclaration);
                    refDeclaration.markExtended(this);
                  } else {
                    setLibraryOverride(true);
                  }
                } else {
                  setLibraryOverride(true);
                }
              }
            }

            if (roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMP_GETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMP_SETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMR_GETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMR_SETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CREATE_IMPL) {
              setBodyEmpty(false);
            }

            if (roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMP_GETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMP_SETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMR_GETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_CMR_SETTER_IMPL ||
                roleType == EjbImplMethodRole.EJB_METHOD_ROLE_FINDER_IMPL) {
              for (int i = 0; i < myParameters.length; i++) {
                RefParameter refParameter = myParameters[i];
                refParameter.parameterReferenced(false);
                refParameter.parameterReferenced(true);
              }
            }

            setCanBeFinal(false);
            setEjbImplementation(true);
          }
        }
      }

      PsiType retType = method.getReturnType();
      if (retType != null) {
        PsiType psiType = retType;
        RefClass ownerClass = RefUtil.getOwnerClass(getRefManager(), method);

        if (ownerClass != null) {
          psiType = psiType.getDeepComponentType();

          if (psiType instanceof PsiClassType) {
            PsiClass psiClass = PsiUtil.resolveClassInType(psiType);
            if (psiClass != null && RefUtil.belongsToScope(psiClass, getRefManager())) {
                RefClass refClass = (RefClass) getRefManager().getReference(psiClass);
              if (refClass != null) {
                refClass.addTypeReference(ownerClass);
                refClass.addClassExporter(this);
              }
            }
          }
        }
      }

      RefParameter[] parameters = getParameters();
      for (int i = 0; i < parameters.length; i++) {
        RefParameter parameter = parameters[i];
        parameter.initializeFinalFlag();
      }
    }
  }

  private boolean isEjbException(String qualifiedName) {
    return "javax.ejb.CreateException".equals(qualifiedName) ||
           "java.rmi.RemoteException".equals(qualifiedName) ||
           "javax.ejb.FinderException".equals(qualifiedName) ||
           "javax.ejb.RemoveException".equals(qualifiedName);
  }

  private void collectUncaughtExceptions(PsiMethod method) {
    if (isLibraryOverride()) return;
    if (getOwnerClass().isTestCase() && method.getName().startsWith("test")) return;

    if (getSuperMethods().size() == 0) {
      PsiClassType[] throwsList = method.getThrowsList().getReferencedTypes();
      if (throwsList.length > 0) {
        EjbClassRole role = J2EERolesUtil.getEjbRole(method.getContainingClass());
        myUnThrownExceptions = new ArrayList<PsiClassType>(throwsList.length);
        for (int i = 0; i < throwsList.length; i++) {
          final PsiClassType type = throwsList[i];
          String qualifiedName = type.getCanonicalText();
          if (role != null && isEjbException(qualifiedName)) continue;
          myUnThrownExceptions.add(type);
        }
      }
    }

    PsiCodeBlock body = method.getBody();
    if (body == null) return;

    PsiClassType[] exceptionTypes = ExceptionUtil.collectUnhandledExceptions(method, body);
    if (exceptionTypes != null) {
      for (int i = 0; i < exceptionTypes.length; i++) {
        final PsiClassType exceptionType = exceptionTypes[i];
        updateThrowsList(exceptionType);
      }
    }
  }

  public void accept(RefVisitor visitor) {
    visitor.visitMethod(this);
  }

  public boolean isLibraryOverride() {
    if (checkFlag(IS_LIBRARY_OVERRIDE_MASK)) return true;
    for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
      RefMethod superMethod = iterator.next();
      if (superMethod.isLibraryOverride()) {
        setFlag(true, IS_LIBRARY_OVERRIDE_MASK);
        return true;
      }
    }

    return false;
  }

  public boolean isAppMain() {
    return checkFlag(IS_APPMAIN_MASK);
  }

  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  public boolean isEjbDeclaration() {
    return checkFlag(IS_EJB_DECLARATION_MASK);
  }

  public boolean isEjbImplementation() {
    return checkFlag(IS_EJB_IMPLEMENTATION_MASK);
  }

  public boolean isReferenced() {
    // Directly called from somewhere..
    for (Iterator<RefElement> iterator = getInReferences().iterator(); iterator.hasNext();) {
      RefElement refCaller = iterator.next();
      if (!getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    return isLibraryOverride();
  }

  public boolean hasSuspiciousCallers() {
    // Directly called from somewhere..
    for (Iterator<RefElement> iterator = getInReferences().iterator(); iterator.hasNext();) {
      RefElement refCaller = iterator.next();
      if (refCaller.isSuspicious() && !getDerivedMethods().contains(refCaller)) return true;
    }

    // Library override probably called from library code.
    if (isLibraryOverride()) return true;

    // Class isn't instantiated. Most probably we have problem with class, not method.
    if (!isStatic() && !isConstructor()) {
      if (getOwnerClass().isSuspicious()) return true;

      // Is an override. Probably called via reference to base class.
      for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        if (refSuper.isSuspicious()) return true;
      }
    }

    return false;
  }

  public boolean isConstructor() {
    return checkFlag(IS_CONSTRUCTOR_MASK);
  }

  public RefClass getOwnerClass() {
    return (RefClass) getOwner();
  }

  public String getName() {
    if (isValid()) {
      final String[] result = new String[1];
      final Runnable runnable = new Runnable() {
        public void run() {
          PsiMethod psiMethod = (PsiMethod) getElement();
          result[0] = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
            PsiFormatUtil.SHOW_PARAMETERS,
            PsiFormatUtil.SHOW_TYPE
          );
        }
      };

      ApplicationManager.getApplication().runReadAction(runnable);

      return result[0];
    } else {
      return super.getName();
    }
  }

  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        PsiMethod psiMethod = (PsiMethod) getElement();
        result[0] = PsiFormatUtil.formatMethod(psiMethod, PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME |
          PsiFormatUtil.SHOW_FQ_NAME |
          PsiFormatUtil.SHOW_TYPE |
          PsiFormatUtil.SHOW_CONTAINING_CLASS |
          PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_NAME |
          PsiFormatUtil.SHOW_TYPE
        );
      }
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  public static RefMethod methodFromExternalName(RefManager manager, String externalName) {
    RefMethod refMethod = null;

    int spaceIdx = externalName.indexOf(' ');
    int lastDotIdx = externalName.lastIndexOf('.');
    boolean notype = false;

    int parenIndex = externalName.indexOf('(');

    while (lastDotIdx > parenIndex) lastDotIdx = externalName.lastIndexOf('.', lastDotIdx - 1);

    if (spaceIdx < 0 || spaceIdx > lastDotIdx || spaceIdx > parenIndex) {
      notype = true;
    }

    String className = externalName.substring(notype ? 0 : spaceIdx + 1, lastDotIdx);
    String methodSignature = notype ? externalName.substring(lastDotIdx + 1)
                                    : externalName.substring(0, spaceIdx) + ' ' + externalName.substring(lastDotIdx + 1);

    if (RefClass.classFromExternalName(manager, className) == null) return null;
    try {
      PsiClass psiClass = PsiManager.getInstance(manager.getProject()).findClass(className);
      PsiElementFactory factory = psiClass.getManager().getElementFactory();
      PsiMethod patternMethod = factory.createMethodFromText(methodSignature, psiClass);
      PsiMethod psiMethod = psiClass.findMethodBySignature(patternMethod, false);

      if (psiMethod != null) {
          refMethod = (RefMethod) manager.getReference(psiMethod);
      }
    } catch (IncorrectOperationException e) {
      // Do nothing. Returning null is acceptable in this case.
      return null;
    }

    return refMethod;
  }

  public void referenceRemoved() {
    if (getOwnerClass() != null) {
      getOwnerClass().methodRemoved(this);
    }

    super.referenceRemoved();

    for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
      RefMethod superMethod = iterator.next();
      superMethod.getDerivedMethods().remove(this);
    }

    for (Iterator<RefMethod> iterator = getDerivedMethods().iterator(); iterator.hasNext();) {
      RefMethod subMethod = iterator.next();
      subMethod.getSuperMethods().remove(this);
    }

    ArrayList<RefElement> deletedRefs = new ArrayList<RefElement>();
    RefParameter[] parameters = getParameters();
    for (int i = 0; i < parameters.length; i++) {
      RefParameter parameter = parameters[i];
      RefUtil.removeRefElement(parameter, deletedRefs);
    }
  }

  public boolean isSuspicious() {
    if (isConstructor() && getAccessModifier() == PsiModifier.PRIVATE && getParameters().length == 0 && getOwnerClass().getConstructors().size() == 1) return false;
    return super.isSuspicious();
  }

  public void setReturnValueUsed(boolean value) {
    if (checkFlag(IS_RETURN_VALUE_USED_MASK) == value) return;
    setFlag(value, IS_RETURN_VALUE_USED_MASK);
    for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
      RefMethod refSuper = iterator.next();
      refSuper.setReturnValueUsed(value);
    }
  }

  public boolean isReturnValueUsed() {
    return checkFlag(IS_RETURN_VALUE_USED_MASK);
  }

  public void updateReturnValueTemplate(PsiExpression expression) {
    if (myReturnValueTemplate == null) return;

    if (getSuperMethods().size() > 0) {
      for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        refSuper.updateReturnValueTemplate(expression);
      }
    } else {
      String newTemplate = null;
      if (expression instanceof PsiLiteralExpression) {
        PsiLiteralExpression psiLiteralExpression = (PsiLiteralExpression) expression;
        newTemplate = psiLiteralExpression.getText();
      } else if (expression instanceof PsiReferenceExpression) {
        PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
        PsiElement resolved = referenceExpression.resolve();
        if (resolved instanceof PsiField) {
          PsiField psiField = (PsiField) resolved;
          if (psiField.hasModifierProperty(PsiModifier.STATIC) &&
              psiField.hasModifierProperty(PsiModifier.FINAL) &&
              RefUtil.compareAccess(RefUtil.getAccessModifier(psiField), getAccessModifier()) >= 0) {
            newTemplate = PsiFormatUtil.formatVariable(psiField, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_CONTAINING_CLASS | PsiFormatUtil.SHOW_FQ_NAME, PsiSubstitutor.EMPTY);
          }
        }
      } else if (RefUtil.isCallToSuperMethod(expression, (PsiMethod) getElement())) return;

      if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) {
        myReturnValueTemplate = newTemplate;
      } else if (!Comparing.equal(myReturnValueTemplate, newTemplate)) {
        myReturnValueTemplate = null;
      }
    }
  }

  public void updateParameterValues(PsiExpression[] args) {
    if (isLibraryOverride()) return;

    if (getSuperMethods().size() > 0) {
      for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        refSuper.updateParameterValues(args);
      }
    } else {
      if (myParameters.length == args.length) {
        for (int i = 0; i < myParameters.length; i++) {
          RefParameter refParameter = myParameters[i];
          refParameter.updateTemplateValue(args[i]);
        }
      }
    }
  }

  public String getReturnValueIfSame() {
    if (myReturnValueTemplate == RETURN_VALUE_UNDEFINED) return null;
    return myReturnValueTemplate;
  }

  public void updateThrowsList(PsiClassType exceptionType) {
    if (getSuperMethods().size() > 0) {
      for (Iterator<RefMethod> iterator = getSuperMethods().iterator(); iterator.hasNext();) {
        RefMethod refSuper = iterator.next();
        refSuper.updateThrowsList(exceptionType);
      }
    } else if (myUnThrownExceptions != null) {
      if (exceptionType == null) {
        myUnThrownExceptions = null;
        return;
      }

      PsiClassType[] arrayed = myUnThrownExceptions.toArray(new PsiClassType[myUnThrownExceptions.size()]);
      for (int i = arrayed.length - 1; i >= 0; i--) {
        PsiClassType classType = arrayed[i];
        if (classType.isAssignableFrom(exceptionType)) {
          myUnThrownExceptions.remove(i);
        }
      }

      if (myUnThrownExceptions.size() == 0) myUnThrownExceptions = null;
    }
  }

  public PsiClassType[] getUnThrownExceptions() {
    if (myUnThrownExceptions == null) return null;
    return myUnThrownExceptions.toArray(new PsiClassType[myUnThrownExceptions.size()]);
  }

  public boolean isOverridesDeprecated() {
    return checkFlag(IS_OVERRIDES_DEPRECATED_MASK);
  }

  private void setOverridesDeprecated(boolean overridesDeprecated) {
    setFlag(overridesDeprecated, IS_OVERRIDES_DEPRECATED_MASK);
  }

  public void setAccessModifier(String am) {
    super.setAccessModifier(am);
    if (am == PsiModifier.PRIVATE && getOwner() != null && !(getOwnerClass().getOwner() instanceof RefElement)) {
      setCanBeFinal(false);
    }
  }

  private void setLibraryOverride(boolean libraryOverride) {
    setFlag(libraryOverride, IS_LIBRARY_OVERRIDE_MASK);
  }

  private void setAppMain(boolean appMain) {
    setFlag(appMain, IS_APPMAIN_MASK);
  }

  private void setAbstract(boolean anAbstract) {
    setFlag(anAbstract, IS_ABSTRACT_MASK);
  }

  private void setBodyEmpty(boolean bodyEmpty) {
    setFlag(bodyEmpty, IS_BODY_EMPTY_MASK);
  }

  private void setOnlyCallsSuper(boolean onlyCallsSuper) {
    setFlag(onlyCallsSuper, IS_ONLY_CALLS_SUPER_MASK);
  }

  private void setEjbDeclaration(boolean ejbDeclaration) {
    setFlag(ejbDeclaration, IS_EJB_DECLARATION_MASK);
  }

  private void setEjbImplementation(boolean ejbImplementation) {
    setFlag(ejbImplementation, IS_EJB_IMPLEMENTATION_MASK);
  }

  private void setConstructor(boolean constructor) {
    setFlag(constructor, IS_CONSTRUCTOR_MASK);
  }
}
