/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Oct 21, 2001
 * Time: 4:29:19 PM
 * To change template for new class use
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.reference;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.j2ee.J2EERolesUtil;
import com.intellij.j2ee.ejb.role.EjbClassRole;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiFormatUtil;

import java.util.*;

public class RefClass extends RefElement {
  private static final int IS_ANONYMOUS_MASK = 0x10000;
  private static final int IS_INTERFACE_MASK = 0x20000;
  private static final int IS_UTILITY_MASK   = 0x40000;
  private static final int IS_ABSTRACT_MASK  = 0x80000;
  private static final int IS_EJB_MASK       = 0x100000;
  private static final int IS_APPLET_MASK    = 0x200000;
  private static final int IS_SERVLET_MASK   = 0x400000;
  private static final int IS_TESTCASE_MASK  = 0x800000;
  private static final int IS_LOCAL_MASK     = 0x1000000;

  private final HashSet<RefClass> myBases;
  private final HashSet<RefClass> mySubClasses;
  private final ArrayList<RefMethod> myConstructors;
  private RefMethod myDefaultConstructor;
  private final ArrayList<RefMethod> myOverridingMethods;
  private final HashSet<RefElement> myInTypeReferences;
  private final HashSet<RefElement> myInstanceReferences;
  private ArrayList<RefElement> myClassExporters;

  public RefClass(PsiClass psiClass, RefManager manager) {
    super(psiClass, manager);

    myConstructors = new ArrayList<RefMethod>(1);
    mySubClasses = new HashSet<RefClass>(0);
    myBases = new HashSet<RefClass>(0);
    myOverridingMethods = new ArrayList<RefMethod>(2);
    myInTypeReferences = new HashSet<RefElement>(0);
    myInstanceReferences = new HashSet<RefElement>(0);
    myDefaultConstructor = null;

    PsiElement psiParent = psiClass.getParent();
    if (psiParent instanceof PsiFile) {
      PsiJavaFile psiFile = (PsiJavaFile) psiParent;
      String packageName = psiFile.getPackageName();
      if (!"".equals(packageName)) {
        manager.getPackage(packageName).add(this);
      } else {
        manager.getRefProject().getDefaultPackage().add(this);
      }

      setCanBeStatic(false);
    } else {
      while (!(psiParent instanceof PsiClass || psiParent instanceof PsiMethod || psiParent instanceof PsiField)) {
        psiParent = psiParent.getParent();
      }
        RefElement refParent = manager.getReference(psiParent);
      refParent.add(this);

      if (!(getOwner().getOwner() instanceof RefPackage)) {
        setCanBeStatic(false);
      }
    }

    setAbstract(psiClass.hasModifierProperty(PsiModifier.ABSTRACT));

    setAnonymous(psiClass instanceof PsiAnonymousClass);
    setIsLocal(!(isAnonymous() || psiParent instanceof PsiClass || psiParent instanceof PsiFile));
    setInterface(psiClass.isInterface());

    if (isAbstract() || isAnonymous() || isInterface()) {
      setCanBeFinal(false);
    }

    initializeSuperReferences(psiClass);

    PsiMethod[] psiMethods = psiClass.getMethods();
    PsiField[] psiFields = psiClass.getFields();

    setUtilityClass(psiMethods.length > 0 || psiFields.length > 0);

    HashSet<PsiField> allFields = new HashSet<PsiField>();

    for (int i = 0; i < psiFields.length; i++) {
      PsiField psiField = psiFields[i];
        getRefManager().getFieldReference(this, psiField);
      allFields.add(psiField);
    }

    for (int i = 0; i < psiMethods.length; i++) {
      PsiMethod psiMethod = psiMethods[i];
        RefMethod refMethod = getRefManager().getMethodReference(this, psiMethod);

      if (refMethod != null) {
        if (psiMethod.isConstructor()) {
          if (psiMethod.getParameterList().getParameters().length > 0 ||
              !psiMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
            setUtilityClass(false);
          }

          addConstructor(refMethod);
          if (psiMethod.getParameterList().getParameters().length == 0) {
            setDefaultConstructor(refMethod);
          }
        } else {
          if (!psiMethod.hasModifierProperty(PsiModifier.STATIC)) {
            setUtilityClass(false);
          }
        }
      }
    }

    if (myConstructors.size() == 0 && !isInterface() && !isAnonymous()) {
      RefImplicitConstructor refImplicitConstructor = new RefImplicitConstructor(this);
      setDefaultConstructor(refImplicitConstructor);
      addConstructor(refImplicitConstructor);
    }

    if (isInterface()) {
      for (int i = 0; i < psiFields.length && isUtilityClass(); i++) {
        PsiField psiField = psiFields[i];
        if (!psiField.hasModifierProperty(PsiModifier.STATIC)) {
          setUtilityClass(false);
        }
      }

      setCanBeStatic(false);
    }

    if (isAnonymous()) {
      setCanBeStatic(false);
    }

    setApplet(manager.getApplet() != null && psiClass.isInheritor(manager.getApplet(), true));
    if (!isApplet()) setServlet(manager.getServlet() != null && psiClass.isInheritor(manager.getServlet(), true));
    if (!isApplet() && !isServlet()) {
      setTestCase(JUnitUtil.isTestCaseClass(psiClass));
      for (Iterator<RefClass> iterator = getBaseClasses().iterator(); iterator.hasNext();) {
        RefClass refBase = iterator.next();
        refBase.setTestCase(true);
      }
    }
  }

  private void initializeSuperReferences(PsiClass psiClass) {
    if (!isSelfInheritor(psiClass)) {
      PsiClass[] supers = psiClass.getSupers();
      for (int i = 0; i < supers.length; i++) {
        PsiClass psiSuperClass = supers[i];
        if (RefUtil.belongsToScope(psiSuperClass, getRefManager())) {
            RefClass refClass = (RefClass) getRefManager().getReference(psiSuperClass);
          if (refClass != null) {
            myBases.add(refClass);
            refClass.markOverriden(this);
          }
        }
      }
    }
  }

  private static boolean isSelfInheritor(PsiClass psiClass) {
    ArrayList<PsiClass> visited = new ArrayList<PsiClass>();
    return isSelfInheritor(psiClass, visited);
  }

  private static boolean isSelfInheritor(PsiClass psiClass, ArrayList<PsiClass> visited) {
    if (visited.contains(psiClass)) return true;

    visited.add(psiClass);
    PsiClass[] supers = psiClass.getSupers();
    for (int i = 0; i < supers.length; i++) {
      PsiClass aSuper = supers[i];
      if (isSelfInheritor(aSuper, visited)) return true;
    }
    visited.remove(psiClass);

    return false;
  }

  private void setDefaultConstructor(RefMethod defaultConstructor) {
    if (defaultConstructor != null) {
      for (Iterator<RefClass> iterator = getBaseClasses().iterator(); iterator.hasNext();) {
        RefClass superClass = iterator.next();
        RefMethod superDefaultConstructor = superClass.getDefaultConstructor();

        if (superDefaultConstructor != null) {
          superDefaultConstructor.addInReference(defaultConstructor);
          defaultConstructor.addOutReference(superDefaultConstructor);
        }
      }
    }

    myDefaultConstructor = defaultConstructor;
  }

  private void markOverriden(RefClass subClass) {
    mySubClasses.add(subClass);
    setCanBeFinal(false);
  }

  public void buildReferences() {
    PsiClass psiClass = (PsiClass) getElement();

    if (psiClass != null) {
      final PsiClassInitializer[] initializers = psiClass.getInitializers();
      for (int i = 0; i < initializers.length; i++) {
        PsiClassInitializer classInitializer = initializers[i];
        RefUtil.addReferences(psiClass, this, classInitializer.getBody());
      }

      PsiMethod[] psiMethods = psiClass.getMethods();
      PsiField[] psiFields = psiClass.getFields();

      HashSet<PsiField> allFields = new HashSet<PsiField>();

      for (int i = 0; i < psiFields.length; i++) {
        PsiField psiField = psiFields[i];
          getRefManager().getFieldReference(this, psiField);
        allFields.add(psiField);
      }

      ArrayList<PsiVariable> instanceInitializerInitializedFields = new ArrayList<PsiVariable>();
      boolean hasInitializers = false;
      for (int i = 0; i < initializers.length; i++) {
        PsiClassInitializer initializer = initializers[i];
        PsiCodeBlock body = initializer.getBody();
        if (body != null) {
          hasInitializers = true;
          ControlFlowAnalyzer analyzer = new ControlFlowAnalyzer(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
          ControlFlow flow;
          try {
            flow = analyzer.buildControlFlow();
          }
          catch (AnalysisCanceledException e) {
            flow = ControlFlow.EMPTY;
          }
          PsiVariable[] ssaVariables = ControlFlowUtil.getSSAVariables(flow, false);
          PsiVariable[] writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize());
          for (int j = 0; j < ssaVariables.length; j++) {
            PsiVariable psiVariable = writtenVariables[j];
            if (allFields.contains(psiVariable)) {
              if (instanceInitializerInitializedFields.contains(psiVariable)) {
                allFields.remove(psiVariable);
                instanceInitializerInitializedFields.remove(psiVariable);
              } else {
                instanceInitializerInitializedFields.add(psiVariable);
              }
            }
          }
          for (int j = 0; j < writtenVariables.length; j++) {
            PsiVariable psiVariable = writtenVariables[j];
            if (!instanceInitializerInitializedFields.contains(psiVariable)) {
              allFields.remove(psiVariable);
            }
          }
        }
      }

      for (int i = 0; i < psiMethods.length; i++) {
        PsiMethod psiMethod = psiMethods[i];
          RefMethod refMethod = getRefManager().getMethodReference(this, psiMethod);

        if (refMethod != null) {
          if (psiMethod.isConstructor()) {
            PsiCodeBlock body = psiMethod.getBody();
            if (body != null) {
              hasInitializers = true;
              ControlFlowAnalyzer analyzer = new ControlFlowAnalyzer(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance());
              ControlFlow flow;
              try {
                flow = analyzer.buildControlFlow();
              }
              catch (AnalysisCanceledException e) {
                flow = ControlFlow.EMPTY;
              }

              PsiVariable[] writtenVariables = ControlFlowUtil.getWrittenVariables(flow, 0, flow.getSize());
              for (int j = 0; j < writtenVariables.length; j++) {
                PsiVariable psiVariable = writtenVariables[j];
                if (instanceInitializerInitializedFields.contains(psiVariable)) {
                  allFields.remove(psiVariable);
                  instanceInitializerInitializedFields.remove(psiVariable);
                }
              }


              List<PsiMethod> redirectedConstructors = HighlightControlFlowUtil.getRedirectedConstructors(psiMethod);
              if ((redirectedConstructors == null || redirectedConstructors.isEmpty())) {
                PsiVariable[] ssaVariables = ControlFlowUtil.getSSAVariables(flow, false);
                ArrayList<PsiVariable> good = new ArrayList<PsiVariable>(Arrays.asList(ssaVariables));
                good.addAll(instanceInitializerInitializedFields);
                allFields.retainAll(good);
              } else {
                allFields.removeAll(Arrays.asList(writtenVariables));
              }
            }
          }
        }
      }

      for (int i = 0; i < psiFields.length; i++) {
        PsiField psiField = psiFields[i];
        if ((!hasInitializers || !allFields.contains(psiField)) && psiField.getInitializer() == null) {
            RefField refField = (RefField) getRefManager().getReference(psiField);
          refField.setCanBeFinal(false);
        }
      }

      EjbClassRole role = J2EERolesUtil.getEjbRole(psiClass);
      if (role != null) {
        setEjb(true);
        setCanBeStatic(false);
        setCanBeFinal(false);
        if (role.getType() == EjbClassRole.EJB_CLASS_ROLE_HOME_INTERFACE ||
            role.getType() == EjbClassRole.EJB_CLASS_ROLE_REMOTE_INTERFACE) {
          PsiClassType remoteExceptionType = psiClass.getManager().getElementFactory().createTypeByFQClassName("java.rmi.RemoteException", psiClass.getResolveScope());
          PsiMethod[] allMethods = psiClass.getAllMethods();
          for (int i = 0; i < allMethods.length; i++) {
            PsiMethod psiMethod = allMethods[i];
            if (!RefUtil.belongsToScope(psiMethod, getRefManager())) continue;
              RefMethod refMethod = getRefManager().getMethodReference(this, psiMethod);
            if (refMethod != null) {
              refMethod.updateThrowsList(remoteExceptionType);
            }
          }
        }
      }
    }
  }

  public void accept(RefVisitor visitor) {
    visitor.visitClass(this);
  }

  public HashSet<RefClass> getBaseClasses() {
    return myBases;
  }

  public HashSet<RefClass> getSubClasses() {
    return mySubClasses;
  }

  public ArrayList<RefMethod> getConstructors() {
    return myConstructors;
  }

  public Set<RefElement> getInTypeReferences() {
    return myInTypeReferences;
  }

  public void addTypeReference(RefElement from) {
    if (from != null) {
      myInTypeReferences.add(from);
    }
  }

  public Set<RefElement> getInstanceReferences() {
    return myInstanceReferences;
  }

  public void addInstanceReference(RefElement from) {
    myInstanceReferences.add(from);
  }

  public RefMethod getDefaultConstructor() {
    return myDefaultConstructor;
  }

  private void addConstructor(RefMethod refConstructor) {
    myConstructors.add(refConstructor);
  }

  public void addLibraryOverrideMethod(RefMethod refMethod) {
    myOverridingMethods.add(refMethod);
  }

  public ArrayList<RefMethod> getLibraryMethods() {
    return myOverridingMethods;
  }

  public boolean isAnonymous() {
    return checkFlag(IS_ANONYMOUS_MASK);
  }

  public boolean isInterface() {
    return checkFlag(IS_INTERFACE_MASK);
  }

  public boolean isSuspicious() {
    if (isUtilityClass() && getOutReferences().isEmpty()) return false;
    return super.isSuspicious();
  }

  public boolean isUtilityClass() {
    return checkFlag(IS_UTILITY_MASK);
  }

  public String getExternalName() {
    final String[] result = new String[1];
    final Runnable runnable = new Runnable() {
      public void run() {
        PsiClass psiClass = (PsiClass) getElement();
        result[0] = PsiFormatUtil.formatClass(psiClass, PsiFormatUtil.SHOW_NAME |
          PsiFormatUtil.SHOW_FQ_NAME);
      }
    };

    ApplicationManager.getApplication().runReadAction(runnable);

    return result[0];
  }

  public static RefClass classFromExternalName(RefManager manager, String externalName) {
    PsiClass psiClass = PsiManager.getInstance(manager.getProject()).findClass(externalName);
    RefClass refClass = null;

    if (psiClass != null) {
        refClass = (RefClass) manager.getReference(psiClass);
    }

    return refClass;
  }

  public void referenceRemoved() {
    super.referenceRemoved();

    for (Iterator<RefClass> iterator = getSubClasses().iterator(); iterator.hasNext();) {
      RefClass subClass = iterator.next();
      subClass.removeBase(this);
    }

    for (Iterator<RefClass> iterator = getBaseClasses().iterator(); iterator.hasNext();) {
      RefClass superClass = iterator.next();
      superClass.getSubClasses().remove(this);
    }
  }

  private void removeBase(RefClass superClass) {
    getBaseClasses().remove(superClass);
  }

  protected void methodRemoved(RefMethod method) {
    getConstructors().remove(method);
    getLibraryMethods().remove(method);

    if (getDefaultConstructor() == method) {
      setDefaultConstructor(null);
    }
  }

  public boolean isAbstract() {
    return checkFlag(IS_ABSTRACT_MASK);
  }

  public boolean isEjb() {
    return checkFlag(IS_EJB_MASK);
  }

  public boolean isApplet() {
    return checkFlag(IS_APPLET_MASK);
  }

  public boolean isServlet() {
    return checkFlag(IS_SERVLET_MASK);
  }

  public boolean isTestCase() {
    return checkFlag(IS_TESTCASE_MASK);
  }

  public boolean isLocalClass() {
    return checkFlag(IS_LOCAL_MASK);
  }

  public boolean isCanBeStatic() {
    for (Iterator<RefClass> iterator = getBaseClasses().iterator(); iterator.hasNext();) {
      RefClass refBase = iterator.next();
      if (!refBase.isCanBeStatic()) {
        setCanBeStatic(false);
        return false;
      }
    }

    return super.isCanBeStatic();
  }

  public boolean isReferenced() {
    if (super.isReferenced()) return true;

    if (isInterface() || isAbstract()) {
      if (getSubClasses().size() > 0) return true;
    }

    return false;
  }

  public boolean hasSuspiciousCallers() {
    if (super.hasSuspiciousCallers()) return true;

    if (isInterface() || isAbstract()) {
      if (getSubClasses().size() > 0) return true;
    }

    return false;
  }

  public void addClassExporter(RefElement exporter) {
    if (myClassExporters == null) myClassExporters = new ArrayList<RefElement>(1);
    if (myClassExporters.contains(exporter)) return;
    myClassExporters.add(exporter);
  }

  public ArrayList<RefElement> getClassExporters() {
    return myClassExporters;
  }

  private void setAnonymous(boolean anonymous) {
    setFlag(anonymous, IS_ANONYMOUS_MASK);
  }

  private void setInterface(boolean anInterface) {
    setFlag(anInterface, IS_INTERFACE_MASK);
  }

  private void setUtilityClass(boolean utilityClass) {
    setFlag(utilityClass, IS_UTILITY_MASK);
  }

  private void setAbstract(boolean anAbstract) {
    setFlag(anAbstract, IS_ABSTRACT_MASK);
  }

  private void setEjb(boolean ejb) {
    setFlag(ejb, IS_EJB_MASK);
  }

  private void setApplet(boolean applet) {
    setFlag(applet, IS_APPLET_MASK);
  }

  private void setServlet(boolean servlet) {
    setFlag(servlet, IS_SERVLET_MASK);
  }

  private void setTestCase(boolean testCase) {
    setFlag(testCase, IS_TESTCASE_MASK);
  }

  public void setIsLocal(boolean isLocal) {
    setFlag(isLocal, IS_LOCAL_MASK);
  }
}

